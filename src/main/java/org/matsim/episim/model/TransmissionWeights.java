package org.matsim.episim.model;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable weights indexed by {@link ContactTransmissionType}.
 *
 * <p>The same value type is used both as a configuration value (parsed from / serialized to the
 * {@code route=weight;route=weight} token notation) and as the runtime value handed to the infection
 * model. Instances are validated on construction: every weight is finite and non-negative and the sum
 * does not exceed 1.</p>
 */
public final class TransmissionWeights {

	/** All-zero transmission weights. */
	public static final TransmissionWeights ZERO = new TransmissionWeights(new double[ContactTransmissionType.values().length]);

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
	 * Resolves a transmission route token (canonical token or enum name, case-insensitive).
	 */
	public static ContactTransmissionType parseRoute(String token) {
		ContactTransmissionType type = ROUTES_BY_TOKEN.get(token.trim().toLowerCase(Locale.ROOT));
		if (type == null) {
			throw new IllegalArgumentException("Unknown transmission route token '" + token + "'.");
		}
		return type;
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

	private static <T> T requireNonNull(T value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " must not be null.");
		}
		return value;
	}
}
