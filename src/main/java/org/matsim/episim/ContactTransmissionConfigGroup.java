package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.ContactTransmissionType;
import org.matsim.episim.model.TransmissionWeights;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-contact split of infectious exposure across {@link ContactTransmissionType} routes
 * ({@code respiratory}, {@code directContact}; {@code fomite} is deferred).
 *
 * <p>For a contact between two activity types &mdash; optionally resolved further by the two persons' age
 * bands &mdash; a {@link TransmissionWeights} vector says how that contact's exposure is distributed over
 * the routes. The weights are <b>relative</b>: they carry no absolute magnitude. The infection model
 * combines them, route by route, with the pathogen's own per-route efficiency
 * ({@code routeTransmissibility} in {@link PathogenConfigGroup}):</p>
 * <pre>
 *   via[route] = contactWeight[route] &middot; pathogenTransmissibility[route] &middot; routeModifiers
 *   p         = 1 - exp( -base &middot; &Sigma; via[route] )
 * </pre>
 * <p>{@code base} carries the absolute scale (global {@code calibrationParameter}, the per-contact
 * {@code contactIntensity} and joint time, susceptibility, infectiousness, &hellip;); {@code routeModifiers}
 * are route-specific attenuations (face masks, ventilation and indoor/outdoor dilution apply to
 * {@code respiratory} only). {@code respiratory=1.0} is the conventional anchor: with the default weights
 * on both the contact and the pathogen side the formula reduces to the previous respiratory-only model.
 * The weights are <b>not</b> normalised &mdash; each route is an independent, additive channel, so a
 * contact can carry, say, {@code respiratory=1.0;directContact=0.5} without the routes competing for a
 * shared budget.</p>
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>{@code defaultTransmissionWeights} &mdash; used for any contact not covered by a {@code contactPair}
 *       (default {@code respiratory=1.0}).</li>
 *   <li>{@code contactPair} &mdash; one unordered activity-type pair; sets either inline
 *       {@code transmissionWeights}, a {@code file} with an age-band CSV
 *       ({@code ageA,ageB,<route>,&hellip;}, rows keyed by the {@code ageBands} lower bounds; every unordered pair
 *       of age bands needs a row, contacts involving a person without age use {@code defaultTransmissionWeights}), or
 *       {@code forbidden=true} to block the pair entirely. The two activity types are prefixes of
 *       container names, as in {@code contactGroup} ({@code edu} covers {@code educ_primary}); when several
 *       pairs match, the one with the longest combined prefix wins, ties go to the pair configured first.</li>
 *   <li>{@code contactGroup} &mdash; which activity types may share a container at all (see the
 *       constructor). Groups must not overlap, i.e. no group activity may be a prefix of another one; to change the
 *       rules for the default {@code home} and {@code edu} groups, configure a group with that activity.</li>
 * </ul>
 */
public class ContactTransmissionConfigGroup extends ReflectiveConfigGroup {

	private static final Logger log = LogManager.getLogger(ContactTransmissionConfigGroup.class);

	/** Name of this config group. */
	public static final String GROUPNAME = "contactTransmission";

	private static final String AGE_BANDS = "ageBands";
	private static final String DEFAULT_TRANSMISSION_WEIGHTS = "defaultTransmissionWeights";

	private final Map<String, ContactPairParams> contactPairs = new LinkedHashMap<>();
	private final Map<String, ContactGroupParams> contactGroups = new LinkedHashMap<>();
	private List<Integer> ageBands = new ArrayList<>(Collections.singletonList(0));
	private TransmissionWeights defaultTransmissionWeights = TransmissionWeights.parse("respiratory=1.0");
	private URL context;

	/**
	 * Whether a contact with a person without age has already been reported by a resolver of this group.
	 */
	private final AtomicBoolean unknownAgeWarned = new AtomicBoolean(false);

	/**
	 * Creates a contact-transmission configuration pre-populated with the historical cross-activity
	 * interaction rules: "home" only shares a container with {@code home}/{@code leisure}/{@code work},
	 * and "education" only with {@code education}/{@code work}. These defaults reproduce the behaviour
	 * that used to be hard-coded in {@code DefaultContactModel} / {@code SymmetricContactModel}.
	 */
	public ContactTransmissionConfigGroup() {
		super(GROUPNAME);
		addDefaultContactGroup("home", "home", "leis", "work");
		addDefaultContactGroup("edu", "edu", "work");
	}

