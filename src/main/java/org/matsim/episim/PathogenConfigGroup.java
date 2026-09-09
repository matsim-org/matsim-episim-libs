package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.ContactTransmissionType;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.TransmissionWeights;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Configuration of the base (natural history) disease-progression probabilities of a {@link Pathogen}.
 *
 * <p>Each {@link PathogenParams} parameter set describes, for one pathogen, the conditional probability of
 * moving on to the more severe branch of a disease-status transition, given that the person is currently in
 * the preceding state and given the person's age:</p>
 * <ul>
 *     <li>{@code P(showingSymptoms | contagious, age)}</li>
 *     <li>{@code P(seriouslySick | showingSymptoms, age)}</li>
 *     <li>{@code P(critical | seriouslySick, age)}</li>
 *     <li>{@code P(deceased | critical, age)}</li>
 * </ul>
 *
 * <p>The remaining probability mass corresponds to the existing alternative branch (usually {@code recovered},
 * or {@code seriouslySickAfterCritical} for the {@code critical} transition).</p>
 *
 * <p>Age profiles use the same {@code age=value;age=value} bucket notation as
 * {@link VirusStrainConfigGroup}. A value applies from the given age (inclusive) up to the next boundary; a
 * single entry such as {@code 0=0.8} therefore yields a constant probability for all ages.</p>
 *
 * <p>Strain-relative differences, vaccination effects, antibody/immunity effects, the {@code hospitalFactor}
 * and every other existing modifier stay where they are today. This config group replaces the base
 * probability that used to be hard-coded in the transition models.</p>
 *
 * <p>Each {@link PathogenParams} also carries {@code routeTransmissibility} &mdash; how well the pathogen
 * transmits per {@link ContactTransmissionType} route (respiratory / direct contact), written in the same
 * {@code route=weight;route=weight} notation as {@link ContactTransmissionConfigGroup}. The infection
 * model multiplies it, route by route, with the contact-side split from
 * {@link ContactTransmissionConfigGroup} and sums the products under the exponent
 * ({@code via[route] = contactWeight[route] &middot; routeTransmissibility[route] &middot; routeModifiers}).</p>
 *
 * <p>The routes are independent, <b>additive</b> channels: the weights are relative and not normalised,
 * so raising one route's transmissibility does not lower another's. {@code respiratory=1.0} is the
 * conventional anchor &mdash; with it (and the default contact-side weights) the formula reduces to the
 * previous respiratory-only model, so SARS-CoV-2 behaves exactly as before. A pathogen that also spreads
 * by direct contact is written as e.g. {@code respiratory=1.0;directContact=0.4} (note the sum exceeds 1,
 * which is allowed). Only finite, non-negative weights are accepted, and a pathogen whose weights are all
 * zero &mdash; it could not transmit at all &mdash; is rejected on config finalisation.</p>
 */
public class PathogenConfigGroup extends ReflectiveConfigGroup {

	private static final String GROUPNAME = "pathogen";

	/**
	 * Base probabilities that are looked up independently of age (a single {@code 0=value} bucket).
	 */
	private final Map<Pathogen, PathogenParams> ageIndependentParams = new LinkedHashMap<>();

	/**
	 * Base probabilities that carry a full age profile.
	 */
	private final Map<Pathogen, PathogenParams> ageDependentParams = new LinkedHashMap<>();

	/**
	 * Default constructor. Registers the SARS-CoV-2 defaults so that existing COVID configurations without an
	 * explicit {@code pathogen} section keep behaving exactly as before.
	 */
	public PathogenConfigGroup() {
		super(GROUPNAME);

		addParameterSet(PathogenParams.sarsCov2AgeIndependentDefault());
		addParameterSet(PathogenParams.sarsCov2AgeDependentDefault());
	}

	private Map<Pathogen, PathogenParams> store(boolean ageDependent) {
		return ageDependent ? ageDependentParams : ageIndependentParams;
	}

	/**
	 * Get the age-independent parameter set for a pathogen.
	 */
	public PathogenParams getParams(Pathogen pathogen) {
		return getParams(pathogen, false);
	}

