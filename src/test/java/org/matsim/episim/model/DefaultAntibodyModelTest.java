package org.matsim.episim.model;


import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import org.apache.log4j.Logger;
import org.assertj.core.data.Offset;
import org.junit.*;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.run.batch.CologneScenarioHubRound3;
import org.matsim.testcases.MatsimTestUtils;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import tech.tablesaw.plotly.api.LinePlot;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.components.Page;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.table.TableSliceGroup;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static com.google.common.math.Quantiles.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class DefaultAntibodyModelTest {

	private static final Logger log = Logger.getLogger(DefaultAntibodyModel.class);


	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	private final List<VirusStrain> strainsToCheck = List.of(VirusStrain.SARS_CoV_2, VirusStrain.ALPHA, VirusStrain.DELTA, VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA2);
	private DefaultAntibodyModel model;
	private AntibodyModel.Config antibodyConfig;
	private final Offset<Double> OFFSET = Offset.offset(0.1);
	;


	@Before
	public void setup() {

		antibodyConfig = AntibodyModel.newConfig();
		model = new DefaultAntibodyModel(antibodyConfig);

	}

	/*
	tests:
	init - (limits btwn 0.1 and 10, lognormal, immune response multiplier)
	init - updating antibodies after the fact
	updateAntibodies - vaccine
					- infection
					decrease
	compare to studies - w/o immuneReponse
						with immuneResponse


	 */


	// PART 1: Test Init
	@Test
	public void testInitImmuneReponseMutliplier() {

		// initialize population
		List<EpisimPerson> episimPeople = new ArrayList<>();
		for (int i = 0; i < 100000; i++) {
			EpisimPerson person = EpisimTestUtils.createPerson();
			person.setImmuneResponseMultiplier(0.22222); // dummy value that will be overwritten
			episimPeople.add(person);
		}

		// test when sigma is 0; all immuneResponseMultipliers should = 1.0
		antibodyConfig.setImmuneReponseSigma(0);
		model.init(episimPeople, 0);

		for (EpisimPerson person : episimPeople) {
			assertThat(person.getImmuneResponseMultiplier()).isEqualTo(1.0);
		}

		// test when sigma is 1; multiplies should range between 0.1 and 10.
		antibodyConfig.setImmuneReponseSigma(1);
		model.init(episimPeople, 0);

		double sigma1q1;
		double sigma1q3;
		{
			DoubleList multipliers = new DoubleArrayList();
			for (EpisimPerson person : episimPeople) {
				double multiplier = person.getImmuneResponseMultiplier();
				assertThat(multiplier).isGreaterThanOrEqualTo(0.1).isLessThanOrEqualTo(10);
				multipliers.add(multiplier);
			}

			assertThat(median().compute(multipliers)).isCloseTo(1.0, Offset.offset(0.01));

			sigma1q1 = percentiles().index(25).compute(multipliers);
			sigma1q3 = percentiles().index(75).compute(multipliers);

		}

		double sigma10q1;
		double sigma10q3;
		{
			antibodyConfig.setImmuneReponseSigma(10);
			model.init(episimPeople, 0);

			DoubleList multipliers = new DoubleArrayList();
			for (EpisimPerson person : episimPeople) {
				double multiplier = person.getImmuneResponseMultiplier();
				assertThat(multiplier).isGreaterThanOrEqualTo(0.1).isLessThanOrEqualTo(10);
				multipliers.add(multiplier);
			}

			assertThat(median().compute(multipliers)).isCloseTo(1.0, Offset.offset(0.01));

			sigma10q1 = percentiles().index(25).compute(multipliers);
			sigma10q3 = percentiles().index(75).compute(multipliers);
		}

		// higher sigma should have a flatter/wider distribution than lower sigma
		assertThat(sigma1q1).isGreaterThan(sigma10q1);
		assertThat(sigma1q3).isLessThan(sigma10q3);

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
	 * <p>
	 * Plots are produced showing the nAb and vaccine effectiveness (ve) against every variant of concern. The plots can
	 * be found in the matsim-episim folder, in html format.
	 */

	@Test
	public void testMixOfVaccinesAndInfections() {

		List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA, VaccinationType.mRNA, VaccinationType.ba1Update);
		IntList immunityEventDays = IntList.of(1, 181, 451);
//		List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA);
//		IntList immunityEventDays = IntList.of(1);

		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 750, EpisimTestUtils.createPerson());

		// Plot 1: nAb
		{
			IntColumn records = IntColumn.create("day");
			DoubleColumn values = DoubleColumn.create("antibodies");
			StringColumn groupings = StringColumn.create("scenario");

			for (int day : antibodyLevels.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevels.get(day);

				for (Object strain : strainToAntibodyMap.keySet()) {
					records.append(day);

					double nAb = strainToAntibodyMap.getOrDefault(strain, 0.);

					values.append(nAb);
					groupings.append(strain.toString());

				}
			}
			producePlot(records, values, groupings, "nAb", "nAb: " + immunityEvents, "nAb.html");

		}

		// Plot 2: ve
		{
			IntColumn records = IntColumn.create("day");
			DoubleColumn values = DoubleColumn.create("antibodies");
			StringColumn groupings = StringColumn.create("scenario");

			for (int day : antibodyLevels.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevels.get(day);

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
			producePlot(records, values, groupings, "ve", "ve: " + immunityEvents, "ve.html");
		}


	}

	@Test
	public void testEuScenarioHub() {


		double mutEscBa5 = 3.0;
		double mutEscStrainX = 8;//103;

		Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
		Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();
		configureAntibodies(initialAntibodies, antibodyRefreshFactors, mutEscBa5, mutEscStrainX);

		antibodyConfig = new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors);

		model = new DefaultAntibodyModel(antibodyConfig);

		List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA, VaccinationType.mRNA, VirusStrain.OMICRON_BA5, VaccinationType.fall22);
		// 2021-06-01 - mRNA
		// 2021-12-01 - mRNA
		// 2022-06-01 - BA5
		// 2022-10-01 - StrainA
		IntList immunityEventDays = IntList.of(1, 182, 365, 480);
