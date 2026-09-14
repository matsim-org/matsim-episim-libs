package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.ImmunityEvent;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Config option specific to the antibody model ({@link org.matsim.episim.model.AntibodyModel}).
 *
 * <p>For every {@link ImmunityEvent} (a {@link VaccinationType} or a {@link VirusStrain}) that can trigger an
 * immune reaction, one {@link AntibodyParams} parameter set holds two per-{@link VirusStrain} maps:</p>
 * <ul>
 *     <li>{@code initialAntibodies} &mdash; the antibody level a person reaches right after the immunity event,
 *     against each virus strain;</li>
 *     <li>{@code antibodyRefreshFactors} &mdash; the factor by which an existing antibody level is refreshed
 *     when the same immunity event happens again ({@code NaN} where no refresh is defined).</li>
 * </ul>
 *
 * <p>A strain without an entry is not affected by the immunity event: no antibodies are induced against it and an
 * existing level is not refreshed. This allows strains of other pathogens to be configured independently.</p>
 *
 * <p>The default constructor fills both maps with the values of the former hard-coded {@code AntibodyModel.Config},
 * so a configuration without an explicit {@code antibodies} section keeps behaving as before.</p>
 */
@SuppressWarnings({"checkstyle:MethodName", "checkstyle:AbbreviationAsWordInName"})
public class AntibodyConfigGroup extends ReflectiveConfigGroup {

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

	private static final String GROUPNAME = "antibodies";

	private static final String IMMUNE_RESPONSE_SIGMA = "immuneResponseSigma";
	private static final String HL_MULTIPLIER_FOR_INFECTED = "hlMultiplierForInfected";

	/**
	 * Sigma of the log-normal distribution used to draw a person's individual immune-response multiplier.
	 * {@code 0} means every person reacts identically.
	 */
	private double immuneResponseSigma = 0.;

	/**
	 * Half-life multiplier applied to the antibody decay of persons that gained their immunity through
	 * infection (as opposed to vaccination).
	 */
	private double hlMultiplierForInfected = 1.0;

	/**
	 * Holds all immunity-event specific params, keyed by the triggering {@link ImmunityEvent}.
	 */
	private final Map<ImmunityEvent, AntibodyParams> params = new LinkedHashMap<>();

	/**
	 * Default constructor. Registers the same initial-antibody and refresh-factor values that
	 * the former hard-coded {@code AntibodyModel.Config} used.
	 */
	public AntibodyConfigGroup() {
		super(GROUPNAME);

		Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = defaultInitialAntibodies();
		Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = defaultAntibodyRefreshFactors();

		for (Map.Entry<ImmunityEvent, Map<VirusStrain, Double>> e : initialAntibodies.entrySet()) {
			ImmunityEvent immunityEvent = e.getKey();
			AntibodyParams p = new AntibodyParams();
			p.setImmunityEvent(immunityEvent);
			p.setInitialAntibodies(e.getValue());
			p.setAntibodyRefreshFactors(antibodyRefreshFactors.getOrDefault(immunityEvent, Map.of()));
			addParameterSet(p);
		}
	}

	/**
	 * Get config parameter for a specific immunity event.
	 */
	public AntibodyParams getParams(ImmunityEvent immunityEvent) {
		if (!params.containsKey(immunityEvent))
			throw new IllegalStateException("Immunity event " + immunityEvent + " is not configured. Add an '"
					+ AntibodyParams.SET_TYPE + "' parameter set for it to the '" + GROUPNAME + "' config group.");

		return params.get(immunityEvent);
	}

	/**
	 * Whether params for the given immunity event are present.
	 */
	public boolean hasParams(ImmunityEvent immunityEvent) {
		return params.containsKey(immunityEvent);
	}

	/**
	 * Get an existing or add a new (empty) parameter set for the given immunity event.
	 */
	public AntibodyParams getOrAddParams(ImmunityEvent immunityEvent) {
		if (!params.containsKey(immunityEvent)) {
			AntibodyParams p = new AntibodyParams();
			p.setImmunityEvent(immunityEvent);
			addParameterSet(p);
			return p;
		}

		return params.get(immunityEvent);
	}

