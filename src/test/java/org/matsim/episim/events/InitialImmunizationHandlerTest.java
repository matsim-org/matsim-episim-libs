package org.matsim.episim.events;

import org.apache.commons.math.stat.inference.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.AntibodyModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class InitialImmunizationHandlerTest {

	private EventsManager manager;
	private InitialImmunizationHandler handler;
	private Map<Id<Person>, EpisimPerson> personMap;

	@Before
	public void setUp() throws Exception {

		personMap = new HashMap<>();

		EpisimConfigGroup episimConfig = new EpisimConfigGroup();
		episimConfig.setStartDate(LocalDate.parse("2022-01-05"));

		handler = new InitialImmunizationHandler(personMap, episimConfig, Mockito.mock(AntibodyModel.class), Mockito.mock(ProgressionModel.class));

		manager = EventsUtils.createEventsManager();
		manager.addHandler(handler);
		manager.initProcessing();
	}


	// 1 	2	3	4	5	6	7	8	9 <---- old count
	//-3	-2	-1	0	1	2	3	4	5 <---- new count

	/**
	 * Tests functionality of InitialImmunizationHandler, which reads infection and vaccination events from an immunization
	 * history in order to initialize the EpisimPerson.
	 */
	@Test
	public void person() {

		// create population of one
		EpisimPerson patient0 = EpisimTestUtils.createPerson();
		personMap.put(patient0.getPersonId(), patient0);

		// start date on day 1
		LocalDate date1 = LocalDate.parse("2022-01-01"); // 1 DAY
		manager.processEvent(new EpisimStartEvent(date1, "..."));

		// initial infection (from import) on day 2
		manager.processEvent(new EpisimInitialInfectionEvent(2 * EpisimUtils.DAY, patient0.getPersonId(), VirusStrain.SARS_CoV_2, -1, -1, -1));

		assertThat(patient0.getNumInfections()).isEqualTo(1);
		assertThat(patient0.getNumVaccinations()).isEqualTo(0);
		assertThat(patient0.getVirusStrain()).isEqualTo(VirusStrain.SARS_CoV_2);
		assertThat(patient0.getDiseaseStatus()).isEqualTo(EpisimPerson.DiseaseStatus.infectedButNotContagious);
		assertThat(patient0.daysSinceInfection(0, 5)).isEqualTo(7);
		assertTrue(patient0.getInfectionDates().contains(-2 * EpisimUtils.DAY));

		// infection on day 3
		manager.processEvent(new EpisimInfectionEvent(3 * EpisimUtils.DAY, patient0.getPersonId(), patient0.getPersonId(), null, "undefined", 1, VirusStrain.SARS_CoV_2, 1.0, -1,-1,-1));

		assertThat(patient0.getNumInfections()).isEqualTo(2);
		assertThat(patient0.getNumVaccinations()).isEqualTo(0);
		assertThat(patient0.getVirusStrain()).isEqualTo(VirusStrain.SARS_CoV_2);
		assertThat(patient0.getDiseaseStatus()).isEqualTo(EpisimPerson.DiseaseStatus.infectedButNotContagious);
		assertThat(patient0.daysSinceInfection(1, 5)).isEqualTo(6);
		assertThat(patient0.getInfectionDates().size()).isEqualTo(2);
		assertTrue(patient0.getInfectionDates().contains(-EpisimUtils.DAY));

		// vaccination on day 4
		manager.processEvent(new EpisimVaccinationEvent(4 * EpisimUtils.DAY, patient0.getPersonId(), VaccinationType.mRNA,1 ));

		assertThat(patient0.getNumInfections()).isEqualTo(2);
		assertThat(patient0.getNumVaccinations()).isEqualTo(1);
		assertTrue(patient0.hadVaccinationType(VaccinationType.mRNA));
		assertThat(patient0.getVaccinationStatus()).isEqualTo(EpisimPerson.VaccinationStatus.yes);
		assertThat(patient0.daysSince(EpisimPerson.VaccinationStatus.yes, 5)).isEqualTo(5);
		assertThat(patient0.getVaccinationDates().size()).isEqualTo(1);
		assertTrue(patient0.getVaccinationDates().contains(0));

		// vaccination on day 5 and infection on day 6; neither should be registered because they occur on or after start date of new simulation
		manager.processEvent(new EpisimVaccinationEvent(5 * EpisimUtils.DAY, patient0.getPersonId(), VaccinationType.mRNA,1 ));
		manager.processEvent(new EpisimInfectionEvent(6 * EpisimUtils.DAY, patient0.getPersonId(), patient0.getPersonId(), null, "undefined", 1, VirusStrain.SARS_CoV_2, 1.0, -1,-1,-1));

		assertThat(patient0.getNumInfections()).isEqualTo(2);
		assertThat(patient0.getInfectionDates().size()).isEqualTo(2);
		assertThat(patient0.getNumVaccinations()).isEqualTo(1);
		assertThat(patient0.getVaccinationDates().size()).isEqualTo(1);


	}
}
