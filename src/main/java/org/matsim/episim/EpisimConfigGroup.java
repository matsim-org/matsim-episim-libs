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

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main config for episim.
 */
public final class EpisimConfigGroup extends ReflectiveConfigGroup {

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

	private static final String WRITE_EVENTS = "writeEvents";
	private static final String CALIBRATION_PARAMETER = "calibrationParameter";
	private static final String HOSPITAL_FACTOR = "hospitalFactor";
	private static final String INITIAL_INFECTIONS = "initialInfections";
	private static final String INITIAL_INFECTION_DISTRICT = "initialInfectionDistrict";
	private static final String INFECTIONS_PER_DAY = "infectionsPerDay";
	private static final String LOWER_AGE_BOUNDARY_FOR_INIT_INFECTIONS = "lowerAgeBoundaryForInitInfections";
	private static final String UPPER_AGE_BOUNDARY_FOR_INIT_INFECTIONS = "upperAgeBoundaryForInitInfections";
	private static final String MAX_CONTACTS = "maxContacts";
	private static final String SAMPLE_SIZE = "sampleSize";
	private static final String START_DATE = "startDate";
	private static final String SNAPSHOT_INTERVAL = "snapshotInterval";
	private static final String START_FROM_SNAPSHOT = "startFromSnapshot";
	private static final String SNAPSHOT_SEED = "snapshotSeed";
	private static final String LEISUREOUTDOORFRACTION = "leisureOutdoorFraction";

	private static final Logger log = LogManager.getLogger(EpisimConfigGroup.class);
	private static final String GROUPNAME = "episim";

	private final Trie<String, InfectionParams> paramsTrie = Tries.forStrings();
	/**
	 * Number of initial infections per day.
	 */
	private final Map<LocalDate, Integer> infectionsPerDay = new TreeMap<>();
	/**
	 * Leisure outdoor fractions per day.
	 */
	private final Map<LocalDate, Double> leisureOutdoorFraction = new TreeMap<>(Map.of(
			LocalDate.parse("2020-01-15"), 0.1,
			LocalDate.parse("2020-04-15"), 0.8,
			LocalDate.parse("2020-09-15"), 0.8,
			LocalDate.parse("2020-11-15"), 0.1,
			LocalDate.parse("2021-02-15"), 0.1,
			LocalDate.parse("2021-04-15"), 0.8,
			LocalDate.parse("2021-09-15"), 0.8)
			);
	/**
	 * Which events to write in the output.
	 */
	private WriteEvents writeEvents = WriteEvents.episim;
	// this is current default for 25% scenarios
	private double calibrationParameter = 0.000002;
	private double hospitalFactor = 1.;
	private double sampleSize = 0.1;
	private int initialInfections = 10;
	private int lowerAgeBoundaryForInitInfections = -1;
	private int upperAgeBoundaryForInitInfections = -1;
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
	/**
	 * Write snapshot every x days.
	 */
	private int snapshotInterval = 0;
	/**
	 * Path to snapshot file.
	 */
	private String startFromSnapshot = null;
	/**
	 * How the internal rng state should be handled.
	 */
	private SnapshotSeed snapshotSeed = SnapshotSeed.restore;
	private FacilitiesHandling facilitiesHandling = FacilitiesHandling.snz;
	private Config policyConfig = ConfigFactory.empty();
	private Config progressionConfig = ConfigFactory.empty();
	private String overwritePolicyLocation = null;
	private Class<? extends ShutdownPolicy> policyClass = FixedPolicy.class;
	private double maxContacts = 3.;

	/**
	 * Default constructor.
	 */
	public EpisimConfigGroup() {
		super(GROUPNAME);
	}

	public String getInputEventsFile() {
		List<EventFileParams> list = Lists.newArrayList(getInputEventsFiles());

		if (list.size() != 1) {
			throw new IllegalStateException("There is not exactly one input event file. Use .getEventFileParams() instead.");
		}

		return list.get(0).path;
	}

