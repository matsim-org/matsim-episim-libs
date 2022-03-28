package org.matsim.run.modules;

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.vaccination.NoVaccination;

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

}
