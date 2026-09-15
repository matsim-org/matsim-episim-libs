package org.matsim.episim.model;

import com.google.common.base.Splitter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable weights for the transmission routes &mdash; currently respiratory and direct contact.
 *
 * <p>The same value type is used both as a configuration value (parsed from / serialized to the
 * {@code route=weight;route=weight} notation) and as the runtime value handed to the infection model.
 * Each weight is validated on construction to be finite and non-negative. The weights are relative and
 * <b>not</b> normalised: routes are independent, additive exposure channels, so adding weight to one
 * route does not take anything away from another. {@code respiratory=1.0} is the conventional anchor
 * (see {@link #RESPIRATORY_ONLY}); other weights are read relative to it.</p>
 *
 * <p>The <b>fomite</b> route is temporarily disabled &mdash; there is no within-facility fomite contact
 * model yet. Every line needed to bring it back is kept in place, commented with a {@code fomite:}
 * marker; restore them together with the {@code FOMITE} constant in {@link ContactTransmissionType}.</p>
 */
public final class TransmissionWeights {

	/** All-zero transmission weights. */
	public static final TransmissionWeights ZERO = new TransmissionWeights(0, 0 /* fomite: , 0 */);

	/** Respiratory-only transmission; the neutral element for both the contact and the pathogen axis. */
	public static final TransmissionWeights RESPIRATORY_ONLY = new TransmissionWeights(1, 0 /* fomite: , 0 */);

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Map<String, ContactTransmissionType> ROUTE_BY_KEY;

	static {
		Map<String, ContactTransmissionType> routes = new HashMap<>();
		routes.put("respiratory", ContactTransmissionType.RESPIRATORY);
		routes.put("directcontact", ContactTransmissionType.DIRECT_CONTACT);
		// fomite: routes.put("fomite", ContactTransmissionType.FOMITE);
		for (ContactTransmissionType type : ContactTransmissionType.values()) {
			routes.put(type.name().toLowerCase(Locale.ROOT), type);
		}
		ROUTE_BY_KEY = Collections.unmodifiableMap(routes);
	}

	private final double respiratory;
	private final double directContact;
	// fomite: private final double fomite;

	/**
	 * Creates weights for the transmission routes.
	 */
	public TransmissionWeights(double respiratory, double directContact /* fomite: , double fomite */) {
		this.respiratory = respiratory;
		this.directContact = directContact;
		// fomite: this.fomite = fomite;
		validate();
	}

	/**
	 * Creates weights from a route map; omitted routes receive zero.
	 */
	public TransmissionWeights(Map<ContactTransmissionType, Double> weights) {
		this(orZero(weights.get(ContactTransmissionType.RESPIRATORY)),
				orZero(weights.get(ContactTransmissionType.DIRECT_CONTACT))
				/* fomite: , orZero(weights.get(ContactTransmissionType.FOMITE)) */);
	}

	/**
	 * Parses the {@code route=weight;route=weight} notation, e.g. {@code respiratory=0.7;directContact=0.3}.
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

	// fomite: /** Weight of the fomite (contaminated-surface) route. */
	// fomite: public double getFomite() {
	// fomite: 	return fomite;
	// fomite: }

	/**
	 * Returns the weight of the given route.
	 */
	public double get(ContactTransmissionType route) {
		switch (requireNonNull(route, "transmission route")) {
			case RESPIRATORY:
				return respiratory;
			case DIRECT_CONTACT:
				return directContact;
			// fomite: case FOMITE:
			// fomite: 	return fomite;
			default:
				throw new IllegalArgumentException("Unhandled transmission route '" + route + "'.");
		}
	}

	/**
	 * Returns the sum of all route weights.
	 */
	public double sum() {
		return respiratory + directContact /* fomite: + fomite */;
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
		// fomite: result.put(ContactTransmissionType.FOMITE, fomite);
		return result;
	}

	/**
	 * Serializes to the {@code route=weight;route=weight} notation using canonical keys.
	 */
	public String toToken() {
		return "respiratory=" + respiratory + ";directContact=" + directContact
				/* fomite: + ";fomite=" + fomite */;
	}

	private void validate() {
		checkFiniteNonNegative("respiratory", respiratory);
		checkFiniteNonNegative("directContact", directContact);
		// fomite: checkFiniteNonNegative("fomite", fomite);
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
