package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.log4j.Logger;
import org.magnos.trie.Trie;
import org.magnos.trie.TrieMatch;
import org.magnos.trie.Tries;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.ShutdownPolicy;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.*;

public final class EpisimConfigGroup extends ReflectiveConfigGroup {

	private static final String INPUT_EVENTS_FILE = "inputEventsFile";
	private static final String OUTPUT_EVENTS_FOLDER = "outputEventsFolder";
	private static final String CALIBRATION_PARAMETER = "calibrationParameter";
	private static final String INITIAL_INFECTIONS = "initialInfections";
	private static final String PUT_TRACABLE_PERSONS_IN_QUARANTINE = "pubTracablePersonsInQuarantine";
	private static final String SAMPLE_SIZE = "sampleSize";

	private static final Logger log = Logger.getLogger(EpisimConfigGroup.class);
	private static final String GROUPNAME = "episim";

	private final Trie<String, InfectionParams> paramsTrie = Tries.forStrings();

	private String inputEventsFile = null;
	private String outputEventsFolder = null;
	
	// this is current default for 25% scenarios
	private double calibrationParameter = 0.000002;
	private double sampleSize = 0.1;
	private int initialInfections = 10;
	private PutTracablePersonsInQuarantine putTracablePersonsInQuarantine = PutTracablePersonsInQuarantine.no;
	private FacilitiesHandling facilitiesHandling = FacilitiesHandling.snz;
	private Config policyConfig = ConfigFactory.empty();
	private String overwritePolicyLocation = null;
	private Class<? extends ShutdownPolicy> policyClass = FixedPolicy.class;

	public EpisimConfigGroup() {
		super(GROUPNAME);
	}

	@StringGetter(INPUT_EVENTS_FILE)
	public String getInputEventsFile() {
		return this.inputEventsFile;
	}

	@StringSetter(INPUT_EVENTS_FILE)
	public void setInputEventsFile(String inputEventsFile) {
		this.inputEventsFile = inputEventsFile;
	}

	/**
	 * Events will be written into subfolder of output (only if set)
	 */
	@StringGetter(OUTPUT_EVENTS_FOLDER)
	public String getOutputEventsFolder() {
		return outputEventsFolder;
	}

	@StringSetter(OUTPUT_EVENTS_FOLDER)
	public void setOutputEventsFolder(String outputEventsFolder) {
		this.outputEventsFolder = outputEventsFolder;
	}

	@StringGetter(CALIBRATION_PARAMETER)
	public double getCalibrationParameter() {
		return this.calibrationParameter;
	}

	@StringSetter(CALIBRATION_PARAMETER)
	public void setCalibrationParameter(double calibrationParameter) {
		this.calibrationParameter = calibrationParameter;
	}
	
	@StringGetter(INITIAL_INFECTIONS)
	public int getInitialInfections() {
		return this.initialInfections;
	}

	@StringSetter(INITIAL_INFECTIONS)
	public void setInitialInfections(int initialInfections) {
		this.initialInfections = initialInfections;
	}

	@StringGetter(PUT_TRACABLE_PERSONS_IN_QUARANTINE)
	public PutTracablePersonsInQuarantine getPutTracablePersonsInQuarantine() {
		return this.putTracablePersonsInQuarantine;
	}

	@StringSetter(PUT_TRACABLE_PERSONS_IN_QUARANTINE)
	public void setPutTracablePersonsInQuarantine(PutTracablePersonsInQuarantine putTracablePersonsInQuarantine) {
		this.putTracablePersonsInQuarantine = putTracablePersonsInQuarantine;
	}

	/**
	 * Sample size in relation to whole population, between (0, 1].
	 */
	@StringGetter(SAMPLE_SIZE)
	public double getSampleSize() {
		return sampleSize;
	}

	@StringSetter(SAMPLE_SIZE)
	public void setSampleSize(double sampleSize) {
		this.sampleSize = sampleSize;
	}

	@StringGetter("policyClass")
	public String getPolicyClass() {
		return policyClass.getName();
	}

