package org.matsim.episim.model;

import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** A named variant of a {@link Pathogen}, optionally derived from a parent strain. */
public final class VirusStrain implements ImmunityEvent, Comparable<VirusStrain> {

	public static final VirusStrain SARS_CoV_2 = new VirusStrain("SARS_CoV_2");
	public static final VirusStrain ALPHA = new VirusStrain("ALPHA", SARS_CoV_2);
	public static final VirusStrain B1351 = new VirusStrain("B1351", SARS_CoV_2);
	public static final VirusStrain DELTA = new VirusStrain("DELTA", ALPHA);
	public static final VirusStrain OMICRON_BA1 = new VirusStrain("OMICRON_BA1", DELTA);
	public static final VirusStrain OMICRON_BA2 = new VirusStrain("OMICRON_BA2", OMICRON_BA1);
	public static final VirusStrain OMICRON_BA5 = new VirusStrain("OMICRON_BA5", OMICRON_BA2);
	public static final VirusStrain XBB_15 = new VirusStrain("XBB_15", OMICRON_BA2);
	public static final VirusStrain XBB_19 = new VirusStrain("XBB_19", OMICRON_BA2);
	public static final VirusStrain BQ = new VirusStrain("BQ", OMICRON_BA5);
	public static final VirusStrain EG = new VirusStrain("EG", XBB_19);
	public static final VirusStrain STRAIN_A = new VirusStrain("STRAIN_A", OMICRON_BA5);
	public static final VirusStrain STRAIN_B = new VirusStrain("STRAIN_B", OMICRON_BA5);
	public static final VirusStrain A_1 = new VirusStrain("A_1", EG);
	public static final VirusStrain A_2 = new VirusStrain("A_2", A_1);
	public static final VirusStrain A_3 = new VirusStrain("A_3", A_2);
	public static final VirusStrain A_4 = new VirusStrain("A_4", A_3);
	public static final VirusStrain A_5 = new VirusStrain("A_5", A_4);
	public static final VirusStrain A_6 = new VirusStrain("A_6", A_5);
	public static final VirusStrain A_7 = new VirusStrain("A_7", A_6);
	public static final VirusStrain A_8 = new VirusStrain("A_8", A_7);
	public static final VirusStrain A_9 = new VirusStrain("A_9", A_8);
	public static final VirusStrain A_10 = new VirusStrain("A_10", A_9);
	public static final VirusStrain A_11 = new VirusStrain("A_11", A_10);
	public static final VirusStrain A_12 = new VirusStrain("A_12", A_11);
	public static final VirusStrain A_13 = new VirusStrain("A_13", A_12);
	public static final VirusStrain A_14 = new VirusStrain("A_14", A_13);
	public static final VirusStrain A_15 = new VirusStrain("A_15", A_14);
	public static final VirusStrain A_16 = new VirusStrain("A_16", A_15);
	public static final VirusStrain A_17 = new VirusStrain("A_17", A_16);
	public static final VirusStrain A_18 = new VirusStrain("A_18", A_17);
	public static final VirusStrain A_19 = new VirusStrain("A_19", A_18);
	public static final VirusStrain A_20 = new VirusStrain("A_20", A_19);
	public static final VirusStrain B_1 = new VirusStrain("B_1");
	public static final VirusStrain B_2 = new VirusStrain("B_2");
	public static final VirusStrain B_3 = new VirusStrain("B_3");
	public static final VirusStrain B_4 = new VirusStrain("B_4");
	public static final VirusStrain B_5 = new VirusStrain("B_5");
	public static final VirusStrain B_6 = new VirusStrain("B_6");
	public static final VirusStrain B_7 = new VirusStrain("B_7");
	public static final VirusStrain B_8 = new VirusStrain("B_8");
	public static final VirusStrain B_9 = new VirusStrain("B_9");
	public static final VirusStrain B_10 = new VirusStrain("B_10");
	public static final VirusStrain B_11 = new VirusStrain("B_11");
	public static final VirusStrain B_12 = new VirusStrain("B_12");
	public static final VirusStrain B_13 = new VirusStrain("B_13");
	public static final VirusStrain B_14 = new VirusStrain("B_14");
	public static final VirusStrain B_15 = new VirusStrain("B_15");
	public static final VirusStrain B_16 = new VirusStrain("B_16");
	public static final VirusStrain B_17 = new VirusStrain("B_17");
	public static final VirusStrain B_18 = new VirusStrain("B_18");
	public static final VirusStrain B_19 = new VirusStrain("B_19");
	public static final VirusStrain B_20 = new VirusStrain("B_20");

	private static final List<VirusStrain> STANDARD_OPTIONS = List.of(
		SARS_CoV_2,
		ALPHA,
		B1351,
		DELTA,
		OMICRON_BA1,
		OMICRON_BA2,
		OMICRON_BA5,
		XBB_15,
		XBB_19,
		BQ,
		EG,
		STRAIN_A,
		STRAIN_B,
		A_1,
		A_2,
		A_3,
		A_4,
		A_5,
		A_6,
		A_7,
		A_8,
		A_9,
		A_10,
		A_11,
		A_12,
		A_13,
		A_14,
		A_15,
		A_16,
		A_17,
		A_18,
		A_19,
		A_20,
		B_1,
		B_2,
		B_3,
		B_4,
		B_5,
		B_6,
		B_7,
		B_8,
		B_9,
		B_10,
		B_11,
		B_12,
		B_13,
		B_14,
		B_15,
		B_16,
		B_17,
		B_18,
		B_19,
		B_20
	);
	private static final Map<String, VirusStrain> STRAINS_BY_NAME = new ConcurrentHashMap<>();

