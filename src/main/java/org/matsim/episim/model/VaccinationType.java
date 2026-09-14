package org.matsim.episim.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enum for different types of vaccinations.
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
	 */
	public static final VaccinationType natural = new VaccinationType("natural");

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

	static {
		for (VaccinationType type : STANDARD_OPTIONS) {
			VACCINATIONS_BY_NAME.put(type.name, type);
		}
	}

	private final String name;

	public VaccinationType(String name) {
		this.name = Objects.requireNonNull(name, "Vaccination type name must not be null");
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
	 * Returns the canonical instance for the given name, registering a new one when the name is first seen.
	 */
	public static VaccinationType of(String name) {
		Objects.requireNonNull(name, "Vaccination type name must not be null");
		return VACCINATIONS_BY_NAME.computeIfAbsent(name, VaccinationType::new);
	}

	public static VaccinationType ofId(String s) {
		return of(s);//in the future we will expand vaccination not only to one pathogen and in this case we will have vaccination type depending on Pathogen. Now it is just name
	}

	public String getName() {
		return name;
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
		return getName(); //in the future we will expand vaccination not only to one pathogen and in this case we will have vaccination type depending on Pathogen. Now it is just name
	}

	@Override
	public String toString() {
		return getId();
	}
}