	@StringSetter("policyClass")
	public void setPolicyClass(String policyClass) {
		try {
			this.policyClass = (Class<? extends ShutdownPolicy>) ClassLoader.getSystemClassLoader().loadClass(policyClass);
		} catch (ClassNotFoundException e) {
			log.error("Policy class not found", e);
			throw new IllegalArgumentException(e);
		}
	}

	@StringGetter("policyConfig")
	public String getPolicyConfig() {
		if (overwritePolicyLocation != null)
			return overwritePolicyLocation;

		return policyConfig.origin().filename();
	}

	/**
	 * Set the policy config instance.
	 */
	public void setPolicyConfig(Config policyConfig) {
		this.policyConfig = policyConfig;
	}

	/**
	 * Sets policy config by loading it from a file first.
	 *
	 * @param policyConfig resource of filename to policy
	 */
	@StringSetter("policyConfig")
	public void setPolicyConfig(String policyConfig) {
		File file = new File(policyConfig);
		if (!policyConfig.equals("null") && !file.exists())
			throw new IllegalArgumentException("Policy config does not exist: " + policyConfig);
		this.policyConfig = ConfigFactory.parseFileAnySyntax(file);
	}

	/**
	 * Overwrite the policy location, which will be returned by {@link #getPolicyConfig()}
	 */
	public void setOverwritePolicyLocation(String overwritePolicyLocation) {
		this.overwritePolicyLocation = overwritePolicyLocation;
	}

	/**
	 * Gets the actual policy configuration.
	 */
	public Config getPolicy() {
		return policyConfig;
	}

	/**
	 * Sets policy class and desired config.
	 */
	public void setPolicy(Class<? extends ShutdownPolicy> policy, Config config) {
		this.policyClass = policy;
		this.policyConfig = config;
	}

