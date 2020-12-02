package org.matsim.run.batch;

import com.google.inject.AbstractModule;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.*;

import java.time.LocalDate;
import java.util.Map;

import javax.annotation.Nullable;




/**
 * Vaccination runs for symmetric Berlin week model
 */
public class BerlinVaccinations implements BatchRun<BerlinVaccinations.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new Builder().setDiseaseImport( DiseaseImport.yes ).setRestrictions( Restrictions.yes ).setMasks( Masks.yes ).setTracing( Tracing.yes ).setSnapshot(
				Snapshot.no ).setInfectionModel( AgeDependentInfectionModelWithSeasonality.class ).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "vaccinations");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new Builder().setDiseaseImport( DiseaseImport.yes ).setRestrictions( Restrictions.yes ).setMasks(
				Masks.yes ).setTracing( Tracing.yes ).setSnapshot( Snapshot.no ).setInfectionModel(
				AgeDependentInfectionModelWithSeasonality.class ).createSnzBerlinProductionScenario();

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		if (params.lockdown.equals("outOfHomeExceptEdu59")) {
			for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
				if (act.contains("educ")) continue;
				builder.clearAfter("2020-11-02", act);
				builder.restrict("2020-11-02", 0.59, act);
			}
		}
		
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
			if (act.contains("educ")) continue;
			builder.restrict("2021-01-04", params.outOfHomeFraction, act);
		}

		VaccinationConfigGroup vaccinationConfigGroup = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		
		vaccinationConfigGroup.setEffectiveness(params.effectiveness);
		vaccinationConfigGroup.setDaysBeforeFullEffect(params.daysBeforeFullEffect);
		
		if (!params.vaccinationsAfter.equals("never")) {
			vaccinationConfigGroup.setVaccinationCapacity_pers_per_day(Map.of(
					episimConfig.getStartDate(), 0,
					LocalDate.parse(params.vaccinationsAfter), params.dailyCapacity
					));
		}

		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(1)
		public long seed;

		@StringParameter({"outOfHomeExceptEdu59"})
		public String lockdown;
		
		@Parameter({0.59, 0.7, 0.8, 0.9, 1.})
		public double outOfHomeFraction;
		
		@Parameter({0.9, 0.5})
		public double effectiveness;
		
		@IntParameter({14, 21, 28})
		public int daysBeforeFullEffect;
		
		@StringParameter({"never", "2021-01-04", "2021-02-01"})
		public String vaccinationsAfter;
		
		@IntParameter({100, 1000, 10000, 20000, 40000})
		public int dailyCapacity;
	}


}
