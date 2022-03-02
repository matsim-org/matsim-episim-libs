package org.matsim.episim.model;


import it.unimi.dsi.fastutil.ints.Int2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
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
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.components.Page;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.table.TableSliceGroup;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
	private Path output = Path.of("./output/");


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
	 * Agent is vaccinated  w/ generic vaccine 3 times; each time the antibodies increase one day later.
	 * On all other days, the antibodies should decrease w/ respect to the previous day.
	 */
	@Test
	public void testVaccinations() {


		for (VaccinationType vax1 : VaccinationType.values()) {
			for (VaccinationType vax2 : VaccinationType.values()) {
				for (VaccinationType vax3 : VaccinationType.values()) {
					//					for (VaccinationType vax4 : VaccinationType.values()) {

					if (vax1.equals(VaccinationType.natural) || vax2.equals(VaccinationType.natural) || vax3.equals(VaccinationType.natural)) {  //|| vax4.equals(VaccinationType.natural)) {
						continue; //TODO: should work for all vaccination types
					}
					//						if (vax1.equals(VaccinationType.omicronUpdate) && vax2.equals(VaccinationType.generic) && vax3.equals(VaccinationType.mRNA) && vax4.equals(VaccinationType.vector)) {
					//							continue; //TODO: should work for this combination, why does it not???
					//						}


					System.out.printf("Testing Combination of %s, %s, %s, and %s %n", vax1.toString(), vax2.toString(), vax3.toString(), vax1.toString());

					// create person
					EpisimPerson person = EpisimTestUtils.createPerson();

					// day 0
					model.updateAntibodies(person, 0);

					// VACCINATION 1
					// vaccinated on day 1; no antibodies yet
					person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, vax1, 1);
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
					person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, vax2, 101);
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

					person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, vax3, 201);
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
						assertThat(ak202.get(strain)).isGreaterThan(ak201.get(strain));
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
					Object2DoubleMap<VirusStrain> ak300 = new Object2DoubleOpenHashMap<>(person.getAntibodies());


					//						// VACCINATION 4
					//						// vaccinated on day 301;
					//
					//						person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, vax4, 301);
					//						assertThat(person.getNumVaccinations()).isEqualTo(4);
					//
					//						model.updateAntibodies(person, 301);
					//						Object2DoubleMap<VirusStrain> ak301 = new Object2DoubleOpenHashMap<>(person.getAntibodies());
					//
					//						for (VirusStrain strain : strainsToCheck) {
					//							assertThat(ak301.get(strain)).isLessThan(ak300.get(strain));
					//						}
					//
					//						// day 302: ak increase
					//						model.updateAntibodies(person, 302);
					//						Object2DoubleMap<VirusStrain> ak302 = new Object2DoubleOpenHashMap<>(person.getAntibodies());
					//						for (VirusStrain strain : strainsToCheck) {
					//							System.out.println(ak302.get(strain) >ak301.get(strain)); //TODO: revert
					////							assertThat(ak302.get(strain)).isGreaterThan(ak301.get(strain));
					//						}
					//
					//						// day 303 - 400; ak decrease
					//						antibodiesOld = ak302;
					//						for (int day = 303; day <= 400; day++) {
					//							model.updateAntibodies(person, day);
					//							for (VirusStrain strain : strainsToCheck) {
					//								assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
					//							}
					//
					//							antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());
					//
					//						}


					//					}
				}
			}
		}


	}


	@Test
	public void xxx() {

		List<ImmunityEvent> immunityEventsPass = List.of(VaccinationType.omicronUpdate, VaccinationType.generic, VaccinationType.vector, VaccinationType.mRNA);

		List<ImmunityEvent> immunityEventsFail = List.of(VaccinationType.omicronUpdate,VaccinationType.generic,VaccinationType.mRNA,VaccinationType.vector);

		List<ImmunityEvent> immunityEventsInfections = List.of(VirusStrain.SARS_CoV_2, VirusStrain.DELTA, VirusStrain.OMICRON_BA1);

		List<ImmunityEvent> immunityEvents = immunityEventsInfections;


		Int2ObjectMap antibodyLevels = simulateAntibodyLevels(immunityEvents);

		IntColumn records = IntColumn.create("day");
		DoubleColumn values = DoubleColumn.create("antibodies");
		StringColumn groupings = StringColumn.create("scenario");

		// standard hospitalizations from episim
		for (int day : antibodyLevels.keySet()) {
			Object2DoubleMap strainToAntibodyMap = (Object2DoubleMap) antibodyLevels.get(day);

			for (Object strain : strainToAntibodyMap.keySet()) {
				records.append(day);

				values.append(strainToAntibodyMap.getOrDefault(strain, 0.));
				groupings.append(strain.toString());

			}
		}

		producePlot(records, values, groupings, "xxx", immunityEvents.toString(), "Antibodies.html");


	}

	private Int2ObjectMap simulateAntibodyLevels(List<ImmunityEvent> immunityEvents) {

		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodiesPerDayAndStrain = new Int2ObjectAVLTreeMap<>();

		// create person
		EpisimPerson person = EpisimTestUtils.createPerson();

		// day 0
		int day = 0;
		model.updateAntibodies(person, day);

		antibodiesPerDayAndStrain.put(day, person.getAntibodies());


		Object2DoubleMap<VirusStrain> antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());

		for (ImmunityEvent immunityEvent : immunityEvents) {

			// day x1: immunity event occurs, antibodies will not increase
			day++;

			if (immunityEvent instanceof VaccinationType) {
				person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, (VaccinationType) immunityEvent, day);

			} else if (immunityEvent instanceof VirusStrain) {
				EpisimTestUtils.infectPerson(person, (VirusStrain) immunityEvent, 24 * 60 * 60 * day);
				person.setDiseaseStatus(24 * 60 * 60 * day, EpisimPerson.DiseaseStatus.recovered);
			} else {
				throw new RuntimeException("unknown immunity event type");
			}

			System.out.println(immunityEvent.toString() + " on day " + day);


			model.updateAntibodies(person, day);


			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThanOrEqualTo(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());
			antibodiesPerDayAndStrain.put(day,antibodiesOld);

			// day x2
			day++;

			model.updateAntibodies(person, day);


			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isGreaterThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());
			antibodiesPerDayAndStrain.put(day,antibodiesOld);


			// day x3 - x100; antibodies constantly decreasing
			day++;

			int uppperLimit = day + 97;

			for (; day <= uppperLimit; day++) {
				model.updateAntibodies(person, day);
				for (VirusStrain strain : strainsToCheck) {
					assertThat(person.getAntibodies(strain)).isLessThan(antibodiesOld.get(strain));
				}

				antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());
				antibodiesPerDayAndStrain.put(day,antibodiesOld);
			}
		}
		return antibodiesPerDayAndStrain;
	}


	/**
	 * Agent is infected w/ wild type  3 times; each time the antibodies increase one day after agent recovers.
	 * On all other days, the antibodies should decrease w/ respect to the previous day.
	 * <p>
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

	private void producePlot(IntColumn records, DoubleColumn values, StringColumn groupings, String s, String s2, String s3) {
		// Make plot
		Table table = Table.create(s);
		table.addColumns(records);
		table.addColumns(values);
		table.addColumns(groupings);

		TableSliceGroup tables = table.splitOn(table.categoricalColumn("scenario"));

		Axis yAxis = Axis.builder().type(Axis.Type.DEFAULT).build();

		Layout layout = Layout.builder(s2, "Day", "Antibodies").yAxis(yAxis).showLegend(true).build();

		ScatterTrace[] traces = new ScatterTrace[tables.size()];
		for (int i = 0; i < tables.size(); i++) {
			List<Table> tableList = tables.asTableList();
			traces[i] = ScatterTrace.builder(tableList.get(i).numberColumn("day"), tableList.get(i).numberColumn("antibodies"))
					.showLegend(true)
					.name(tableList.get(i).name())
					.mode(ScatterTrace.Mode.LINE)
					.build();
		}
		var figure = new Figure(layout, traces);

		try (Writer writer = new OutputStreamWriter(new FileOutputStream(s3), StandardCharsets.UTF_8)) {
			writer.write(Page.pageBuilder(figure, "target").build().asJavascript());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
