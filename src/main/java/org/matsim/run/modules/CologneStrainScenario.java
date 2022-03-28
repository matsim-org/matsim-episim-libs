package org.matsim.run.modules;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.vaccination.NoVaccination;

import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * Cologne scenario for calibration of different strains.
 */
public class CologneStrainScenario extends SnzCologneProductionScenario {


	public CologneStrainScenario() {
		super((Builder) new Builder()
				.setScaleForActivityLevels(1.3)
				.setSuscHouseholds_pct(0.0)
				.setLeisureCorrection(false)
				.setVaccinations(Vaccinations.no)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setVaccinationModel(NoVaccination.class)
				.setInfectionModel(InfectionModelWithAntibodies.class)
		);

	}
	
	@Provides
 	@Singleton
 	public Config config() {

 		Config config = super.config();
 		
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		Map<LocalDate, Integer> infPerDayALPHA = new HashMap<>();
		infPerDayALPHA.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayALPHA.put(LocalDate.parse("2021-01-15"), 4);
		infPerDayALPHA.put(LocalDate.parse("2021-01-22"), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.ALPHA, infPerDayALPHA);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(1.65);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setFactorSeriouslySick(1.0);

 		return config;
 	}

}
