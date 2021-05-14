package org.matsim.episim.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimPersonStatusEvent;
import org.matsim.episim.model.VirusStrain;
import org.matsim.facilities.ActivityFacility;
import org.matsim.testcases.MatsimTestUtils;
import picocli.CommandLine;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



public class RValuesFromEventsTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	final private VirusStrain COV2 = VirusStrain.SARS_CoV_2;
	final private VirusStrain B117 = VirusStrain.B117;

	@org.testng.annotations.Test
	public void testOneVirusStrain() {

		Id<Person> a = Id.createPersonId("a");
		Id<Person> a1 = Id.createPersonId("a1");
		Id<Person> a2 = Id.createPersonId("a2");
		Id<Person> a3 = Id.createPersonId("a3");

		Id<Person> b = Id.createPersonId("b");
		Id<Person> b1 = Id.createPersonId("b1");

		String testEventsLocation = "test/output/org/matsim/episim/analysis/RValuesFromEventsTest";
		final EventWriter eventsWriter = new EventWriterXML(testEventsLocation + "/test01/events/test_events.xml.gz");
		EventsManager eventsManager = EventsUtils.createEventsManager();
		eventsManager.addHandler(eventsWriter);
		eventsManager.initProcessing();

		// day 1 : R = 3.0
		eventsManager.processEvent(buildStatusEvent(a, 1.));
		eventsManager.processEvent(buildInfectionEvent(a, a1, COV2,1.));
		eventsManager.processEvent(buildInfectionEvent(a, a2, COV2,1.));
		eventsManager.processEvent(buildInfectionEvent(a, a3, COV2, 1.));

		// day 2 : R = 0.25
		eventsManager.processEvent(buildStatusEvent(a1, 2));
		eventsManager.processEvent(buildStatusEvent(a2, 2));
		eventsManager.processEvent(buildStatusEvent(a3, 2));
		eventsManager.processEvent(buildStatusEvent(b, 2));
		eventsManager.processEvent(buildInfectionEvent(b, b1, COV2, 2));

		eventsManager.finishProcessing();
		eventsWriter.closeFile();

		RValuesFromEvents rValuesFromEvents = new RValuesFromEvents();
		new CommandLine(rValuesFromEvents).execute("--output", testEventsLocation);

		List<List<String>> records = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader( testEventsLocation + "/rValues.txt"))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split("\t");
				records.add(Arrays.asList(values));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		List<String> day1_allStrains = records.get(1);
		List<String> day1_cov2 = records.get(2);
		List<String> day2_allStrains = records.get(3);
		List<String> day2_cov2 = records.get(4);

		assertThat(Double.parseDouble(day1_allStrains.get(3))).isEqualTo(3.0);
		assertThat(Double.parseDouble(day1_cov2.get(3))).isEqualTo(3.0);

		assertThat(Double.parseDouble(day2_allStrains.get(3))).isEqualTo(0.25);
		assertThat(Double.parseDouble(day2_cov2.get(3))).isEqualTo(0.25);

	}

	@Test
	public void testTwoVirusStrains() {

		Id<Person> a = Id.createPersonId("a");
		Id<Person> a1 = Id.createPersonId("a1");
		Id<Person> a2 = Id.createPersonId("a2");
		Id<Person> a3 = Id.createPersonId("a3");

		Id<Person> b = Id.createPersonId("b");
		Id<Person> b1 = Id.createPersonId("b1");

		String testEventsLocation = "test/output/org/matsim/episim/analysis/RValuesFromEventsTest";
		final EventWriter eventsWriter = new EventWriterXML(testEventsLocation + "/test01/events/test_events.xml.gz");
		EventsManager eventsManager = EventsUtils.createEventsManager();
		eventsManager.addHandler(eventsWriter);
		eventsManager.initProcessing();

		// day 1 :  :  R = 3.0 = 3 infected / 1 infector
		eventsManager.processEvent(buildStatusEvent(a, 1.));
		eventsManager.processEvent(buildInfectionEvent(a, a1, COV2,1.));
		eventsManager.processEvent(buildInfectionEvent(a, a2, COV2,1.));
		eventsManager.processEvent(buildInfectionEvent(a, a3, COV2, 1.));

		// day 1 : B117 : R = 1.0 = 1 infected / 1 infector
		eventsManager.processEvent(buildStatusEvent(b, 1));
		eventsManager.processEvent(buildInfectionEvent(b, b1, B117, 1));

		// day 1 : all-strains : R = 2.0 = 4 infected / 2 infectors
		eventsManager.finishProcessing();
		eventsWriter.closeFile();

		RValuesFromEvents rValuesFromEvents = new RValuesFromEvents();
		new CommandLine(rValuesFromEvents).execute("--output", testEventsLocation);

		List<List<String>> records = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader( testEventsLocation + "/rValues.txt"))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split("\t");
				records.add(Arrays.asList(values));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		List<String> day1_allStrains = records.get(1);
		List<String> day1_cov2 = records.get(2);
		List<String> day1_b117 = records.get(3);

		assertThat(Double.parseDouble(day1_allStrains.get(3))).isEqualTo(2.0);
		assertThat(Double.parseDouble(day1_cov2.get(3))).isEqualTo(3.0);
		assertThat(Double.parseDouble(day1_b117.get(3))).isEqualTo(1.0);

	}

	private EpisimPersonStatusEvent buildStatusEvent(Id<Person> a1, double day) {
		double time = (day - 0.5) * (24 * 60 * 60);
		return new EpisimPersonStatusEvent(time, a1, EpisimPerson.DiseaseStatus.contagious);
	}

	private EpisimInfectionEvent buildInfectionEvent(Id<Person> infector, Id<Person> infected, VirusStrain virusStrain, double day) {
		Id<ActivityFacility> containerId = Id.create("fac", ActivityFacility.class);
		String infectionType = "home";
		int groupSize = 10;
		double time = (day - 0.5) * (24 * 60 * 60);
		double probability = 0.25;
		return new EpisimInfectionEvent(time, infected, infector, containerId, infectionType, groupSize, virusStrain, probability);
	}
}