	/**
	 * Adds one single input event file for all days of the week.
	 */
	public void setInputEventsFile(String inputEventsFile) {

		clearParameterSetsForType(EventFileParams.SET_TYPE);
		addInputEventsFile(inputEventsFile)
				.addDays(DayOfWeek.values());
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

	/**
	 * Is multiplied with probability to transition to seriously sick in age dependent progression model
	 */
	@StringGetter(HOSPITAL_FACTOR)
	public double getHospitalFactor() {
		return this.hospitalFactor;
	}

	@StringSetter(HOSPITAL_FACTOR)
	public void setHospitalFactor(double hospitalFactor) {
		this.hospitalFactor = hospitalFactor;
	}

	@StringGetter(INITIAL_INFECTIONS)
	public int getInitialInfections() {
		return this.initialInfections;
	}

	/**
	 * @param initialInfections -- number of initial infections to start the dynamics.  These will be distributed over several days.
	 * @see       #setInfections_pers_per_day(Map)
	 */
	@StringSetter(INITIAL_INFECTIONS)
	public void setInitialInfections(int initialInfections) {
		this.initialInfections = initialInfections;
	}
	@StringGetter(LOWER_AGE_BOUNDARY_FOR_INIT_INFECTIONS)
	public int getLowerAgeBoundaryForInitInfections() {
		return this.lowerAgeBoundaryForInitInfections;
	}

	@StringSetter(LOWER_AGE_BOUNDARY_FOR_INIT_INFECTIONS)
	public void setLowerAgeBoundaryForInitInfections(int lowerAgeBoundaryForInitInfections) {
		this.lowerAgeBoundaryForInitInfections = lowerAgeBoundaryForInitInfections;
	}

	@StringGetter(UPPER_AGE_BOUNDARY_FOR_INIT_INFECTIONS)
	public int getUpperAgeBoundaryForInitInfections() {
		return this.upperAgeBoundaryForInitInfections;
	}

	@StringSetter(UPPER_AGE_BOUNDARY_FOR_INIT_INFECTIONS)
	public void setUpperAgeBoundaryForInitInfections(int upperAgeBoundaryForInitInfections) {
		this.upperAgeBoundaryForInitInfections = upperAgeBoundaryForInitInfections;
	}

	public Map<LocalDate, Integer> getInfections_pers_per_day() {
		return infectionsPerDay;
	}

	/**
	 * @param infectionsPerDay -- From each given date, this will be the number of infections.  Until {@link #setInitialInfections(int)} are used up.
	 */
	public void setInfections_pers_per_day(Map<LocalDate, Integer> infectionsPerDay) {
		// yyyy Is it really so plausible to have this here _and_ the plain integer initial infections?  kai, oct'20
		// yyyyyy Is it correct that the default of this is empty, so even if someone sets the initial infections to some number, this will not have any effect?  kai, nov'20
		// No, If no entry is present, 1 will be assumed (because this was default at some point).
		// This logic of handling no entries is not part of the config, but the initial infection handler  - cr, nov'20
		this.infectionsPerDay.clear();
		this.infectionsPerDay.putAll(infectionsPerDay);
	}

	@StringGetter(INFECTIONS_PER_DAY)
	String getInfectionsPerDay() {
		return JOINER.join(infectionsPerDay);
	}

	@StringSetter(INFECTIONS_PER_DAY)
	void setInfectionsPerDay(String capacity) {

		Map<String, String> map = SPLITTER.split(capacity);
		setInfections_pers_per_day(map.entrySet().stream().collect(Collectors.toMap(
				e -> LocalDate.parse(e.getKey()), e -> Integer.parseInt(e.getValue())
		)));
	}

	@StringGetter(INITIAL_INFECTION_DISTRICT)
	public String getInitialInfectionDistrict() {
		return initialInfectionDistrict;
	}

	@StringSetter(INITIAL_INFECTION_DISTRICT)
	public void setInitialInfectionDistrict(String initialInfectionDistrict) {
		this.initialInfectionDistrict = initialInfectionDistrict;
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

	@StringGetter(SNAPSHOT_INTERVAL)
	public int getSnapshotInterval() {
		return snapshotInterval;
	}

	@StringSetter(SNAPSHOT_INTERVAL)
	public void setSnapshotInterval(int snapshotInterval) {
		this.snapshotInterval = snapshotInterval;
	}

	@StringGetter(START_FROM_SNAPSHOT)
	public String getStartFromSnapshot() {
		return startFromSnapshot;
	}

	@StringSetter(START_FROM_SNAPSHOT)
	public void setStartFromSnapshot(String startFromSnapshot) {
		this.startFromSnapshot = startFromSnapshot;
	}

	@StringGetter(SNAPSHOT_SEED)
	public SnapshotSeed getSnapshotSeed() {
		return snapshotSeed;
	}

	@StringSetter(SNAPSHOT_SEED)
	public void setSnapshotSeed(SnapshotSeed snapshotSeed) {
		this.snapshotSeed = snapshotSeed;
	}

	public long getStartOffset() {
		return startOffset;
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

	@StringGetter("progressionConfig")
	public String getProgressionConfigName() {
		if (progressionConfig.origin().filename() != null)
			return progressionConfig.origin().filename();

		return progressionConfig.origin().description();
	}

	/**
	 * Gets the progression config configuration.
	 */
	public Config getProgressionConfig() {
		return progressionConfig;
	}

	public void setProgressionConfig(Config progressionConfig) {
		this.progressionConfig = progressionConfig;
	}

	/**
	 * Sets the progression config location as file name.
	 */
	@StringSetter("progressionConfig")
	public void setProgressionConfig(String progressionConfig) {
		if (progressionConfig == null)
			this.progressionConfig = ConfigFactory.empty();
		else {
			File file = new File(progressionConfig);
			if (!progressionConfig.equals("null") && !file.exists())
				throw new IllegalArgumentException("Progression config does not exist: " + progressionConfig);
			this.progressionConfig = ConfigFactory.parseFileAnySyntax(file);
		}
	}

	@StringGetter(MAX_CONTACTS)
	public double getMaxContacts() {
		return maxContacts;
	}

	@StringSetter(MAX_CONTACTS)
	public void setMaxContacts(double maxContacts) {
		this.maxContacts = maxContacts;
	}

	/**
	 * Sets the leisure outdoor fraction for the whole simulation period.
	 */
	public void setLeisureOutdoorFraction(double fraction) {
		setLeisureOutdoorFraction(Map.of(LocalDate.of(1970, 1, 1), fraction));
	}

	/**
	 * Sets the leisure outdoor fraction for individual days. If a day has no entry the values will be interpolated.
	 */
	public void setLeisureOutdoorFraction(Map<LocalDate, Double> fraction) {
		leisureOutdoorFraction.clear();
		leisureOutdoorFraction.putAll(fraction);
	}

	public Map<LocalDate, Double> getLeisureOutdoorFraction() {
		return leisureOutdoorFraction;
	}

	@StringSetter(LEISUREOUTDOORFRACTION)
	void setLeisureOutdoorFraction(String capacity) {

		Map<String, String> map = SPLITTER.split(capacity);
		setLeisureOutdoorFraction(map.entrySet().stream().collect(Collectors.toMap(
				e -> LocalDate.parse(e.getKey()), e -> Double.parseDouble(e.getValue())
		)));
	}

	@StringGetter(LEISUREOUTDOORFRACTION)
	String getLeisureOutdoorFractionString() {
		return JOINER.join(leisureOutdoorFraction);
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
			case EventFileParams.SET_TYPE:
				super.addParameterSet(set);
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
			case EventFileParams.SET_TYPE:
				return new EventFileParams();
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
			case EventFileParams.SET_TYPE:
				if (!(module instanceof EventFileParams)) {
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

		Optional<String> match = params.mappedNames.stream().filter(s -> paramsTrie.get(s, TrieMatch.STARTS_WITH) != null).findAny();
		if (match.isPresent()) {
			throw new IllegalArgumentException("New param for " + match.get() + " matches one of the already present params.");
		}

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
	public InfectionParams getOrAddContainerParams(final String containerName, String... mappedNames) {
		InfectionParams params = this.getContainerParams().get(containerName);

		if (params != null)
			return params;

		params = new InfectionParams(containerName, mappedNames);

		addParameterSet(params);
		return params;
	}

	/**
	 * Adds an event file to the config.
	 */
	public EventFileParams addInputEventsFile(final String path) {

		for (EventFileParams f : getInputEventsFiles()) {
			if (f.path.equals(path)) throw new IllegalArgumentException("Input file already defined: " + path);
		}

		EventFileParams params = new EventFileParams(path);
		addParameterSet(params);
		return params;
	}

	/**
	 * Removes all defined input files.
	 */
	public void clearInputEventsFiles() {
		clearParameterSetsForType(EventFileParams.SET_TYPE);
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
	 * All defined input event files.
	 */
	public Collection<EventFileParams> getInputEventsFiles() {
		return (Collection<EventFileParams>) getParameterSets(EventFileParams.SET_TYPE);
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

	/**
	 * Defines how the snapshot seed should be processed.
	 */
	public enum SnapshotSeed {
		/**
		 * Restore rng state from the snapshot and continue as before.
		 */
		restore,

		/**
		 * Overwrite the rng state with a new seed taken from config.
		 */
		reseed,
	}

	/**
	 * Parameter set for one activity type.
	 */
	public static final class InfectionParams extends ReflectiveConfigGroup {
		public static final String ACTIVITY_TYPE = "activityType";
		public static final String CONTACT_INTENSITY = "contactIntensity";
		public static final String SPACES_PER_FACILITY = "nSpacesPerFacility";
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
		 * Typical number of distinct spaces per facility.
		 */
		private double spacesPerFacility = 20.;


		/**
		 * See {@link #InfectionParams(String, String...)}. Name itself will also be used as prefix.
		 */
		InfectionParams(final String containerName) {
			this();
			this.containerName = containerName;
			this.mappedNames = Sets.newHashSet(containerName);
		}

		/**
		 * Constructor.
		 *
		 * @param containerName name of this activity type
		 * @param mappedNames   activity prefixes that will also be mapped to this container
		 */
		InfectionParams(final String containerName, String... mappedNames) {
			this();
			this.containerName = containerName;
			this.mappedNames = mappedNames.length == 0 ?
					Sets.newHashSet(containerName) : Sets.newHashSet(mappedNames);
		}

		/**
		 * Copy constructor.
		 */
		private InfectionParams(InfectionParams other) {
			this();
			this.containerName = other.containerName;
			this.mappedNames = other.mappedNames;
			this.contactIntensity = other.contactIntensity;
			this.spacesPerFacility = other.spacesPerFacility;
		}

		private InfectionParams() {
			super(SET_TYPE);
		}

		@StringGetter(MAPPED_NAMES)
		public String getMappedNames() {
			return Joiner.on(",").join(mappedNames);
		}

		@StringSetter(MAPPED_NAMES)
		void setMappedNames(String mappedNames) {
			this.mappedNames = Sets.newHashSet(mappedNames.split(","));
		}

		@StringGetter(ACTIVITY_TYPE)
		public String getContainerName() {
			return containerName;
		}

		@StringSetter(ACTIVITY_TYPE)
		void setContainerName(String actType) {
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
		 * Returns the spaces for facilities.
		 * @implNote Don't use this yet, may be removed or renamed.
		 */
		@Beta
		@StringGetter(SPACES_PER_FACILITY)
		public double getSpacesPerFacility() {
			return spacesPerFacility;
		}

		@Beta
		@StringSetter(SPACES_PER_FACILITY)
		public InfectionParams setSpacesPerFacility(double nSpacesPerFacility) {
			this.spacesPerFacility = nSpacesPerFacility;
			return this;
		}

		/**
		 * Create a copy of the this infection params.
		 *
		 * @return new contact intensity to set
		 */
		public InfectionParams copy(double contactIntensity) {
			return new InfectionParams(this).setContactIntensity(contactIntensity);
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

	/**
	 * Event file configuration for certain weekdays.
	 */
	public static final class EventFileParams extends ReflectiveConfigGroup {

		public static final String DAYS = "days";
		public static final String PATH = "path";
		static final String SET_TYPE = "eventFiles";
		private final Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		private String path;

		EventFileParams(String path) {
			this();
			this.path = path;
		}

		private EventFileParams() {
			super(SET_TYPE);
		}

		@StringGetter(PATH)
		public String getPath() {
			return path;
		}

		@StringSetter(PATH)
		void setPath(String path) {
			this.path = path;
		}

		/**
		 * Adds week days when this event file should be used.
		 */
		public void addDays(DayOfWeek... days) {
			this.days.addAll(Arrays.asList(days));
		}

		@StringGetter(DAYS)
		public Set<DayOfWeek> getDays() {
			return days;
		}

		@StringSetter(DAYS)
		public void setDays(String days) {
			String str = days.replace("[", "").replace(" ", "").replace("]", "");

			this.days.addAll(
					Arrays.stream(str.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toSet())
			);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			EventFileParams that = (EventFileParams) o;
			return path.equals(that.path) &&
					days.equals(that.days);
		}

		@Override
		public int hashCode() {
			return Objects.hash(path, days);
		}
	}
}