	/**
	 * Initial antibody levels for every configured immunity event, in the same nested-map shape as
	 * the former {@code AntibodyModel.Config}.
	 */
	public Map<ImmunityEvent, Map<VirusStrain, Double>> getInitialAntibodies() {
		Map<ImmunityEvent, Map<VirusStrain, Double>> result = new LinkedHashMap<>();
		for (Map.Entry<ImmunityEvent, AntibodyParams> e : params.entrySet())
			result.put(e.getKey(), new LinkedHashMap<>(e.getValue().getInitialAntibodies()));

		return result;
	}

	/**
	 * Antibody refresh factors for every configured immunity event, in the same nested-map shape as
	 * the former {@code AntibodyModel.Config}.
	 */
	public Map<ImmunityEvent, Map<VirusStrain, Double>> getAntibodyRefreshFactors() {
		Map<ImmunityEvent, Map<VirusStrain, Double>> result = new LinkedHashMap<>();
		for (Map.Entry<ImmunityEvent, AntibodyParams> e : params.entrySet())
			result.put(e.getKey(), new LinkedHashMap<>(e.getValue().getAntibodyRefreshFactors()));

		return result;
	}

	@StringGetter(IMMUNE_RESPONSE_SIGMA)
	public double getImmuneResponseSigma() {
		return immuneResponseSigma;
	}

	@StringSetter(IMMUNE_RESPONSE_SIGMA)
	public void setImmuneResponseSigma(double immuneResponseSigma) {
		this.immuneResponseSigma = immuneResponseSigma;
	}

	@StringGetter(HL_MULTIPLIER_FOR_INFECTED)
	public double getHlMultiplierForInfected() {
		return hlMultiplierForInfected;
	}

	@StringSetter(HL_MULTIPLIER_FOR_INFECTED)
	public void setHlMultiplierForInfected(double hlMultiplierForInfected) {
		this.hlMultiplierForInfected = hlMultiplierForInfected;
	}

	@Override
	public ConfigGroup createParameterSet(String type) {
		if (AntibodyParams.SET_TYPE.equals(type)) {
			return new AntibodyParams();
		}
		throw new IllegalArgumentException("Unknown type " + type);
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		if (AntibodyParams.SET_TYPE.equals(set.getName())) {
			AntibodyParams p = (AntibodyParams) set;
			AntibodyParams previous = params.put(p.getImmunityEvent(), p);
			// replace a previously registered set (e.g. an auto-added default) instead of keeping a stale duplicate
			if (previous != null){
				super.removeParameterSet(previous);}
			super.addParameterSet(set);

		} else
			throw new IllegalStateException("Unknown set type " + set.getName());
	}