	/**
	 * Create a configured instance of the desired policy.
	 */
	public ShutdownPolicy createPolicyInstance() {
		try {
			return policyClass.getConstructor(Config.class).newInstance(policyConfig);
		} catch (ReflectiveOperationException e) {
			log.error("Could not create policy", e);
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Create restriction for each {@link InfectionParams}.
	 */
	public Map<String, ShutdownPolicy.Restriction> createInitialRestrictions() {
		Map<String, ShutdownPolicy.Restriction> r = new LinkedHashMap<>();
		getContainerParams().forEach((s, p) -> r.put(s, ShutdownPolicy.Restriction.newInstance()));
		return r;
	}

	@StringGetter("facilitiesHandling")
	public FacilitiesHandling getFacilitiesHandling() {
		return facilitiesHandling;
	}

	@StringSetter("facilitiesHandling")
	public void setFacilitiesHandling(FacilitiesHandling facilitiesHandling) {
		this.facilitiesHandling = facilitiesHandling;
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		// this is, I think, necessary for the automatic reading from file, and possibly for the commandline stuff.
		switch (set.getName()) {
			case InfectionParams.SET_TYPE:
				addContainerParams((InfectionParams) set);
				break;
			default:
				throw new IllegalArgumentException(set.getName());
		}
	}

	@Override
	public ConfigGroup createParameterSet(final String type) {
		switch (type) {
			case InfectionParams.SET_TYPE:
				return new InfectionParams();
			default:
				throw new IllegalArgumentException(type);
		}
	}

	@Override
	protected void checkParameterSet(final ConfigGroup module) {
		switch (module.getName()) {
			case InfectionParams.SET_TYPE:
				if (!(module instanceof InfectionParams)) {
					throw new IllegalArgumentException("unexpected class for module " + module);
				}
				break;
			default:
				throw new IllegalArgumentException(module.getName());
		}
	}

	/**
	 * Adds given params to the parameter set, replacing existing ones.
	 */
	public void addContainerParams(final InfectionParams params) {
		final InfectionParams previous = this.getContainerParams().get(params.getContainerName());

		params.mappedNames.forEach(name -> paramsTrie.put(name, params));

		if (previous != null) {
			log.info("scoring parameters for activityType=" + previous.getContainerName() + " were just replaced.");

			params.mappedNames.forEach(paramsTrie::remove);

			final boolean removed = removeParameterSet(previous);
			if (!removed)
				throw new IllegalStateException("problem replacing params");
		}

		super.addParameterSet(params);
	}

	/**
	 * Returns a container from the parameter set if it exists or creates a new one.
	 */
	public InfectionParams getOrAddContainerParams(final String containerName) {
		InfectionParams params = this.getContainerParams().get(containerName);

		if (params != null)
			return params;

		params = new InfectionParams(containerName);

		addParameterSet(params);
		return params;
	}

	/**
	 * Get a copy of container params. Don't use this heavily, it is slow because a new map is created every time.
	 */
	Map<String, InfectionParams> getContainerParams() {
		@SuppressWarnings("unchecked") final Collection<InfectionParams> parameters = (Collection<InfectionParams>) getParameterSets(InfectionParams.SET_TYPE);
		final Map<String, InfectionParams> map = new LinkedHashMap<>();

		for (InfectionParams pars : parameters) {
			if (this.isLocked()) {
				pars.setLocked();
			}
			map.put(pars.getContainerName(), pars);
		}

		return Collections.unmodifiableMap(map);
	}

	/**
	 * Lookup which infection param is relevant for an activity. Throws exception when none was found.
	 *
	 * @param activity full activity identifier (including id etc.)
	 * @return matched infection param
	 * @throws NoSuchElementException when no param could be matched
	 */
	public @NotNull
	InfectionParams selectInfectionParams(String activity) {

		InfectionParams params = paramsTrie.get(activity, TrieMatch.STARTS_WITH);
		if (params != null)
			return params;

		throw new NoSuchElementException("No params known for activity %s. Please add prefix to one infection parameter.");
	}

	public Collection<InfectionParams> getInfectionParams() {
		return (Collection<InfectionParams>) getParameterSets(InfectionParams.SET_TYPE);
	}

	public enum PutTracablePersonsInQuarantine {yes, no}

	public enum FacilitiesHandling {bln, snz}

	public static final class InfectionParams extends ReflectiveConfigGroup {
		public static final String ACTIVITY_TYPE = "activityType";
		public static final String CONTACT_INTENSITY = "contactIntensity";
		public static final String MAPPED_NAMES = "mappedNames";

		static final String SET_TYPE = "infectionParams";
		/**
		 * Name of the container as reference by {@link ShutdownPolicy}.
		 */
		private String containerName;

		/**
		 * Prefixes of activity names that will be associated with this container type.
		 */
		private Set<String> mappedNames;
		private double contactIntensity = 1.;

		public InfectionParams(final String containerName) {
			this();
			this.containerName = containerName;
			this.mappedNames = Sets.newHashSet(containerName);
		}

		public InfectionParams(final String containerName, String... mappedNames) {
			this();
			this.containerName = containerName;
			this.mappedNames = Sets.newHashSet(mappedNames);
		}

		private InfectionParams() {
			super(SET_TYPE);
		}

		@StringGetter(MAPPED_NAMES)
		public String getMappedNames() {
			return Joiner.on(",").join(mappedNames);
		}

		@StringSetter(MAPPED_NAMES)
		public void setMappedNames(String mappedNames) {
			this.mappedNames = Sets.newHashSet(mappedNames.split(","));
		}

		@StringGetter(ACTIVITY_TYPE)
		public String getContainerName() {
			return containerName;
		}

		@StringSetter(ACTIVITY_TYPE)
		public void setContainerName(String actType) {
			this.containerName = actType;
		}

		/**
		 * this is from iteration 0!
		 */
		@StringGetter(CONTACT_INTENSITY)
		public double getContactIntensity() {
			return contactIntensity;
		}

		/**
		 * this is from iteration 0!
		 **/
		@StringSetter(CONTACT_INTENSITY)
		public InfectionParams setContactIntensity(double contactIntensity) {
			this.contactIntensity = contactIntensity;
			return this;
		}

		/**
		 * Check whether an activity belong to this container group.
		 */
		public boolean includesActivity(String actType) {
			for (String mapped : mappedNames)
				if (actType.startsWith(mapped))
					return true;

			return false;
		}

	}

}
