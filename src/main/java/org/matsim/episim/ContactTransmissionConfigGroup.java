package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.ContactTransmissionType;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Configures transmission-route weights for contacts between activity types.
 */
public class ContactTransmissionConfigGroup extends ReflectiveConfigGroup {

	/** Name of this config group. */
	public static final String GROUPNAME = "contactTransmission";

	private static final String AGE_BANDS = "ageBands";
	private static final String DEFAULT_TRANSMISSION_WEIGHTS = "defaultTransmissionWeights";
	private static final double SUM_TOLERANCE = 1e-9;
	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");
	private static final Map<ContactTransmissionType, String> ROUTE_TOKENS;
	private static final Map<String, ContactTransmissionType> ROUTES_BY_TOKEN;

	static {
		EnumMap<ContactTransmissionType, String> tokens = new EnumMap<>(ContactTransmissionType.class);
		tokens.put(ContactTransmissionType.RESPIRATORY, "respiratory");
		tokens.put(ContactTransmissionType.DIRECT_CONTACT, "directContact");
		tokens.put(ContactTransmissionType.FOMITE, "fomite");
		ROUTE_TOKENS = Collections.unmodifiableMap(tokens);

		Map<String, ContactTransmissionType> routes = new HashMap<>();
		for (ContactTransmissionType type : ContactTransmissionType.values()) {
			routes.put(tokens.get(type).toLowerCase(Locale.ROOT), type);
			routes.put(type.name().toLowerCase(Locale.ROOT), type);
		}
		ROUTES_BY_TOKEN = Collections.unmodifiableMap(routes);
	}

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
	 * Returns the weights used when no contact pair is configured.
	 */
	public TransmissionWeights getDefaultTransmissionWeights() {
		return defaultTransmissionWeights;
	}

	/**
	 * Sets the weights used when no contact pair is configured.
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
				ContactTransmissionType route = parseRoute(header);
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

				double[] weights = new double[ContactTransmissionType.values().length];
				for (Map.Entry<ContactTransmissionType, String> column : routeColumns.entrySet()) {
					String value = record.get(column.getValue());
					try {
						weights[column.getKey().ordinal()] = Double.parseDouble(value);
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

	private static ContactTransmissionType parseRoute(String token) {
		ContactTransmissionType type = ROUTES_BY_TOKEN.get(token.trim().toLowerCase(Locale.ROOT));
		if (type == null) {
			throw new IllegalArgumentException("Unknown transmission route token '" + token + "'.");
		}
		return type;
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
	 * Holds options for one unordered pair of activity types.
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

		/** Returns the inline transmission weights, or {@code null}. */
		public TransmissionWeights getTransmissionWeights() {
			return transmissionWeights;
		}

		/** Sets the inline transmission weights. */
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
	 * Immutable weights indexed by transmission route.
	 */
	public static final class TransmissionWeights {

		/** All-zero transmission weights. */
		public static final TransmissionWeights ZERO = new TransmissionWeights(new double[ContactTransmissionType.values().length]);

		private final double[] weights;

		/**
		 * Creates weights from values in enum declaration order.
		 */
		public TransmissionWeights(double... weights) {
			if (weights.length != ContactTransmissionType.values().length) {
				throw new IllegalArgumentException("Expected " + ContactTransmissionType.values().length
						+ " transmission weights, but got '" + weights.length + "'.");
			}
			this.weights = weights.clone();
			validate();
		}

		/**
		 * Creates weights from a route map; omitted routes receive zero.
		 */
		public TransmissionWeights(Map<ContactTransmissionType, Double> weights) {
			this.weights = new double[ContactTransmissionType.values().length];
			for (Map.Entry<ContactTransmissionType, Double> entry : weights.entrySet()) {
				if (entry.getKey() == null || entry.getValue() == null) {
					throw new IllegalArgumentException("Transmission weight entries must not contain null: '" + entry + "'.");
				}
				this.weights[entry.getKey().ordinal()] = entry.getValue();
			}
			validate();
		}

		/**
		 * Parses a semicolon-separated route-to-weight token.
		 */
		public static TransmissionWeights parse(String token) {
			requireNonNull(token, "transmissionWeights");
			double[] weights = new double[ContactTransmissionType.values().length];
			boolean[] seen = new boolean[weights.length];
			Map<String, String> values;
			try {
				values = SPLITTER.split(token);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid transmissionWeights value '" + token + "'.", e);
			}
			for (Map.Entry<String, String> entry : values.entrySet()) {
				ContactTransmissionType route = parseRoute(entry.getKey());
				if (seen[route.ordinal()]) {
					throw new IllegalArgumentException("Duplicate transmission route token '" + entry.getKey()
							+ "' in '" + token + "'.");
				}
				seen[route.ordinal()] = true;
				try {
					weights[route.ordinal()] = Double.parseDouble(entry.getValue().trim());
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Invalid transmission weight '" + entry.getValue()
							+ "' in '" + token + "'.", e);
				}
			}
			return new TransmissionWeights(weights);
		}

		/**
		 * Serializes all routes using canonical tokens.
		 */
		public String toToken() {
			Map<String, Double> values = new LinkedHashMap<>();
			for (ContactTransmissionType type : ContactTransmissionType.values()) {
				values.put(ROUTE_TOKENS.get(type), weights[type.ordinal()]);
			}
			return JOINER.join(values);
		}

		/**
		 * Returns the weight for a transmission route.
		 */
		public double get(ContactTransmissionType type) {
			return weights[requireNonNull(type, "transmission type").ordinal()];
		}

		/**
		 * Returns the sum of all route weights.
		 */
		public double sum() {
			double sum = 0;
			for (double weight : weights) {
				sum += weight;
			}
			return sum;
		}

		/**
		 * Returns whether the weight sum is effectively zero.
		 */
		public boolean isZero() {
			return sum() < 1e-12;
		}

		/**
		 * Returns a defensive enum-keyed map of all route weights.
		 */
		public EnumMap<ContactTransmissionType, Double> asMap() {
			EnumMap<ContactTransmissionType, Double> result = new EnumMap<>(ContactTransmissionType.class);
			for (ContactTransmissionType type : ContactTransmissionType.values()) {
				result.put(type, weights[type.ordinal()]);
			}
			return result;
		}

		private void validate() {
			for (ContactTransmissionType type : ContactTransmissionType.values()) {
				double value = weights[type.ordinal()];
				if (!Double.isFinite(value) || value < 0) {
					throw new IllegalArgumentException("Invalid " + ROUTE_TOKENS.get(type) + " weight '" + value + "'.");
				}
			}
			double sum = sum();
			if (sum > 1 + SUM_TOLERANCE) {
				throw new IllegalArgumentException("Transmission weights sum '" + sum + "' exceeds 1.");
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
