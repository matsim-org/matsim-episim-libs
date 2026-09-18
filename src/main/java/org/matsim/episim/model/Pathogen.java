package org.matsim.episim.model;

import java.util.Objects;

/** A disease-causing organism whose variants are represented by {@link VirusStrain}. */
public final class Pathogen {
	public static final Pathogen SARS_COV_2 = new Pathogen("SARS_CoV_2");

	private final String name;

	/**
	 * Creates a pathogen with the given stable name.
	 */
	public Pathogen(String name) {
		this.name = Objects.requireNonNull(name, "Pathogen name must not be null");
	}

	public String getName() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		Pathogen pathogen = (Pathogen) o;
		return name.equals(pathogen.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return name;
	}
}
