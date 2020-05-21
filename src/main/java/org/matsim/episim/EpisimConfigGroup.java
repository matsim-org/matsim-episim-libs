/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magnos.trie.Trie;
import org.magnos.trie.TrieMatch;
import org.magnos.trie.Tries;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.ShutdownPolicy;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.time.LocalDate;
import java.util.*;

/**
 * Main config for episim.
 */
public final class EpisimConfigGroup extends ReflectiveConfigGroup {

	private static final String INPUT_EVENTS_FILE = "inputEventsFile";
	private static final String WRITE_EVENTS = "writeEvents";
	private static final String CALIBRATION_PARAMETER = "calibrationParameter";
	private static final String INITIAL_INFECTIONS = "initialInfections";
	private static final String INITIAL_INFECTION_DISTRICT = "initialInfectionDistrict";
	private static final String INITIAL_START_INFECTIONS = "initialStartInfections";
	private static final String MASK_COMPLIANCE = "maskCompliance";
	private static final String SAMPLE_SIZE = "sampleSize";
	private static final String START_DATE = "startDate";

	private static final Logger log = LogManager.getLogger(EpisimConfigGroup.class);
	private static final String GROUPNAME = "episim";

	private final Trie<String, InfectionParams> paramsTrie = Tries.forStrings();

	private String inputEventsFile = null;

	/**
	 * Which events to write in the output.
	 */
	private WriteEvents writeEvents = WriteEvents.episim;

	// this is current default for 25% scenarios
	private double calibrationParameter = 0.000002;
	private double sampleSize = 0.1;
	private int initialInfections = 10;
	private double maskCompliance = 1d;
	private int initialStartInfections = 0;
	/**
	 * If not null, filter persons for initial infection by district.
	 */
	private String initialInfectionDistrict = null;
	/**
	 * Start date of the simulation (Day 1).
	 */
	private LocalDate startDate = LocalDate.of(1970, 1, 1);
	/**
	 * Offset of start date in unix epoch seconds.
	 */
	private long startOffset = 0;

	private FacilitiesHandling facilitiesHandling = FacilitiesHandling.snz;
	private Config policyConfig = ConfigFactory.empty();
	private String overwritePolicyLocation = null;
	private Class<? extends ShutdownPolicy> policyClass = FixedPolicy.class;

	/**
	 * Default constructor.
	 */
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

	@StringGetter(WRITE_EVENTS)
	public WriteEvents getWriteEvents() {
		return writeEvents;
	}

	@StringSetter(WRITE_EVENTS)
	public void setWriteEvents(WriteEvents writeEvents) {
		this.writeEvents = writeEvents;
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

	@StringGetter(INITIAL_INFECTION_DISTRICT)
	public String getInitialInfectionDistrict() {
		return initialInfectionDistrict;
	}

	@StringSetter(INITIAL_INFECTION_DISTRICT)
	public void setInitialInfectionDistrict(String initialInfectionDistrict) {
		this.initialInfectionDistrict = initialInfectionDistrict;
	}

	@StringGetter(INITIAL_START_INFECTIONS)
	public int getInitialStartInfection() {
		return initialStartInfections;
	}

	@StringSetter(INITIAL_START_INFECTIONS)
	public void setInitialStartInfection(int initialStartInfections) {
		this.initialStartInfections = initialStartInfections;
	}

	@StringGetter(START_DATE)
	public LocalDate getStartDate() {
		return startDate;
	}

	@StringSetter(START_DATE)
	public void setStartDate(String startDate) {
		setStartDate(LocalDate.parse(startDate));
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
		this.startOffset = EpisimUtils.getStartOffset(startDate);
	}

	public long getStartOffset() {
		return startOffset;
	}

	@StringGetter(MASK_COMPLIANCE)
	public double getMaskCompliance() {
		return maskCompliance;
	}

	@StringSetter(MASK_COMPLIANCE)
	public void setMaskCompliance(double maskCompliance) {
		this.maskCompliance = maskCompliance;
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
		if (policyConfig == null)
			this.policyConfig = ConfigFactory.empty();
		else {
			File file = new File(policyConfig);
			if (!policyConfig.equals("null") && !file.exists())
				throw new IllegalArgumentException("Policy config does not exist: " + policyConfig);
			this.policyConfig = ConfigFactory.parseFileAnySyntax(file);
		}
	}

	/**
	 * Overwrite the policy location, which will be returned by {@link #getPolicyConfig()}.
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
	public Map<String, Restriction> createInitialRestrictions() {
		Map<String, Restriction> r = new LinkedHashMap<>();
		getContainerParams().forEach((s, p) -> r.put(s, Restriction.none()));
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
	@NotNull
	public InfectionParams selectInfectionParams(String activity) {

		InfectionParams params = paramsTrie.get(activity, TrieMatch.STARTS_WITH);
		if (params != null)
			return params;

		throw new NoSuchElementException(String.format("No params known for activity %s. Please add prefix to one infection parameter.", activity));
	}

	/**
	 * Get the {@link InfectionParams} of a container by its name.
	 */
	public InfectionParams getInfectionParam(String containerName) {
		return this.getContainerParams().get(containerName);
	}

	/**
	 * All defined infection parameter.
	 */
	public Collection<InfectionParams> getInfectionParams() {
		return (Collection<InfectionParams>) getParameterSets(InfectionParams.SET_TYPE);
	}

	/**
	 * Defines how facilities should be handled.
	 */
	public enum FacilitiesHandling {
		/**
		 * A facility id will be constructed using the link id where the activity is performed.
		 */
		bln,
		/**
		 * Facilities ids of activities will be used directly.
		 */
		snz
	}

	/**
	 * Defines which events will be written.
	 */
	public enum WriteEvents {
		/**
		 * Disable event writing completely.
		 */
		none,
		/**
		 * Write basic events like infections or disease status change.
		 */
		episim,
		/**
		 * Write additional contact tracing events.
		 */
		tracing,
		/**
		 * Write all, including input events.
		 */
		all
	}
	private int maxInteractions = 3;
	public int getMaxInteractions(){
		return maxInteractions;
	}
	public void setMaxInteractions( int maxInteractions ){
		this.maxInteractions = maxInteractions;
	}

	/**
	 * Parameter set for one activity type.
	 */
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

		/**
		 * See {@link #InfectionParams(String, String...)}. Name itself will also be used as prefix.
		 */
		public InfectionParams(final String containerName) {
			this();
			this.containerName = containerName;
			this.mappedNames = Sets.newHashSet(containerName);
		}

		/**
		 * Constructor.
		 *
		 * @param containerName name name of this activity type
		 * @param mappedNames   activity prefixes that will also be mapped to this container
		 */
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
