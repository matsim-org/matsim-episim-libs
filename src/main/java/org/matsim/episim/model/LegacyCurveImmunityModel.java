package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.Immunizable;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VaccinationFactorFunction;

/**
 * Immunity from the effectiveness curves of {@link VaccinationConfigGroup}.
 *
 * <p>This is a faithful extraction of the code that computed immunity before {@link ImmunityModel} existed: the
 * default methods of {@code DiseaseStatusTransitionModel} for the severity side, and
 * {@code DefaultInfectionModel.getVaccinationEffectiveness} / {@code getImmunityEffectiveness} /
 * {@code getInfectivity} for the infection side. Nothing about the computation was changed, so the known defects of that
 * code are still here on purpose and are removed in separate steps:</p>
 * <ul>
 *     <li>natural immunity is the single {@code VaccinationType.natural}, not one type per source strain;</li>
 *     <li>it is timed by {@code daysSince(recovered)}, which is one global timestamp across all pathogens;</li>
 *     <li>a strain without a curve silently falls back to the SARS-CoV-2 curve;</li>
 *     <li>{@code getImmunityEffectiveness} contains a hard-coded 180 days of sterilising immunity.</li>
 * </ul>
 */
public class LegacyCurveImmunityModel implements ImmunityModel {

	private final VaccinationConfigGroup vaccinationConfig;

	@Inject
	public LegacyCurveImmunityModel(VaccinationConfigGroup vaccinationConfig) {
		this.vaccinationConfig = vaccinationConfig;
	}

	@Override
	public double getFactor(Immunizable person, VirusStrain strain, DiseaseStatus target, int day) {

		if (target == DiseaseStatus.infectedButNotContagious)
			return Math.min(vaccinationEffectiveness(person, strain, day), naturalEffectiveness(person, strain, day));

		return severityFactor(person, strain, curveFor(target), day);
	}

	@Override
	public double getInfectivityFactor(Immunizable infector, VirusStrain strain, int day) {

		double naturalInfectivity = 1;

		if (vaccinationConfig.hasParams(VaccinationType.natural) && infector.hadDiseaseStatus(DiseaseStatus.recovered)) {
			VaccinationConfigGroup.VaccinationParams params = vaccinationConfig.getParams(VaccinationType.natural);
			naturalInfectivity = params.getInfectivity(strain, infector.daysSince(DiseaseStatus.recovered, day));
		}

		return Math.min(vaccinationInfectivity(infector, strain, day), naturalInfectivity);
	}

	/**
	 * Which of the per-strain curves describes protection against this target. The three severity curves differ
	 * only in which one is read, which is why the three near-identical methods of the old interface collapse here.
	 */
	private static VaccinationFactorFunction curveFor(DiseaseStatus target) {
		switch (target) {
			case showingSymptoms:
				return VaccinationConfigGroup.VaccinationParams::getFactorShowingSymptoms;
			case seriouslySick:
				return VaccinationConfigGroup.VaccinationParams::getFactorSeriouslySick;
			case critical:
				return VaccinationConfigGroup.VaccinationParams::getFactorCritical;
			default:
				throw new IllegalArgumentException("No protection is configured against disease status '" + target
					+ "'. Supported are infectedButNotContagious, showingSymptoms, seriouslySick and critical.");
		}
	}

	private double severityFactor(Immunizable person, VirusStrain strain, VaccinationFactorFunction curve, int day) {

		double fromVaccination = vaccinationConfig.getMinFactor(asPerson(person), day, curve);

		double fromInfection = (person.getNumInfections() > 0
			&& person.hadDiseaseStatus(DiseaseStatus.recovered)
			&& vaccinationConfig.hasParams(VaccinationType.natural))
			? curve.getFactor(vaccinationConfig.getParams(VaccinationType.natural), strain,
				person.daysSince(DiseaseStatus.recovered, day))
			: 1d;

		return Math.min(fromVaccination, fromInfection);
	}

	private double vaccinationEffectiveness(Immunizable person, VirusStrain strain, int day) {

		EpisimPerson target = asPerson(person);

		if (target.getVaccinationStatus() == EpisimPerson.VaccinationStatus.no)
			return 1;

		int daysVaccinated = target.daysSince(EpisimPerson.VaccinationStatus.yes, day);
		VaccinationConfigGroup.VaccinationParams params = vaccinationConfig.getParams(target.getVaccinationType());

		double vaccineEffectiveness;
		// a person with a previous infection also gets the boost effectiveness
		if (target.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.yes || target.getNumInfections() >= 1) {
			vaccineEffectiveness = params.getBoostEffectiveness(strain,
				Math.min(daysVaccinated, target.daysSinceOrElse(DiseaseStatus.recovered, day, Integer.MAX_VALUE)));
		} else {
			vaccineEffectiveness = params.getEffectiveness(strain, daysVaccinated);
		}

		return 1 - vaccineEffectiveness;
	}

	private double naturalEffectiveness(Immunizable person, VirusStrain strain, int day) {

		if (person.getNumInfections() < 1 || !person.hadDiseaseStatus(DiseaseStatus.recovered))
			return 1;

		if (!vaccinationConfig.hasParams(VaccinationType.natural))
			return 1;

		int daysSince = person.daysSince(DiseaseStatus.recovered, day);

		// persons can not get infected for 180 days when they had 2 infections, or 1 infection and vaccinations
		// TODO: only here a quick fix and needs to be remodelled
		if (daysSince < 180 && (person.getNumInfections() >= 2
			|| (person.getNumInfections() >= 1 && asPerson(person).getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes)))
			return 0;

		return 1 - vaccinationConfig.getParams(VaccinationType.natural).getEffectiveness(strain, daysSince);
	}

	private double vaccinationInfectivity(Immunizable person, VirusStrain strain, int day) {

		EpisimPerson infector = asPerson(person);

		if (infector.getVaccinationStatus() == EpisimPerson.VaccinationStatus.no)
			return 1;

		int daysVaccinated = infector.daysSince(EpisimPerson.VaccinationStatus.yes, day);
		VaccinationConfigGroup.VaccinationParams params = vaccinationConfig.getParams(infector.getVaccinationType());

		if (infector.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.yes || infector.getNumInfections() >= 1) {
			return params.getBoostInfectivity(strain,
				Math.min(daysVaccinated, infector.daysSinceOrElse(DiseaseStatus.recovered, day, Integer.MAX_VALUE)));
		}

		return params.getInfectivity(strain, daysVaccinated);
	}

	/**
	 * The vaccination history is not part of {@link Immunizable}, so everything that reads it needs the concrete
	 * person. The old code hid the same cast inside the default methods of {@code DiseaseStatusTransitionModel};
	 * keeping it here does not make it safe, it only makes it visible and removable in one place. It fails for the
	 * replaying {@code Immunizable} of {@code HospitalNumbersFromEvents}, which is why that caller must not be
	 * routed here until {@link Immunizable} exposes the vaccination history.
	 */
	private static EpisimPerson asPerson(Immunizable person) {
		if (person instanceof EpisimPerson)
			return (EpisimPerson) person;

		throw new IllegalArgumentException("This immunity model reads the vaccination history, which only "
			+ EpisimPerson.class.getSimpleName() + " provides, but got " + person.getClass().getName());
	}
}
