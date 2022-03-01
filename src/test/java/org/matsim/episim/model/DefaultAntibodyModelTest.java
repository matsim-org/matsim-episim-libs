package org.matsim.episim.model;


import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class DefaultAntibodyModelTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	private Map<VirusStrain, Double> ak50PerStrain = new HashMap<>();

	private List<VirusStrain> strainsToCheck = List.of(VirusStrain.SARS_CoV_2, VirusStrain.ALPHA, VirusStrain.DELTA, VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA2);
	private Config config;
	private DefaultAntibodyModel model;


	@Before
	public void setup() {

		config = EpisimTestUtils.createTestConfig();

		model = new DefaultAntibodyModel(config);
		var vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);

		ak50PerStrain = vaccinationConfig.getAk50PerStrain();
	}


	/**
	 * Tests when there are no immunity events. Antibodies should remain 0.
	 */
	@Test
	public void testNoImmunityEvents() {

		// create person; antibodies map is empty
		EpisimPerson person = EpisimTestUtils.createPerson();

		assertTrue(person.getAntibodies().isEmpty());

		// update antibodies on day 0; antibody map should be filled with strains but ak values should equal 0.0
		model.updateAntibodies(person, 0);

		for (VirusStrain strain : VirusStrain.values()) {
			assertThat(person.getAntibodies(strain)).isEqualTo(0.0);
		}

		// at higher iterations, antibody levels should remain at 0.0 if there is no vaccination or infection
		for (int day = 0; day <= 100; day++) {
			model.updateAntibodies(person, day);
			for (VirusStrain strain : VirusStrain.values()) {
				assertThat(person.getAntibodies(strain)).isEqualTo(0.0);
			}
		}

	}

	/**
	 *  Agent is vaccinated  w/ generic vaccine 3 times; each time the antibodies increase one day later.
	 *  On all other days, the antibodies should decrease w/ respect to the previous day.
	 */
	@Test
	public void testVaccinations() {

		// create person
		EpisimPerson person = EpisimTestUtils.createPerson();

		// day 0
		model.updateAntibodies(person, 0);

		// VACCINATION 1
		// vaccinated on day 1; no antibodies yet
		person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 1);
		assertThat(person.getNumVaccinations()).isEqualTo(1);
		model.updateAntibodies(person, 1);

		for (VirusStrain strain : strainsToCheck) {
			assertThat(person.getAntibodies(strain)).isEqualTo(0.0);
		}

		// day 2; antibodies are generated
		model.updateAntibodies(person, 2);

		Object2DoubleMap<VirusStrain> antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		for (VirusStrain strain : strainsToCheck) {
			assertThat(person.getAntibodies(strain)).isNotEqualTo(0.0);
		}

		// day 3 - 100; antibodies constantly decreasing

		for (int day = 3; day <= 100; day++) {
			model.updateAntibodies(person, day);
			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		}

		Object2DoubleMap<VirusStrain> ak100 = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		// VACCINATION 2
		// vaccinated on day 101;
		person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 101);
		assertThat(person.getNumVaccinations()).isEqualTo(2);
		model.updateAntibodies(person, 101);
		Object2DoubleMap<VirusStrain> ak101 = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		for (VirusStrain strain : strainsToCheck) {
			assertThat(ak101.get(strain)).isLessThan(ak100.get(strain));
		}

		// day 102: ak increase
		model.updateAntibodies(person, 102);
		Object2DoubleMap<VirusStrain> ak102 = new Object2DoubleOpenHashMap<>(person.getAntibodies());
		for (VirusStrain strain : strainsToCheck) {
			assertThat(ak102.get(strain)).isGreaterThan(ak101.get(strain));
		}

		// day 103 - 200; ak decrease
		antibodiesOld = ak102;
		for (int day = 103; day <= 200; day++) {
			model.updateAntibodies(person, day);
			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		}

		Object2DoubleMap<VirusStrain> ak200 = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		// VACCINATION 3
		// vaccinated on day 201;
		person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 201);
		assertThat(person.getNumVaccinations()).isEqualTo(3);

		model.updateAntibodies(person, 201);
		Object2DoubleMap<VirusStrain> ak201 = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		for (VirusStrain strain : strainsToCheck) {
			assertThat(ak201.get(strain)).isLessThan(ak200.get(strain));
		}

		// day 202: ak increase
		model.updateAntibodies(person, 202);
		Object2DoubleMap<VirusStrain> ak202 = new Object2DoubleOpenHashMap<>(person.getAntibodies());
		for (VirusStrain strain : strainsToCheck) {
			assertThat(ak202.get(strain)).isGreaterThan(ak101.get(strain));
		}

		// day 203 - 300; ak decrease
		antibodiesOld = ak202;
		for (int day = 203; day <= 300; day++) {
			model.updateAntibodies(person, day);
			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		}


	}

	/**
	 * Agent is infected w/ wild type  3 times; each time the antibodies increase one day after agent recovers.
	 * On all other days, the antibodies should decrease w/ respect to the previous day.
	 *
	 * TODO: tests only pass when "if statement" on line 394 of EpisimPerson is commented out: if (!statusChanges.containsKey(status))
	 */
	@Test
	public void testInfections() {

		// create person
		EpisimPerson person = EpisimTestUtils.createPerson();

		// update antibodies on day 0
		model.updateAntibodies(person, 0);

		// INFECTION 1
		// infection on day 1 (midday)
		person.setInitialInfection(24 * 60 * 60 * 1.5, VirusStrain.SARS_CoV_2);
		assertThat(person.getNumInfections()).isEqualTo(1);

		// day 1 - 7; no antibodies
		for (int day = 1; day <= 7; day++) {
			model.updateAntibodies(person, day);
			for (VirusStrain strain : VirusStrain.values()) {
				assertThat(person.getAntibodies(strain)).isEqualTo(0.0);
			}
		}

		// recovered on day 8 (midday)
		person.setDiseaseStatus(24 * 60 * 60 * 8.5, EpisimPerson.DiseaseStatus.recovered);
		model.updateAntibodies(person, 8);

		for (VirusStrain strain : strainsToCheck) {
			assertThat(person.getAntibodies(strain)).isEqualTo(0.0);
		}

		// day 9: antibodies should appear
		model.updateAntibodies(person, 9);

		Object2DoubleMap<VirusStrain> antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		for (VirusStrain strain : VirusStrain.values()) {
			assertThat(person.getAntibodies(strain)).isNotEqualTo(0.0);
		}

//		person.setDiseaseStatus(24 * 60 * 60 * 9.5, EpisimPerson.DiseaseStatus.susceptible);

		// day 10 - 100, antibodies should decrease
		for (int day = 10; day <= 100; day++) {
			model.updateAntibodies(person, day);
			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		}


		// INFECTION 2
		// infection on day 101 (midday)

		EpisimTestUtils.infectPerson(person, VirusStrain.SARS_CoV_2, 24 * 60 * 60 * 101.5);

		assertThat(person.getNumInfections()).isEqualTo(2);

		// day 101 - 107; antibodies continue to decrease
		for (int day = 101; day <= 107; day++) {
			model.updateAntibodies(person, day);
			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		}

		// recovered on day 108 (midday); still decreased on that day
		person.setDiseaseStatus(24 * 60 * 60 * 108.5, EpisimPerson.DiseaseStatus.recovered);
		model.updateAntibodies(person, 108);

		for (VirusStrain strain : strainsToCheck) {
			assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
		}

		antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		// day 109: antibodies should increase
		model.updateAntibodies(person, 109);

		for (VirusStrain strain : strainsToCheck) {
			assertThat(person.getAntibodies(strain)).isGreaterThan(antibodiesOld.get(strain));
		}

		antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		// day 110 - 200, antibodies should decrease
		for (int day = 110; day <= 200; day++) {
			model.updateAntibodies(person, day);

			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		}

		// INFECTION 3
		// infection on day 201 (midday)
		person.setInitialInfection(24 * 60 * 60 * 201.5, VirusStrain.SARS_CoV_2);
		assertThat(person.getNumInfections()).isEqualTo(3);

		// day 101 - 107; antibodies continue to decrease
		for (int day = 201; day <= 207; day++) {
			model.updateAntibodies(person, day);
			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		}

		// recovered on day 208 (midday); still decreased on that day
		person.setDiseaseStatus(24 * 60 * 60 * 208.5, EpisimPerson.DiseaseStatus.recovered);
		model.updateAntibodies(person, 208);

		for (VirusStrain strain : strainsToCheck) {
			assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
		}

		antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		// day 209: antibodies should increase
		model.updateAntibodies(person, 209);

		for (VirusStrain strain : strainsToCheck) {
			assertThat(person.getAntibodies(strain)).isGreaterThan(antibodiesOld.get(strain));
		}

		antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());


		// day 210 - 200, antibodies should decrease
		for (int day = 210; day <= 300; day++) {
			model.updateAntibodies(person, day);

			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		}

	}
}