//		List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA);
//		IntList immunityEventDays = IntList.of(1);

		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 1000, EpisimTestUtils.createPerson());

		// Plot 1: nAb
		{
			IntColumn records = IntColumn.create("day");
			DoubleColumn values = DoubleColumn.create("antibodies");
			StringColumn groupings = StringColumn.create("scenario");

			for (int day : antibodyLevels.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevels.get(day);

				for (Object strain : strainToAntibodyMap.keySet()) {
					records.append(day);

					double nAb = strainToAntibodyMap.getOrDefault(strain, 0.);

					values.append(nAb);
					groupings.append(strain.toString());

				}
			}
			producePlot(records, values, groupings, "nAb", "nAb: " + immunityEvents, "euScenarioHub3-nAb.html");

		}

		// Plot 2: ve
		{
			IntColumn records = IntColumn.create("day");
			DoubleColumn values = DoubleColumn.create("antibodies");
			StringColumn groupings = StringColumn.create("scenario");

			for (int day : antibodyLevels.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevels.get(day);

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
			producePlot(records, values, groupings, "ve", "ve: " + immunityEvents, "euScenarioHub3-ve.html");
		}


	}


	private void configureAntibodies(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies,
									 Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors,
									 double mutEscBa5, double mutEscStrainX) {

		for (VaccinationType immunityType : VaccinationType.values()) {
			initialAntibodies.put(immunityType, new EnumMap<>(VirusStrain.class));
			for (VirusStrain virusStrain : VirusStrain.values()) {

				if (immunityType == VaccinationType.mRNA) {
					initialAntibodies.get(immunityType).put(virusStrain, 29.2); //10.0
				} else if (immunityType == VaccinationType.vector) {
					initialAntibodies.get(immunityType).put(virusStrain, 6.8);  //2.5
				} else {
					initialAntibodies.get(immunityType).put(virusStrain, 5.0);
				}
			}
		}

		for (VirusStrain immunityType : VirusStrain.values()) {
			initialAntibodies.put(immunityType, new EnumMap<>(VirusStrain.class));
			for (VirusStrain virusStrain : VirusStrain.values()) {
				initialAntibodies.get(immunityType).put(virusStrain, 5.0);
			}
		}


		//mRNAAlpha, mRNADelta, mRNABA1 comes from Sydney's calibration.
		//The other values come from Rössler et al.
		//Wildtype
		double mRNAAlpha = 29.2;

		// initialAntibodies.get(IMMUNITY GIVER).put(IMMUNITY AGAINST, ab level);
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.SARS_CoV_2, mRNAAlpha);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.SARS_CoV_2, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.SARS_CoV_2, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.SARS_CoV_2, 0.01);


		//Alpha
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.ALPHA, mRNAAlpha);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.ALPHA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.ALPHA, 0.01);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.ALPHA, 0.01);

		//DELTA
		double mRNADelta = 10.9;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150. / 300.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450. / 300.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 0.2 / 6.4);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 0.2 / 6.4);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.DELTA, 0.2 / 6.4);


		//BA.1
		double mRNABA1 = 1.9;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, mRNABA1);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, mRNABA1 * 4. / 20.); //???
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 8. / 20.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 64.0 / 300.);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4); //todo: is 1.4

		//BA.2
		double mRNABA2 = mRNABA1;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, mRNABA2);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, mRNABA2 * 4. / 20.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 8. / 20.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);


		//BA.5
		double mRNABa5 = mRNABA2 / mutEscBa5;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA5, mRNABa5);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA5, mRNABa5 * 4. / 20.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 8. / 20.);
		initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / 1.4 / mutEscBa5);// todo: do we need 1.4?
		initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / mutEscBa5);
		initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA5, 64.0 / 300.);

		// NEW STRAINS

		// A) all "new/updated/novel" vaccinations and infections give only 0.01 protection against "old" strains

		for (VirusStrain protectionAgainst : List.of(VirusStrain.SARS_CoV_2, VirusStrain.ALPHA, VirusStrain.DELTA, VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA2, VirusStrain.OMICRON_BA5)) {
			for (ImmunityEvent vax : CologneScenarioHubRound3.newVaccinations.values()) {
				initialAntibodies.get(vax).put(protectionAgainst, 0.01);
			}

			for (ImmunityEvent strain : CologneScenarioHubRound3.newVirusStrains.values()) {
				initialAntibodies.get(strain).put(protectionAgainst, 0.01);
			}
		}

		// B) "old" vaccinations and infections give the same protection to StrainX as to BA5 PLUS an escape
		double mRNAStrainX = mRNABa5 / mutEscStrainX;

		for (VirusStrain newStrain : CologneScenarioHubRound3.newVirusStrains.values()) {
			initialAntibodies.get(VaccinationType.mRNA).put(newStrain, mRNAStrainX);
			initialAntibodies.get(VaccinationType.vector).put(newStrain, mRNAStrainX * 4. / 20.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(newStrain, mRNAStrainX * 6. / 20.);
			initialAntibodies.get(VirusStrain.ALPHA).put(newStrain, mRNAStrainX * 6. / 20.);
			initialAntibodies.get(VirusStrain.DELTA).put(newStrain, mRNAStrainX * 8. / 20.);

			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(newStrain, 64.0 / 300. / mutEscBa5 / mutEscStrainX);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(newStrain, 64.0 / 300. / mutEscBa5 / mutEscStrainX);
			initialAntibodies.get(VirusStrain.OMICRON_BA5).put(newStrain, 64.0 / 300. / mutEscStrainX);

		}

		// C) Immunity between the novel StrainsX:
		// Newer Strains give full (non-escaped) protection against themselves AND previous strains
		// New Strains give escaped protection against future strainA

		// initialAntibodies.get(IMMUNITY GIVER).put(IMMUNITY AGAINST, ab level);

		for (LocalDate dateProtectionGiver : CologneScenarioHubRound3.newVirusStrains.keySet()) {

			for (LocalDate dateProtectionAgainst : CologneScenarioHubRound3.newVirusStrains.keySet()) {

				if (dateProtectionGiver.equals(dateProtectionAgainst) || dateProtectionGiver.isAfter(dateProtectionAgainst)) {
					// Newer Strains give full (non-escaped) protection against themselves AND previous strains
					initialAntibodies.get(CologneScenarioHubRound3.newVirusStrains.get(dateProtectionGiver)).put(CologneScenarioHubRound3.newVirusStrains.get(dateProtectionAgainst), mRNAAlpha);

				} else {
					// New Strains give escaped protection against future strainA
					initialAntibodies.get(CologneScenarioHubRound3.newVirusStrains.get(dateProtectionGiver)).put(CologneScenarioHubRound3.newVirusStrains.get(dateProtectionAgainst), mRNAAlpha/ mutEscStrainX);

				}

			}
		}

		// D) Immunity provided by new vaccines against novel StrainsX:
		// Provides baseline immunity if StrainX was spawned more than 6 months before vaccination campaign begins
		// provides reduced immunity otherwise

		for (LocalDate dateProtectionGiver : CologneScenarioHubRound3.newVaccinations.keySet()) {

			for (LocalDate dateProtectionAgainst : CologneScenarioHubRound3.newVirusStrains.keySet()) {

				if (dateProtectionGiver.isAfter(dateProtectionAgainst.plusMonths(6))) {
					// Provides baseline immunity if StrainX was spawned more than 6 months before vaccination campaign begins

					initialAntibodies.get(CologneScenarioHubRound3.newVaccinations.get(dateProtectionGiver)).put(CologneScenarioHubRound3.newVirusStrains.get(dateProtectionAgainst), mRNAAlpha);

				}
				else {
					// provides reduced immunity otherwise
					initialAntibodies.get(CologneScenarioHubRound3.newVaccinations.get(dateProtectionGiver)).put(CologneScenarioHubRound3.newVirusStrains.get(dateProtectionAgainst), mRNAAlpha/ mutEscStrainX);

				}


			}
		}

		// R E F R E S H    F A C T O R S
		for (VaccinationType immunityType : VaccinationType.values()) {
			antibodyRefreshFactors.put(immunityType, new EnumMap<>(VirusStrain.class));
			for (VirusStrain virusStrain : VirusStrain.values()) {

				if (immunityType == VaccinationType.mRNA) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				} else if (immunityType == VaccinationType.vector) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
				} else if (immunityType == VaccinationType.ba1Update) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				} else if (CologneScenarioHubRound3.newVaccinations.containsValue(immunityType)) {
					if (CologneScenarioHubRound3.newVirusStrains.containsValue(virusStrain)) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 1.0);
					} else {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
					}

				} else {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, Double.NaN);
				}

			}
		}

		for (VirusStrain immunityType : VirusStrain.values()) {
			antibodyRefreshFactors.put(immunityType, new EnumMap<>(VirusStrain.class));
			for (VirusStrain virusStrain : VirusStrain.values()) {
				if (CologneScenarioHubRound3.newVirusStrains.containsValue(immunityType) && CologneScenarioHubRound3.newVirusStrains.containsValue(virusStrain)) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 1.0);
				} else {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				}
			}
		}
	}

	/**
	 * Tests the immuneResponseMultiplier of EpisimPerson; the agent w/ a higher immune response to vaccination/infection
	 * will have a multiplier of 2 while the "normal" agent has a multiplier of 1.
	 * a) 1st immunity event: high-response agent will gain 2x antibodies as regular-response agent
	 * b) 2nd immunity event: high-response agent will have their antibodies multiplied/refreshed by a factor 2x as high as the regular-response agent
	 */
	@Test
	public void testImmunityResponseMultiplier() {

		List<ImmunityEvent> immunityEvents = List.of(VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA1, VirusStrain.OMICRON_BA1);

		int secondImmunityEvent = 5;
		int thirdImmunityEvent = 10;
		IntList immunityEventDays = IntList.of(1, secondImmunityEvent, thirdImmunityEvent);

		// Person with normal immune response to immunity events
		EpisimPerson personNormal = EpisimTestUtils.createPerson();
		personNormal.setImmuneResponseMultiplier(1);
		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevelsNormal = simulateAntibodyLevels(immunityEvents, immunityEventDays, thirdImmunityEvent + 1, personNormal);

		// Person with high immune response to immunity events (2x as much as normal person)
		EpisimPerson personHigh = EpisimTestUtils.createPerson();
		personHigh.setImmuneResponseMultiplier(2);
		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevelsHigh = simulateAntibodyLevels(immunityEvents, immunityEventDays, thirdImmunityEvent + 1, personHigh);


		assertThat(antibodyLevelsNormal.size()).isEqualTo(antibodyLevelsHigh.size());


		// day of 1st infection; both agents have 0 antibodies
		for (VirusStrain strain : strainsToCheck) {
			assertThat(antibodyLevelsNormal.get(1).get(strain)).isEqualTo(0);
			assertThat(antibodyLevelsNormal.get(1).get(strain)).isEqualTo(0);
		}

		// day after 1st infection; both agents have >0 antibodies; high-response agent has 2x # of antibodies as regular-response agent
		for (VirusStrain strain : strainsToCheck) {
			assertThat(antibodyLevelsNormal.get(2).get(strain)).isGreaterThan(0);
			assertThat(2 * antibodyLevelsNormal.get(2).get(strain)).isCloseTo(antibodyLevelsHigh.get(2).getDouble(strain), OFFSET);
		}


		// days between 1st and 2nd infection:  high-response agent continues to have 2x # of antibodies as regular-response agent
		for (int i = 3; i <= secondImmunityEvent; i++) {
			for (VirusStrain strain : strainsToCheck) {
				assertThat(2 * antibodyLevelsNormal.get(i).get(strain)).isCloseTo(antibodyLevelsHigh.get(i).getDouble(strain), OFFSET);
			}
		}

		// antibody jump after 2nd infection will be 2x higher for high immunity agent.
		for (VirusStrain strain : strainsToCheck) {
			double jumpNormal = antibodyLevelsNormal.get(secondImmunityEvent + 1).getDouble(strain) / antibodyLevelsNormal.get(secondImmunityEvent).getDouble(strain);
			double jumpHigh = antibodyLevelsHigh.get(secondImmunityEvent + 1).getDouble(strain) / antibodyLevelsHigh.get(secondImmunityEvent).getDouble(strain);

			assertThat(jumpHigh).isCloseTo(2 * jumpNormal, OFFSET);

		}

	}



	@Test
	public void immunizationByBa1() {

		final String days = "day";
		Column<Integer> records = IntColumn.create(days);

		final String vaccineEfficacies = "VE";
		Column<Double> values = DoubleColumn.create(vaccineEfficacies);
		final String grouping = "grouping";
		var groupings = StringColumn.create(grouping);

		var fact = 0.001;
		var beta = 3.;

		// gather results from antibody model
		List<ImmunityEvent> immunityEvents = List.of(VirusStrain.OMICRON_BA1);
		IntList immunityEventDays = IntList.of(0);
		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 600, EpisimTestUtils.createPerson());

		for (int ii = 0; ii < 600; ii++) {
			Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevels.get(ii);
			{
				var nAb = strainToAntibodyMap.get(VirusStrain.OMICRON_BA1);
				double immunityFactor = 1.0 / (1.0 + Math.pow(nAb, beta));
				final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
				final double probaWoVacc = 1 - Math.exp(-fact);
				final double ve = 1. - probaWVacc / probaWoVacc;
				log.info(ve);
				records.append(ii);
				values.append(ve);
				groupings.append("... against ba1");
			}
			{
				var nAb = strainToAntibodyMap.get(VirusStrain.OMICRON_BA2);
				double immunityFactor = 1.0 / (1.0 + Math.pow(nAb, beta));
				final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
				final double probaWoVacc = 1 - Math.exp(-fact);
				final double ve = 1. - probaWVacc / probaWoVacc;
				log.info(ve);
				records.append(ii);
				values.append(ve);
				groupings.append("... against ba2");
			}
			{
				var nAb = strainToAntibodyMap.get(VirusStrain.DELTA);
				double immunityFactor = 1.0 / (1.0 + Math.pow(nAb, beta));
				final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
				final double probaWoVacc = 1 - Math.exp(-fact);
				final double ve = 1. - probaWVacc / probaWoVacc;
				log.info(ve);
				records.append(ii);
				values.append(ve);
				groupings.append("... against delta");
			}

		}

		Table table = Table.create("Infection history: BA.1 infection");
		table.addColumns(records);
		table.addColumns(values);
		table.addColumns(groupings);
		var figure = LinePlot.create(table.name(), table, days, vaccineEfficacies, grouping);

		try (Writer writer = new OutputStreamWriter(new FileOutputStream("output.html"), StandardCharsets.UTF_8)) {
			writer.write(Page.pageBuilder(figure, "target").build().asJavascript());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


	// PART 3: test antibody model against studies

	/**
	 * https://papers.ssrn.com/sol3/papers.cfm?abstract_id=3949410
	 */
	@Test
	public void testNordstroemEtAl() {


		final String days = "day";
		IntColumn records = IntColumn.create(days);

		final String vaccineEfficacies = "VE";
		DoubleColumn values = DoubleColumn.create(vaccineEfficacies);
		final String grouping = "grouping";
		var groupings = StringColumn.create(grouping);

		final String nordstrom = "Nordström (Delta)";
		final String ukhsa = "UKHSA (Omicron)";
		final String ba1booster = "UKHSA (BA.1, booster)";
		final String ba2booster = "UKHSA (BA.2, booster)";
		final String deltabooster = "UKHSA (delta, booster)";
		final String eyreBNTDelta = "EyreBNTDelta";
		final String eyreBNTAlpha = "EyreBNTAlpha";

		var fact = 0.001;
		var beta = 1.2;

		// gather results from antibody model
		List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA, VaccinationType.mRNA);

		IntList immunityEventDays = IntList.of(1, 220);

		List<EpisimPerson> episimPeople = new ArrayList<>();
		int popSize = 10000;
		for (int i = 0; i < popSize; i++) {
			episimPeople.add(EpisimTestUtils.createPerson());
		}

//		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevelsAvg = simulateAntibodyLevels(immunityEvents, immunityEventDays, 600, EpisimTestUtils.createPerson());

		antibodyConfig.setImmuneReponseSigma(3.);
		model.init(episimPeople, 0);
		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevelsAvg = new Int2ObjectArrayMap<>();
		for (EpisimPerson person : episimPeople) {
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 600, person);
			if (antibodyLevelsAvg.isEmpty()) {

				antibodyLevelsAvg = new Int2ObjectAVLTreeMap<>(antibodyLevels) ;
				antibodyLevelsAvg.forEach((k, v) -> v.forEach((k2, v2) -> antibodyLevels.get(k).put(k2, v2 / popSize)));

				System.out.println("");
			} else {
				for (int day : antibodyLevels.keySet()) {
					Object2DoubleMap<VirusStrain> dayLevelAvg = antibodyLevelsAvg.get(day);
					Object2DoubleMap<VirusStrain> dayLevelNew = antibodyLevels.get(day);

					for (VirusStrain strain : dayLevelNew.keySet()) {
						dayLevelAvg.merge(strain, dayLevelNew.getDouble(strain), (a, b) -> Double.sum(a, b/popSize));
					}
				}
			}
		}






		// antibodies from DefaultAntibodyModel, converted to vaccine efficiency (for beta = 1 and beta = 3)
		{
			for (int day : antibodyLevelsAvg.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevelsAvg.get(day);

				double nAb = strainToAntibodyMap.getOrDefault(VirusStrain.DELTA, 0.);

				{
					records.append(day);
					double immunityFactor = 1.0 / (1.0 + Math.pow(nAb, beta));
					final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
					final double probaWoVacc = 1 - Math.exp(-fact);
					final double ve = 1. - probaWVacc / probaWoVacc;

					values.append(ve);
					groupings.append("Delta; beta=" + beta);
				}
			}
		}

		{
			EpisimPerson personMin = EpisimTestUtils.createPerson();
			personMin.setImmuneResponseMultiplier(0.1);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevelsMin = simulateAntibodyLevels(immunityEvents, immunityEventDays, 600, personMin);



			for (int day : antibodyLevelsMin.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevelsMin.get(day);

				double nAb = strainToAntibodyMap.getOrDefault(VirusStrain.DELTA, 0.);

				{
					records.append(day);
					double immunityFactor = 1.0 / (1.0 + Math.pow(nAb, beta));
					final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
					final double probaWoVacc = 1 - Math.exp(-fact);
					final double ve = 1. - probaWVacc / probaWoVacc;

					values.append(ve);
					groupings.append("Delta (min); beta=" + beta);
				}
			}
		}

		{
			EpisimPerson personMax = EpisimTestUtils.createPerson();
			personMax.setImmuneResponseMultiplier(10);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevelsMax = simulateAntibodyLevels(immunityEvents, immunityEventDays, 600, personMax);

			for (int day : antibodyLevelsMax.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevelsMax.get(day);

				double nAb = strainToAntibodyMap.getOrDefault(VirusStrain.DELTA, 0.);

				{
					records.append(day);
					double immunityFactor = 1.0 / (1.0 + Math.pow(nAb, beta));
					final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
					final double probaWoVacc = 1 - Math.exp(-fact);
					final double ve = 1. - probaWVacc / probaWoVacc;

					values.append(ve);
					groupings.append("Delta (max); beta=" + beta);
				}
			}
		}

		{
			EpisimPerson personMed = EpisimTestUtils.createPerson();
			personMed.setImmuneResponseMultiplier(1);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevelsMax = simulateAntibodyLevels(immunityEvents, immunityEventDays, 600, personMed);

			for (int day : antibodyLevelsMax.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevelsMax.get(day);

				double nAb = strainToAntibodyMap.getOrDefault(VirusStrain.DELTA, 0.);

				{
					records.append(day);
					double immunityFactor = 1.0 / (1.0 + Math.pow(nAb, beta));
					final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
					final double probaWoVacc = 1 - Math.exp(-fact);
					final double ve = 1. - probaWVacc / probaWoVacc;

					values.append(ve);
					groupings.append("Delta (median); beta=" + beta);
				}
			}
		}

		// once more antibodies from DefaultAntibodyModel, converted to vaccine efficiency (for beta = 1 and beta = 3), this time plotting VE against omicron
		{
			for (int day : antibodyLevelsAvg.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevelsAvg.get(day);
				double nAb = strainToAntibodyMap.getOrDefault(VirusStrain.OMICRON_BA1, 0.);
				{
					records.append(day);
					double immunityFactor = 1.0 / (1.0 + Math.pow(nAb, beta));
					final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
					final double probaWoVacc = 1 - Math.exp(-fact);
					final double ve = 1. - probaWVacc / probaWoVacc;

					values.append(ve);
					groupings.append("Omicron; beta=" + beta);
				}
			}
		}


		// add ve progression from literature
		for (int ii = 0; ii < 600; ii++) {

			//eyreBNTDelta
			{
				records.append(ii);
				groupings.append(eyreBNTDelta);
				if (ii < 14) {
					values.appendMissing();
				} else if (ii < 28) {
					values.append(interpolate(ii, 14, 28, 1. - 0.2, 1. - 0.28));
				} else if (ii < 42) {
					values.append(interpolate(ii, 28, 42, 1. - 0.28, 1. - 0.33));
				} else if (ii < 8 * 7) {
					values.append(interpolate(ii, 42, 8 * 7, 1. - 0.33, 1. - 0.38));
				} else if (ii < 14 * 7) {
					values.append(interpolate(ii, 8 * 7, 14 * 7, 1. - 0.38, 1. - 0.47));
				} else {
					values.appendMissing();
				}
			}
			//eyreBNTAlpha
			{
				records.append(ii);
				groupings.append(eyreBNTAlpha);
				if (ii < 14) {
					values.appendMissing();
				} else if (ii < 28) {
					values.append(interpolate(ii, 14, 28, 1. - 0.15, 1. - 0.22));
				} else if (ii < 42) {
					values.append(interpolate(ii, 28, 42, 1. - 0.22, 1. - 0.26));
				} else if (ii < 8 * 7) {
					values.append(interpolate(ii, 42, 8 * 7, 1. - 0.26, 1. - 0.3));
				} else if (ii < 14 * 7) {
					values.append(interpolate(ii, 8 * 7, 14 * 7, 1. - 0.3, 1. - 0.36));
				} else {
					values.appendMissing();
				}
			}
			//nordström
			{
				records.append(ii);
				groupings.append(nordstrom);
				//				if (ii <= 30) {
				if (ii == 15) {
					values.append(0.92);
					//				} else if (ii <= 60) {
				} else if (ii == 45) {
					values.append(0.89);
					//				} else if (ii <= 120) {
				} else if (ii == 90) {
					values.append(0.85);
					//				} else if (ii <= 180) {
				} else if (ii == 90 + 45) {
					values.append(0.47);
					//				} else if (ii <= 210) {
				} else if (ii == 180 + 15) {
					values.append(0.29);
				} else if (ii == 210 + 30) {
					values.append(0.23);
				} else {
					values.appendMissing();
				}
			}

			//UKHSA, VE against Omicron
			{
				records.append(ii);
				groupings.append(ukhsa);
				if (ii <= 28) {
					values.append(0.63);
				} else if (ii <= 63) {
					values.append(0.50);
				} else if (ii <= 98) {
					values.append(0.30);
				} else if (ii <= 133) {
					values.append(0.19);
				} else if (ii <= 168) {
					values.append(0.15);
				} else {
					values.append(0.1);
				}
			}
			// UKHSA, VE against BA.1 after booster dose
			{
				records.append(ii);
				groupings.append(ba1booster);
				if (ii <= 168) {
					values.append(0.1);
				} else if (ii <= 168 + 28) {
					values.append(0.69);
				} else if (ii <= 168 + 63) {
					values.append(0.61);
				} else {
					values.append(0.49);
				}
			}
			// UKHSA, VE against BA.2 after booster dose
			{
				records.append(ii);
				groupings.append(ba2booster);
				if (ii <= 168) {
					values.append(0.1);
				} else if (ii <= 168 + 28) {
					values.append(0.74);
				} else if (ii <= 168 + 63) {
					values.append(0.67);
				} else {
					values.append(0.46);
				}
			}

			// UKHSA, VE against delta after booster dose
			{
				records.append(ii);
				groupings.append(deltabooster);
				if (ii <= 168) {
					values.append(0.61);
				} else if (ii <= 168 + 28) {
					values.append(0.95);
				} else if (ii <= 168 + 63) {
					values.append(0.92);
				} else {
					values.append(0.90);
				}
			}
		}

		final String title = "Vaccine Efficacy, DefaultAntibodyModel vs. NordstromEtAl.; beta=" + beta;
		Table table = Table.create(title);
		table.addColumns(records);
		table.addColumns(values);
		table.addColumns(groupings);

		TableSliceGroup tables = table.splitOn(table.categoricalColumn(grouping));

		Layout layout = Layout.builder(title, days, vaccineEfficacies).showLegend(true).build();

		ScatterTrace[] traces = new ScatterTrace[tables.size()];
		for (int i = 0; i < tables.size(); i++) {
			List<Table> tableList = tables.asTableList();
			final ScatterTrace.Mode mode;
			if (tableList.get(i).name().contains("beta=")) {
				traces[i] = ScatterTrace.builder(tableList.get(i).numberColumn(days), tableList.get(i).numberColumn(vaccineEfficacies))
						.showLegend(true)
						.name(tableList.get(i).name())
						.mode(ScatterTrace.Mode.LINE)
						.build();
			} else {
				traces[i] = ScatterTrace.builder(tableList.get(i).numberColumn(days), tableList.get(i).numberColumn(vaccineEfficacies))
						.showLegend(true)
						.name(tableList.get(i).name())
						.mode(ScatterTrace.Mode.MARKERS)
						.build();
			}
		}
		var figure = new Figure(layout, traces);
		figure.setLayout(Layout.builder().width(1400).height(800).build());

		try (Writer writer = new OutputStreamWriter(new FileOutputStream("nordstrom.html"), StandardCharsets.UTF_8)) {
			writer.write(Page.pageBuilder(figure, "target").build().asJavascript());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}


	}

	@Test
	public void testNordstroemEtAl_hosp(){


		final String days = "day";
		IntColumn records = IntColumn.create(days);

		final String vaccineEfficacies = "VE";
		DoubleColumn values = DoubleColumn.create(vaccineEfficacies);
		final String grouping = "grouping";
		var groupings = StringColumn.create(grouping);


		final String ukhsaDelta = "UKHSA (Delta)";
		final String ukhsaDeltaBoost = "UKHSA (Delta, booster)";

		final String ukhsaOmicron = "UKHSA (Omicron)";
		final String ukhsaOmicronBoost = "UKHSA (Omicron, booster)";


		var fact = 0.001;
		var beta = 1.2;

		// gather results from antibody model
		List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA, VaccinationType.mRNA);

		IntList immunityEventDays = IntList.of(1, 220);
		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodyLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 600, EpisimTestUtils.createPerson());


		// antibodies from DefaultAntibodyModel, converted to vaccine efficiency (for beta = 1 and beta = 3)
		{
			for (int day : antibodyLevels.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevels.get(day);

				double nAb = strainToAntibodyMap.getOrDefault(VirusStrain.DELTA, 0.);

				{
					records.append(day);
					double immunityFactor = 1.0 / (1.0 + Math.pow(0.5 * nAb, beta)); // this is the only difference
					final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
					final double probaWoVacc = 1 - Math.exp(-fact);
					final double ve = 1. - probaWVacc / probaWoVacc;

					values.append(ve);
					groupings.append("Delta; beta=" + beta);
				}
			}
		}

		// once more antibodies from DefaultAntibodyModel, converted to vaccine efficiency (for beta = 1 and beta = 3), this time plotting VE against omicron
		{
			for (int day : antibodyLevels.keySet()) {
				Object2DoubleMap<VirusStrain> strainToAntibodyMap = antibodyLevels.get(day);
				double nAb = strainToAntibodyMap.getOrDefault(VirusStrain.OMICRON_BA1, 0.);
				{
					records.append(day);
					double immunityFactor = 1.0 / (1.0 + Math.pow(0.5 * nAb, beta)); // here again
					final double probaWVacc = 1 - Math.exp(-fact * immunityFactor);
					final double probaWoVacc = 1 - Math.exp(-fact);
					final double ve = 1. - probaWVacc / probaWoVacc;

					values.append(ve);
					groupings.append("Omicron; beta=" + beta);
				}
			}
		}


		// add ve progression from literature
		for (int ii = 0; ii < 600; ii++) {


			//UKHSA, VE against Delta
			{
				records.append(ii);
				groupings.append(ukhsaDelta);
				if (ii <= 28) {
					values.append(0.95);
				} else if (ii <= 63) {
					values.append(0.99);
				} else if (ii <= 98) {
					values.append(0.98);
				} else if (ii <= 133) {
					values.append(0.98);
				} else if (ii <= 168) {
					values.append(0.96);
				} else {
					values.append(0.95);
				}
			}
			// UKHSA, VE against Delta after booster dose
			{
				records.append(ii);
				groupings.append(ukhsaDeltaBoost);
				if (ii <= 168) {
					values.append(0.99);
				} else if (ii <= 168 + 28) {
					values.append(1.);
				} else if (ii <= 168 + 63) {
					values.append(0.99);
				} else {
					values.append(0.99);
				}
			}
			//UKHSA, VE against Omicron
			{
				records.append(ii);
				groupings.append(ukhsaOmicron);
				if (ii <= 28) {
					values.append(0.72);
				} else if (ii <= 63) {
					values.append(0.71);
				} else if (ii <= 98) {
					values.append(0.53);
				} else if (ii <= 133) {
					values.append(0.6);
				} else if (ii <= 168) {
					values.append(0.58);
				} else {
					values.append(0.35);
				}
			}
			// UKHSA, VE against Omicron after booster dose
			{
				records.append(ii);
				groupings.append(ukhsaOmicronBoost);
				if (ii <= 168) {
					values.append(0.79);
				} else if (ii <= 168 + 28) {
					values.append(0.89);
				} else if (ii <= 168 + 63) {
					values.append(0.85);
				} else {
					values.append(0.76);
				}
			}
		}

		final String title = "Vaccine Efficacy, DefaultAntibodyModel vs. UKHSA; beta=" + beta;
		Table table = Table.create(title);
		table.addColumns(records);
		table.addColumns(values);
		table.addColumns(groupings);

		TableSliceGroup tables = table.splitOn(table.categoricalColumn(grouping));

		Layout layout = Layout.builder(title, days, vaccineEfficacies).showLegend(true).build();

		ScatterTrace[] traces = new ScatterTrace[tables.size()];
		for (int i = 0; i < tables.size(); i++) {
			List<Table> tableList = tables.asTableList();
			final ScatterTrace.Mode mode;
			if (tableList.get(i).name().contains("beta=")) {
				traces[i] = ScatterTrace.builder(tableList.get(i).numberColumn(days), tableList.get(i).numberColumn(vaccineEfficacies))
						.showLegend(true)
						.name(tableList.get(i).name())
						.mode(ScatterTrace.Mode.LINE)
						.build();
			} else {
				traces[i] = ScatterTrace.builder(tableList.get(i).numberColumn(days), tableList.get(i).numberColumn(vaccineEfficacies))
						.showLegend(true)
						.name(tableList.get(i).name())
						.mode(ScatterTrace.Mode.MARKERS)
						.build();
			}
		}
		var figure = new Figure(layout, traces);
		figure.setLayout(Layout.builder().width(1400).height(800).build());

		try (Writer writer = new OutputStreamWriter(new FileOutputStream("nordstrom.html"), StandardCharsets.UTF_8)) {
			writer.write(Page.pageBuilder(figure, "target").build().asJavascript());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}


	}



	@Test
	public void yuEtAl() {
		// https://doi.org/10.1101/2022.02.06.22270533

		{
			// I use the 658 against the wild variant as base:
			double nAbBase;
			{
				List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA);
				IntList immunityEventDays = IntList.of(0);
				nAbBase = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson()).get(100).get(VirusStrain.SARS_CoV_2);
			}

			// only vaccinated:
			List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA);
			IntList immunityEventDays = IntList.of(0);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson());

			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(658. / 658., nAb / nAbBase, 0.0);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(29. / 658., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(24. / 658., nAb / nAbBase, 0.1);
			}

			// yyyy more to be added here ...

			{
				// the following are to print out antibody levels, but they do not test anything as of now.


				VirusStrain strain = VirusStrain.SARS_CoV_2;
				log.warn("double vaccination against " + strain.name() + "=" + abLevels.get(100).get(strain));

				strain = VirusStrain.DELTA;
				log.warn("double vaccination against " + strain.name() + "=" + abLevels.get(100).get(strain));

				strain = VirusStrain.OMICRON_BA1;
				log.warn("double vaccination against " + strain.name() + "=" + abLevels.get(100).get(strain));

				immunityEvents = List.of(VaccinationType.mRNA, VaccinationType.mRNA);
				immunityEventDays = IntList.of(0, 200);
				abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 300, EpisimTestUtils.createPerson());

				strain = VirusStrain.DELTA;
				log.warn("triple vaccination against " + strain.name() + "=" + abLevels.get(300).get(strain));
				strain = VirusStrain.OMICRON_BA1;
				log.warn("triple vaccination against " + strain.name() + "=" + abLevels.get(300).get(strain));
			}


		}
	}


	@Test
	public void roesslerEtAl() {
		// http://dx.doi.org/10.1101/2022.02.01.22270263 Fig.1

		{
			// Fig.1: I use the top left (vaccinated; against wild variant) as base:
			double nAbBase;
			{
				List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA, VirusStrain.OMICRON_BA1);
				IntList immunityEventDays = IntList.of(0, 50);
				EpisimPerson person = EpisimTestUtils.createPerson();
				nAbBase = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, person).get(100).get(VirusStrain.SARS_CoV_2);
			}

			// Fig.1 A (vaccinated + omicron):
			List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA, VirusStrain.OMICRON_BA1);
			IntList immunityEventDays = IntList.of(0, 50);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson());

			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(4000. / 4000., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(4000. / 4000., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(3000. / 4000., nAb / nAbBase, 0.4);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(1000. / 4000., nAb / nAbBase, 0.4);
			}

			// Fig1 B (only omicron):
			immunityEvents = List.of(VirusStrain.OMICRON_BA1);
			immunityEventDays = IntList.of(50);
			abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson());

			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(0. / 4000., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(0. / 4000., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(10. / 4000., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(60. / 4000., nAb / nAbBase, 0.1);
			}

			// Fig1 C (vaccinated + delta + omicron):
			// (not plausible that these come out lower than without the delta infection in between)
			//			person = EpisimTestUtils.createPerson();
			//			person.setVaccinationStatus( EpisimPerson.VaccinationStatus.yes, VaccinationType.mRNA, 0 );
			//			person.possibleInfection(
			//					new EpisimInfectionEvent( 33 * 3600 * 24., person.getPersonId(), infector.getPersonId(), null, "dummy", 2, VirusStrain.DELTA, 1. ) );
			//			person.checkInfection();
			//			person.possibleInfection(
			//					new EpisimInfectionEvent( 66 * 3600 * 24., person.getPersonId(), infector.getPersonId(), null, "dummy", 2, VirusStrain.OMICRON_BA1, 1. ) );
			//			person.checkInfection();
			//			{
			//				VirusStrain strain = VirusStrain.SARS_CoV_2;
			//				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
			//							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
			//				Assert.assertEquals( 2000./4000., nAb/nAbBase, 0.5);
			//			}
			//			{
			//				VirusStrain strain = VirusStrain.ALPHA;
			//				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
			//							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
			//				Assert.assertEquals( 4000./4000., nAb/nAbBase, 0.1 );
			//			}
			//			{
			//				VirusStrain strain = VirusStrain.DELTA;
			//				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
			//							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
			//				Assert.assertEquals( 3000./4000., nAb/nAbBase, 0.1 );
			//			}
			//			{
			//				VirusStrain strain = VirusStrain.OMICRON_BA1;
			//				double nAb = InfectionModelWithAntibodies.getAntibodyLevel( person, 100, person.getNumVaccinations(), person.getNumInfections(), strain )
			//							     / InfectionModelWithAntibodies.getAk50( person, strain, ak50PerStrain, person.getNumInfections() );
			//				Assert.assertEquals( 300./4000., nAb/nAbBase, 0.1 );
			//			}

			// Fig1 D (delta + omicron):
			immunityEvents = List.of(VirusStrain.DELTA, VirusStrain.OMICRON_BA1);
			immunityEventDays = IntList.of(0, 50);
			abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson());
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(1000. / 4000., nAb / nAbBase, 0.5);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(1000. / 4000., nAb / nAbBase, 0.5);
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(2000. / 4000., nAb / nAbBase, 0.5);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(500. / 4000., nAb / nAbBase, 0.5);
			}


		}

	}

	@Test
	@Ignore
	public void roesslerEtAlOlderPaper() {
		// https://www.nejm.org/doi/full/10.1056/NEJMc2119236 Fig.1

		{
			// Fig.1: I use the top left (vaccinated with mRNA; against alpha) as base:
			double nAbBase;
			{
				List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA);
				IntList immunityEventDays = IntList.of(0);
				nAbBase = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson()).get(100).get(VirusStrain.ALPHA);
			}

			// Fig.1 A (vaccinated with mRNA):
			List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA);
			IntList immunityEventDays = IntList.of(0);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson());

			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(700. / 700., nAb / nAbBase, 0);
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(300. / 700., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(20. / 700., nAb / nAbBase, 0.5); // Treat these results with care: The authors state that the antibody level against omicron is so little that it's hard to measure it
			}

			// Fig.1 B (vaccinated with vector):
			immunityEvents = List.of(VaccinationType.vector);
			immunityEventDays = IntList.of(0);
			abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson());

			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(210. / 700., nAb / nAbBase, 0.10);
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(150. / 700., nAb / nAbBase, 0.10);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(4. / 700., nAb / nAbBase, 0.1); // Treat these results with care: The authors state that the antibody level against omicron is so little that it's hard to measure it
			}

			// Fig.1 E (infected with Alpha):
			immunityEvents = List.of(VirusStrain.ALPHA);
			immunityEventDays = IntList.of(0);
			abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson());

			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(4. * 300. / 700., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(4. * 64. / 700., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(6. / 700., nAb / nAbBase, 0.1); // Treat these results with care: The authors state that the antibody level against omicron is so little that it's hard to measure it
			}

			// Fig.1 G (infected with Delta):
			immunityEvents = List.of(VirusStrain.DELTA);
			immunityEventDays = IntList.of(0);
			abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson());

			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(4 * 210. / 700., nAb / nAbBase, 0.1);
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(4 * 450. / 700., nAb / nAbBase, 0.35);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals(8 * 4. / 700., nAb / nAbBase, 0.1); // Treat these results with care: The authors state that the antibody level against omicron is so little that it's hard to measure it
			}

			// Fig.1 H (infected + vaccinated):
			immunityEvents = List.of(VirusStrain.DELTA, VaccinationType.vector);
			immunityEventDays = IntList.of(0, 50);
			abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100, EpisimTestUtils.createPerson());