	/**
	 * Get the parameter set for a pathogen, selecting the age-dependent or age-independent variant.
	 *
	 * @throws IllegalStateException if the pathogen (in the requested variant) has not been configured
	 */
	public PathogenParams getParams(Pathogen pathogen, boolean ageDependent) {
		PathogenParams params = store(ageDependent).get(pathogen);
		if (params == null) {
			throw new IllegalStateException("No " + (ageDependent ? "age-dependent " : "")
					+ "pathogen configuration for '" + pathogen.getName() + "'. Add a '" + PathogenParams.SET_TYPE
					+ "' parameter set for this pathogen to the '" + GROUPNAME + "' config group"
					+ (ageDependent ? " with ageDependent=true" : "") + ".");
		}
		return params;
	}

	/**
	 * Whether a parameter set for the pathogen (in the given variant) is present.
	 */
	public boolean hasParams(Pathogen pathogen, boolean ageDependent) {
		return store(ageDependent).containsKey(pathogen);
	}

	/**
	 * Get an existing or add a new (empty) parameter set for the given pathogen and variant.
	 */
	public PathogenParams getOrAddParams(Pathogen pathogen, boolean ageDependent) {
		PathogenParams existing = store(ageDependent).get(pathogen);
		if (existing != null)
			return existing;

		PathogenParams p = new PathogenParams();
		p.setPathogen(pathogen);
		p.setAgeDependent(ageDependent);
		addParameterSet(p);
		return p;
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);

		for (ConfigGroup set : getParameterSets(PathogenParams.SET_TYPE)) {
			((PathogenParams) set).validateComplete(getName());
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

		PathogenParams previous = store(p.isAgeDependent()).put(p.getPathogen(), p);

		// Replace a previously registered set (e.g. the auto-added SARS-CoV-2 default) instead of keeping a stale duplicate.
		if (previous != null)
			super.removeParameterSet(previous);

		super.addParameterSet(set);
	}

	/**
	 * Base disease-progression probabilities for a single {@link Pathogen}.
	 */
	public static final class PathogenParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "pathogenParams";

		private static final String PATHOGEN = "pathogen";
		private static final String AGE_DEPENDENT = "ageDependent";
		private static final String SHOWING_SYMPTOMS = "showingSymptomsProbabilityByAge";
		private static final String SERIOUSLY_SICK = "seriouslySickProbabilityByAge";
		private static final String CRITICAL = "criticalProbabilityByAge";
		private static final String DEATH = "deathProbabilityByAge";
		private static final String ROUTE_TRANSMISSIBILITY = "routeTransmissibility";

		private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
		private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

		/**
		 * Pathogen this parameter set applies to.
		 */
		private Pathogen pathogen = Pathogen.SARS_COV_2;

		/**
		 * Whether this parameter set carries a full age profile. Age-independent sets and age-dependent sets are
		 * stored separately so a pathogen can keep both the flat base values and the age tables.
		 */
		private boolean ageDependent = false;

		private final NavigableMap<Integer, Double> showingSymptomsProbabilityByAge = new TreeMap<>();
		private final NavigableMap<Integer, Double> seriouslySickProbabilityByAge = new TreeMap<>();
		private final NavigableMap<Integer, Double> criticalProbabilityByAge = new TreeMap<>();
		private final NavigableMap<Integer, Double> deathProbabilityByAge = new TreeMap<>();

		/**
		 * Per-route transmissibility of this pathogen. Relative, additive weights (no sum constraint);
		 * the infection model multiplies them route by route with the contact-side split from
		 * {@link ContactTransmissionConfigGroup}. Defaults to respiratory-only.
		 */
		private TransmissionWeights routeTransmissibility = TransmissionWeights.parse("respiratory=1.0");

		PathogenParams() {
			super(SET_TYPE);
		}

		/**
		 * SARS-CoV-2 defaults for the age-independent transition models
		 * ({@code DefaultDiseaseStatusTransitionModel}, {@code AntibodyDependentTransitionModel}).
		 */
		static PathogenParams sarsCov2AgeIndependentDefault() {
			PathogenParams p = new PathogenParams();
			p.setPathogen(Pathogen.SARS_COV_2);
			p.setAgeDependent(false);
			p.setShowingSymptomsProbabilityByAge(Map.of(0, 0.8));
			p.setSeriouslySickProbabilityByAge(Map.of(0, 0.05625));
			p.setCriticalProbabilityByAge(Map.of(0, 0.25));
			p.setDeathProbabilityByAge(Map.of(0, 0.0));
			// SARS-CoV-2 is treated as respiratory-only, matching the pre-route behaviour.
			p.setRouteTransmissibility(TransmissionWeights.parse("respiratory=1.0"));
			return p;
		}

