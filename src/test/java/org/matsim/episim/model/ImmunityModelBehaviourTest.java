package org.matsim.episim.model;

import org.junit.Test;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.VaccinationConfigGroup;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down the behaviour of the two immunity models.
 *
 * <p>When these models were extracted, an equivalence test compared every value against the code they replaced
 * &mdash; the default methods of {@code DiseaseStatusTransitionModel} and the overrides of
 * {@code AntibodyDependentTransitionModel} &mdash; and found them identical. That code is gone now, so what
 * remains worth protecting are the oddities it carried, which look like mistakes and would otherwise be
 * "corrected" by the next reader. Each assertion below fails if one of them is silently removed.</p>
 */
public class ImmunityModelBehaviourTest {

	private static final VirusStrain STRAIN = VirusStrain.SARS_CoV_2;

	private final VaccinationConfigGroup vaccinationConfig = config();
	private final LegacyCurveImmunityModel curves = new LegacyCurveImmunityModel(vaccinationConfig);
	private final LegacyAntibodyImmunityModel antibodies = new LegacyAntibodyImmunityModel(vaccinationConfig);

	private static VaccinationConfigGroup config() {
		VaccinationConfigGroup config = new VaccinationConfigGroup();
		config.getOrAddParams(VaccinationType.natural)
			.setDaysBeforeFullEffect(1)
			.setEffectiveness(VaccinationConfigGroup.forStrain(STRAIN).atDay(1, 0.7).atDay(400, 0.3));
		return config;
	}

	private static EpisimPerson person(VirusStrain strain, int... infectionDays) {
		EpisimPerson person = EpisimTestUtils.createPerson(true, 40);
		for (int day : infectionDays)
			EpisimTestUtils.infectPerson(person, strain, day * EpisimUtils.DAY);

		person.setDiseaseStatus(infectionDays[infectionDays.length - 1] * EpisimUtils.DAY, DiseaseStatus.recovered);
		person.updateMaxAntibodies(strain, 5.0);
		return person;
	}

	/**
	 * The omicron factor of {@code seriouslySick} lists BA.5, the one of {@code critical} does not. Making the two
	 * lists agree looks like an obvious clean-up and would change results.
	 */
	@Test
	public void omicronFactorIsAppliedToSeverityButNotToIntensiveCare() {
		EpisimPerson ba5 = person(VirusStrain.OMICRON_BA5, 0, 1);

		double seriouslySick = antibodies.getFactor(ba5, VirusStrain.OMICRON_BA5, DiseaseStatus.seriouslySick, 10);
		double critical = antibodies.getFactor(ba5, VirusStrain.OMICRON_BA5, DiseaseStatus.critical, 10);

		assertThat(seriouslySick)
			.as("BA.5 is in the list of seriouslySick, so the antibody level is multiplied and protection is higher")
			.isLessThan(critical);

		// BA.1 is in both lists, so there the two agree
		EpisimPerson ba1 = person(VirusStrain.OMICRON_BA1, 0, 1);
		assertThat(antibodies.getFactor(ba1, VirusStrain.OMICRON_BA1, DiseaseStatus.seriouslySick, 10))
			.isEqualTo(antibodies.getFactor(ba1, VirusStrain.OMICRON_BA1, DiseaseStatus.critical, 10));
	}

	/**
	 * A person without any immunity before the current infection is spared by {@code critical} but not by
	 * {@code seriouslySick}, where the same guard sits commented out in the original code.
	 */
	@Test
	public void guardForPersonsWithoutPreviousImmunityAppliesOnlyToIntensiveCare() {
		EpisimPerson firstEver = person(STRAIN, 0);

		assertThat(antibodies.getFactor(firstEver, STRAIN, DiseaseStatus.critical, 10))
			.as("guard is active for critical")
			.isEqualTo(1.0);

		assertThat(antibodies.getFactor(firstEver, STRAIN, DiseaseStatus.seriouslySick, 10))
			.as("and commented out for seriouslySick, so the current infection discounts its own severity")
			.isLessThan(1.0);
	}

	/**
	 * {@code getImmunityEffectiveness} grants complete sterilising immunity for 180 days to anyone with two
	 * infections, regardless of any configured curve. Its own comment calls it a quick fix.
	 */
	@Test
	public void twoInfectionsGiveCompleteProtectionForOneHundredEightyDays() {
		EpisimPerson twice = person(STRAIN, 0, 1);

		assertThat(curves.getFactor(twice, STRAIN, DiseaseStatus.infectedButNotContagious, 179))
			.as("inside the window nothing can infect this person")
			.isEqualTo(0.0);

		assertThat(curves.getFactor(twice, STRAIN, DiseaseStatus.infectedButNotContagious, 181))
			.as("outside it the configured curve takes over")
			.isGreaterThan(0.0);

		assertThat(curves.getFactor(person(STRAIN, 0), STRAIN, DiseaseStatus.infectedButNotContagious, 179))
			.as("a single infection does not trigger it")
			.isGreaterThan(0.0);
	}

	/**
	 * The composite has to reproduce the split that the two independent Guice bindings produced: infection and
	 * infectivity from the curves, everything about the course of the disease from antibodies.
	 */
	@Test
	public void compositeRoutesInfectionToCurvesAndSeverityToAntibodies() {
		LegacySplitImmunityModel composite = new LegacySplitImmunityModel(curves, antibodies);
		EpisimPerson person = person(STRAIN, 0, 1);

		for (int day : new int[]{10, 181}) {
			assertThat(composite.getFactor(person, STRAIN, DiseaseStatus.infectedButNotContagious, day))
				.isEqualTo(curves.getFactor(person, STRAIN, DiseaseStatus.infectedButNotContagious, day));
			assertThat(composite.getInfectivityFactor(person, STRAIN, day))
				.isEqualTo(curves.getInfectivityFactor(person, STRAIN, day));

			for (DiseaseStatus target : new DiseaseStatus[]{DiseaseStatus.showingSymptoms, DiseaseStatus.seriouslySick, DiseaseStatus.critical})
				assertThat(composite.getFactor(person, STRAIN, target, day))
					.as("%s", target)
					.isEqualTo(antibodies.getFactor(person, STRAIN, target, day));
		}
	}
}
