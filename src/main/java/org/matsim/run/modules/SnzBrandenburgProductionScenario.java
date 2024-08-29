package org.matsim.run.modules;

import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.*;
import org.matsim.episim.model.activity.ActivityParticipationModel;
import org.matsim.episim.model.activity.DefaultParticipationModel;
import org.matsim.episim.model.activity.LocationBasedParticipationModel;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.model.listener.HouseholdSusceptibility;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.model.testing.DefaultTestingModel;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.model.testing.TestingModel;
import org.matsim.episim.model.vaccination.VaccinationByAge;
import org.matsim.episim.model.vaccination.VaccinationFromData;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.ShutdownPolicy;
import org.matsim.run.batch.UtilsJR;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

public class SnzBrandenburgProductionScenario extends SnzProductionScenario {


	public static class Builder extends SnzProductionScenario.Builder<SnzBrandenburgProductionScenario> {

		private double householdSusc = 1.0;

		@Override
		public SnzBrandenburgProductionScenario build() {
			return new SnzBrandenburgProductionScenario(this);
		}

		public Builder setSuscHouseholds_pct(double householdSusc) {
			this.householdSusc = householdSusc;
			return this;
		}

	}

	private final int sample;

	private final LocationBasedRestrictions locationBasedRestrictions;

	private final double householdSusc;

	private final EpisimConfigGroup.ActivityHandling activityHandling;

	/**
	 * Path pointing to the input folder. Can be configured at runtime with EPISIM_INPUT variable.
	 */
	public static final Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/Brandenburg/episim-input");


	/**
	 * Empty constructor is needed for running scenario from command line.
	 */
	@SuppressWarnings("unused")
	private SnzBrandenburgProductionScenario() {
		this(new Builder());
	}

	protected  SnzBrandenburgProductionScenario(Builder builder){
		this.sample = builder.sample;
		this.locationBasedRestrictions = builder.locationBasedRestrictions;
		this.householdSusc = builder.householdSusc;
		this.activityHandling = builder.activityHandling;
	}

	private static String inputForSample(String base, int sample) {
		Path folder = (sample == 100 | sample == 25) ? INPUT : INPUT.resolve("samples");
		return folder.resolve(String.format(base, sample)).toString();
	}