	private void addDefaultContactGroup(String activity, String... allowedPartners) {
		ContactGroupParams group = new ContactGroupParams();
		group.setActivity(activity);
		group.setAllowedPartners(Arrays.asList(allowedPartners));
		addParameterSet(group);
	}

	@StringGetter(AGE_BANDS)
	String getAgeBandsString() {
		return Joiner.on(",").join(ageBands);
	}

	@StringSetter(AGE_BANDS)
	void setAgeBandsString(String value) {
		List<Integer> parsed = new ArrayList<>();
		for (String token : Splitter.on(",").trimResults().split(value)) {
			try {
				parsed.add(Integer.parseInt(token));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid age band '" + token + "' in '" + value + "'.", e);
			}
		}
		setAgeBands(parsed);
	}

	/**
	 * Returns the configured age-band lower bounds.
	 */
	public List<Integer> getAgeBands() {
		return Collections.unmodifiableList(ageBands);
	}

	/**
	 * Sets the age-band lower bounds.
	 */
	public void setAgeBands(Collection<Integer> ageBands) {
		this.ageBands = new ArrayList<>(ageBands);
	}

	@StringGetter(DEFAULT_TRANSMISSION_WEIGHTS)
	String getDefaultTransmissionWeightsString() {
		return defaultTransmissionWeights.toToken();
	}

	@StringSetter(DEFAULT_TRANSMISSION_WEIGHTS)
	void setDefaultTransmissionWeightsString(String value) {
		defaultTransmissionWeights = TransmissionWeights.parse(value);
	}

	/**
	 * Returns the relative route split used for any contact not covered by a {@code contactPair}
	 * (default {@code respiratory=1.0}).
	 */
	public TransmissionWeights getDefaultTransmissionWeights() {
		return defaultTransmissionWeights;
	}

	/**
	 * Sets the relative route split used for any contact not covered by a {@code contactPair}.
	 */
	public void setDefaultTransmissionWeights(TransmissionWeights weights) {
		defaultTransmissionWeights = requireNonNull(weights, DEFAULT_TRANSMISSION_WEIGHTS);
	}

	/**
	 * Gets an existing contact pair or adds a new one.
	 */
	public ContactPairParams getOrAddContactPair(String activityA, String activityB) {
		String key = pairKey(activityA, activityB);
		ContactPairParams existing = contactPairs.get(key);
		if (existing != null) {
			return existing;
		}

		ContactPairParams params = new ContactPairParams();
		if (activityA.compareTo(activityB) <= 0) {
			params.activityA = activityA;
			params.activityB = activityB;
		} else {
			params.activityA = activityB;
			params.activityB = activityA;
		}
		addParameterSet(params);
		return params;
	}

	/**
	 * Returns the configured parameters for a contact pair.
	 */
	public ContactPairParams getContactPair(String activityA, String activityB) {
		ContactPairParams params = contactPairs.get(pairKey(activityA, activityB));
		if (params == null) {
			throw new IllegalStateException("Contact pair '" + activityA + "'/'" + activityB + "' is not configured.");
		}
		return params;
	}

	/**
	 * Returns whether a contact pair is configured.
	 */
	public boolean hasContactPair(String activityA, String activityB) {
		return contactPairs.containsKey(pairKey(activityA, activityB));
	}

	/**
	 * Returns the contact pairs, as {@code "activityA/activityB"}, whose activity-type prefixes do not both match
	 * one of the given container names. Such pairs never apply and usually point to a misspelled prefix.
	 */
	public List<String> unmatchedContactPairs(Collection<String> containerNames) {
		List<String> unmatched = new ArrayList<>();
		for (ContactPairParams params : contactPairs.values()) {
			if (params.activityA == null || params.activityB == null) {
				continue;
			}
			boolean matchesA = containerNames.stream().anyMatch(name -> name.startsWith(params.activityA));
			boolean matchesB = containerNames.stream().anyMatch(name -> name.startsWith(params.activityB));
			if (!matchesA || !matchesB) {
				unmatched.add(params.activityA + "/" + params.activityB);
			}
		}
		return unmatched;
	}

	/**
	 * Returns all configured contact pairs in insertion order.
	 */
	public Collection<ContactPairParams> getContactPairs() {
		return Collections.unmodifiableCollection(contactPairs.values());
	}

