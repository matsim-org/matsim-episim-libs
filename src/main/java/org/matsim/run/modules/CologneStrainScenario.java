package org.matsim.run.modules;

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.vaccination.NoVaccination;
import org.matsim.scenarioCreation.RunTrial;

/**
 * Cologne scenario for calibration of different strains.
 */
public class CologneStrainScenario extends SnzCologneProductionScenario {


	public CologneStrainScenario() {
		super((Builder) new Builder()
				.setScaleForActivityLevels(1.3)
				.setSuscHouseholds_pct(0.0)
				.setLeisureCorrection(RunTrial.parseParam("leisureCorrection", 1.9))
				.setVaccinations(Vaccinations.no)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setVaccinationModel(NoVaccination.class)
				.setInfectionModel(InfectionModelWithAntibodies.class)
		);

	}

}