	@Override
	protected void configure() {

		bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
		bind(DiseaseStatusTransitionModel.class).to(AgeDependentDiseaseStatusTransitionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(InfectionModelWithAntibodies.class).in(Singleton.class);
		bind(VaccinationModel.class).to(VaccinationByAge.class).in(Singleton.class);
		bind(TestingModel.class).to(DefaultTestingModel.class).in(Singleton.class);
		bind(ShutdownPolicy.class).to(FixedPolicy.class).in(Singleton.class);

		if (activityHandling == EpisimConfigGroup.ActivityHandling.startOfDay) {
			if (locationBasedRestrictions == LocationBasedRestrictions.yes) {
				bind(ActivityParticipationModel.class).to(LocationBasedParticipationModel.class);
			} else {
				bind(ActivityParticipationModel.class).to(DefaultParticipationModel.class);
			}
		}

		bind(HouseholdSusceptibility.Config.class).toInstance(
			HouseholdSusceptibility.newConfig().withSusceptibleHouseholds(householdSusc, 5.0)
		);

		// source for brandenburg: https://de.statista.com/statistik/daten/studie/1095791/umfrage/bevoelkerung-brandenburgs-nach-altersgruppen/
		bind(VaccinationFromData.Config.class).toInstance(
			VaccinationFromData.newConfig("12")// what is this location-id.
				.withAgeGroup("05-11", 172_271)
				.withAgeGroup("12-17", 145_764)
				.withAgeGroup("18-59", 1_261_204)
				.withAgeGroup("60+", 899_510)
		);



		//Antibody Model
		Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();
		Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();
		configureAntibodies(initialAntibodies, antibodyRefreshFactors);


		AntibodyModel.Config antibodyConfig = new AntibodyModel.Config(initialAntibodies, antibodyRefreshFactors);

		double immuneSigma = 3.0;
		antibodyConfig.setImmuneReponseSigma(immuneSigma);

		bind(AntibodyModel.Config.class).toInstance(antibodyConfig);


		// TODO: what does this do?
		Multibinder<SimulationListener> listener = Multibinder.newSetBinder(binder(), SimulationListener.class);
		listener.addBinding().to(HouseholdSusceptibility.class);

	}

	@Provides
	@Singleton
	public Config config() {

		// populations (in millions) of brandenburg vs, as taken from wikipedia
		double brandenburgFactor = 2.520 / 3.878;

//		if (this.sample != 25 && this.sample != 100)
//			throw new RuntimeException("Sample size not calibrated! Currently only 25% is calibrated. Comment this line out to continue.");

		//general config
		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		// set seed - same as for cologne and berlin.
		config.global().setRandomSeed(7564655870752979346L);

		config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		config.plans().setInputFile(inputForSample("br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

		config.controler().setOutputDirectory("output-snzWeekScenario-" + sample + "%");

		//episim config
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);


		// the cologne events files are appended with "withLeisure". Do we need this for Brandenburg as well?
		episimConfig.addInputEventsFile(inputForSample("br_2020-week_snz_episim_events_wt_%dpt_split.xml.gz", sample))
			.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		episimConfig.addInputEventsFile(inputForSample("br_2020-week_snz_episim_events_sa_%dpt_split.xml.gz", sample))
			.addDays(DayOfWeek.SATURDAY);

		episimConfig.addInputEventsFile(inputForSample("br_2020-week_snz_episim_events_so_%dpt_split.xml.gz", sample))
			.addDays(DayOfWeek.SUNDAY);

		episimConfig.setActivityHandling(activityHandling);

		episimConfig.setCalibrationParameter(1.0e-05 * 0.83 * 1.4); // TODO: this will have to be updated obviously
		episimConfig.setStartDate("2020-02-25");
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		episimConfig.setSampleSize(this.sample / 100.);
		episimConfig.setHospitalFactor(0.5);
		episimConfig.setThreads(8);
		episimConfig.setDaysInfectious(Integer.MAX_VALUE);

		episimConfig.setProgressionConfig(SnzProductionScenario.progressionConfig(Transition.config()).build());


		//----------------------------------------------------------------------------
		//		C O N T A C T     I N T E N S I T Y    /    S E A S O N A L I T Y
		//----------------------------------------------------------------------------

		// for cologne, this is modified quite a bit...
		SnzProductionScenario.configureContactIntensities(episimConfig);

		//SEASONALITY
		episimConfig.getOrAddContainerParams("work").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("educ_kiga").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("educ_primary").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("educ_secondary").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("educ_tertiary").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("educ_higher").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("educ_other").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("errands").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("business").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("visit").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("home").setSeasonality(0.5);
		episimConfig.getOrAddContainerParams("quarantine_home").setSeasonality(0.5);

		//---------------------------------------
		//		R E S T R I C T I O N S
		//---------------------------------------


		CreateRestrictionsFromCSV activityParticipation = new CreateRestrictionsFromCSV(episimConfig);

		activityParticipation.setInput(INPUT.resolve("BrandenburgSnzData_daily_until20221231.csv"));
		// in cologne, "activityParticipation.setNightlyScale()" etc. are modified
		FixedPolicy.ConfigBuilder builder;
		try {
			builder = activityParticipation.createPolicy();
		} catch (IOException e1) {
			throw new UncheckedIOException(e1);
		}

		// school vacations & lockdowns.
		// https://www.maz-online.de/brandenburg/corona-krise-in-brandenburg-eine-chronologie-SIFYHOEII3ZG6G5XW4HULSD4GI.html
		//
		// https://brandenburg.de/cms/detail.php/detail.php?gsid=bb1.c.663534.de
		//
		//	https://brandenburg.de/cms/detail.php/bb1.c.691163.de
		// "Das Kabinett beschließt die Schließung von Schulen und Kitas ab 18. März"
		builder.restrict(LocalDate.parse("2020-03-18"), 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_higher");
		// 17. April 2020: Das Kabinett beschließt erste Lockerungen:[...], eingesetzt erst am 27. Nur Abschlussklassen  Auch die Schulen starten nach einem Stufenplan wieder den Unterricht.
		builder.restrict(LocalDate.parse("2020-04-27"), 0.3, "educ_secondary", "educ_tertiary", "educ_other");
		// 6th grade goes back
		builder.restrict(LocalDate.parse("2020-05-04"), 0.3, "educ_primary");
		// 9th and 12th grade of secondary school, and 11th grade of gymnasium
		builder.restrict(LocalDate.parse("2020-05-04"), 0.4, "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-05-25"), 1.0, "educ_kiga", "educ_primary",  "educ_secondary", "educ_tertiary", "educ_other");
		//Sommerferien (Source = https://www.payback.de/ratgeber/besser-leben/reisen/ferien-2020#Brandenburg)
		builder.restrict(LocalDate.parse("2020-06-25"), 0.2,  "educ_kiga", "educ_primary","educ_secondary", "educ_tertiary", "educ_other");
		//10. August 2020: Die Schulen starten nach den Sommerferien wieder in den Regelbetrieb.
		builder.restrict(LocalDate.parse("2020-08-10"), 1.0,  "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		//Lueften nach den Sommerferien
		builder.restrict(LocalDate.parse("2020-08-10"), Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-12-31"), Restriction.ofCiCorrection(1.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//Herbstferien
		builder.restrict(LocalDate.parse("2020-10-12"), 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-10-24"), 1.0, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		//14. Dezember 2020: Die Präsenzpflicht in den Schulen wird eine Woche vor den Weihnachtsferien aufgehoben.
		builder.restrict(LocalDate.parse("2020-12-14"), 0.5, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		//Weihnachtsferien (which morph into school closure)
		builder.restrict(LocalDate.parse("2020-12-21"), 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		// Abschlussklassen und Förderschulen wieder unterichtet.
		builder.restrict(LocalDate.parse("2021-01-21"), 0.3, "educ_secondary", "educ_tertiary", "educ_other");
		//22. Februar 2021: Die Grundschulen öffnen wieder im Wechselunterricht zwischen Schule und zuhause.
		builder.restrict(LocalDate.parse("2021-02-22"), 0.5, "educ_primary");
		builder.restrict(LocalDate.parse("2021-03-15"), 1.0, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		//Osterferien
		builder.restrict(LocalDate.parse("2021-03-29"), 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-04-10"), 1.0, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		//Sommerferien
		builder.restrict(LocalDate.parse("2021-06-24"), 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-08-08"), 1.0, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other");


		// TODO: university

		//---------------------------------------
		//		M A S K S
		//---------------------------------------
		{
			// Part 1: pt, shopping, errands

			LocalDate masksCenterDate = LocalDate.of(2020, 4, 27);
			for (int ii = 0; ii <= 14; ii++) {
				LocalDate date = masksCenterDate.plusDays(-14 / 2 + ii);
				// assume 90% compliance, divided btwn 3 mask types.
				double clothFraction = 1. / 3. * 0.9;
				double ffpFraction = 1. / 3. * 0.9;
				double surgicalFraction = 1. / 3. * 0.9;

				builder.restrict(date, Restriction.ofMask(Map.of(
						FaceMask.CLOTH, clothFraction * ii / 14,
						FaceMask.N95, ffpFraction * ii / 14,
						FaceMask.SURGICAL, surgicalFraction * ii / 14)),
					"pt", "shop_daily", "shop_other", "errands");
			}


			// cloth mask no longer allowed
			//https://brandenburg.de/cms/detail.php/detail.php?gsid=bb1.c.664579.de
			//https://brandenburg.de/cms/detail.php/detail.php?gsid=bb1.c.693535.de
			builder.restrict(LocalDate.of(2021, 1, 23), Restriction.ofMask(Map.of(
					FaceMask.CLOTH, 0.,
					FaceMask.N95, 1. / 2. * 0.9,
					FaceMask.SURGICAL, 1. / 2. * 0.9)),
				"pt", "shop_daily", "shop_other", "errands");


			// Part 2: School
			// skipped the mask recommendations/mandates for spaces within school but not within classrooms
			//https://mbjs.brandenburg.de/aktuelles/pressemitteilungen.html?news=bb1.c.684838.de
			//
			//https://bravors.brandenburg.de/verordnungen/sars_cov_2_eindv_30_10_2020#17
			//
			//https://bravors.brandenburg.de/verordnungen/2__sars_cov_2_eindv_30_11_2020#17
			//
			//https://bravors.brandenburg.de/verordnungen/5__sars_cov_2_eindv_22_01_2021
			builder.restrict(LocalDate.parse("2020-11-02"), Restriction.ofMask(Map.of(
				FaceMask.CLOTH, 1./3. * 0.9,
				FaceMask.N95, 1. / 3. * 0.9,
				FaceMask.SURGICAL, 1. / 3. * 0.9)), "educ_tertiary");
			builder.restrict(LocalDate.parse("2020-12-01"), Restriction.ofMask(Map.of(
				FaceMask.CLOTH, 1./3. * 0.9,
				FaceMask.N95, 1. / 3. * 0.9,
				FaceMask.SURGICAL, 1. / 3. * 0.9)), "educ_secondary");
			builder.restrict(LocalDate.parse("2021-01-23"), Restriction.ofMask(Map.of(
					FaceMask.CLOTH, 0.,
					FaceMask.N95, 1. / 2. * 0.9,
					FaceMask.SURGICAL, 1. / 2. * 0.9)),
				"educ_primary", "educ_secondary", "educ_tertiary", "educ_other");
		}

		//curfew
		https://www.destatis.de/DE/Presse/Pressemitteilungen/2021/05/PD21_215_12.html#:~:text=Seit%20dem%20ersten%20Inkrafttreten%20n%C3%A4chtlicher,Ausgangssperren%20im%20Rahmen%20der%20Bundesnotbremse.
		builder.restrict("2021-04-24", Restriction.ofClosingHours(21, 5), "leisure","leisPublic","leisPrivate", "visit");
		Map<LocalDate, Double> curfewCompliance = new HashMap<LocalDate, Double>();
		curfewCompliance.put(LocalDate.parse("2021-04-24"), 1.0);
		curfewCompliance.put(LocalDate.parse("2021-05-02"), 0.0);
		episimConfig.setCurfewCompliance(curfewCompliance);

		// TODO: unrestrict closing hours after end of curfew.


		episimConfig.setPolicy(builder.build());

		//---------------------------------------
		//		V A C C I N A T I O N S
		//---------------------------------------

		VaccinationConfigGroup vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		int population = 2_760_044;
		SnzProductionScenario.configureVaccines(vaccinationConfig, population); // counted from br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz

		// Compliance and capacity will come from data
		vaccinationConfig.setCompliancePerAge(Map.of(0, 1.0));

		vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of());
		vaccinationConfig.setReVaccinationCapacity_pers_per_day(Map.of());

		vaccinationConfig.setFromFile(INPUT.resolve("vaccinationsBrandeburg.csv").toString());

		vaccinationConfig.setUseIgA(true);
		vaccinationConfig.setTimePeriodIgA(730);

//		Map<Integer, Double> vaccinationCompliance = new HashMap<>();
//		for (int i = 0; i < 5; i++) vaccinationCompliance.put(i, 0.0);
//		for (int i = 5; i <= 11; i++) vaccinationCompliance.put(i, 0.4);
//		for (int i = 12; i <= 120; i++) vaccinationCompliance.put(i, 1.0);
//		vaccinationConfig.setCompliancePerAge(vaccinationCompliance);

//		Map<LocalDate, Integer> vaccinations = new HashMap<>();
//		vaccinations.put(LocalDate.parse("2022-04-12"), (int) (0.0002 * population / 7));
//		vaccinations.put(LocalDate.parse("2022-06-30"), 0);
//
//		vaccinationConfig.setVaccinationCapacity_pers_per_day(vaccinations);
		vaccinationConfig.setDaysValid(270);
		vaccinationConfig.setValidDeadline(LocalDate.parse("2022-01-01"));

		vaccinationConfig.setBeta(1.2);


		// ??
//		builder.setHospitalScale(this.scale);


		//---------------------------------------
		//		S T R A I N S
		//---------------------------------------

		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);


		//alpha
		double aInf = 1.9 * 1.4;
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(aInf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(0.5);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySickVaccinated(0.5);


		//delta
		double deltaInf = 3.1 * 0.9;
		double deltaHos = 1.0;
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setInfectiousness(deltaInf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySick(deltaHos);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.DELTA).setFactorSeriouslySickVaccinated(deltaHos);


		//---------------------------------------
		//		I M P O R T
		//---------------------------------------

		// TODO: implement normal import + commuter import
		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		// Source of Import #1: People returning from vacation etc.
		episimConfig.setInitialInfectionDistrict("Potsdam");

		// initialize map to store initial infections per strain
		Map<LocalDate, Integer> infPerDayWild = new HashMap<>();
		Map<LocalDate, Integer> infPerDayAlpha = new HashMap<>();
		Map<LocalDate, Integer> infPerDayDelta = new HashMap<>();

		infPerDayAlpha.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayDelta.put(LocalDate.parse("2020-01-01"), 0);

//		Alpha: 18.01. - 24.01.2021
//		https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/DESH/Bericht_VOC_2021-03-17.pdf?__blob=publicationFile
		LocalDate startDateAlpha = LocalDate.parse("2021-01-18");
		for (int i = 0; i < 7; i++) {
			infPerDayAlpha.put(startDateAlpha.plusDays(i), 4);
		}
		infPerDayAlpha.put(startDateAlpha.plusDays(7), 1);


//		Delta: 26.04. - 02.05.2021 https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/DESH/Bericht_VOC_2021-05-26.pdf?__blob=publicationFile
		LocalDate startDateDelta = LocalDate.parse("2021-04-26");
		for (int i = 0; i < 7; i++) {
			infPerDayDelta.put(startDateDelta.plusDays(i), 4);
		}
		infPerDayDelta.put(startDateDelta.plusDays(7), 1);



		episimConfig.setInfections_pers_per_day(VirusStrain.SARS_CoV_2, infPerDayWild);
		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayAlpha);
		episimConfig.setInfections_pers_per_day(VirusStrain.DELTA, infPerDayDelta);





		// Source of Import #2: Commuters from Berlin. TODO: fill out.


		//---------------------------------------
		//		T E S T I N G
		//---------------------------------------

		// this section is split into four nested sections:
		// A) RAPID test
		// 	i) unvaccinated

		// load testing config group and set general parameters
		TestingConfigGroup testingConfigGroup = ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);

		testingConfigGroup.setTestAllPersonsAfter(LocalDate.parse("2021-10-01"));

		testingConfigGroup.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);

		List<String> actsList = new ArrayList<String>();
		actsList.add("leisure");
		actsList.add("leisPublic");
		actsList.add("leisPrivate");
		actsList.add("work");
		actsList.add("business");
		actsList.add("educ_kiga");
		actsList.add("educ_primary");
		actsList.add("educ_secondary");
		actsList.add("educ_tertiary");
		actsList.add("educ_other");
		actsList.add("educ_higher");
		testingConfigGroup.setActivities(actsList);

		// 1) Rapid test
		TestingConfigGroup.TestingParams rapidTest = testingConfigGroup.getOrAddParams(TestType.RAPID_TEST);
		rapidTest.setFalseNegativeRate(0.3);
		rapidTest.setFalsePositiveRate(0.03);

		//		1i) unvaccianted

		testingConfigGroup.setHouseholdCompliance(1.0);

		Map<LocalDate, Double> leisureTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> workTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> eduTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> kigaPrimaryTests = new HashMap<LocalDate, Double>();
		Map<LocalDate, Double> uniTests = new HashMap<LocalDate, Double>();

		leisureTests.put(LocalDate.parse("2020-01-01"), 0.);
		workTests.put(LocalDate.parse("2020-01-01"), 0.);
		eduTests.put(LocalDate.parse("2020-01-01"), 0.);
		kigaPrimaryTests.put(LocalDate.parse("2020-01-01"), 0.);
		uniTests.put(LocalDate.parse("2020-01-01"), 0.);

//		19.04.2021: Arbeitgeber ist verpflichtet, 1x pro Woche Test bereitzustellen; 2x Test pro Woche für Mitarbeiter*innen, die arbeitsbedingt in Sammelunterkünften untergebracht sind, Beschäftigten personennaher Dienstleistungen, Beschäftigte mit betriebsbedingtem häufig wechselndem Personenkontakt
		LocalDate testingStartDateWork = LocalDate.parse("2021-04-19");
		double testingRateWork = 0.2;



		//21.05.2021 : Wiedereröffnung Gastronomie → Nur Außenbereich geöffnet, Testplficht
		//01.06.2021: Sporthallen, Fitnessstudios, Tanzstudios, Tanzschulen und Bowlingcenter → Testpflicht
		LocalDate testingStartDateLeisure = LocalDate.parse("2021-06-01");
		Double testingRateLeisure = 0.5;

		for (int i = 1; i <= 31; i++) {
			leisureTests.put(testingStartDateWork.plusDays(i), testingRateWork * i / 31.);
			workTests.put(testingStartDateWork.plusDays(i), testingRateLeisure * i / 31.);
		}

		// schools give students and teachers option to test w/ rapid test
		eduTests.put(LocalDate.parse("2021-03-01"), 0.1);
		// 2 tests per week : https://mbjs.brandenburg.de/aktuelles/pressemitteilungen.html?news=bb1.c.713192.de
		eduTests.put(LocalDate.parse("2021-04-19"), 0.4);

		Map<String, Map<LocalDate, Double>> testingRatePerAct = new HashMap<>(Map.of(
			"leisure", leisureTests,
			"work", workTests,
			"business", workTests,
			"educ_kiga", kigaPrimaryTests,
			"educ_primary", kigaPrimaryTests,
			"educ_secondary", eduTests,
			"educ_tertiary", eduTests,
			"educ_higher", uniTests,
			"educ_other", eduTests
		));

		testingRatePerAct.put("leisPrivate", leisureTests);
		testingRatePerAct.put("leisPublic", leisureTests);

		rapidTest.setTestingRatePerActivityAndDate(testingRatePerAct);



		//---------------------------------------
		//		T R A C I N G
		//---------------------------------------

		SnzProductionScenario.configureTracing(config, brandenburgFactor);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setQuarantineVaccinated((Map.of(
			episimConfig.getStartDate(), false
		)));

		tracingConfig.setQuarantineDuration(Map.of(
			episimConfig.getStartDate(), 14,
			LocalDate.parse("2022-01-01"), 10
		));

		tracingConfig.setQuarantineStatus(Map.of(
			episimConfig.getStartDate(), EpisimPerson.QuarantineStatus.atHome
		));
		//---------------------------------------
		//		W E A T H E R
		//---------------------------------------

		// in berlin the maxOutdoor fraction is set at 1.0, in cologne 0.8. I stuck w/ Berlin
		SnzProductionScenario.configureWeather(episimConfig, WeatherModel.midpoints_185_250,
			INPUT.resolve("tempelhofWeatherUntil20220208.csv").toFile(),
			INPUT.resolve("temeplhofWeatherDataAvg2000-2020.csv").toFile(), 1.0)
		;



		return config;

	}

	private void configureAntibodies(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies,
									 Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors) {

		double mutEscDelta = 29.2 / 10.9;
		for (VaccinationType immunityType : VaccinationType.values()) {
			initialAntibodies.put(immunityType, new EnumMap<>( VirusStrain.class ) );
			for (VirusStrain virusStrain : VirusStrain.values()) {

				if (immunityType == VaccinationType.mRNA) {
					initialAntibodies.get(immunityType).put(virusStrain, 29.2); //10.0
				}
				else if (immunityType == VaccinationType.vector) {
					initialAntibodies.get(immunityType).put(virusStrain, 6.8);  //2.5
				}
				else {
					initialAntibodies.get(immunityType).put(virusStrain, 5.0);
				}
			}
		}

		for (VirusStrain immunityType : VirusStrain.values()) {
			initialAntibodies.put(immunityType, new EnumMap<>( VirusStrain.class ) );
			for (VirusStrain virusStrain : VirusStrain.values()) {
				initialAntibodies.get(immunityType).put(virusStrain, 5.0);
			}
		}

		//mRNAAlpha, mRNADelta, mRNABA1 comes from Sydney's calibration.
		//The other values come from Rössler et al.

		//Wildtype
		double mRNAAlpha = 29.2;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.SARS_CoV_2, mRNAAlpha);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);

		//Alpha
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.ALPHA, mRNAAlpha);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);

		//DELTA
		double mRNADelta = mRNAAlpha / mutEscDelta;
		initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
		initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150./300.);
		initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64./300.);
		initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64./300.);
		initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450./300.);

		for (VaccinationType immunityType : VaccinationType.values()) {
			antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
			for (VirusStrain virusStrain : VirusStrain.values()) {

				if (immunityType == VaccinationType.mRNA) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				}
				else if (immunityType == VaccinationType.vector) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
				}
				else if (immunityType == VaccinationType.ba1Update) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				}
				else if (immunityType == VaccinationType.ba5Update) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				}
				else {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, Double.NaN);
				}

			}
		}

		for (VirusStrain immunityType : VirusStrain.values()) {
			antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
			for (VirusStrain virusStrain : VirusStrain.values()) {
				antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
			}
		}


			UtilsJR.printInitialAntibodiesToConsole(initialAntibodies, true);

	}

}
