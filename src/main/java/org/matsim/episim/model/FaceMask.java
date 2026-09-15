package org.matsim.episim.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Type of face mask a person can wear, to decrease shedding rate, virus intake etc.
 * Standard masks are predefined; further masks can be registered by name via {@link #of(String, Double, Double)}.
 */
public class FaceMask {

	// Mask types need to be ordered by effectiveness
	// Values based on Eikenberry et al. https://arxiv.org/pdf/2004.03251.pdf, chapter 2.3
	public static final FaceMask NONE = new FaceMask("NONE", 1d, 1d);
//	CLOTH(0.6, 0.5),
//	SURGICAL(0.3, 0.2),
//	N95(0.15, 0.025);

	// values based on Kriegel
	public static final FaceMask CLOTH = new FaceMask("CLOTH", 0.8, 0.7);
	public static final FaceMask SURGICAL = new FaceMask("SURGICAL", 0.8, 0.7);
	public static final FaceMask N95 = new FaceMask("N95", 0.6, 0.2);

	private static final List<FaceMask> STANDARD_OPTIONS = List.of(NONE, CLOTH, SURGICAL, N95);

	private static final Map<String, FaceMask> FACE_MASK = new ConcurrentHashMap<>();

	static {
		for (FaceMask mask : STANDARD_OPTIONS) {
			FACE_MASK.put(mask.name, mask);
		}
	}

	public final String name;
	public final double shedding;
	public final double intake;

	/**
	 * Creates a face mask. Use {@link #of(String, Double, Double)} to register it for name based lookup.
	 */
	public FaceMask(String name, double shedding, double intake) {
		this.name = name;
		this.shedding = shedding;
		this.intake = intake;
	}

	/**
	 * Returns the standard face masks.
	 */
	public static List<FaceMask> values() {
		return STANDARD_OPTIONS;
	}

	/**
	 * Returns the registered face mask with the given name.
	 *
	 * @throws IllegalArgumentException if no face mask with this name has been registered
	 */
	public static FaceMask of(String name) {
		Objects.requireNonNull(name, "FaceMask name must not be null");
		FaceMask mask = FACE_MASK.get(name);
		if (mask == null)
			throw new IllegalArgumentException("Unknown face mask '" + name + "'; register it with FaceMask.of(name, shedding, intake) first.");
		return mask;
	}

	/**
	 * Returns the face mask with the given name, registering it when the name is first seen.
	 *
	 * @throws IllegalArgumentException if the name is already registered with different shedding or intake
	 */
	public static FaceMask of(String name, Double shedding, Double intake) {
		Objects.requireNonNull(name, "FaceMask name must not be null");
		Objects.requireNonNull(shedding, "FaceMask shedding must not be null");
		Objects.requireNonNull(intake, "FaceMask intake must not be null");
		FaceMask mask = FACE_MASK.computeIfAbsent(name, a -> new FaceMask(name, shedding, intake));
		if (mask.shedding != shedding || mask.intake != intake)
			throw new IllegalArgumentException("Face mask '" + name + "' is already registered with shedding=" + mask.shedding
				+ " and intake=" + mask.intake + ".");
		return mask;
	}

	public String getName() {
		return name;
	}

	public double getShedding() {
		return shedding;
	}

	public double getIntake() {
		return intake;
	}

	public static Collection<FaceMask> getAllStandardOptions() {
		return STANDARD_OPTIONS;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		FaceMask faceMask = (FaceMask) o;
		return Objects.equals(name, faceMask.name);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name);
	}

	@Override
	public String toString() {
		return name;
	}
}
