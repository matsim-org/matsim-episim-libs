package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *       ({@code ageA,ageB,<route>,&hellip;}, rows keyed by the {@code ageBands} lower bounds), or
 *       {@code forbidden=true} to block the pair entirely.</li>
 *   <li>{@code contactGroup} &mdash; which activity types may share a container at all (see the
 *       constructor).</li>
 * </ul>
 */
public class ContactTransmissionConfigGroup extends ReflectiveConfigGroup {

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
			contactPairs.put(pairKey(params.activityA, params.activityB), params);
			super.addParameterSet(set);
		} else if (ContactGroupParams.SET_TYPE.equals(set.getName())) {
			ContactGroupParams params = (ContactGroupParams) set;
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
		validateAgeBands(ageBands);
		requireNonNull(defaultTransmissionWeights, DEFAULT_TRANSMISSION_WEIGHTS);

		List<Integer> bands = Collections.unmodifiableList(new ArrayList<>(ageBands));
		Map<Integer, Integer> bandIndices = new HashMap<>();
		for (int i = 0; i < bands.size(); i++) {
			bandIndices.put(bands.get(i), i);
		}

		Map<String, ResolvedPair> resolvedPairs = new LinkedHashMap<>();
		Set<String> forbiddenPairs = new HashSet<>();
		Map<String, TransmissionWeights[][]> loadedFiles = new HashMap<>();
		for (ContactPairParams params : contactPairs.values()) {
			params.validate();
			String key = pairKey(params.activityA, params.activityB);
			if (params.forbidden) {
				forbiddenPairs.add(key);
				resolvedPairs.put(key, new ResolvedPair(TransmissionWeights.ZERO, null));
			} else if (params.file != null) {
				URL url = resolveFile(params.file);
				String resolvedPath = url.toExternalForm();
				TransmissionWeights[][] table = loadedFiles.get(resolvedPath);
				if (table == null) {
					table = readAgeMatrix(url, params.file, bands.size(), bandIndices);
					loadedFiles.put(resolvedPath, table);
				}
				resolvedPairs.put(key, new ResolvedPair(null, table));
			} else {
				resolvedPairs.put(key, new ResolvedPair(params.transmissionWeights, null));
			}
		}

		Map<String, List<String>> groupWhitelist = new LinkedHashMap<>();
		for (ContactGroupParams group : contactGroups.values()) {
			group.validate();
			groupWhitelist.put(group.getActivity(), new ArrayList<>(group.getAllowedPartners()));
		}

		return new Resolver(bands, defaultTransmissionWeights, resolvedPairs, groupWhitelist, forbiddenPairs);
	}

	void setContext(URL context) {
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

	private static TransmissionWeights[][] readAgeMatrix(URL url, String file, int bandCount,
			Map<Integer, Integer> bandIndices) {
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
			return table;
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read contact transmission file '" + file + "'.", e);
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
	 * or {@code forbidden=true}.
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
		private final Map<String, ResolvedPair> pairs;
		private final Map<String, List<String>> groupWhitelist;
		private final Set<String> forbiddenPairs;

		private Resolver(List<Integer> bands, TransmissionWeights defaultWeights, Map<String, ResolvedPair> pairs,
				Map<String, List<String>> groupWhitelist, Set<String> forbiddenPairs) {
			this.bands = bands;
			this.defaultWeights = defaultWeights;
			this.pairs = Collections.unmodifiableMap(new LinkedHashMap<>(pairs));
			this.groupWhitelist = Collections.unmodifiableMap(new LinkedHashMap<>(groupWhitelist));
			this.forbiddenPairs = Collections.unmodifiableSet(new HashSet<>(forbiddenPairs));
		}

		/**
		 * Returns whether two activity types may share a container for transmission purposes.
		 * Applies both the explicit forbidden pairs and the per-activity allow-lists; unknown activity
		 * types without a group are unconstrained.
		 */
		public boolean isContactAllowed(String activityA, String activityB) {
			if (forbiddenPairs.contains(pairKey(activityA, activityB))) {
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
		 * The simple version, if it will be too slow, we could change it later
		 */
		public TransmissionWeights resolve(String activityA, String activityB, int ageA, int ageB) {
			ResolvedPair pair = pairs.get(pairKey(activityA, activityB));
			if (pair == null) {
				return defaultWeights;
			}
			if (pair.table == null) {
				return pair.inlineWeights;
			}

			int indexA = bandIndex(ageA);
			int indexB = bandIndex(ageB);
			TransmissionWeights weights = pair.table[Math.min(indexA, indexB)][Math.max(indexA, indexB)];
			return weights == null ? TransmissionWeights.ZERO : weights;
		}

		/**
		 * Returns the age-band index containing the supplied age.
		 */
		public int bandIndex(int age) {
			if (age < 0) {
				throw new IllegalArgumentException("Age must be non-negative, but was '" + age + "'.");
			}
			int result = Collections.binarySearch(bands, age);
			return result >= 0 ? result : -result - 2;
		}
	}

	private static final class ResolvedPair {
		private final TransmissionWeights inlineWeights;
		private final TransmissionWeights[][] table;

		private ResolvedPair(TransmissionWeights inlineWeights, TransmissionWeights[][] table) {
			this.inlineWeights = inlineWeights;
			this.table = table;
		}
	}
}
