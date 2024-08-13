package org.matsim.run.modules;

import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.model.*;
import org.matsim.episim.model.activity.ActivityParticipationModel;
import org.matsim.episim.model.activity.DefaultParticipationModel;
import org.matsim.episim.model.activity.LocationBasedParticipationModel;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.model.listener.HouseholdSusceptibility;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.model.testing.DefaultTestingModel;
import org.matsim.episim.model.testing.TestingModel;
import org.matsim.episim.model.vaccination.VaccinationByAge;
import org.matsim.episim.model.vaccination.VaccinationFromData;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.ShutdownPolicy;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

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
	public static final Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input");


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

	@Override
	protected void configure() {

		bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
		bind(DiseaseStatusTransitionModel.class).to(AgeDependentDiseaseStatusTransitionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(AgeAndProgressionDependentInfectionModelWithSeasonality.class).in(Singleton.class);
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

		// TODO: THIS NEEDS TO BE REPLACED. - Our currently study time-period doesn't include vaccinations
		bind(VaccinationFromData.Config.class).toInstance(
			VaccinationFromData.newConfig("05315")// what is this location-id.
				.withAgeGroup("05-11", Double.NaN)
				.withAgeGroup("12-17", Double.NaN)
				.withAgeGroup("18-59", Double.NaN)
				.withAgeGroup("60+", Double.NaN)
		);


		// antibody model
		AntibodyModel.Config antibodyConfig = new AntibodyModel.Config();
		antibodyConfig.setImmuneReponseSigma(3.0);
		bind(AntibodyModel.Config.class).toInstance(antibodyConfig);


		// TODO: what does this do?
		Multibinder<SimulationListener> listener = Multibinder.newSetBinder(binder(), SimulationListener.class);
		listener.addBinding().to(HouseholdSusceptibility.class);

	}

	@Provides
	@Singleton
	public Config config() {

		// populations (in millions) of brandenburg vs. berlin, as taken from wikipedia
		double brandenburgFactor = 2.520 / 3.878;

//		if (this.sample != 25 && this.sample != 100)
//			throw new RuntimeException("Sample size not calibrated! Currently only 25% is calibrated. Comment this line out to continue.");

		//general config
		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		// set seed - same as for cologne and berlin.
		config.global().setRandomSeed(7564655870752979346L);

		config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		config.plans().setInputFile(inputForSample("br_snz_entirePopulation_emptyPlans_withDistricts_%dpt_split.xml.gz", sample));

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

		//---------------------------------------
		//		I M P O R T
		//---------------------------------------

		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		// Source of Import #1: People returning from vacation etc.
		episimConfig.setInitialInfectionDistrict("Potsdam");

		// Source of Import #2: Commuters from Berlin.

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
		// "Das Kabinett beschließt die Schließung von Schulen und Kitas ab 18. März"
		builder.restrict(LocalDate.parse("2020-03-18"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		// 17. April 2020: Das Kabinett beschließt erste Lockerungen:[...] Auch die Schulen starten nach einem Stufenplan wieder den Unterricht.
		builder.restrict(LocalDate.parse("2020-04-17"), 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//Sommerferien (Source = https://www.payback.de/ratgeber/besser-leben/reisen/ferien-2020#Brandenburg)
		builder.restrict(LocalDate.parse("2020-06-25"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//10. August 2020: Die Schulen starten nach den Sommerferien wieder in den Regelbetrieb.
		builder.restrict(LocalDate.parse("2020-08-08"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//Lueften nach den Sommerferien
		builder.restrict(LocalDate.parse("2020-08-11"), Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-12-31"), Restriction.ofCiCorrection(1.0), "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//Herbstferien
		builder.restrict(LocalDate.parse("2020-10-12"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2020-10-24"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//14. Dezember 2020: Die Präsenzpflicht in den Schulen wird eine Woche vor den Weihnachtsferien aufgehoben.
		builder.restrict(LocalDate.parse("2020-12-21"), 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//Weihnachtsferien (which morph into school closure)
		builder.restrict(LocalDate.parse("2020-12-21"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//22. Februar 2021: Die Grundschulen öffnen wieder im Wechselunterricht zwischen Schule und zuhause.
		builder.restrict(LocalDate.parse("2021-02-22"), 0.5, "educ_primary");

		-------- CONTINUE WORKING HERE ------

		builder.restrict(LocalDate.parse("2021-03-15"), 0.5, "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//Osterferien
		builder.restrict(LocalDate.parse("2021-03-29"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-04-10"), 0.5, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		//Sommerferien
		builder.restrict(LocalDate.parse("2021-07-05"), 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");
		builder.restrict(LocalDate.parse("2021-08-17"), 1.0, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other");


		return config;

	}



}