		/**
		 * SARS-CoV-2 defaults for {@code AgeDependentDiseaseStatusTransitionModel}. The values and age boundaries
		 * are taken verbatim from that model; the {@code hospitalFactor} multiplier stays in the model.
		 */
		static PathogenParams sarsCov2AgeDependentDefault() {
			PathogenParams p = new PathogenParams();
			p.setPathogen(Pathogen.SARS_COV_2);
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
			// SARS-CoV-2 is treated as respiratory-only, matching the pre-route behaviour.
			p.setRouteTransmissibility(TransmissionWeights.parse("respiratory=1.0"));
			return p;
		}

		@StringGetter(PATHOGEN)
		public String getPathogenName() {
			return pathogen.getName();
		}

		public Pathogen getPathogen() {
			return pathogen;
		}

		@StringSetter(PATHOGEN)
		public void setPathogen(String pathogen) {
			setPathogen(new Pathogen(pathogen));
		}

		public void setPathogen(Pathogen pathogen) {
			this.pathogen = java.util.Objects.requireNonNull(pathogen, "Pathogen must not be null");
		}

		@StringGetter(AGE_DEPENDENT)
		public boolean isAgeDependent() {
			return ageDependent;
		}

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

		@StringGetter(SERIOUSLY_SICK)
		String getSeriouslySickProbabilityByAgeString() {
			return JOINER.join(seriouslySickProbabilityByAge);
		}

		@StringSetter(SERIOUSLY_SICK)
		void setSeriouslySickProbabilityByAge(String config) {
			replace(seriouslySickProbabilityByAge, parse(SERIOUSLY_SICK, config));
		}

		@StringGetter(CRITICAL)
		String getCriticalProbabilityByAgeString() {
			return JOINER.join(criticalProbabilityByAge);
		}

		@StringSetter(CRITICAL)
		void setCriticalProbabilityByAge(String config) {
			replace(criticalProbabilityByAge, parse(CRITICAL, config));
		}

		@StringGetter(DEATH)
		String getDeathProbabilityByAgeString() {
			return JOINER.join(deathProbabilityByAge);
		}

		@StringSetter(DEATH)
		void setDeathProbabilityByAge(String config) {
			replace(deathProbabilityByAge, parse(DEATH, config));
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

		/**
		 * Set the {@code showingSymptoms} age profile, replacing all previous entries.
		 */
		public void setShowingSymptomsProbabilityByAge(Map<Integer, Double> values) {
			replace(showingSymptomsProbabilityByAge, validated(SHOWING_SYMPTOMS, values));
		}

		/**
		 * Set the {@code seriouslySick} age profile, replacing all previous entries.
		 */
		public void setSeriouslySickProbabilityByAge(Map<Integer, Double> values) {
			replace(seriouslySickProbabilityByAge, validated(SERIOUSLY_SICK, values));
		}

		/**
		 * Set the {@code critical} age profile, replacing all previous entries.
		 */
		public void setCriticalProbabilityByAge(Map<Integer, Double> values) {
			replace(criticalProbabilityByAge, validated(CRITICAL, values));
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
		 * Ensure every transition has an age profile. Called on config finalisation, not on every
		 * {@code addParameterSet}, so that sets can still be filled in after being registered.
		 */
		void validateComplete(String groupName) {
			requireNonEmpty(groupName, SHOWING_SYMPTOMS, showingSymptomsProbabilityByAge);
			requireNonEmpty(groupName, SERIOUSLY_SICK, seriouslySickProbabilityByAge);
			requireNonEmpty(groupName, CRITICAL, criticalProbabilityByAge);
			requireNonEmpty(groupName, DEATH, deathProbabilityByAge);
			if (routeTransmissibility.isZero())
				throw new IllegalStateException("pathogen '" + pathogen.getName() + "' in config group '" + groupName
						+ "' has an all-zero '" + ROUTE_TRANSMISSIBILITY + "'; it cannot transmit by any route.");
		}

		private void requireNonEmpty(String groupName, String param, NavigableMap<Integer, Double> map) {
			if (map.isEmpty())
				throw new IllegalStateException("pathogen '" + pathogen.getName() + "' in config group '" + groupName
						+ "' is missing an age profile for '" + param + "'.");
		}

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
	}
}
