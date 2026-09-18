package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.Immunizable;
import org.matsim.episim.VaccinationConfigGroup;

/**
 * Immunity from the antibody level a person carries.
 *
 * <p>Extraction of the code that computed immunity from antibodies before {@link ImmunityModel} existed: the
 * three overrides of {@code AntibodyDependentTransitionModel} for the severity side and the immunity factors of
 * {@code InfectionModelWithAntibodies} for the infection side. The formulas are unchanged, including the
 * peculiarities that are almost certainly unintended but are current behaviour:</p>
 * <ul>
 *     <li>severity reads {@code getMaxAntibodies}, the highest level ever reached, which never wanes, and which
 *     already includes the boost of the very infection whose severity is being decided;</li>
 *     <li>the "is this omicron" list is long for {@code seriouslySick} but only BA.1/BA.2 for {@code critical};</li>
 *     <li>the guard that spares a person without previous immunity is active for {@code critical} and commented
 *     out for {@code seriouslySick};</li>
 *     <li>{@code showingSymptoms} is not modified at all.</li>
 * </ul>
 *
 * <p><b>One deliberate omission.</b> The IgA term of {@code InfectionModelWithAntibodies} is not carried over. It
 * needs the per-infection history, which {@link Immunizable} does not expose, and it is built on hard-coded
 * SARS-CoV-2 lineage lists. No scenario in this repository binds that infection model, and the transitional
 * composite routes protection against infection to the curves, so this path is unreachable today. Anyone binding
 * this model for the infection side has to decide what the IgA term should become first.</p>
 */
public class LegacyAntibodyImmunityModel implements ImmunityModel {

	private final VaccinationConfigGroup vaccinationConfig;

	@Inject
	public LegacyAntibodyImmunityModel(VaccinationConfigGroup vaccinationConfig) {
		this.vaccinationConfig = vaccinationConfig;
	}

	@Override
	public double getFactor(Immunizable person, VirusStrain strain, DiseaseStatus target, int day) {

		switch (target) {
			case infectedButNotContagious:
				return remainingRisk(currentAntibodies(person, strain));

			case showingSymptoms:
				return 1.0;

			case seriouslySick:
				return remainingRisk(boosted(person, strain, omicronFactorForSeriouslySick(strain)));

			case critical:
				if (person.getNumVaccinations() == 0 && person.getNumInfections() - 1 == 0)
					return 1.0;

				return remainingRisk(boosted(person, strain, omicronFactorForCritical(strain)));

			default:
				throw new IllegalArgumentException("No protection is configured against disease status '" + target
					+ "'. Supported are infectedButNotContagious, showingSymptoms, seriouslySick and critical.");
		}
	}

	@Override
	public double getInfectivityFactor(Immunizable infector, VirusStrain strain, int day) {
		// an infector with antibodies sheds less, but only a quarter of the effect is applied
		return 1.0 - 0.25 * (1.0 - remainingRisk(infector.getAntibodyLevelAtInfection()));
	}

	/**
	 * Remaining risk for an antibody level, the shape shared by every use: {@code 1} at level zero, falling
	 * towards {@code 0} as the level grows.
	 */
	private double remainingRisk(double antibodies) {
		return 1.0 / (1.0 + Math.pow(antibodies, vaccinationConfig.getBeta()));
	}

	/**
	 * Highest antibody level ever reached, with the two modifications the severity code applies: four times as
	 * much for a boostered person, and an extra factor for omicron strains. Note the direction: the factor
	 * multiplies the antibody level, so it <b>increases</b> protection rather than describing immune escape.
	 */
	private static double boosted(Immunizable person, VirusStrain strain, double omicronFactor) {
		double antibodies = person.getMaxAntibodies(strain);

		if (person.getNumVaccinations() > 1)
			antibodies *= 4;

		return antibodies * omicronFactor;
	}

	private static double omicronFactorForSeriouslySick(VirusStrain strain) {
		boolean isOmicron = strain.equals(VirusStrain.OMICRON_BA1)
			|| strain.equals(VirusStrain.OMICRON_BA2)
			|| strain.equals(VirusStrain.OMICRON_BA5)
			|| strain.equals(VirusStrain.BQ)
			|| strain.equals(VirusStrain.EG)
			|| strain.equals(VirusStrain.XBB_15)
			|| strain.equals(VirusStrain.XBB_19)
			|| strain.equals(VirusStrain.STRAIN_A)
			|| strain.equals(VirusStrain.STRAIN_B)
			|| strain.toString().startsWith("A_")
			|| strain.toString().startsWith("B_");

		return isOmicron ? 3.7 : 1.0;
	}

	/**
	 * Deliberately shorter than {@link #omicronFactorForSeriouslySick}: the original code only listed BA.1 and BA.2 here.
	 */
	private static double omicronFactorForCritical(VirusStrain strain) {
		return strain.equals(VirusStrain.OMICRON_BA1) || strain.equals(VirusStrain.OMICRON_BA2) ? 3.7 : 1.0;
	}

	/**
	 * The current, waning antibody level, as opposed to the lifetime maximum used for severity. It is not part of
	 * {@link Immunizable}, so this needs the concrete person; see {@code LegacyCurveImmunityModel.asPerson} for the same
	 * problem on the other side.
	 */
	private static double currentAntibodies(Immunizable person, VirusStrain strain) {
		if (person instanceof EpisimPerson)
			return ((EpisimPerson) person).getAntibodies(strain);

		throw new IllegalArgumentException("Protection against infection reads the current antibody level, which "
			+ "only " + EpisimPerson.class.getSimpleName() + " provides, but got " + person.getClass().getName());
	}
}
