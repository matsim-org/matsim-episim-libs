package org.matsim.run.batch;

import com.google.inject.AbstractModule;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.RandomVaccination;
import org.matsim.episim.model.VaccinationByAge;
import org.matsim.episim.model.VaccinationModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;




/**
 * Vaccination runs for symmetric Berlin week model
 */
public class BerlinVaccinationsWinterEnd implements BatchRun<BerlinVaccinationsWinterEnd.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		if (params == null) {
			return new Builder().setDiseaseImport( DiseaseImport.yes ).setRestrictions( Restrictions.yes ).setMasks( Masks.yes ).setTracing( Tracing.yes ).setSnapshot(
					Snapshot.episim_snapshot_240_2020_10_12 ).setInfectionModel( AgeDependentInfectionModelWithSeasonality.class ).setVaccinationModel(VaccinationByAge.class).createSnzBerlinProductionScenario();
		}
		Class<? extends VaccinationModel> vaccinationModel = VaccinationByAge.class;
		if (params.vaccinationModel.equals("RandomVaccination")) vaccinationModel = RandomVaccination.class;
		return new Builder().setDiseaseImport( DiseaseImport.yes ).setRestrictions( Restrictions.yes ).setMasks( Masks.yes ).setTracing( Tracing.yes ).setSnapshot(
				Snapshot.episim_snapshot_240_2020_10_12 ).setInfectionModel( AgeDependentInfectionModelWithSeasonality.class ).setVaccinationModel(vaccinationModel).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "vaccinations&winterEnd");
	}

	@Override
	public Config prepareConfig(int id, Params params) {
		Class<? extends VaccinationModel> vaccinationModel = VaccinationByAge.class;
		if (params.vaccinationModel.equals("RandomVaccination")) vaccinationModel = RandomVaccination.class;
		SnzBerlinProductionScenario module = new Builder().setDiseaseImport( DiseaseImport.yes ).setRestrictions( Restrictions.yes ).setMasks( Masks.yes ).setTracing( Tracing.yes ).setSnapshot(
				Snapshot.episim_snapshot_240_2020_10_12 ).setInfectionModel( AgeDependentInfectionModelWithSeasonality.class ).setVaccinationModel(vaccinationModel).createSnzBerlinProductionScenario();

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			builder.clearAfter("2020-11-02", act);
			builder.restrict("2020-11-02", 0.59, act);
		}
		
		
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			builder.restrict("2021-01-04", params.outOfHomeFraction, act);
		}
		
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			builder.restrict("2020-12-24", 1.0, act);
			builder.restrict("2020-12-27", 0.59, act);
			builder.restrict("2020-12-31", 1.0, act);
			builder.restrict("2021-01-02", 0.59, act);
		}
		
		Map<LocalDate, DayOfWeek> christmasInputDays = new HashMap<>();
		
		christmasInputDays.put(LocalDate.parse("2020-12-21"), DayOfWeek.SATURDAY);
		christmasInputDays.put(LocalDate.parse("2020-12-22"), DayOfWeek.SATURDAY);
		christmasInputDays.put(LocalDate.parse("2020-12-23"), DayOfWeek.SATURDAY);
		christmasInputDays.put(LocalDate.parse("2020-12-24"), DayOfWeek.SUNDAY);
		christmasInputDays.put(LocalDate.parse("2020-12-25"), DayOfWeek.SUNDAY);
		christmasInputDays.put(LocalDate.parse("2020-12-26"), DayOfWeek.SUNDAY);
		
		christmasInputDays.put(LocalDate.parse("2020-12-28"), DayOfWeek.SATURDAY);
		christmasInputDays.put(LocalDate.parse("2020-12-29"), DayOfWeek.SATURDAY);
		christmasInputDays.put(LocalDate.parse("2020-12-30"), DayOfWeek.SATURDAY);
		christmasInputDays.put(LocalDate.parse("2020-12-31"), DayOfWeek.SUNDAY);
		christmasInputDays.put(LocalDate.parse("2021-01-01"), DayOfWeek.SUNDAY);
		
		episimConfig.setInputDays(christmasInputDays);	

		VaccinationConfigGroup vaccinationConfigGroup = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		
		vaccinationConfigGroup.setEffectiveness(0.9);
		vaccinationConfigGroup.setDaysBeforeFullEffect(28);
		
		if (!params.vaccinationsAfter.equals("never")) {
			vaccinationConfigGroup.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse(params.vaccinationsAfter), (int) (10000 * 4./3.),
					LocalDate.parse(params.vaccinationsAfter).plusDays((int) params.totalCapacity / 10000), 0
					));
		}
		
		episimConfig.setLeisureOutdoorFraction(Map.of(
			LocalDate.parse("2020-01-15"), 0.1,
			LocalDate.parse("2020-04-15"), 0.8,
			LocalDate.parse("2020-09-15"), 0.8,
			LocalDate.parse("2020-11-15"), 0.1,
			LocalDate.parse(params.winterEnd), 0.1,
			LocalDate.parse(params.winterEnd).plusMonths(2), 0.8,
			LocalDate.parse("2021-09-15"), 0.8)
	);
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(2)
		public long seed;
		
		@StringParameter({"VaccinationByAge", "RandomVaccination"})
		public String vaccinationModel;
		
		@Parameter({0.49, 0.59, 0.69})
		public double outOfHomeFraction;
		
//		@Parameter({0.9, 0.45})
//		public double effectiveness;
		
//		@IntParameter({21, 42})
//		public int daysBeforeFullEffect;
		
		@StringParameter({"never", "2021-01-04"})
		public String vaccinationsAfter;
		
//		@IntParameter({5000, 10000, 20000})
//		public int dailyCapacity;
		
		@IntParameter({400000, 800000, 10000000})
		public int totalCapacity;
		
		@StringParameter({"2021-02-15", "2021-03-15", "2021-04-15"})
		public String winterEnd;
	}


}