/*			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 4*1024./700., nAb/nAbBase, 0.1 );
			}*/
/*			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 256./550., nAb/nAbBase, 0.5 ); // Treat these results with care: The authors state that the antibody level against omicron is so little that it's hard to measure it
			}*/
		}

	}

/*	@Test // All the parts where this test failed were turned into comments
		public void yamasobaEtAl(){
		// https://www.biorxiv.org/content/10.1101/2022.02.14.480335v1.full.pdf  p. 42 b,c,e,f and p. 47 a,b

		{
			// Fig.2 b: vaccinated with mRNA is used as base:
			double nAbBase;
			{
				List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA);
				IntList immunityEventDays = IntList.of(0);
				nAbBase = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100).get(100).get(VirusStrain.SARS_CoV_2);
			}

			List<ImmunityEvent> immunityEvents = List.of(VaccinationType.mRNA);
			IntList immunityEventDays = IntList.of(0);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100);
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 1, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 3.3, nAbBase/nAb, 1);
			}
*//*			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 15, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 18, nAbBase/nAb, 1);
			}*//*

		}

		{
			// Fig.2 c: Vaccinated with vector vaccine is used as base:
			double nAbBase;
			{
				List<ImmunityEvent> immunityEvents = List.of(VaccinationType.vector);
				IntList immunityEventDays = IntList.of(0);
				nAbBase = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100).get(100).get(VirusStrain.SARS_CoV_2);
			}

			List<ImmunityEvent> immunityEvents = List.of(VaccinationType.vector);
			IntList immunityEventDays = IntList.of(0);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100);
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 1, nAbBase/nAb, 1);
			}
*//*			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 3.2, nAbBase/nAb, 1);
			}*//*
	 *//*			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 17, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 24, nAbBase/nAb, 1);
			}*//*

		}

		{
			// Fig.2 e: Infected during early pandemic (WT) is used as base:
			double nAbBase;
			{
				List<ImmunityEvent> immunityEvents = List.of(VirusStrain.SARS_CoV_2);
				IntList immunityEventDays = IntList.of(0);
				nAbBase = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100).get(100).get(VirusStrain.SARS_CoV_2);
			}

			List<ImmunityEvent> immunityEvents = List.of(VirusStrain.SARS_CoV_2);
			IntList immunityEventDays = IntList.of(0);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100);
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 1, nAbBase/nAb, 1);
			}
*//*			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 2.4, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 6.6, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 12, nAbBase/nAb, 1);
			}*//*

		}

		{
			// Fig.2 f: BA.1-infected is used as base:
			double nAbBase;
			{
				List<ImmunityEvent> immunityEvents = List.of(VirusStrain.OMICRON_BA1);
				IntList immunityEventDays = IntList.of(0);
				nAbBase = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100).get(100).get(VirusStrain.OMICRON_BA1);
			}

			List<ImmunityEvent> immunityEvents = List.of(VirusStrain.OMICRON_BA1);
			IntList immunityEventDays = IntList.of(0);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100);
*//*			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 0.38, nAbBase/nAb, 1);
			}*//*
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 0.56, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 1, nAbBase/nAb, 1);
			}
*//*			{
				VirusStrain strain = VirusStrain.OMICRON_BA2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 1.4, nAbBase/nAb, 1);
			}*//*

		}

		{
			// Ext Fig.2 a: Alpha-infected is used as base:
			double nAbBase;
			{
				List<ImmunityEvent> immunityEvents = List.of(VirusStrain.ALPHA);
				IntList immunityEventDays = IntList.of(0);
				nAbBase = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100).get(100).get(VirusStrain.SARS_CoV_2);
			}

			List<ImmunityEvent> immunityEvents = List.of(VirusStrain.ALPHA);
			IntList immunityEventDays = IntList.of(0);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100);
			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 1.7, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.ALPHA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 1, nAbBase/nAb, 1);
			}
*//*			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 2.6, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 18, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 6.5, nAbBase/nAb, 1);
			}*//*

		}

		{
			// Ext Fig.2 b: Delta-infected is used as base:
			double nAbBase;
			{
				List<ImmunityEvent> immunityEvents = List.of(VirusStrain.DELTA);
				IntList immunityEventDays = IntList.of(0);
				nAbBase = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100).get(100).get(VirusStrain.SARS_CoV_2);
			}

			List<ImmunityEvent> immunityEvents = List.of(VirusStrain.DELTA);
			IntList immunityEventDays = IntList.of(0);
			Int2ObjectMap<Object2DoubleMap<VirusStrain>> abLevels = simulateAntibodyLevels(immunityEvents, immunityEventDays, 100);
*//*			{
				VirusStrain strain = VirusStrain.SARS_CoV_2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 26, nAbBase/nAb, 1);
			}*//*
			{
				VirusStrain strain = VirusStrain.DELTA;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 1, nAbBase/nAb, 1);
			}
*//*			{
				VirusStrain strain = VirusStrain.OMICRON_BA1;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 46, nAbBase/nAb, 1);
			}
			{
				VirusStrain strain = VirusStrain.OMICRON_BA2;
				double nAb = abLevels.get(100).get(strain);
				Assert.assertEquals( 31, nAbBase/nAb, 1);
			}*//*

		}
	}*/


	private double interpolate(int ii, int startDay, int endDay, double startVal, double endVal) {
		return startVal + (endVal - startVal) / (endDay - startDay) * (ii - startDay);
	}

	/**
	 * @param immunityEvents    List of ImmunityEvent (either VaccinationType or VirusStrain) chronological order
	 * @param immunityEventDays List of days that the ImmunityEvent occurs
	 * @param maxDay            final day for which antibody levels are calculated
	 * @param person
	 * @return map where keys are days and values are a second map. This nested map contains virus strain as key and antibody level as value
	 */
	private Int2ObjectMap<Object2DoubleMap<VirusStrain>> simulateAntibodyLevels(List<ImmunityEvent> immunityEvents, IntList immunityEventDays, int maxDay, EpisimPerson person) {

		if (immunityEventDays.size() != immunityEvents.size()) {
			throw new RuntimeException("inputs must have same size");
		}

//		if (immunityEventDays.getInt(0) < 1) {
//			throw new RuntimeException("first immunity event cannot take place before day 1");
//		}

		Int2ObjectMap<Object2DoubleMap<VirusStrain>> antibodiesPerDayAndStrain = new Int2ObjectAVLTreeMap<>();

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

			System.out.println(immunityEvent + " on day " + day);


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

		// continue the plot after final immunization event until maxDay
		while (day < maxDay) {
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
