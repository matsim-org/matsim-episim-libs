package org.matsim.episim.analysis;

import org.assertj.core.data.Offset;
import org.assertj.core.data.Percentage;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimVaccinationEvent;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.facilities.Facility;
import org.matsim.testcases.MatsimTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.assertj.core.api.Assertions.assertThat;

public class HospitalNumbersFromEventsTest {


	private HospitalNumbersFromEvents.Handler handler;
	private Population population;
	private final int populationSize = 1_000_000;
	private VirusStrainConfigGroup strainConfig;

	@Before
	public void setUp() throws Exception {

		// instantiate configs
		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		strainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);


		// create population
		final Scenario scenario = ScenarioUtils.createScenario(config);
		PopulationFactory popFac = scenario.getPopulation().getFactory();
		population = scenario.getPopulation();


		for (int i = 0; i < populationSize; i++) {
			Id<Person> personId = Id.createPersonId(i);
			Person person = popFac.createPerson(personId);
			person.getAttributes().putAttribute("microm:modeled:age", 25);
			person.getAttributes().putAttribute("district", "Köln");
			population.addPerson(person);
		}

		// instantiate event handler
		Map<Id<Person>, HospitalNumbersFromEvents.Handler.ImmunizablePerson> data = new IdMap<>(Person.class, population.getPersons().size());
		handler = new HospitalNumbersFromEvents.Handler(data, population, episimConfig, strainConfig, vaccinationConfig);

	}

	/**
	 * check's that once a vaccination or infection event is logged, the person is entered into the "data" of the handler.
	 */
	@Test
	public void testHandlerData() {

		Id<Person> personId1 = Id.createPersonId(1);
		assertThat(handler.data.isEmpty()).isTrue();
		handler.handleEvent(new EpisimVaccinationEvent(0., personId1, VaccinationType.mRNA, 1));
		assertThat(handler.data.isEmpty()).isFalse();
		assertThat(handler.data.containsKey(personId1)).isTrue();

		handler.data.clear();

		assertThat(handler.data.isEmpty()).isTrue();
		handler.handleEvent(new EpisimInfectionEvent(0., personId1, Id.createPersonId("infector"), Id.create("facility", Facility.class), "", 2, VirusStrain.SARS_CoV_2, 0., 0.));
		assertThat(handler.data.isEmpty()).isFalse();
		assertThat(handler.data.containsKey(personId1)).isTrue();


		// strain config

		// immunity config

//		handler.handleEvent();

	}

	/**
	 * Age-dependent probability of being admitted to the hospital given infection matches configuration.
	 * In addition, probability of transitioning to critical (ICU) given hospitalization is checked.
	 * Test is applied to 25 year-olds and 75 year-olds.
	 */
	@Test
	public void testAgeComponent() {

		for (int i = 0; i < populationSize; i++) {
			Id<Person> personId = Id.createPersonId(i);
			handler.handleEvent(new EpisimInfectionEvent(0., personId, Id.createPersonId("infector"), Id.create("facility", Facility.class), "", 2, VirusStrain.SARS_CoV_2, 0., 0.));
		}

		int hospitalizations25 = handler.postProcessHospitalAdmissions.get(handler.postProcessHospitalAdmissions.lastIntKey());
		int icu25 = handler.postProcessICUAdmissions.get(handler.postProcessICUAdmissions.lastIntKey());

		assertThat((double) hospitalizations25 / populationSize).isCloseTo(0.024, Percentage.withPercentage(2));
		assertThat((double) icu25 / hospitalizations25).isCloseTo(0.15, Percentage.withPercentage(2));

		handler.data.clear();
		for (int i = 0; i < populationSize; i++) {
			Id<Person> personId = Id.createPersonId(i);
			Person person = population.getPersons().get(personId);
			person.getAttributes().putAttribute("microm:modeled:age", 75);
			handler.handleEvent(new EpisimInfectionEvent(10 * 24 * 3600., personId, Id.createPersonId("infector"), Id.create("facility", Facility.class), "", 2, VirusStrain.SARS_CoV_2, 0., 0.));
		}

		int hospitalizations75 = handler.postProcessHospitalAdmissions.get(handler.postProcessHospitalAdmissions.lastIntKey());
		int icu75 = handler.postProcessICUAdmissions.get(handler.postProcessICUAdmissions.lastIntKey());


		assertThat((double) hospitalizations75 / populationSize).isCloseTo(0.23, Percentage.withPercentage(2));
		assertThat((double) icu75 / hospitalizations75).isCloseTo(0.41, Percentage.withPercentage(2));

		assertThat(hospitalizations75).isGreaterThan(hospitalizations25);
		assertThat(icu75).isGreaterThan(icu25);
	}

	/**
	 * Checks differences in strain configuration when age & immunity is constant. In this scenario, a lower chance of
	 * hospitalization (0.6x) and lower chance of ICU (0.8x) is associated with Omicron BA.1 as compared to the wild type
	 */
	@Test
	public void testStrainComponent() {

		// set up strain configuration
		strainConfig.getParams(VirusStrain.SARS_CoV_2).setFactorSeriouslySick(1.);
		strainConfig.getParams(VirusStrain.SARS_CoV_2).setFactorCritical(1.);

		strainConfig.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorSeriouslySick(0.6);
		strainConfig.getOrAddParams(VirusStrain.OMICRON_BA1).setFactorCritical(0.8);


		// calculate hospitalizations/ICU for wild type
		for (int i = 0; i < populationSize; i++) {
			Id<Person> personId = Id.createPersonId(i);
			handler.handleEvent(new EpisimInfectionEvent(0., personId, Id.createPersonId("infector"), Id.create("facility", Facility.class), "", 2, VirusStrain.SARS_CoV_2, 0., 0.));
		}

		int hospitalizations100 = handler.postProcessHospitalAdmissions.get(handler.postProcessHospitalAdmissions.lastIntKey());
		int icu100 = handler.postProcessICUAdmissions.get(handler.postProcessICUAdmissions.lastIntKey());

		handler.data.clear();


		// calculate hospitalizations/ICU for omicron
		for (int i = 0; i < populationSize; i++) {
			Id<Person> personId = Id.createPersonId(i);
			handler.handleEvent(new EpisimInfectionEvent(100*24*3600., personId, Id.createPersonId("infector"), Id.create("facility", Facility.class), "", 2, VirusStrain.OMICRON_BA1, 0., 0.));
		}

		int hospitalizations60 = handler.postProcessHospitalAdmissions.get(handler.postProcessHospitalAdmissions.lastIntKey());
		int icu80 = handler.postProcessICUAdmissions.get(handler.postProcessICUAdmissions.lastIntKey());

		// omicron hospitalizations should be 0.6x wild type hospitalizations (see factorSeriouslySick above)
		assertThat(((double) hospitalizations60) / hospitalizations100).isCloseTo(0.6, Percentage.withPercentage(2));

		// The chance to visit icu given hospitalization (factorCritical) for omicron should be 0.8x wild typ.
		// However, the chance of going to the hospital at all is also lower for omicron (see above assert).
		// Thus we need to multiply the two probabilities 0.8*0.6
		assertThat(((double) icu80) / icu100).isCloseTo(0.8 * 0.6, Percentage.withPercentage(2));

	}

	@Test
	public void testImmunityComponent() {

		// calculate hospitalizations/ICU for people without any antibodies
		for (int i = 0; i < populationSize; i++) {
			Id<Person> personId = Id.createPersonId(i);
			handler.handleEvent(new EpisimInfectionEvent(0., personId, Id.createPersonId("infector"), Id.create("facility", Facility.class), "", 2, VirusStrain.SARS_CoV_2, 0., 0.));
		}

		int hospitalizationsNoImmunity = handler.postProcessHospitalAdmissions.get(handler.postProcessHospitalAdmissions.lastIntKey());
		int icuNoImmunity = handler.postProcessICUAdmissions.get(handler.postProcessICUAdmissions.lastIntKey());

		// calculate hospitalizations/ICU for people with antibodies
		// this time we do not clear the the handler.data, because we need to know how long ago their last immunity event was.
		for (int i = 0; i < populationSize; i++) {
			Id<Person> personId = Id.createPersonId(i);
			handler.handleEvent(new EpisimInfectionEvent(10*24*3600., personId, Id.createPersonId("infector"), Id.create("facility", Facility.class), "", 2, VirusStrain.SARS_CoV_2, 0., 10.));
		}

		int hospitalizationsWithImmunity = handler.postProcessHospitalAdmissions.get(handler.postProcessHospitalAdmissions.lastIntKey());
		int icuWithImmunity = handler.postProcessICUAdmissions.get(handler.postProcessICUAdmissions.lastIntKey());

		assertThat(hospitalizationsWithImmunity).isGreaterThan(hospitalizationsNoImmunity);
		assertThat(icuWithImmunity).isGreaterThan(icuNoImmunity);

	}

	@Test
	public void testImmunityComponent2() {

		// expected value (2 doses mRNA vs. DELTA) prob w/ respect to unvaccinated individual
		// p(hosp) of vaccinated person w/ respect to unvaccianted
//		double pHosp = 1. - 0.95;
//		// p(symptoms)
//		double pSym = 1. - 0.75;
//		// p(hosp|symptoms)

		strainConfig.getOrAddParams(VirusStrain.OMICRON_BA1);
		double pHospGivenSym = 0.1;

		// calculate hospitalizations/ICU for people without any antibodies
		for (int i = 0; i < populationSize; i++) {
			Id<Person> personId = Id.createPersonId(i);
			handler.handleEvent(new EpisimInfectionEvent(0., personId, Id.createPersonId("infector"), Id.create("facility", Facility.class), "", 2, VirusStrain.OMICRON_BA1, 0., 0.));
		}

		int hospitalizationsNoImmunity = handler.postProcessHospitalAdmissions.get(handler.postProcessHospitalAdmissions.lastIntKey());
		int icuNoImmunity = handler.postProcessICUAdmissions.get(handler.postProcessICUAdmissions.lastIntKey());

		// calculate hospitalizations/ICU for people with antibodies
		// this time we do not clear the the handler.data, because we need to know how long ago their last immunity event was.
		handler.postProcessICUAdmissions.clear();
		handler.postProcessHospitalAdmissions.clear();
		handler.data.clear();

		for (int i = 0; i < populationSize; i++) {
			Id<Person> personId = Id.createPersonId(i);
			handler.handleEvent(new EpisimVaccinationEvent(0., personId, VaccinationType.mRNA, 1));
			handler.handleEvent(new EpisimInfectionEvent(1.5 * 24 * 3600, personId, Id.createPersonId("infector"), Id.create("facility", Facility.class), "", 2, VirusStrain.OMICRON_BA1, 0., 1.9));
		}

		int hospitalizationsWithImmunity = handler.postProcessHospitalAdmissions.get(handler.postProcessHospitalAdmissions.lastIntKey());
		int icuWithImmunity = handler.postProcessICUAdmissions.get(handler.postProcessICUAdmissions.lastIntKey());


		double probHosp = ((double) hospitalizationsWithImmunity) / hospitalizationsNoImmunity;

		return;

//		assertThat(hospitalizationsWithImmunity).isGreaterThan(hospitalizationsNoImmunity);
//		assertThat(icuWithImmunity).isGreaterThan(icuNoImmunity);

	}





}