	/**
	 * Gets an existing per-activity contact group or adds a new one.
	 */
	public ContactGroupParams getOrAddContactGroup(String activity) {
		ContactGroupParams existing = contactGroups.get(activity);
		if (existing != null) {
			return existing;
		}

		ContactGroupParams group = new ContactGroupParams();
		group.setActivity(activity);
		addParameterSet(group);
		return group;
	}

	/**
	 * Returns whether a contact group is configured for the given activity type.
	 */
	public boolean hasContactGroup(String activity) {
		return contactGroups.containsKey(activity);
	}

	/**
	 * Returns all configured per-activity contact groups in insertion order.
	 */
	public Collection<ContactGroupParams> getContactGroups() {
		return Collections.unmodifiableCollection(contactGroups.values());
	}

	/** {@inheritDoc} */
	@Override
	public ConfigGroup createParameterSet(String type) {
		if (ContactPairParams.SET_TYPE.equals(type)) {
			return new ContactPairParams();
		}
		if (ContactGroupParams.SET_TYPE.equals(type)) {
			return new ContactGroupParams();
		}
		throw new IllegalArgumentException("Unknown type " + type);
	}

	/** {@inheritDoc} */
	@Override
	public void addParameterSet(ConfigGroup set) {
		if (ContactPairParams.SET_TYPE.equals(set.getName())) {
			ContactPairParams params = (ContactPairParams) set;
			ContactPairParams previous = contactPairs.put(pairKey(params.activityA, params.activityB), params);
			// replace a previously registered pair instead of keeping a stale duplicate
			if (previous != null) {
				super.removeParameterSet(previous);
			}
			super.addParameterSet(set);
		} else if (ContactGroupParams.SET_TYPE.equals(set.getName())) {
			ContactGroupParams params = (ContactGroupParams) set;
			for (String activity : contactGroups.keySet()) {
				if (params.getActivity() != null && !activity.equals(params.getActivity()))
					checkNotOverlapping(activity, params.getActivity());
			}
			ContactGroupParams previous = contactGroups.put(params.getActivity(), params);
			// replace a previously registered group (e.g. an auto-added default) instead of keeping a duplicate
			if (previous != null) {
				super.removeParameterSet(previous);
			}
			super.addParameterSet(set);
		} else {
			throw new IllegalStateException("Unknown set type " + set.getName());
		}
	}

	/**
	 * Validates this configuration and builds an immutable runtime resolver.
	 */
	public Resolver createResolver() {
		return createResolver(Collections.emptyList());
	}

	/**
	 * Validates this configuration and builds an immutable runtime resolver with precomputed rules for the given
	 * container names. Lookups for these names avoid string operations on the hot path; other names are still
	 * resolved, but through the slower prefix matching.
	 */
	public Resolver createResolver(Collection<String> containerNames) {
		validateAgeBands(ageBands);
		requireNonNull(defaultTransmissionWeights, DEFAULT_TRANSMISSION_WEIGHTS);

		List<Integer> bands = Collections.unmodifiableList(new ArrayList<>(ageBands));
		Map<Integer, Integer> bandIndices = new HashMap<>();
		for (int i = 0; i < bands.size(); i++) {
			bandIndices.put(bands.get(i), i);
		}

		List<PairRule> pairRules = new ArrayList<>();
		Map<String, TransmissionWeights[][]> loadedFiles = new HashMap<>();
		for (ContactPairParams params : contactPairs.values()) {
			params.validate();
			ResolvedPair resolved;
			if (params.forbidden) {
				resolved = new ResolvedPair(TransmissionWeights.ZERO, null, true);
			} else if (params.file != null) {
				URL url = resolveFile(params.file);
				String resolvedPath = url.toExternalForm();
				TransmissionWeights[][] table = loadedFiles.get(resolvedPath);
				if (table == null) {
					table = readAgeMatrix(url, params.file, bands, bandIndices);
					loadedFiles.put(resolvedPath, table);
				}
				resolved = new ResolvedPair(null, table, false);
			} else {
				resolved = new ResolvedPair(params.transmissionWeights, null, false);
			}
			pairRules.add(new PairRule(params.activityA, params.activityB, resolved));
		}

		Map<String, List<String>> groupWhitelist = new LinkedHashMap<>();
		for (ContactGroupParams group : contactGroups.values()) {
			group.validate();
			// checked again, the activity of a group may have been changed after it was added
			for (String activity : groupWhitelist.keySet())
				checkNotOverlapping(activity, group.getActivity());
			groupWhitelist.put(group.getActivity(), new ArrayList<>(group.getAllowedPartners()));
		}

		return new Resolver(bands, defaultTransmissionWeights, pairRules, groupWhitelist, unknownAgeWarned, containerNames);
	}

