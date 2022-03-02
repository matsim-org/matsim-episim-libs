package org.matsim.episim.model;


import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class DefaultAntibodyModelTest {


	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	private List<VirusStrain> strainsToCheck = List.of(VirusStrain.SARS_CoV_2, VirusStrain.ALPHA, VirusStrain.DELTA, VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA2);
	private Config config;
	private DefaultAntibodyModel model;


	@Before
	public void setup() {

		config = EpisimTestUtils.createTestConfig();
		model = new DefaultAntibodyModel(config);

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
	 * Tests that relative antibody levels (nAb) spike when an immunity events occur (infection or vaccination) and
	 * decrease on all other days. In this example, the agent is infected w/ the wild type on day 50, gets vaccinated
	 * w/ mRNA on day 200 and gets infected w/ Delta on day 600.
	 *
	 * Plots are produced showing the nAb and vaccine effectiveness (ve) against every variant of concern. The plots can
	 * be found in the matsim-episim folder, in html format.
	 */

	@Test
	public void testMixOfVaccinesAndInfections() {

		List<ImmunityEvent> immunityEvents = List.of(VirusStrain.SARS_CoV_2, VaccinationType.mRNA, VirusStrain.DELTA);
		IntList immunityEventDays = IntList.of(50, 200, 600);

		Int2ObjectMap antibodyLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays);

		// Plot 1: nAb
		{
			IntColumn records = IntColumn.create("day");
			DoubleColumn values = DoubleColumn.create("antibodies");
			StringColumn groupings = StringColumn.create("scenario");

			for (int day : antibodyLevels.keySet()) {
				Object2DoubleMap strainToAntibodyMap = (Object2DoubleMap) antibodyLevels.get(day);

				for (Object strain : strainToAntibodyMap.keySet()) {
					records.append(day);

					double nAb = strainToAntibodyMap.getOrDefault(strain, 0.);

					values.append(nAb);
					groupings.append(strain.toString());

				}
			}
			producePlot(records, values, groupings, "nAb", "nAb: " + immunityEvents.toString(), "nAb.html");

		}

		// Plot 2: ve
		{
			IntColumn records = IntColumn.create("day");
			DoubleColumn values = DoubleColumn.create("antibodies");
			StringColumn groupings = StringColumn.create("scenario");

			for (int day : antibodyLevels.keySet()) {
				Object2DoubleMap strainToAntibodyMap = (Object2DoubleMap) antibodyLevels.get(day);

				for (Object strain : strainToAntibodyMap.keySet()) {
					records.append(day);

					double nAb = strainToAntibodyMap.getOrDefault(strain, 0.);

					var beta = 1.;
					var fact = 0.001;
					double immunityFactor = 1.0 / (1.0 + Math.pow(nAb, beta));
					final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
					final double probaWoVacc = 1 - Math.exp(-fact);
					final double ve = 1. - probaWVacc / probaWoVacc;

					values.append(ve);
					groupings.append(strain.toString());

				}
			}
			producePlot(records, values, groupings, "ve", "ve: " + immunityEvents.toString(), "ve.html");
		}


	}

	private Int2ObjectMap simulateAntibodyLevels(List<ImmunityEvent> immunityEvents, IntList immunityEventDays) {

		if (immunityEventDays.size() != immunityEvents.size()) {
			throw new RuntimeException("inputs must have same size");
		}

		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodiesPerDayAndStrain = new Int2ObjectAVLTreeMap<>();

		// create person
		EpisimPerson person = EpisimTestUtils.createPerson();

		// day 0: initialization
		int day = 0;
		model.updateAntibodies(person, day);

		Object2DoubleMap<VirusStrain> antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());
		antibodiesPerDayAndStrain.put(day, antibodiesOld);


		for (int i = 0; i < immunityEvents.size(); i++) {
			ImmunityEvent immunityEvent = immunityEvents.get(i);
			int immunityEventDay = immunityEventDays.getInt(i);

			if (day > immunityEventDay) {
				throw new RuntimeException("invalid immunity event day");
			}

			// antibodies constantly decreasing until immunity event
			while (day < immunityEventDay - 1) {
				day++;
				model.updateAntibodies(person, day);
				for (VirusStrain strain : strainsToCheck) {
					assertThat(person.getAntibodies(strain)).isLessThanOrEqualTo(antibodiesOld.get(strain));
				}

				antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());
				antibodiesPerDayAndStrain.put(day, antibodiesOld);
			}

			// immunity event occurs, antibodies will not increase on same day
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
			antibodiesPerDayAndStrain.put(day, antibodiesOld);

			// day after immunity event: antibodies should increase
			day++;

			model.updateAntibodies(person, day);


			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isGreaterThan(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());
			antibodiesPerDayAndStrain.put(day, antibodiesOld);


		}

		// continue the plot 100 days after final immunization event
		int upperLim = day + 100;
		while (day < upperLim) {
			day++;
			model.updateAntibodies(person, day);
			for (VirusStrain strain : strainsToCheck) {
				assertThat(person.getAntibodies(strain)).isLessThanOrEqualTo(antibodiesOld.get(strain));
			}

			antibodiesOld = new Object2DoubleOpenHashMap<>(person.getAntibodies());
			antibodiesPerDayAndStrain.put(day, antibodiesOld);
		}

		return antibodiesPerDayAndStrain;
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
