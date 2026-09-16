package org.matsim.episim.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enum for different types of vaccinations.
 *
 * <p>A type either describes a real vaccination or the immunity gained from an infection. The latter carries the
 * {@link VirusStrain} whose infection induced it ({@link #getSourceOfNaturalVaccination()}), so that protection can be
 * configured per (source strain, target strain) pair. Use {@link #naturalFor(VirusStrain)} to obtain it.</p>
 */
public class VaccinationType implements ImmunityEvent {

	public static final VaccinationType generic = new VaccinationType("generic");
	public static final VaccinationType mRNA = new VaccinationType("mRNA");
	public static final VaccinationType vector = new VaccinationType("vector");
	public static final VaccinationType ba1Update = new VaccinationType("ba1Update");
	public static final VaccinationType ba5Update = new VaccinationType("ba5Update");
	public static final VaccinationType xbbUpdate = new VaccinationType("xbbUpdate");

	/**
	 * Not a real vaccination, but used to describe the profile for persons that have been infected and gained a natural immunity.
	 *
	 * <p>Legacy anchor: declared with SARS-CoV-2 as its source so that existing {@code getParams(natural)} call sites
	 * keep resolving and {@code naturalFor(SARS_CoV_2)} returns this very instance. New code uses
	 * {@link #naturalFor(VirusStrain)}.</p>
	 */
	public static final VaccinationType natural = new VaccinationType("natural", VirusStrain.SARS_CoV_2, true);

	private static final List<VaccinationType> STANDARD_OPTIONS = List.of(
		generic,
		mRNA,
		vector,
		ba1Update,
		ba5Update,
		xbbUpdate,
		natural
	);

	private static final Map<String, VaccinationType> VACCINATIONS_BY_NAME = new ConcurrentHashMap<>();

	/**
	 * Canonical natural-immunity type per source strain. Natural types are created on demand by
	 * {@link #naturalFor(VirusStrain)}; they are deliberately not pre-created for every standard strain, because every
	 * registered type becomes a column in {@code vaccinations.tsv} and a default parameter set in
	 * {@code AntibodyConfigGroup}.
	 */
	private static final Map<VirusStrain, VaccinationType> NATURAL_BY_SOURCE = new ConcurrentHashMap<>();

	static {
		for (VaccinationType type : STANDARD_OPTIONS) {
			VACCINATIONS_BY_NAME.put(type.name, type);
			if (type.sourceOfNaturalVaccination != null)
				NATURAL_BY_SOURCE.put(type.sourceOfNaturalVaccination, type);
		}
	}

	private final String name;

	/**
	 * Strain whose infection induces this immunity; {@code null} for a real vaccination.
	 */
	private volatile VirusStrain sourceOfNaturalVaccination;

	/**
	 * Whether the source has been declared explicitly. A type that was first referenced by name only (e.g. by a config
	 * group that is read before the scenario declares it) is provisional and assumes it is a real vaccination until its
	 * source is declared via {@link #of(String, VirusStrain)}. This keeps config loading independent of the order in
	 * which the config groups are read.
	 */
	private volatile boolean sourceDeclared;

	private VaccinationType(String name) {
		this(name, null, true);
	}

	private VaccinationType(String name, VirusStrain sourceOfNaturalVaccination, boolean sourceDeclared) {
		this.name = Objects.requireNonNull(name, "Vaccination type name must not be null");
		this.sourceOfNaturalVaccination = sourceOfNaturalVaccination;
		this.sourceDeclared = sourceDeclared;
	}

	public static List<VaccinationType> getAllStandardOptions() {
		return STANDARD_OPTIONS;
	}

	/**
	 * Returns the standard vaccination types in declaration order, followed by all further registered types sorted by id.
	 * The standard order is kept so that outputs like {@code vaccinations.tsv} stay unchanged.
	 */
	public static List<VaccinationType> getAllOptions() {
		List<VaccinationType> vaccinationTypes = new ArrayList<>(STANDARD_OPTIONS);
		VACCINATIONS_BY_NAME.values().stream()
			.filter(t -> !STANDARD_OPTIONS.contains(t))
			.sorted(Comparator.comparing(VaccinationType::getId))
			.forEach(vaccinationTypes::add);
		return vaccinationTypes;
	}

	/**
	 * Returns the canonical instance for the given name. An unknown name is registered provisionally as a real
	 * vaccination; a natural-immunity source can still be declared later via {@link #of(String, VirusStrain)}.
	 */
	public static VaccinationType of(String name) {
		Objects.requireNonNull(name, "Vaccination type name must not be null");
		return VACCINATIONS_BY_NAME.computeIfAbsent(name, n -> new VaccinationType(n, null, false));
	}

	/**
	 * Returns the canonical instance and declares the strain whose infection induces it. A provisional type (only
	 * referenced by name so far) takes over the given source. A type cannot be declared for more than one source, and a
	 * source cannot be claimed by more than one type.
	 */
	public static VaccinationType of(String name, VirusStrain sourceOfNaturalVaccination) {
		Objects.requireNonNull(name, "Vaccination type name must not be null");
		Objects.requireNonNull(sourceOfNaturalVaccination, "Source strain must not be null");

		VaccinationType type = VACCINATIONS_BY_NAME.compute(name, (typeName, existing) -> {
			if (existing == null)
				return new VaccinationType(typeName, sourceOfNaturalVaccination, true);

			if (!existing.sourceDeclared) {
				existing.sourceOfNaturalVaccination = sourceOfNaturalVaccination;
				existing.sourceDeclared = true;
				return existing;
			}
			if (!sourceOfNaturalVaccination.equals(existing.sourceOfNaturalVaccination))
				throw new IllegalArgumentException("Vaccination type '" + typeName + "' is already registered for source strain '"
					+ existing.sourceOfNaturalVaccination + "' and cannot be registered for source strain '"
					+ sourceOfNaturalVaccination + "'");

			return existing;
		});

		// maintained outside the compute above: a ConcurrentHashMap must not be modified from within its own remapping
		VaccinationType previous = NATURAL_BY_SOURCE.putIfAbsent(sourceOfNaturalVaccination, type);
		if (previous != null && !previous.equals(type))
			throw new IllegalArgumentException("Source strain '" + sourceOfNaturalVaccination
				+ "' already induces vaccination type '" + previous + "' and cannot also induce '" + type + "'");

		return type;
	}

	/**
	 * Returns the canonical natural-immunity type induced by an infection with the given strain, creating and
	 * registering it when the strain is first seen. For SARS-CoV-2 this is the legacy {@link #natural} constant.
	 */
	public static VaccinationType naturalFor(VirusStrain sourceOfNaturalVaccination) {
		Objects.requireNonNull(sourceOfNaturalVaccination, "Source strain must not be null");

		VaccinationType existing = NATURAL_BY_SOURCE.get(sourceOfNaturalVaccination);
		if (existing != null)
			return existing;

		return of(naturalName(sourceOfNaturalVaccination), sourceOfNaturalVaccination);
	}

	/**
	 * Canonical name of the natural-immunity type induced by the given strain.
	 */
	public static String naturalName(VirusStrain sourceOfNaturalVaccination) {
		Objects.requireNonNull(sourceOfNaturalVaccination, "Source strain must not be null");
		return "natural_" + sourceOfNaturalVaccination.getPathogen().getName()
			+ "_" + sourceOfNaturalVaccination.getVirusStrainName();
	}

	/**
	 * Returns the canonical instance for an id as written to configs and event files, i.e. the type name.
	 */
	public static VaccinationType ofId(String s) {
		return of(s);
	}

	public String getName() {
		return name;
	}

	/**
	 * Strain whose infection induces this immunity, or {@code null} for a real vaccination.
	 */
	public VirusStrain getSourceOfNaturalVaccination() {
		return sourceOfNaturalVaccination;
	}

	/**
	 * Whether this type describes immunity gained from an infection rather than a real vaccination.
	 */
	public boolean isNaturalVaccination() {
		return sourceOfNaturalVaccination != null;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		VaccinationType that = (VaccinationType) o;
		return Objects.equals(name, that.name);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name);
	}

	public String getId() {
		return getName();
	}

	@Override
	public String toString() {
		return getId();
	}
}