	/**
	 * Builds the default initial-antibody map. Ported verbatim (values-wise) from
	 * the former {@code AntibodyModel.Config}; only the map implementation is swapped for a deterministic
	 * {@link LinkedHashMap}.
	 */
	private static Map<ImmunityEvent, Map<VirusStrain, Double>> defaultInitialAntibodies() {

		Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new LinkedHashMap<>();

		for (VaccinationType immunityType : VaccinationType.getAllStandardOptions()) {
			initialAntibodies.put(immunityType, new LinkedHashMap<>());
			for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {

				if (immunityType == VaccinationType.mRNA) {
					initialAntibodies.get(immunityType).put(virusStrain, 29.2); //10.0
				} else if (immunityType == VaccinationType.vector) {
					initialAntibodies.get(immunityType).put(virusStrain, 6.8);  //2.5
				} else {
					initialAntibodies.get(immunityType).put(virusStrain, 5.0);
				}
			}
		}

		for (VirusStrain immunityType : VirusStrain.getAllStandardOptions()) {
			initialAntibodies.put(immunityType, new LinkedHashMap<>());
			for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {
				initialAntibodies.get(immunityType).put(virusStrain, 5.0);
			}
		}

		//mRNAAlpha, mRNADelta, mRNABA1 comes from Sydney's calibration.
		//The other values come from Rössler et al.

		double mutEscDelta = 29.2 / 10.9;
		double mutEscBa1 = 10.9 / 1.9;
		double mutEscBa5 = 2.9;

		//Wildtype
		double mRNAAlpha = 29.2;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.SARS_CoV_2, mRNAAlpha);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.SARS_CoV_2, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.SARS_CoV_2, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.SARS_CoV_2, 0.01);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.SARS_CoV_2, mRNAAlpha / mutEscDelta / mutEscBa1);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.SARS_CoV_2, mRNAAlpha / mutEscDelta / mutEscBa1 / mutEscBa5);

		//Alpha
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.ALPHA, mRNAAlpha);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.ALPHA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.ALPHA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.ALPHA, 0.01);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.ALPHA, mRNAAlpha / mutEscDelta / mutEscBa1);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.ALPHA, mRNAAlpha / mutEscDelta / mutEscBa1 / mutEscBa5);

		//DELTA
		double mRNADelta = mRNAAlpha / mutEscDelta;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150. / 300.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450. / 300.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.DELTA, 0.01);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.DELTA, mRNADelta / mutEscBa1);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.DELTA, mRNADelta / mutEscBa1 / mutEscBa5);

		//BA.1
		double mRNABA1 = mRNADelta / mutEscBa1;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, mRNABA1);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, mRNABA1 * 4. / 20.); //???
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 8. / 20.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 64.0 / 300.);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / mutEscBa5); //todo: is 1.4
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA1, mRNAAlpha);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA1, mRNAAlpha / mutEscBa5);

		//BA.2
		double mRNABA2 = mRNABA1;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, mRNABA2);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, mRNABA2 * 4. / 20.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 8. / 20.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / mutEscBa5);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA2, mRNAAlpha);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA2, mRNAAlpha / mutEscBa5);

		//BA.5
		double mRNABa5 = mRNABA2 / mutEscBa5;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA5, mRNABa5);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA5, mRNABa5 * 4. / 20.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 8. / 20.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / mutEscBa5); // todo: do we need 1.4?
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / mutEscBa5);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA5, 64.0 / 300.);
		initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA5, mRNAAlpha / mutEscBa5);
		initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA5, mRNAAlpha);

		return initialAntibodies;
	}

	/**
	 * Builds the default antibody-refresh-factor map. Ported verbatim (values-wise) from
	 * the former {@code AntibodyModel.Config}.
	 */
	private static Map<ImmunityEvent, Map<VirusStrain, Double>> defaultAntibodyRefreshFactors() {

		Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new LinkedHashMap<>();

		for (VaccinationType immunityType : VaccinationType.getAllStandardOptions()) {
			antibodyRefreshFactors.put(immunityType, new LinkedHashMap<>());
			for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {

				if (immunityType == VaccinationType.mRNA) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				} else if (immunityType == VaccinationType.vector) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
				} else if (immunityType == VaccinationType.ba1Update) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				} else if (immunityType == VaccinationType.ba5Update) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				} else {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, Double.NaN);
				}

			}
		}

		for (VirusStrain immunityType : VirusStrain.getAllStandardOptions()) {
			antibodyRefreshFactors.put(immunityType, new LinkedHashMap<>());
			for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {
				antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
			}
		}

		return antibodyRefreshFactors;
	}

	/**
	 * Antibody parameters that apply to a single {@link ImmunityEvent}.
	 */
	public static final class AntibodyParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "antibodyParams";

		static final String KIND_VACCINATION = "vaccination";
		static final String KIND_STRAIN = "strain";

		private static final String IMMUNITY_EVENT = "immunityEvent";
		private static final String IMMUNITY_EVENT_KIND = "immunityEventKind";
		private static final String INITIAL_ANTIBODIES = "initialAntibodies";
		private static final String ANTIBODY_REFRESH_FACTORS = "antibodyRefreshFactors";

		/**
		 * Name of the immunity event ({@link VaccinationType} constant name or {@link VirusStrain} name).
		 */
		private String immunityEventName;

		/**
		 * Discriminator that says how to resolve {@link #immunityEventName} back into an {@link ImmunityEvent}.
		 */
		private String immunityEventKind;

		/**
		 * Antibody level reached against each strain right after this immunity event.
		 */
		private final Map<VirusStrain, Double> initialAntibodies = new LinkedHashMap<>();

		/**
		 * Refresh factor applied against each strain when this immunity event repeats.
		 */
		private final Map<VirusStrain, Double> antibodyRefreshFactors = new LinkedHashMap<>();

		AntibodyParams() {
			super(SET_TYPE);
		}

		@StringGetter(IMMUNITY_EVENT)
		public String getImmunityEventName() {
			return immunityEventName;
		}

		@StringSetter(IMMUNITY_EVENT)
		public void setImmunityEventName(String immunityEventName) {
			this.immunityEventName = immunityEventName;
		}

		@StringGetter(IMMUNITY_EVENT_KIND)
		public String getImmunityEventKind() {
			return immunityEventKind;
		}

		@StringSetter(IMMUNITY_EVENT_KIND)
		public void setImmunityEventKind(String immunityEventKind) {
			this.immunityEventKind = immunityEventKind;
		}

		/**
		 * Resolve the configured {@link ImmunityEvent}.
		 */
		public ImmunityEvent getImmunityEvent() {
			Objects.requireNonNull(immunityEventKind, IMMUNITY_EVENT_KIND + " must be set");
			Objects.requireNonNull(immunityEventName, IMMUNITY_EVENT + " must be set");

			switch (immunityEventKind) {
				case KIND_VACCINATION:
					return VaccinationType.of(immunityEventName);
				case KIND_STRAIN:
					return VirusStrain.of(immunityEventName);
				default:
					throw new IllegalStateException("Unknown " + IMMUNITY_EVENT_KIND + " '" + immunityEventKind + "'");
			}
		}

		/**
		 * Set the immunity event this parameter set applies to.
		 */
		public void setImmunityEvent(ImmunityEvent immunityEvent) {
			Objects.requireNonNull(immunityEvent, "immunityEvent must not be null");

			if (immunityEvent instanceof VaccinationType) {
				this.immunityEventKind = KIND_VACCINATION;
				this.immunityEventName = ((VaccinationType) immunityEvent).getName();
			} else if (immunityEvent instanceof VirusStrain) {
				this.immunityEventKind = KIND_STRAIN;
				this.immunityEventName = ((VirusStrain) immunityEvent).getVirusStrainName();
			} else {
				throw new IllegalArgumentException("Unsupported immunity event type: " + immunityEvent.getClass());
			}
		}

		@StringGetter(INITIAL_ANTIBODIES)
		String getInitialAntibodiesString() {
			return join(initialAntibodies);
		}

		@StringSetter(INITIAL_ANTIBODIES)
		void setInitialAntibodiesString(String config) {
			replace(initialAntibodies, parse(config));
		}

		@StringGetter(ANTIBODY_REFRESH_FACTORS)
		String getAntibodyRefreshFactorsString() {
			return join(antibodyRefreshFactors);
		}

		@StringSetter(ANTIBODY_REFRESH_FACTORS)
		void setAntibodyRefreshFactorsString(String config) {
			replace(antibodyRefreshFactors, parse(config));
		}

		/**
		 * Initial antibody level per strain (live map).
		 */
		public Map<VirusStrain, Double> getInitialAntibodies() {
			return initialAntibodies;
		}

		/**
		 * Antibody refresh factor per strain (live map).
		 */
		public Map<VirusStrain, Double> getAntibodyRefreshFactors() {
			return antibodyRefreshFactors;
		}

		/**
		 * Set the initial antibody levels per strain, replacing all previous entries.
		 */
		public AntibodyParams setInitialAntibodies(Map<VirusStrain, Double> values) {
			replace(initialAntibodies, values);
			return this;
		}

		/**
		 * Set the antibody refresh factors per strain, replacing all previous entries.
		 */
		public AntibodyParams setAntibodyRefreshFactors(Map<VirusStrain, Double> values) {
			replace(antibodyRefreshFactors, values);
			return this;
		}

		private static String join(Map<VirusStrain, Double> map) {
			Map<String, Double> byName = new LinkedHashMap<>();
			for (Map.Entry<VirusStrain, Double> e : map.entrySet())
				byName.put(e.getKey().getVirusStrainName(), e.getValue());

			return JOINER.join(byName);
		}

		private static Map<VirusStrain, Double> parse(String config) {
			Map<VirusStrain, Double> parsed = new LinkedHashMap<>();
			if (config == null || config.isBlank())
				return parsed;

			for (Map.Entry<String, String> e : SPLITTER.split(config).entrySet()) {
				parsed.put(VirusStrain.of(e.getKey().trim()), Double.parseDouble(e.getValue().trim()));
			}
			return parsed;
		}

		private static void replace(Map<VirusStrain, Double> target, Map<VirusStrain, Double> values) {
			target.clear();
			target.putAll(values);
		}
	}
}
