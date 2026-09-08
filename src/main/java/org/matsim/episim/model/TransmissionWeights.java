package org.matsim.episim.model;

import com.google.common.base.Splitter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable weights for the three transmission routes &mdash; respiratory, direct contact and fomite.
 *
 * <p>The same value type is used both as a configuration value (parsed from / serialized to the
 * {@code route=weight;route=weight} notation) and as the runtime value handed to the infection model.
 * Instances are validated on construction: every weight is finite and non-negative and the sum does not
 * exceed 1.</p>
 */
public final class TransmissionWeights {

	/** All-zero transmission weights. */
	public static final TransmissionWeights ZERO = new TransmissionWeights(0, 0, 0);

	/** Respiratory-only transmission; the neutral element for both the contact and the pathogen axis. */
	public static final TransmissionWeights RESPIRATORY_ONLY = new TransmissionWeights(1, 0, 0);

	private static final double SUM_TOLERANCE = 1e-9;
	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Map<String, ContactTransmissionType> ROUTE_BY_KEY;

	static {
		Map<String, ContactTransmissionType> routes = new HashMap<>();
		routes.put("respiratory", ContactTransmissionType.RESPIRATORY);
		routes.put("directcontact", ContactTransmissionType.DIRECT_CONTACT);
		routes.put("fomite", ContactTransmissionType.FOMITE);
		for (ContactTransmissionType type : ContactTransmissionType.values()) {
			routes.put(type.name().toLowerCase(Locale.ROOT), type);
		}
		ROUTE_BY_KEY = Collections.unmodifiableMap(routes);
	}

	private final double respiratory;
	private final double directContact;
	private final double fomite;

	/**
	 * Creates weights for the three transmission routes.
	 */
	public TransmissionWeights(double respiratory, double directContact, double fomite) {
		this.respiratory = respiratory;
		this.directContact = directContact;
		this.fomite = fomite;
		validate();
	}

	/**
	 * Creates weights from a route map; omitted routes receive zero.
	 */
	public TransmissionWeights(Map<ContactTransmissionType, Double> weights) {
		this(orZero(weights.get(ContactTransmissionType.RESPIRATORY)),
				orZero(weights.get(ContactTransmissionType.DIRECT_CONTACT)),
				orZero(weights.get(ContactTransmissionType.FOMITE)));
	}

	/**
	 * Parses the {@code route=weight;route=weight} notation, e.g. {@code respiratory=0.7;fomite=0.3}.
	 */
	public static TransmissionWeights parse(String encoded) {
		requireNonNull(encoded, "transmissionWeights");

		Map<String, String> entries;
		try {
			entries = SPLITTER.split(encoded);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid transmissionWeights value '" + encoded + "'.", e);
		}

		EnumMap<ContactTransmissionType, Double> weights = new EnumMap<>(ContactTransmissionType.class);
		for (Map.Entry<String, String> entry : entries.entrySet()) {
			ContactTransmissionType route = parseRoute(entry.getKey());
			if (weights.containsKey(route)) {
				throw new IllegalArgumentException("Duplicate transmission route '" + entry.getKey()
						+ "' in '" + encoded + "'.");
			}
			try {
				weights.put(route, Double.parseDouble(entry.getValue().trim()));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid transmission weight '" + entry.getValue()
						+ "' in '" + encoded + "'.", e);
			}
		}
		return new TransmissionWeights(weights);
	}

	/**
	 * Resolves a transmission route from its key (canonical key such as {@code directContact}, or the
	 * enum name, case-insensitive).
	 */
	public static ContactTransmissionType parseRoute(String key) {
		ContactTransmissionType type = ROUTE_BY_KEY.get(key.trim().toLowerCase(Locale.ROOT));
		if (type == null) {
			throw new IllegalArgumentException("Unknown transmission route '" + key + "'.");
		}
		return type;
	}

	/** Weight of the respiratory route. */
	public double getRespiratory() {
		return respiratory;
	}

	/** Weight of the direct-contact route. */
	public double getDirectContact() {
		return directContact;
	}

	/** Weight of the fomite (contaminated-surface) route. */
	public double getFomite() {
		return fomite;
	}

	/**
	 * Returns the weight of the given route.
	 */
	public double get(ContactTransmissionType route) {
		switch (requireNonNull(route, "transmission route")) {
			case RESPIRATORY:
				return respiratory;
			case DIRECT_CONTACT:
				return directContact;
			case FOMITE:
				return fomite;
			default:
				throw new IllegalArgumentException("Unhandled transmission route '" + route + "'.");
		}
	}

	/**
	 * Returns the sum of all route weights.
	 */
	public double sum() {
		return respiratory + directContact + fomite;
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
		result.put(ContactTransmissionType.RESPIRATORY, respiratory);
		result.put(ContactTransmissionType.DIRECT_CONTACT, directContact);
		result.put(ContactTransmissionType.FOMITE, fomite);
		return result;
	}

	/**
	 * Serializes to the {@code route=weight;route=weight} notation using canonical keys.
	 */
	public String toToken() {
		return "respiratory=" + respiratory + ";directContact=" + directContact + ";fomite=" + fomite;
	}

	private void validate() {
		checkFiniteNonNegative("respiratory", respiratory);
		checkFiniteNonNegative("directContact", directContact);
		checkFiniteNonNegative("fomite", fomite);

		double sum = sum();
		if (sum > 1 + SUM_TOLERANCE) {
			throw new IllegalArgumentException("Transmission weights sum '" + sum + "' exceeds 1.");
		}
	}

	private static void checkFiniteNonNegative(String route, double value) {
		if (!Double.isFinite(value) || value < 0) {
			throw new IllegalArgumentException("Invalid " + route + " weight '" + value + "'.");
		}
	}

	private static double orZero(Double value) {
		return value == null ? 0.0 : value;
	}

	private static <T> T requireNonNull(T value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " must not be null.");
		}
		return value;
	}
}
