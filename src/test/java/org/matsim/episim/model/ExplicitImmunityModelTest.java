package org.matsim.episim.model;

import org.junit.Test;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.ImmunityConfigGroup;
import org.matsim.episim.ImmunityConfigGroup.Combinator;
import org.matsim.episim.ImmunityConfigGroup.Model;
import org.matsim.episim.ProtectionCurve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behaviour of the immunity model that reads {@link ImmunityConfigGroup}.
 */
public class ExplicitImmunityModelTest {

	private static final Pathogen RSV = new Pathogen("RSV_ExplicitImmunityModelTest");
	private static final VirusStrain RSV_A = VirusStrain.of(RSV, "RSV_A_ExplicitImmunityModelTest");
	private static final VirusStrain FLU = VirusStrain.of(new Pathogen("FLU_ExplicitImmunityModelTest"),
		"FLU_A_ExplicitImmunityModelTest");
	private static final VaccinationType NIRSEVIMAB = VaccinationType.of("nirsevimab_ExplicitImmunityModelTest");

	private final ImmunityConfigGroup config = config();

	/**
	 * RSV-A leaves protection against infection that fades out over a year, and constant protection against a
	 * severe course. Nirsevimab protects against infection with a shorter curve.
	 */
	private static ImmunityConfigGroup config() {
		ImmunityConfigGroup config = new ImmunityConfigGroup();
		config.setModel(Model.explicit);
		config.setOtherPathogensProtection(0.0);

		config.getOrAddSource(RSV_A)
			.setProtection(DiseaseStatus.infectedButNotContagious, RSV_A, ProtectionCurve.parse("0>0.5|365>0.0"));
		config.getOrAddSource(RSV_A)
			.setProtection(DiseaseStatus.seriouslySick, RSV_A, ProtectionCurve.parse("0>0.8"));

		config.getOrAddSource(NIRSEVIMAB)
			.setProtection(DiseaseStatus.infectedButNotContagious, RSV_A, ProtectionCurve.parse("0>0.8|180>0.0"));

		return config;
	}

	private ExplicitImmunityModel model() {
		return new ExplicitImmunityModel(config);
	}

	private static EpisimPerson person(VirusStrain strain, int... infectionDays) {
		EpisimPerson person = EpisimTestUtils.createPerson(true, 40);
		for (int day : infectionDays)
			EpisimTestUtils.infectPerson(person, strain, day * EpisimUtils.DAY);
		return person;
	}

	@Test
	public void personWithoutHistoryIsNotProtected() {
		EpisimPerson person = EpisimTestUtils.createPerson(true, 40);

		assertThat(model().getFactor(person, RSV_A, DiseaseStatus.infectedButNotContagious, 100))
			.isEqualTo(1.0);
	}

	@Test
	public void protectionIsInterpolatedBetweenPointsAndHeldAfterTheLastOne() {
		EpisimPerson person = person(RSV_A, 0);
		ExplicitImmunityModel model = model();

		// the infection itself is the current episode for severity, but counts for a new infection
		assertThat(model.getFactor(person, RSV_A, DiseaseStatus.infectedButNotContagious, 0))
			.as("full protection of the curve on the day of the event")
			.isEqualTo(0.5);

		assertThat(model.getFactor(person, RSV_A, DiseaseStatus.infectedButNotContagious, 365 / 2))
			.as("halfway between 0.5 and 0.0")
			.isCloseTo(0.75, org.assertj.core.data.Offset.offset(0.01));

		assertThat(model.getFactor(person, RSV_A, DiseaseStatus.infectedButNotContagious, 500))
			.as("the last value is held")
			.isEqualTo(1.0);
	}

	@Test
	public void currentEpisodeDoesNotProtectAgainstItsOwnCourse() {
		EpisimPerson person = person(RSV_A, 10);

		assertThat(model().getFactor(person, RSV_A, DiseaseStatus.seriouslySick, 12))
			.as("the only infection is the current illness and is left out")
			.isEqualTo(1.0);

		EpisimPerson twice = person(RSV_A, 10, 400);

		assertThat(model().getFactor(twice, RSV_A, DiseaseStatus.seriouslySick, 402))
			.as("the earlier infection counts, the current one does not")
			.isCloseTo(1.0 - 0.8, org.assertj.core.data.Offset.offset(1e-9));
	}

	@Test
	public void combinatorMinTakesTheBestEvent() {
		EpisimPerson person = person(RSV_A, 0);
		person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, NIRSEVIMAB, 0);

		config.setCombinator(Combinator.min);

		assertThat(model().getFactor(person, RSV_A, DiseaseStatus.infectedButNotContagious, 0))
			.as("remaining risk of the better event, 0.8 protection")
			.isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-9));
	}

	@Test
	public void combinatorProductMultipliesRemainingRisk() {
		EpisimPerson person = person(RSV_A, 0);
		person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, NIRSEVIMAB, 0);

		config.setCombinator(Combinator.product);

		assertThat(model().getFactor(person, RSV_A, DiseaseStatus.infectedButNotContagious, 0))
			.as("0.5 remaining risk of the infection times 0.2 of the product")
			.isCloseTo(0.1, org.assertj.core.data.Offset.offset(1e-9));
	}

	@Test
	public void strainOfAnotherPathogenUsesTheConfiguredDefault() {
		EpisimPerson person = person(RSV_A, 0);

		config.setOtherPathogensProtection(0.25);

		assertThat(model().getFactor(person, FLU, DiseaseStatus.infectedButNotContagious, 10))
			.as("no curve for the other pathogen, the explicit default is used")
			.isCloseTo(0.75, org.assertj.core.data.Offset.offset(1e-9));
	}

	@Test
	public void infectionWithoutSourceFails() {
		EpisimPerson person = person(FLU, 0);

		assertThatThrownBy(() -> model().getFactor(person, RSV_A, DiseaseStatus.infectedButNotContagious, 10))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("an infection with '" + FLU + "'");
	}

	@Test
	public void vaccinationWithoutSourceFails() {
		EpisimPerson person = person(RSV_A, 0);
		person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 0);

		assertThatThrownBy(() -> model().getFactor(person, RSV_A, DiseaseStatus.infectedButNotContagious, 10))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("a vaccination with '" + VaccinationType.generic + "'");
	}

	@Test
	public void missingTargetOfAnOwnPathogenFails() {
		EpisimPerson person = person(RSV_A, 0, 100);

		assertThatThrownBy(() -> model().getFactor(person, RSV_A, DiseaseStatus.critical, 110))
			.as("the source describes RSV, so a missing curve is a hole in the config, not another pathogen")
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("critical");
	}

	@Test
	public void unsupportedTargetFails() {
		EpisimPerson person = person(RSV_A, 0);

		assertThatThrownBy(() -> model().getFactor(person, RSV_A, DiseaseStatus.deceased, 10))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void eventAfterTheDayAskedAboutIsIgnored() {
		EpisimPerson person = person(RSV_A, 100);

		assertThat(model().getFactor(person, RSV_A, DiseaseStatus.infectedButNotContagious, 50))
			.as("the infection has not happened on that day")
			.isEqualTo(1.0);
	}

	@Test
	public void infectivityIsNotReduced() {
		EpisimPerson person = person(RSV_A, 0);

		assertThat(model().getInfectivityFactor(person, RSV_A, 10)).isEqualTo(1.0);
	}
}