	/**
	 * Sets the context against which relative {@code file} paths of contact pairs are resolved, usually
	 * {@link org.matsim.core.config.Config#getContext()}. Without a context, relative paths are resolved against
	 * the working directory.
	 */
	public void setContext(URL context) {
		this.context = context;
	}

	private URL getContext() {
		return context;
	}

	private URL resolveFile(String file) {
		if (getContext() != null) {
			return ConfigGroup.getInputFileURL(getContext(), file);
		}
		try {
			return Path.of(file).toUri().toURL();
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Invalid contact transmission file '" + file + "'.", e);
		}
	}

	private static TransmissionWeights[][] readAgeMatrix(URL url, String file, List<Integer> bands,
			Map<Integer, Integer> bandIndices) {
		int bandCount = bands.size();
		TransmissionWeights[][] table = new TransmissionWeights[bandCount][bandCount];
		try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8);
			 CSVParser csv = new CSVParser(reader,
					 CSVFormat.DEFAULT.withFirstRecordAsHeader().withCommentMarker('#'))) {
			List<String> headers = csv.getHeaderNames();
			if (!headers.contains("ageA") || !headers.contains("ageB")) {
				throw new IllegalArgumentException("CSV file '" + file + "' must contain ageA and ageB columns.");
			}

			Map<ContactTransmissionType, String> routeColumns = new EnumMap<>(ContactTransmissionType.class);
			for (String header : headers) {
				if ("ageA".equals(header) || "ageB".equals(header)) {
					continue;
				}
				ContactTransmissionType route = TransmissionWeights.parseRoute(header);
				String previous = routeColumns.put(route, header);
				if (previous != null) {
					throw new IllegalArgumentException("Duplicate route columns '" + previous + "' and '" + header
							+ "' in CSV file '" + file + "'.");
				}
			}
			if (routeColumns.isEmpty()) {
				throw new IllegalArgumentException("CSV file '" + file + "' must contain at least one transmission route column.");
			}

			for (CSVRecord record : csv) {
				int ageA = parseAge(record.get("ageA"), "ageA", file);
				int ageB = parseAge(record.get("ageB"), "ageB", file);
				Integer indexA = bandIndices.get(ageA);
				Integer indexB = bandIndices.get(ageB);
				if (indexA == null) {
					throw new IllegalArgumentException("CSV ageA value '" + ageA + "' in file '" + file
							+ "' is not a declared ageBands lower bound.");
				}
				if (indexB == null) {
					throw new IllegalArgumentException("CSV ageB value '" + ageB + "' in file '" + file
							+ "' is not a declared ageBands lower bound.");
				}
				int lower = Math.min(indexA, indexB);
				int upper = Math.max(indexA, indexB);
				if (table[lower][upper] != null) {
					throw new IllegalArgumentException("Duplicate CSV age cell ('" + Math.min(ageA, ageB) + "','"
							+ Math.max(ageA, ageB) + "') in file '" + file + "'.");
				}

				EnumMap<ContactTransmissionType, Double> weights = new EnumMap<>(ContactTransmissionType.class);
				for (Map.Entry<ContactTransmissionType, String> column : routeColumns.entrySet()) {
					String value = record.get(column.getValue());
					try {
						weights.put(column.getKey(), Double.parseDouble(value));
					} catch (NumberFormatException e) {
						throw new IllegalArgumentException("Invalid weight '" + value + "' for column '"
								+ column.getValue() + "' in file '" + file + "'.", e);
					}
				}
				table[lower][upper] = new TransmissionWeights(weights);
			}
			requireComplete(table, bands, file);
			return table;
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read contact transmission file '" + file + "'.", e);
		}
	}

	/**
	 * A missing cell would silently block transmission for that age pair, so every unordered pair of age bands needs a row.
	 */
	private static void requireComplete(TransmissionWeights[][] table, List<Integer> bands, String file) {
		List<String> missing = new ArrayList<>();
		for (int i = 0; i < bands.size(); i++) {
			for (int j = i; j < bands.size(); j++) {
				if (table[i][j] == null) {
					missing.add("(" + bands.get(i) + "," + bands.get(j) + ")");
				}
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("CSV file '" + file + "' does not cover all ageBands pairs, missing (ageA,ageB): "
					+ String.join(", ", missing.subList(0, Math.min(missing.size(), 20)))
					+ (missing.size() > 20 ? " and " + (missing.size() - 20) + " more" : "")
					+ ". Add a row for every pair, with 0 weights where no transmission is intended.");
		}
	}

	private static int parseAge(String value, String column, String file) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid " + column + " value '" + value + "' in file '" + file + "'.", e);
		}
	}

	private static void validateAgeBands(List<Integer> bands) {
		if (bands.isEmpty()) {
			throw new IllegalArgumentException("ageBands must not be empty.");
		}
		if (bands.get(0) == null || bands.get(0) != 0) {
			throw new IllegalArgumentException("ageBands first value must be 0, but was '" + bands.get(0) + "'.");
		}
		for (int i = 0; i < bands.size(); i++) {
			Integer band = bands.get(i);
			if (band == null || band < 0) {
				throw new IllegalArgumentException("ageBands contains invalid value '" + band + "'.");
			}
			if (i > 0 && band <= bands.get(i - 1)) {
				throw new IllegalArgumentException("ageBands must be strictly ascending; offending value '" + band + "'.");
			}
		}
	}

	/**
	 * As with the infection params, every activity type must be matched by at most one contact group. Otherwise all
	 * matching groups would apply, and a more specific group (e.g. {@code educ}) could not relax a broader one
	 * (e.g. the default {@code edu}).
	 */
	private static void checkNotOverlapping(String existing, String activity) {
		if (!existing.startsWith(activity) && !activity.startsWith(existing))
			return;

		String broader = existing.length() <= activity.length() ? existing : activity;
		String specific = broader.equals(existing) ? activity : existing;
		throw new IllegalArgumentException("Contact group '" + activity + "' overlaps with contact group '" + existing
				+ "': every activity type must be matched by one contact group only, but '" + broader + "' also covers '"
				+ specific + "'. To change the rules of an existing group, configure a group with the same activity"
				+ " (the default groups are 'home' and 'edu').");
	}

	private static String pairKey(String activityA, String activityB) {
		if (activityA == null || activityB == null) {
			throw new IllegalArgumentException("Contact pair activities must not be null: '" + activityA + "', '" + activityB + "'.");
		}
		return activityA.compareTo(activityB) <= 0 ? activityA + " " + activityB : activityB + " " + activityA;
	}

	private static <T> T requireNonNull(T value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " must not be null.");
		}
		return value;
	}

	/**
	 * Route split for one unordered pair of activity types: exactly one of an inline
	 * {@code transmissionWeights} (relative per-route weights), a {@code file} with an age-band matrix,
	 * or {@code forbidden=true}. {@code activityA} and {@code activityB} are container-name prefixes; the most
	 * specific matching pair (longest combined prefix) applies.
	 */
	public static final class ContactPairParams extends ReflectiveConfigGroup {

		/** Parameter-set type used in MATSim configuration files. */
		public static final String SET_TYPE = "contactPair";

		private static final String ACTIVITY_A = "activityA";
		private static final String ACTIVITY_B = "activityB";
		private static final String TRANSMISSION_WEIGHTS = "transmissionWeights";
		private static final String FILE = "file";
		private static final String FORBIDDEN = "forbidden";

		private String activityA;
		private String activityB;
		private TransmissionWeights transmissionWeights;
		private String file;
		private boolean forbidden;

		ContactPairParams() {
			super(SET_TYPE);
		}

		/** Returns the first activity type. */
		@StringGetter(ACTIVITY_A)
		public String getActivityA() {
			return activityA;
		}

		/** Sets the first activity type. */
		@StringSetter(ACTIVITY_A)
		public void setActivityA(String activityA) {
			this.activityA = activityA;
		}

		/** Returns the second activity type. */
		@StringGetter(ACTIVITY_B)
		public String getActivityB() {
			return activityB;
		}

		/** Sets the second activity type. */
		@StringSetter(ACTIVITY_B)
		public void setActivityB(String activityB) {
			this.activityB = activityB;
		}

		@StringGetter(TRANSMISSION_WEIGHTS)
		String getTransmissionWeightsString() {
			return transmissionWeights == null ? null : transmissionWeights.toToken();
		}

		@StringSetter(TRANSMISSION_WEIGHTS)
		void setTransmissionWeightsString(String value) {
			transmissionWeights = value == null ? null : TransmissionWeights.parse(value);
		}

		/** Returns the inline relative per-route weights for this pair, or {@code null}. */
		public TransmissionWeights getTransmissionWeights() {
			return transmissionWeights;
		}

		/** Sets the inline relative per-route weights for this pair. */
		public void setTransmissionWeights(TransmissionWeights transmissionWeights) {
			this.transmissionWeights = transmissionWeights;
		}

		/** Returns the age-matrix file path, or {@code null}. */
		@StringGetter(FILE)
		public String getFile() {
			return file;
		}

		/** Sets the age-matrix file path. */
		@StringSetter(FILE)
		public void setFile(String file) {
			this.file = file;
		}

		/** Returns whether this activity pair is blocked from any transmission. */
		@StringGetter(FORBIDDEN)
		public boolean isForbidden() {
			return forbidden;
		}

		/**
		 * Marks this activity pair as blocked from any transmission. A forbidden pair must not also set
		 * {@code transmissionWeights} or {@code file}.
		 */
		@StringSetter(FORBIDDEN)
		public void setForbidden(boolean forbidden) {
			this.forbidden = forbidden;
		}

		private void validate() {
			if (activityA == null || activityA.trim().isEmpty()) {
				throw new IllegalArgumentException("Invalid activityA '" + activityA + "' for contact pair.");
			}
			if (activityB == null || activityB.trim().isEmpty()) {
				throw new IllegalArgumentException("Invalid activityB '" + activityB + "' for contact pair.");
			}
			boolean hasWeights = transmissionWeights != null;
			boolean hasFile = file != null;
			if (forbidden) {
				if (hasWeights || hasFile) {
					throw new IllegalArgumentException("Forbidden contact pair '" + activityA + "'/'" + activityB
							+ "' must not set transmissionWeights or file.");
				}
				return;
			}
			if (hasWeights == hasFile) {
				throw new IllegalArgumentException("Contact pair '" + activityA + "'/'" + activityB
						+ "' must set exactly one of transmissionWeights or file.");
			}
			if (hasFile && file.trim().isEmpty()) {
				throw new IllegalArgumentException("Invalid file value '" + file + "' for contact pair '"
						+ activityA + "'/'" + activityB + "'.");
			}
		}
	}

	/**
	 * Whitelist of activity types that one activity type may share a container with.
	 *
	 * <p>Matching is prefix-based: an activity {@code educ_primary} is covered by a group whose
	 * {@code activity} is {@code edu}, and it is allowed to meet any partner whose type starts with one
	 * of the {@code allowedPartners} prefixes. An activity without a group may meet anyone.</p>
	 *
	 * <p>As with the infection params, an activity is matched by at most one group: a group whose activity overlaps
	 * with another group (e.g. {@code educ} next to {@code edu}) is rejected.</p>
	 */
	public static final class ContactGroupParams extends ReflectiveConfigGroup {

		/** Parameter-set type used in MATSim configuration files. */
		public static final String SET_TYPE = "contactGroup";

		private static final String ACTIVITY = "activity";
		private static final String ALLOWED_PARTNERS = "allowedPartners";

		private String activity;
		private List<String> allowedPartners = new ArrayList<>();

		ContactGroupParams() {
			super(SET_TYPE);
		}

		/** Returns the activity type this group constrains. */
		@StringGetter(ACTIVITY)
		public String getActivity() {
			return activity;
		}

		/** Sets the activity type this group constrains. */
		@StringSetter(ACTIVITY)
		public void setActivity(String activity) {
			this.activity = activity;
		}

		@StringGetter(ALLOWED_PARTNERS)
		String getAllowedPartnersString() {
			return Joiner.on(",").join(allowedPartners);
		}

		@StringSetter(ALLOWED_PARTNERS)
		void setAllowedPartnersString(String value) {
			List<String> parsed = new ArrayList<>();
			for (String token : Splitter.on(",").trimResults().omitEmptyStrings().split(value)) {
				parsed.add(token);
			}
			this.allowedPartners = parsed;
		}

		/** Returns the allowed partner activity-type prefixes. */
		public List<String> getAllowedPartners() {
			return Collections.unmodifiableList(allowedPartners);
		}

		/** Sets the allowed partner activity-type prefixes. */
		public void setAllowedPartners(Collection<String> allowedPartners) {
			this.allowedPartners = new ArrayList<>(allowedPartners);
		}

		private void validate() {
			if (activity == null || activity.trim().isEmpty()) {
				throw new IllegalArgumentException("Invalid activity '" + activity + "' for contact group.");
			}
			if (allowedPartners.isEmpty()) {
				throw new IllegalArgumentException("Contact group '" + activity
						+ "' must list at least one allowed partner.");
			}
			for (String partner : allowedPartners) {
				if (partner == null || partner.trim().isEmpty()) {
					throw new IllegalArgumentException("Contact group '" + activity
							+ "' has an invalid allowed partner '" + partner + "'.");
				}
			}
		}
	}

	/**
	 * Immutable, thread-safe runtime resolver for contact transmission weights.
	 */
	public static final class Resolver {

		private final List<Integer> bands;
		private final TransmissionWeights defaultWeights;
		private final List<PairRule> pairRules;
		private final Map<String, List<String>> groupWhitelist;

		/**
		 * Most specific matching pair per unordered container-name pair ({@link #NO_MATCH} if none). Filled
		 * lazily; the set of container names is small and fixed during a run.
		 */
		private final Map<String, ResolvedPair> matchCache = new ConcurrentHashMap<>();

		/**
		 * Shared by all resolvers of the same config group, so that the warning is logged once per run.
		 */
		private final AtomicBoolean unknownAgeWarned;

		/**
		 * Index of the precomputed container names; -1 for other names. Only read after construction.
		 */
		private final Object2IntOpenHashMap<String> nameIndex = new Object2IntOpenHashMap<>();

		/**
		 * Number of precomputed container names.
		 */
		private final int numNames;

		/**
		 * Precomputed {@link #isContactAllowed} for all pairs of precomputed names, indexed {@code i * numNames + j}.
		 */
		private final boolean[] allowedTable;

		/**
		 * Precomputed {@link #match} result for all pairs of precomputed names ({@code null} if no pair matches).
		 */
		private final ResolvedPair[] matchTable;

		/**
		 * Band index for ages {@code 0 .. ageToBand.length - 1}; larger ages use a binary search.
		 */
		private final int[] ageToBand;

		private final int[] bandBounds;

		private Resolver(List<Integer> bands, TransmissionWeights defaultWeights, List<PairRule> pairRules,
				Map<String, List<String>> groupWhitelist, AtomicBoolean unknownAgeWarned, Collection<String> containerNames) {
			this.bands = bands;
			this.unknownAgeWarned = unknownAgeWarned;
			this.defaultWeights = defaultWeights;
			this.pairRules = Collections.unmodifiableList(new ArrayList<>(pairRules));
			this.groupWhitelist = Collections.unmodifiableMap(new LinkedHashMap<>(groupWhitelist));

			this.bandBounds = bands.stream().mapToInt(Integer::intValue).toArray();
			this.ageToBand = new int[128];
			for (int age = 0; age < ageToBand.length; age++) {
				ageToBand[age] = searchBand(age);
			}

			nameIndex.defaultReturnValue(-1);
			for (String name : containerNames) {
				if (!nameIndex.containsKey(name)) {
					nameIndex.put(name, nameIndex.size());
				}
			}
			numNames = nameIndex.size();
			allowedTable = new boolean[numNames * numNames];
			matchTable = new ResolvedPair[numNames * numNames];
			for (Object2IntMap.Entry<String> a : nameIndex.object2IntEntrySet()) {
				for (Object2IntMap.Entry<String> b : nameIndex.object2IntEntrySet()) {
					int idx = a.getIntValue() * numNames + b.getIntValue();
					allowedTable[idx] = computeContactAllowed(a.getKey(), b.getKey());
					matchTable[idx] = match(a.getKey(), b.getKey());
				}
			}
		}

		/**
		 * Returns whether two activity types may share a container for transmission purposes.
		 * Applies both the explicit forbidden pairs and the per-activity allow-lists; unknown activity
		 * types without a group are unconstrained.
		 */
		public boolean isContactAllowed(String activityA, String activityB) {
			int i = nameIndex.getInt(activityA);
			int j = nameIndex.getInt(activityB);
			if (i >= 0 && j >= 0) {
				return allowedTable[i * numNames + j];
			}
			return computeContactAllowed(activityA, activityB);
		}

		private boolean computeContactAllowed(String activityA, String activityB) {
			ResolvedPair pair = match(activityA, activityB);
			if (pair != null && pair.forbidden) {
				return false;
			}
			return partnerAllowed(activityA, activityB) && partnerAllowed(activityB, activityA);
		}

		private boolean partnerAllowed(String activity, String partner) {
			for (Map.Entry<String, List<String>> rule : groupWhitelist.entrySet()) {
				if (!activity.startsWith(rule.getKey())) {
					continue;
				}
				boolean matched = false;
				for (String allowed : rule.getValue()) {
					if (partner.startsWith(allowed)) {
						matched = true;
						break;
					}
				}
				if (!matched) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Resolves weights for an unordered activity and age pair.
		 */
		public TransmissionWeights resolve(String activityA, String activityB, int ageA, int ageB) {
			int i = nameIndex.getInt(activityA);
			int j = nameIndex.getInt(activityB);
			ResolvedPair pair = i >= 0 && j >= 0 ? matchTable[i * numNames + j] : match(activityA, activityB);
			if (pair == null) {
				return defaultWeights;
			}
			if (pair.table == null) {
				return pair.inlineWeights;
			}

			// an unknown age cannot be placed in an age band: treat the contact as not covered by the pair
			if (ageA < 0 || ageB < 0) {
				if (unknownAgeWarned.compareAndSet(false, true)) {
					log.warn("Contact {}/{} involves a person without age, but the matching contactPair uses an age matrix. "
							+ "Default transmission weights {} are used for such contacts; this is logged only once. "
							+ "The number of persons without age is reported by the household check.",
							activityA, activityB, defaultWeights.toToken());
				}
				return defaultWeights;
			}

			int indexA = bandIndex(ageA);
			int indexB = bandIndex(ageB);
			return pair.table[Math.min(indexA, indexB)][Math.max(indexA, indexB)];
		}

		/**
		 * Most specific configured pair for two container names, or {@code null}. A pair matches when each of its
		 * activity types is a prefix of one of the two names (in either order); the longest combined prefix wins,
		 * ties go to the pair configured first.
		 */
		private ResolvedPair match(String activityA, String activityB) {
			String key = pairKey(activityA, activityB);
			ResolvedPair cached = matchCache.get(key);
			if (cached == null) {
				cached = findBestMatch(activityA, activityB);
				matchCache.putIfAbsent(key, cached);
			}
			return cached == NO_MATCH ? null : cached;
		}

		private ResolvedPair findBestMatch(String activityA, String activityB) {
			ResolvedPair best = NO_MATCH;
			int bestSpecificity = -1;
			for (PairRule rule : pairRules) {
				if (rule.matches(activityA, activityB) && rule.specificity() > bestSpecificity) {
					best = rule.pair;
					bestSpecificity = rule.specificity();
				}
			}
			return best;
		}

		/**
		 * Returns the age-band index containing the supplied age.
		 */
		public int bandIndex(int age) {
			if (age < 0) {
				throw new IllegalArgumentException("Age must be non-negative, but was '" + age + "'.");
			}
			return age < ageToBand.length ? ageToBand[age] : searchBand(age);
		}

		private int searchBand(int age) {
			int result = Arrays.binarySearch(bandBounds, age);
			return result >= 0 ? result : -result - 2;
		}
	}

	/** Marker for "no configured pair matches" in the resolver cache; never returned to callers. */
	private static final ResolvedPair NO_MATCH = new ResolvedPair(null, null, false);

	private static final class ResolvedPair {
		private final TransmissionWeights inlineWeights;
		private final TransmissionWeights[][] table;
		private final boolean forbidden;

		private ResolvedPair(TransmissionWeights inlineWeights, TransmissionWeights[][] table, boolean forbidden) {
			this.inlineWeights = inlineWeights;
			this.table = table;
			this.forbidden = forbidden;
		}
	}

	/**
	 * One configured contact pair: two activity-type prefixes and what they resolve to.
	 */
	private static final class PairRule {
		private final String activityA;
		private final String activityB;
		private final ResolvedPair pair;

		private PairRule(String activityA, String activityB, ResolvedPair pair) {
			this.activityA = activityA;
			this.activityB = activityB;
			this.pair = pair;
		}

		private boolean matches(String x, String y) {
			return (x.startsWith(activityA) && y.startsWith(activityB))
					|| (x.startsWith(activityB) && y.startsWith(activityA));
		}

		private int specificity() {
			return activityA.length() + activityB.length();
		}
	}
}
