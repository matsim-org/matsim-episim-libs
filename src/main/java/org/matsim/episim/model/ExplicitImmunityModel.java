package org.matsim.episim.model;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.Immunizable;
import org.matsim.episim.ImmunityConfigGroup;
import org.matsim.episim.ImmunityConfigGroup.SourceKind;
import org.matsim.episim.ImmunityConfigGroup.SourceParams;
import org.matsim.episim.ProtectionCurve;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Immunity from the curves of {@link ImmunityConfigGroup}, without antibodies and without hard-coded rules.
 *
 * <p>Every past infection and vaccination is a source of immunity. Each source carries one curve per target and
 * per strain that protection is asked against, on a time axis of days since that event. The curves of all events
 * are combined with the configured combinator.</p>
 *
 * <p>The last infection is the current episode when severity is asked about, so it is left out for every target
 * except {@link DiseaseStatus#infectedButNotContagious}: otherwise the illness would protect against its own
 * course, which is the defect the antibody based model has. Vaccinations are always counted, including one
 * received during an episode, which is rare enough not to be worth a rule of its own.</p>
 */
@Singleton
public final class ExplicitImmunityModel implements ImmunityModel {

	private static final Logger log = LogManager.getLogger(ExplicitImmunityModel.class);

	private final ImmunityConfigGroup config;

	/**
	 * Curves and covered pathogens per source, built once from the config group.
	 */
	private final Map<ImmunityEvent, SourceIndex> index = new HashMap<>();

	/**
	 * Remaining risk for strains of pathogens that a source does not describe.
	 */
	private final double otherPathogensRisk;

	/**
	 * An event before the day that is asked about is only possible when a caller replays past days; warned about
	 * once, because the question is asked in the contact loop.
	 */
	private boolean warnedAboutFutureEvent;

	@Inject
	public ExplicitImmunityModel(ImmunityConfigGroup config) {
		this.config = config;

		for (SourceParams source : config.getSources())
			index.put(source.getSource(), new SourceIndex(source));

		OptionalDouble other = config.getOtherPathogensProtection();
		this.otherPathogensRisk = other.isPresent() ? 1.0 - other.getAsDouble() : Double.NaN;
	}

	@Override
	public double getFactor(Immunizable person, VirusStrain strain, DiseaseStatus target, int day) {

		if (!ImmunityConfigGroup.SUPPORTED_TARGETS.contains(target))
			throw new IllegalArgumentException("Immunity is not defined against " + target + ", supported are "
				+ ImmunityConfigGroup.SUPPORTED_TARGETS);

		// the last infection is the current episode, which must not protect against its own course
		int current = target == DiseaseStatus.infectedButNotContagious ? -1 : person.getNumInfections() - 1;

		double risk = 1.0;
		for (int i = 0; i < person.getNumInfections(); i++) {
			if (i == current)
				continue;

			int days = person.daysSinceInfection(i, day);
			if (isInFuture(days, person.getVirusStrain(i), day))
				continue;

			risk = combine(risk, riskFrom(person.getVirusStrain(i), strain, target, days));
		}

		for (int i = 0; i < person.getNumVaccinations(); i++) {

			int days = person.daysSinceVaccination(i, day);
			if (isInFuture(days, person.getVaccinationType(i), day))
				continue;

			risk = combine(risk, riskFrom(person.getVaccinationType(i), strain, target, days));
		}

		return risk;
	}

	@Override
	public double getInfectivityFactor(Immunizable infector, VirusStrain strain, int day) {
		// how much immunity reduces shedding is not parametrised for any pathogen in this model yet
		return 1.0;
	}

	/**
	 * Remaining risk left by one source event.
	 */
	private double riskFrom(ImmunityEvent source, VirusStrain strain, DiseaseStatus target, int days) {

		SourceIndex entry = index.get(source);
		if (entry == null)
			throw new IllegalStateException("No '" + SourceParams.SET_TYPE + "' for " + describe(source) + " in config group '"
				+ ImmunityConfigGroup.GROUPNAME + "'. Describe the immunity it leaves behind, with curves '0>0.0' if there is none.");

		ProtectionCurve curve = entry.curve(target, strain);
		if (curve != null)
			return 1.0 - curve.protectionAt(days);

		if (entry.covers(strain.getPathogen()))
			throw new IllegalStateException(describe(source) + " describes pathogen '" + strain.getPathogen() + "' but has no "
				+ "protection against " + target + " for its strain '" + strain + "'. Every target has to be listed; "
				+ "write curve '0>0.0' for no protection.");

		if (Double.isNaN(otherPathogensRisk))
			throw new IllegalStateException(describe(source) + " gives no curve for strain '" + strain + "' of another pathogen, "
				+ "and 'otherPathogensProtection' is not set in config group '" + ImmunityConfigGroup.GROUPNAME + "'.");

		return otherPathogensRisk;
	}

	private double combine(double risk, double additional) {
		switch (config.getCombinator()) {
			case min:
				return Math.min(risk, additional);
			case product:
				return risk * additional;
			default:
				throw new IllegalStateException("Unknown combinator: " + config.getCombinator());
		}
	}

	/**
	 * An event that has not happened on the day that is asked about cannot protect.
	 */
	private boolean isInFuture(int days, ImmunityEvent source, int day) {
		if (days >= 0)
			return false;

		if (!warnedAboutFutureEvent) {
			warnedAboutFutureEvent = true;
			log.warn("Immunity was asked for day {}, but {} happened {} days later; this event is ignored. "
				+ "Further occurrences are not logged.", day, describe(source), -days);
		}

		return true;
	}

	private static String describe(ImmunityEvent source) {
		return (source instanceof VirusStrain ? "an infection with '" : "a vaccination with '") + source + "'";
	}

	/**
	 * Curves of one source by target and strain, plus the pathogens it describes: its own one if it is an
	 * infection, and every pathogen it lists a curve for. The same rule is used by the config check.
	 */
	private static final class SourceIndex {

		private final Map<DiseaseStatus, Map<VirusStrain, ProtectionCurve>> curves = new EnumMap<>(DiseaseStatus.class);
		private final Set<Pathogen> covered = new HashSet<>();

		SourceIndex(SourceParams source) {

			if (source.getSourceKind() == SourceKind.infection)
				covered.add(((VirusStrain) source.getSource()).getPathogen());

			for (ImmunityConfigGroup.ProtectionParams p : source.getProtections()) {
				curves.computeIfAbsent(p.getAgainst(), k -> new HashMap<>()).put(p.getStrain(), p.getCurve());
				covered.add(p.getStrain().getPathogen());
			}
		}

		ProtectionCurve curve(DiseaseStatus target, VirusStrain strain) {
			Map<VirusStrain, ProtectionCurve> byStrain = curves.get(target);
			return byStrain == null ? null : byStrain.get(strain);
		}

		boolean covers(Pathogen pathogen) {
			return covered.contains(pathogen);
		}
	}
}
