package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.ContactTransmissionType;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.TransmissionWeights;
import org.matsim.episim.model.VirusStrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Configuration of the properties of a {@link Pathogen}.
 *
 * <p>There is exactly one {@link PathogenParams} parameter set per pathogen. It holds everything that is a property
 * of the pathogen itself, independent of how disease progression is modelled:</p>
 * <ul>
 *     <li>{@code routeTransmissibility} &mdash; how well the pathogen transmits per {@link ContactTransmissionType}
 *     route (respiratory / direct contact), in the {@code route=weight;route=weight} notation of
 *     {@link ContactTransmissionConfigGroup};</li>
 *     <li>the <b>isolation at symptom onset</b> ({@code symptomaticIsolationProbabilityByAge},
 *     {@code symptomaticIsolationStatus});</li>
 *     <li>an optional <b>infectivity profile</b> ({@code infectivityProfile}): relative infectivity by day, see
 *     {@link PathogenParams#getInfectivity(double)};</li>
 *     <li>one or two nested {@link ProgressionParams} with the base disease-progression probabilities, one per
 *     variant ({@code ageDependent=false} for {@code DefaultDiseaseStatusTransitionModel} /
 *     {@code AntibodyDependentTransitionModel}, {@code ageDependent=true} for
 *     {@code AgeDependentDiseaseStatusTransitionModel}).</li>
 * </ul>
 *
 * <p>Route weights are independent, <b>additive</b> channels: they are relative and not normalised, so raising one
 * route's transmissibility does not lower another's. The infection model multiplies them, route by route, with the
 * contact-side split from {@link ContactTransmissionConfigGroup} and sums the products under the exponent
 * ({@code via[route] = contactWeight[route] &middot; routeTransmissibility[route] &middot; routeModifiers}).
 * {@code respiratory=1.0} is the conventional anchor &mdash; with it (and the default contact-side weights) the
 * formula reduces to the previous respiratory-only model, so SARS-CoV-2 behaves exactly as before. A pathogen that
 * also spreads by direct contact is written as e.g. {@code respiratory=1.0;directContact=0.4} (a sum above 1 is
 * allowed). A pathogen whose weights are all zero is rejected on config finalisation.</p>
 *
 * <p>A person who starts showing symptoms goes into {@code symptomaticIsolationStatus} ({@code full}: no contacts at
 * all; {@code atHome}: home activities only) with probability {@code symptomaticIsolationProbabilityByAge}. The
 * decision is drawn once per person at symptom onset and holds until recovery. The defaults ({@code 0=1.0},
 * {@code full}) reproduce the previously hard-coded behaviour, including the random number stream.</p>
 *
 * <p>Each {@link ProgressionParams} describes the conditional probability of moving on to the more severe branch
 * of a disease-status transition, given the preceding state and the person's age:</p>
 * <ul>
 *     <li>{@code P(showingSymptoms | contagious, age)}</li>
 *     <li>{@code P(seriouslySick | showingSymptoms, age)}</li>
 *     <li>{@code P(critical | seriouslySick, age)}</li>
 *     <li>{@code P(deceased | critical, age)}</li>
 * </ul>
 * <p>The remaining probability mass corresponds to the existing alternative branch (usually {@code recovered},
 * or {@code seriouslySickAfterCritical} for the {@code critical} transition). Strain-relative differences,
 * vaccination and antibody effects, the {@code hospitalFactor} and every other modifier stay in the models.</p>
 *
 * <p>All age profiles use the {@code age=value;age=value} bucket notation of {@link VirusStrainConfigGroup}. A value
 * applies from the given age (inclusive) up to the next boundary; a single entry such as {@code 0=0.8} yields a
 * constant for all ages.</p>
 */
public class PathogenConfigGroup extends ReflectiveConfigGroup {

	static final String GROUPNAME = "pathogen";

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

	/**
	 * Parameters per pathogen.
	 */
	private final Map<Pathogen, PathogenParams> params = new LinkedHashMap<>();

	/**
	 * Default constructor. Registers the SARS-CoV-2 defaults so that existing COVID configurations without an
	 * explicit {@code pathogen} section keep behaving exactly as before.
	 */
	public PathogenConfigGroup() {
		super(GROUPNAME);

		addParameterSet(PathogenParams.sarsCov2Default());
	}

	/**
	 * Get the parameter set of a pathogen.
	 *
	 * @throws IllegalStateException if the pathogen has not been configured
	 */
	public PathogenParams getParams(Pathogen pathogen) {
		PathogenParams p = params.get(pathogen);
		if (p == null)
			throw new IllegalStateException("No pathogen configuration for '" + pathogen.getName() + "'. Add a '"
					+ PathogenParams.SET_TYPE + "' parameter set for this pathogen to the '" + GROUPNAME + "' config group.");
		return p;
	}

	/**
	 * Get the disease-progression probabilities of a pathogen in the requested variant.
	 *
	 * @throws IllegalStateException if the pathogen or the requested variant has not been configured
	 */
	public ProgressionParams getProgressionParams(Pathogen pathogen, boolean ageDependent) {
		return getParams(pathogen).getProgressionParams(ageDependent);
	}

	/**
	 * Whether a parameter set for the pathogen is present.
	 */
	public boolean hasParams(Pathogen pathogen) {
		return params.containsKey(pathogen);
	}

	/**
	 * Get an existing or add a new parameter set for the given pathogen. A new set carries the defaults
	 * (respiratory-only, isolation of everybody) and no progression variant yet.
	 */
	public PathogenParams getOrAddParams(Pathogen pathogen) {
		PathogenParams existing = params.get(pathogen);
		if (existing != null)
			return existing;

		PathogenParams p = new PathogenParams();
		p.setPathogen(pathogen);
		addParameterSet(p);
		return p;
	}

	/**
	 * Checks that the pathogens of all given strains are configured with the requested progression variant. Transition
	 * models call this when they are created, i.e. before the simulation starts, because only they know which variant
	 * they need.
	 *
	 * @param strains      strains that can infect persons
	 * @param ageDependent the progression variant used by the model
	 * @param model        name of the model for the error message
	 * @throws IllegalStateException listing all pathogens without parameter set or without this variant
	 */
	public void checkProgressionConfigured(Collection<VirusStrain> strains, boolean ageDependent, String model) {

		Map<Pathogen, List<String>> byPathogen = new TreeMap<>(Comparator.comparing(Pathogen::getName));
		for (VirusStrain strain : strains)
			byPathogen.computeIfAbsent(strain.getPathogen(), k -> new ArrayList<>()).add(strain.getVirusStrainName());

		List<String> problems = new ArrayList<>();
		byPathogen.forEach((pathogen, names) -> {
			if (!hasParams(pathogen))
				problems.add("pathogen '" + pathogen.getName() + "' of strains " + names + " has no '" + PathogenParams.SET_TYPE + "'");
			else if (!params.get(pathogen).hasProgressionParams(ageDependent))
				problems.add("pathogen '" + pathogen.getName() + "' of strains " + names + " has no '" + ProgressionParams.SET_TYPE
						+ "' with ageDependent=" + ageDependent);
		});

		if (!problems.isEmpty())
			throw new IllegalStateException(model + " needs the " + (ageDependent ? "age-dependent" : "age-independent")
					+ " disease progression of every configured strain in config group '" + GROUPNAME + "', but "
					+ String.join("; ", problems) + ".");
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);

		for (PathogenParams p : params.values()) {
			p.validateComplete(getName());
		}
	}

	@Override
	public ConfigGroup createParameterSet(String type) {
		if (PathogenParams.SET_TYPE.equals(type))
			return new PathogenParams();

		throw new IllegalArgumentException("Unknown parameter set type: " + type);
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		if (!PathogenParams.SET_TYPE.equals(set.getName()))
			throw new IllegalStateException("Unknown parameter set type: " + set.getName());

		PathogenParams p = (PathogenParams) set;
		PathogenParams previous = params.put(p.getPathogen(), p);

		// Replace a previously registered set (e.g. the auto-added SARS-CoV-2 default) instead of keeping a stale duplicate.
		if (previous != null)
			super.removeParameterSet(previous);

		super.addParameterSet(set);
	}

	/**
	 * Value of an age profile for the given age; ages below the first bucket use the first bucket.
	 */
	private static double forAge(String param, NavigableMap<Integer, Double> map, int age) {
		Map.Entry<Integer, Double> entry = map.floorEntry(age);
		if (entry == null)
			entry = map.firstEntry();
		if (entry == null)
			throw new IllegalStateException("No age profile configured for '" + param + "'.");
		return entry.getValue();
	}

	private static void replace(NavigableMap<Integer, Double> target, NavigableMap<Integer, Double> values) {
		target.clear();
		target.putAll(values);
	}

	private static NavigableMap<Integer, Double> parse(String param, String config) {
		NavigableMap<Integer, Double> parsed = new TreeMap<>();
		for (Map.Entry<String, String> e : SPLITTER.split(config).entrySet()) {
			parsed.put(Integer.parseInt(e.getKey().trim()), Double.parseDouble(e.getValue().trim()));
		}
		return validated(param, parsed);
	}

	private static NavigableMap<Integer, Double> validated(String param, Map<Integer, Double> values) {
		if (values.isEmpty())
			throw new IllegalArgumentException("'" + param + "' needs at least one age bucket.");

		NavigableMap<Integer, Double> out = new TreeMap<>();
		for (Map.Entry<Integer, Double> e : values.entrySet()) {
			int age = e.getKey();
			double p = e.getValue();
			if (age < 0)
				throw new IllegalArgumentException("'" + param + "' age bucket must not be negative but was " + age + ".");
			if (Double.isNaN(p) || p < 0.0 || p > 1.0)
				throw new IllegalArgumentException("'" + param + "' probability for age " + age
						+ " must be within [0, 1] but was " + p + ".");
			out.put(age, p);
		}
		return out;
	}

	private static void requireNonEmpty(String owner, String param, NavigableMap<Integer, Double> map) {
		if (map.isEmpty())
			throw new IllegalStateException(owner + " is missing an age profile for '" + param + "'.");
	}

	/**
	 * Properties of a single {@link Pathogen}: transmission routes, isolation at symptom onset and the nested
	 * disease-progression variants.
	 */
	public static final class PathogenParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "pathogenParams";

		private static final String PATHOGEN = "pathogen";
		private static final String ROUTE_TRANSMISSIBILITY = "routeTransmissibility";
		private static final String SYMPTOMATIC_ISOLATION = "symptomaticIsolationProbabilityByAge";
		private static final String SYMPTOMATIC_ISOLATION_STATUS = "symptomaticIsolationStatus";
		private static final String INFECTIVITY_PROFILE = "infectivityProfile";

		/**
		 * Pathogen this parameter set applies to.
		 */
		private Pathogen pathogen = Pathogen.SARS_COV_2;

		/**
		 * Per-route transmissibility of this pathogen. Relative, additive weights (no sum constraint);
		 * the infection model multiplies them route by route with the contact-side split from
		 * {@link ContactTransmissionConfigGroup}. Defaults to respiratory-only.
		 */
		private TransmissionWeights routeTransmissibility = TransmissionWeights.RESPIRATORY_ONLY;

		/**
		 * Probability that a person isolates at symptom onset, by age. Defaults to everybody (the previous behaviour).
		 */
		private final NavigableMap<Integer, Double> symptomaticIsolationProbabilityByAge = new TreeMap<>(Map.of(0, 1.0));

		/**
		 * Quarantine status of a person who isolates at symptom onset: {@code full} or {@code atHome}.
		 */
		private EpisimPerson.QuarantineStatus symptomaticIsolationStatus = EpisimPerson.QuarantineStatus.full;

		/**
		 * Relative infectivity by signed day offset; empty means not configured.
		 */
		private final NavigableMap<Integer, Double> infectivityProfile = new TreeMap<>();

		/**
		 * Disease-progression variants; index 0 is age-independent, index 1 age-dependent.
		 */
		private final ProgressionParams[] progression = new ProgressionParams[2];

		PathogenParams() {
			super(SET_TYPE);
		}

		/**
		 * SARS-CoV-2 defaults: respiratory-only, isolation of everybody, and both progression variants with the values
		 * that used to be hard-coded in the transition models.
		 */
		static PathogenParams sarsCov2Default() {
			PathogenParams p = new PathogenParams();
			p.setPathogen(Pathogen.SARS_COV_2);
			p.addParameterSet(ProgressionParams.sarsCov2AgeIndependentDefault());
			p.addParameterSet(ProgressionParams.sarsCov2AgeDependentDefault());
			return p;
		}

		@StringGetter(PATHOGEN)
		public String getPathogenName() {
			return pathogen.getName();
		}

		@StringSetter(PATHOGEN)
		public void setPathogen(String pathogen) {
			setPathogen(new Pathogen(pathogen));
		}

		public Pathogen getPathogen() {
			return pathogen;
		}

		public void setPathogen(Pathogen pathogen) {
			this.pathogen = Objects.requireNonNull(pathogen, "Pathogen must not be null");
		}

		/**
		 * Disease-progression probabilities in the requested variant.
		 *
		 * @throws IllegalStateException if this variant has not been configured
		 */
		public ProgressionParams getProgressionParams(boolean ageDependent) {
			ProgressionParams p = progression[index(ageDependent)];
			if (p == null)
				throw new IllegalStateException("No " + (ageDependent ? "age-dependent" : "age-independent")
						+ " disease progression for pathogen '" + pathogen.getName() + "'. Add a '" + ProgressionParams.SET_TYPE
						+ "' parameter set with ageDependent=" + ageDependent + " to its '" + SET_TYPE + "'.");
			return p;
		}

		/**
		 * Whether the progression variant is present.
		 */
		public boolean hasProgressionParams(boolean ageDependent) {
			return progression[index(ageDependent)] != null;
		}

		/**
		 * Get an existing or add a new (empty) progression variant.
		 */
		public ProgressionParams getOrAddProgressionParams(boolean ageDependent) {
			ProgressionParams existing = progression[index(ageDependent)];
			if (existing != null)
				return existing;

			ProgressionParams p = new ProgressionParams();
			p.setAgeDependent(ageDependent);
			addParameterSet(p);
			return p;
		}

		@Override
		public ConfigGroup createParameterSet(String type) {
			if (ProgressionParams.SET_TYPE.equals(type))
				return new ProgressionParams();

			throw new IllegalArgumentException("Unknown parameter set type: " + type);
		}

		@Override
		public void addParameterSet(final ConfigGroup set) {
			if (!ProgressionParams.SET_TYPE.equals(set.getName()))
				throw new IllegalStateException("Unknown parameter set type: " + set.getName());

			ProgressionParams p = (ProgressionParams) set;
			int idx = index(p.isAgeDependent());
			ProgressionParams previous = progression[idx];
			progression[idx] = p;

			// replace a previously registered variant instead of keeping a stale duplicate
			if (previous != null)
				super.removeParameterSet(previous);

			super.addParameterSet(set);
		}

		private static int index(boolean ageDependent) {
			return ageDependent ? 1 : 0;
		}

		@StringGetter(ROUTE_TRANSMISSIBILITY)
		String getRouteTransmissibilityString() {
			return routeTransmissibility.toToken();
		}

		@StringSetter(ROUTE_TRANSMISSIBILITY)
		void setRouteTransmissibilityString(String config) {
			this.routeTransmissibility = TransmissionWeights.parse(config);
		}

		/**
		 * Per-route transmissibility of this pathogen (respiratory / direct contact) &mdash; relative,
		 * additive weights, {@code respiratory=1.0} by default.
		 */
		public TransmissionWeights getRouteTransmissibility() {
			return routeTransmissibility;
		}

		/**
		 * Set the per-route transmissibility of this pathogen, replacing the previous value.
		 */
		public void setRouteTransmissibility(TransmissionWeights routeTransmissibility) {
			this.routeTransmissibility = Objects.requireNonNull(routeTransmissibility, ROUTE_TRANSMISSIBILITY);
		}

		@StringGetter(SYMPTOMATIC_ISOLATION)
		String getSymptomaticIsolationProbabilityByAgeString() {
			return JOINER.join(symptomaticIsolationProbabilityByAge);
		}

		@StringSetter(SYMPTOMATIC_ISOLATION)
		void setSymptomaticIsolationProbabilityByAge(String config) {
			replace(symptomaticIsolationProbabilityByAge, parse(SYMPTOMATIC_ISOLATION, config));
		}

		/**
		 * Set the probability of isolating at symptom onset by age, replacing all previous entries.
		 */
		public void setSymptomaticIsolationProbabilityByAge(Map<Integer, Double> values) {
			replace(symptomaticIsolationProbabilityByAge, validated(SYMPTOMATIC_ISOLATION, values));
		}

		public NavigableMap<Integer, Double> getSymptomaticIsolationProbabilityByAge() {
			return symptomaticIsolationProbabilityByAge;
		}

		/**
		 * Probability that a person of the given age isolates at symptom onset.
		 */
		public double getSymptomaticIsolationProbability(int age) {
			return forAge(SYMPTOMATIC_ISOLATION, symptomaticIsolationProbabilityByAge, age);
		}

		@StringGetter(SYMPTOMATIC_ISOLATION_STATUS)
		String getSymptomaticIsolationStatusString() {
			return symptomaticIsolationStatus.name();
		}

		@StringSetter(SYMPTOMATIC_ISOLATION_STATUS)
		void setSymptomaticIsolationStatusString(String status) {
			setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus.valueOf(status.trim()));
		}

		/**
		 * Quarantine status of a person who isolates at symptom onset.
		 */
		public EpisimPerson.QuarantineStatus getSymptomaticIsolationStatus() {
			return symptomaticIsolationStatus;
		}

		/**
		 * Set the quarantine status used for isolation at symptom onset; only {@code full} and {@code atHome} are
		 * allowed. Disable isolation with a probability of 0 instead.
		 */
		public void setSymptomaticIsolationStatus(EpisimPerson.QuarantineStatus status) {
			Objects.requireNonNull(status, SYMPTOMATIC_ISOLATION_STATUS);
			if (status != EpisimPerson.QuarantineStatus.full && status != EpisimPerson.QuarantineStatus.atHome)
				throw new IllegalArgumentException("'" + SYMPTOMATIC_ISOLATION_STATUS + "' must be 'full' or 'atHome' but was '"
						+ status + "'; set '" + SYMPTOMATIC_ISOLATION + "' to 0 to disable isolation.");
			this.symptomaticIsolationStatus = status;
		}

		@StringGetter(INFECTIVITY_PROFILE)
		String getInfectivityProfileString() {
			return JOINER.join(infectivityProfile);
		}

		@StringSetter(INFECTIVITY_PROFILE)
		void setInfectivityProfileString(String config) {
			// an unset profile is written as an empty value, which must be read back as unset
			if (config == null || config.isBlank()) {
				infectivityProfile.clear();
				return;
			}

			Map<Integer, Double> parsed = new TreeMap<>();
			for (Map.Entry<String, String> e : SPLITTER.split(config).entrySet())
				parsed.put(Integer.parseInt(e.getKey().trim()), Double.parseDouble(e.getValue().trim()));
			setInfectivityProfile(parsed);
		}

		/**
		 * Set the infectivity profile, replacing the previous one; an empty map unsets it.
		 *
		 * @param values relative infectivity by signed day offset, see {@link #getInfectivity(double)}. Values must be
		 *               finite and non-negative, and a non-empty profile needs at least one positive value.
		 */
		public void setInfectivityProfile(Map<Integer, Double> values) {
			boolean positive = false;
			for (Map.Entry<Integer, Double> e : values.entrySet()) {
				double v = e.getValue();
				if (!Double.isFinite(v) || v < 0.0)
					throw new IllegalArgumentException("'" + INFECTIVITY_PROFILE + "' value for day " + e.getKey()
							+ " must be finite and non-negative but was " + v + ".");
				positive |= v > 0.0;
			}
			if (!values.isEmpty() && !positive)
				throw new IllegalArgumentException("'" + INFECTIVITY_PROFILE + "' of pathogen '" + pathogen.getName()
						+ "' is zero everywhere; leave it empty to use the infection model's built-in curve.");

			infectivityProfile.clear();
			infectivityProfile.putAll(values);
		}

		public NavigableMap<Integer, Double> getInfectivityProfile() {
			return infectivityProfile;
		}

		/**
		 * Whether an infectivity profile is configured. Without one the infection model uses its built-in curve.
		 */
		public boolean hasInfectivityProfile() {
			return !infectivityProfile.isEmpty();
		}

		/**
		 * Relative infectivity at a signed day offset from the reference point: symptom onset for an infection that
		 * becomes symptomatic (negative before onset), the middle of the contagious period for one that does not.
		 * Linear between configured days, zero outside the configured range. The values are relative: with a peak of
		 * 1.0, {@code infectiousness} and {@code calibrationParameter} keep their meaning.
		 *
		 * @throws IllegalStateException if no profile is configured
		 */
		public double getInfectivity(double daysFromReference) {
			if (infectivityProfile.isEmpty())
				throw new IllegalStateException("No '" + INFECTIVITY_PROFILE + "' configured for pathogen '" + pathogen.getName() + "'.");

			Map.Entry<Integer, Double> lower = infectivityProfile.floorEntry((int) Math.floor(daysFromReference));
			Map.Entry<Integer, Double> upper = infectivityProfile.ceilingEntry((int) Math.ceil(daysFromReference));
			if (lower == null || upper == null)
				return 0.0;
			if (lower.getKey().equals(upper.getKey()))
				return lower.getValue();

			double t = (daysFromReference - lower.getKey()) / (upper.getKey() - lower.getKey());
			return lower.getValue() + t * (upper.getValue() - lower.getValue());
		}

		/**
		 * Ensure the pathogen can transmit, has an isolation profile and at least one complete progression variant.
		 * Called on config finalisation, not on every {@code addParameterSet}, so that sets can still be filled in after
		 * being registered.
		 */
		void validateComplete(String groupName) {
			String owner = "pathogen '" + pathogen.getName() + "' in config group '" + groupName + "'";

			requireNonEmpty(owner, SYMPTOMATIC_ISOLATION, symptomaticIsolationProbabilityByAge);
			if (routeTransmissibility.isZero())
				throw new IllegalStateException(owner + " has an all-zero '" + ROUTE_TRANSMISSIBILITY
						+ "'; it cannot transmit by any route.");

			if (progression[0] == null && progression[1] == null)
				throw new IllegalStateException(owner + " has no '" + ProgressionParams.SET_TYPE + "' parameter set.");

			for (ProgressionParams p : progression) {
				if (p != null)
					p.validateComplete(owner);
			}
		}
	}

	/**
	 * Base disease-progression probabilities of a pathogen in one variant (age-dependent or age-independent).
	 */
	public static final class ProgressionParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "progressionParams";

		private static final String AGE_DEPENDENT = "ageDependent";
		private static final String SHOWING_SYMPTOMS = "showingSymptomsProbabilityByAge";
		private static final String SERIOUSLY_SICK = "seriouslySickProbabilityByAge";
		private static final String CRITICAL = "criticalProbabilityByAge";
		private static final String DEATH = "deathProbabilityByAge";

		/**
		 * Whether this variant is used by the age-dependent transition model.
		 */
		private boolean ageDependent = false;

		private final NavigableMap<Integer, Double> showingSymptomsProbabilityByAge = new TreeMap<>();
		private final NavigableMap<Integer, Double> seriouslySickProbabilityByAge = new TreeMap<>();
		private final NavigableMap<Integer, Double> criticalProbabilityByAge = new TreeMap<>();
		private final NavigableMap<Integer, Double> deathProbabilityByAge = new TreeMap<>();

		ProgressionParams() {
			super(SET_TYPE);
		}

		/**
		 * SARS-CoV-2 defaults for the age-independent transition models
		 * ({@code DefaultDiseaseStatusTransitionModel}, {@code AntibodyDependentTransitionModel}).
		 */
		static ProgressionParams sarsCov2AgeIndependentDefault() {
			ProgressionParams p = new ProgressionParams();
			p.setAgeDependent(false);
			p.setShowingSymptomsProbabilityByAge(Map.of(0, 0.8));
			p.setSeriouslySickProbabilityByAge(Map.of(0, 0.05625));
			p.setCriticalProbabilityByAge(Map.of(0, 0.25));
			p.setDeathProbabilityByAge(Map.of(0, 0.0));
			return p;
		}

		/**
		 * SARS-CoV-2 defaults for {@code AgeDependentDiseaseStatusTransitionModel}. The values and age boundaries
		 * are taken verbatim from that model; the {@code hospitalFactor} multiplier stays in the model.
		 */
		static ProgressionParams sarsCov2AgeDependentDefault() {
			ProgressionParams p = new ProgressionParams();
			p.setAgeDependent(true);
			p.setShowingSymptomsProbabilityByAge(Map.of(0, 0.8));

			NavigableMap<Integer, Double> seriouslySick = new TreeMap<>();
			seriouslySick.put(0, 4.0 / 100);
			seriouslySick.put(5, 1.1 / 100);
			seriouslySick.put(15, 2.4 / 100);
			seriouslySick.put(35, 5.6 / 100);
			seriouslySick.put(60, 23. / 100);
			seriouslySick.put(80, 36. / 100);
			p.setSeriouslySickProbabilityByAge(seriouslySick);

			NavigableMap<Integer, Double> critical = new TreeMap<>();
			critical.put(0, 7.0 / 100);
			critical.put(5, 0.0 / 100);
			critical.put(15, 15.0 / 100);
			critical.put(35, 30.0 / 100);
			critical.put(60, 41. / 100);
			critical.put(80, 27. / 100);
			p.setCriticalProbabilityByAge(critical);

			p.setDeathProbabilityByAge(Map.of(0, 0.0));
			return p;
		}

		@StringGetter(AGE_DEPENDENT)
		public boolean isAgeDependent() {
			return ageDependent;
		}

		/**
		 * Set the variant. Must be set before the parameter set is added to its {@link PathogenParams}.
		 */
		@StringSetter(AGE_DEPENDENT)
		public void setAgeDependent(boolean ageDependent) {
			this.ageDependent = ageDependent;
		}

		@StringGetter(SHOWING_SYMPTOMS)
		String getShowingSymptomsProbabilityByAgeString() {
			return JOINER.join(showingSymptomsProbabilityByAge);
		}

		@StringSetter(SHOWING_SYMPTOMS)
		void setShowingSymptomsProbabilityByAge(String config) {
			replace(showingSymptomsProbabilityByAge, parse(SHOWING_SYMPTOMS, config));
		}

		/**
		 * Set the {@code showingSymptoms} age profile, replacing all previous entries.
		 */
		public void setShowingSymptomsProbabilityByAge(Map<Integer, Double> values) {
			replace(showingSymptomsProbabilityByAge, validated(SHOWING_SYMPTOMS, values));
		}

		@StringGetter(SERIOUSLY_SICK)
		String getSeriouslySickProbabilityByAgeString() {
			return JOINER.join(seriouslySickProbabilityByAge);
		}

		@StringSetter(SERIOUSLY_SICK)
		void setSeriouslySickProbabilityByAge(String config) {
			replace(seriouslySickProbabilityByAge, parse(SERIOUSLY_SICK, config));
		}

		/**
		 * Set the {@code seriouslySick} age profile, replacing all previous entries.
		 */
		public void setSeriouslySickProbabilityByAge(Map<Integer, Double> values) {
			replace(seriouslySickProbabilityByAge, validated(SERIOUSLY_SICK, values));
		}

		@StringGetter(CRITICAL)
		String getCriticalProbabilityByAgeString() {
			return JOINER.join(criticalProbabilityByAge);
		}

		@StringSetter(CRITICAL)
		void setCriticalProbabilityByAge(String config) {
			replace(criticalProbabilityByAge, parse(CRITICAL, config));
		}

		/**
		 * Set the {@code critical} age profile, replacing all previous entries.
		 */
		public void setCriticalProbabilityByAge(Map<Integer, Double> values) {
			replace(criticalProbabilityByAge, validated(CRITICAL, values));
		}

		@StringGetter(DEATH)
		String getDeathProbabilityByAgeString() {
			return JOINER.join(deathProbabilityByAge);
		}

		@StringSetter(DEATH)
		void setDeathProbabilityByAge(String config) {
			replace(deathProbabilityByAge, parse(DEATH, config));
		}

		/**
		 * Set the {@code deceased} age profile, replacing all previous entries.
		 */
		public void setDeathProbabilityByAge(Map<Integer, Double> values) {
			replace(deathProbabilityByAge, validated(DEATH, values));
		}

		public NavigableMap<Integer, Double> getShowingSymptomsProbabilityByAge() {
			return showingSymptomsProbabilityByAge;
		}

		public NavigableMap<Integer, Double> getSeriouslySickProbabilityByAge() {
			return seriouslySickProbabilityByAge;
		}

		public NavigableMap<Integer, Double> getCriticalProbabilityByAge() {
			return criticalProbabilityByAge;
		}

		public NavigableMap<Integer, Double> getDeathProbabilityByAge() {
			return deathProbabilityByAge;
		}

		/**
		 * Probability of transitioning {@code contagious -> showingSymptoms} for a person of the given age.
		 */
		public double getShowingSymptomsProbability(int age) {
			return forAge(SHOWING_SYMPTOMS, showingSymptomsProbabilityByAge, age);
		}

		/**
		 * Probability of transitioning {@code showingSymptoms -> seriouslySick} for a person of the given age.
		 */
		public double getSeriouslySickProbability(int age) {
			return forAge(SERIOUSLY_SICK, seriouslySickProbabilityByAge, age);
		}

		/**
		 * Probability of transitioning {@code seriouslySick -> critical} for a person of the given age.
		 */
		public double getCriticalProbability(int age) {
			return forAge(CRITICAL, criticalProbabilityByAge, age);
		}

		/**
		 * Probability of transitioning {@code critical -> deceased} for a person of the given age.
		 */
		public double getDeathProbability(int age) {
			return forAge(DEATH, deathProbabilityByAge, age);
		}

		/**
		 * Ensure every transition has an age profile.
		 */
		void validateComplete(String owner) {
			String variant = owner + " (" + SET_TYPE + " ageDependent=" + ageDependent + ")";
			requireNonEmpty(variant, SHOWING_SYMPTOMS, showingSymptomsProbabilityByAge);
			requireNonEmpty(variant, SERIOUSLY_SICK, seriouslySickProbabilityByAge);
			requireNonEmpty(variant, CRITICAL, criticalProbabilityByAge);
			requireNonEmpty(variant, DEATH, deathProbabilityByAge);
		}
	}
}