	/**
	 * Position of each standard strain in {@link #STANDARD_OPTIONS}.
	 */
	private static final Map<String, Integer> STANDARD_INDEX = new HashMap<>();

	static {
		for (VirusStrain strain : STANDARD_OPTIONS) {
			STRAINS_BY_NAME.put(strain.virusStrainName, strain);
			STANDARD_INDEX.put(strain.virusStrainName, STANDARD_INDEX.size());
		}
	}

	/**
	 * Standard strains in declaration order (the order of the former enum), followed by all other strains sorted by name.
	 * Use this where the iteration order over strains influences results, e.g. the random numbers drawn per strain.
	 * Unlike insertion order, it does not depend on whether a config was built in code or read from a file.
	 */
	public static final Comparator<VirusStrain> DECLARATION_ORDER = Comparator
		.comparingInt((VirusStrain s) -> STANDARD_INDEX.getOrDefault(s.virusStrainName, STANDARD_OPTIONS.size()))
		.thenComparing(s -> s.virusStrainName);

	private final String virusStrainName;
	public final VirusStrain parent;

	/**
	 * Pathogen of a root strain; child strains always use the pathogen of their parent.
	 */
	private volatile Pathogen pathogen;

	/**
	 * Whether the pathogen has been declared explicitly. A strain that was first referenced by name only
	 * (e.g. by a config group that is read before {@code virusStrains}) is provisional and assumes SARS-CoV-2
	 * until its pathogen is declared via {@link #of(Pathogen, String)}.
	 */
	private volatile boolean pathogenDeclared;

	private VirusStrain(String virusStrainName) {
		this(Pathogen.SARS_COV_2, virusStrainName, null, true);
	}

	private VirusStrain(String virusStrainName, VirusStrain parent) {
		this(parent == null ? Pathogen.SARS_COV_2 : parent.getPathogen(), virusStrainName, parent, true);
	}

	private VirusStrain(Pathogen pathogen, String virusStrainName, VirusStrain parent, boolean pathogenDeclared) {
		this.pathogen = Objects.requireNonNull(pathogen, "Pathogen must not be null");
		this.virusStrainName = Objects.requireNonNull(virusStrainName, "Virus strain name must not be null");
		this.parent = parent;
		this.pathogenDeclared = pathogenDeclared;
	}

	public static Collection<VirusStrain> getAllStandardOptions() {
		return STANDARD_OPTIONS;
	}

	/**
	 * Returns the canonical strain instance for the given name. An unknown name is registered provisionally with
	 * pathogen SARS-CoV-2; the pathogen can still be declared later via {@link #of(Pathogen, String)}. This keeps
	 * config loading independent of the order in which the config groups are read.
	 */
	public static VirusStrain of(String name) {
		Objects.requireNonNull(name, "Virus strain name must not be null");
		return STRAINS_BY_NAME.computeIfAbsent(name, n -> new VirusStrain(Pathogen.SARS_COV_2, n, null, false));
	}

	/**
	 * Returns the canonical strain instance and declares its pathogen. A provisional strain (only referenced by
	 * name so far) takes over the given pathogen. A strain name cannot be declared for more than one pathogen.
	 */
	public static VirusStrain of(Pathogen pathogen, String name) {
		Objects.requireNonNull(pathogen, "Pathogen must not be null");
		Objects.requireNonNull(name, "Virus strain name must not be null");

		return STRAINS_BY_NAME.compute(name, (strainName, existing) -> {
			if (existing == null) {
				return new VirusStrain(pathogen, strainName, null, true);
			}
			if (!existing.pathogenDeclared) {
				existing.pathogen = pathogen;
				existing.pathogenDeclared = true;
				return existing;
			}
			if (!existing.getPathogen().equals(pathogen)) {
				throw new IllegalArgumentException("Virus strain name '" + strainName
					+ "' is already registered for pathogen '" + existing.getPathogen()
					+ "' and cannot be registered for pathogen '" + pathogen + "'");
			}
			return existing;
		});
	}

	/**
	 * Returns the canonical child strain instance, inheriting the pathogen from its parent.
	 * A strain name cannot be associated with a different parent after registration.
	 */
	public static VirusStrain of(String name, VirusStrain parent) {
		Objects.requireNonNull(name, "Virus strain name must not be null");
		Objects.requireNonNull(parent, "Parent strain must not be null");

		return STRAINS_BY_NAME.compute(name, (strainName, existing) -> {
			if (existing == null) {
				return new VirusStrain(strainName, parent);
			}
			if (!existing.getPathogen().equals(parent.getPathogen())) {
				throw new IllegalArgumentException("Virus strain name '" + strainName
					+ "' is already registered for pathogen '" + existing.getPathogen()
					+ "' and cannot inherit from pathogen '" + parent.getPathogen() + "'");
			}
			if (existing.parent != parent) {
				throw new IllegalArgumentException("Virus strain name '" + strainName
					+ "' is already registered with parent '" + existing.parent
					+ "' and cannot be registered with parent '" + parent + "'");
			}
			return existing;
		});
	}

	public String getVirusStrainName() {
		return virusStrainName;
	}

	public Pathogen getPathogen() {
		return parent != null ? parent.getPathogen() : pathogen;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		VirusStrain that = (VirusStrain) o;
		return Objects.equals(virusStrainName, that.virusStrainName);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(virusStrainName);
	}

	@Override
	public String toString() {
		return virusStrainName;
	}

	@Override
	public int compareTo(@NonNull VirusStrain o) {
		return Comparator.nullsFirst(String::compareTo)
			.compare(virusStrainName, o.virusStrainName);
	}
}
