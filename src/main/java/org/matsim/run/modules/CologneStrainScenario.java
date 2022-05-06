package org.matsim.run.modules;

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.AgeAndProgressionDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.vaccination.NoVaccination;
import org.matsim.episim.model.vaccination.VaccinationFromData;
import org.matsim.scenarioCreation.RunTrial;

/**
 * Cologne scenario for calibration of different strains.
 */
public class CologneStrainScenario extends SnzCologneProductionScenario {

	public CologneStrainScenario(double leisureCorrection) {
		super((Builder) new Builder()
				.setScaleForActivityLevels(1.3)
				.setSuscHouseholds_pct(0.0)
				.setLeisureCorrection(RunTrial.parseParam("leisureCorrection", leisureCorrection))
				.setAlphaOffsetDays((int) RunTrial.parseParam("alphaOffsetDays", 0))
				.setVaccinations(Vaccinations.no)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setVaccinationModel(VaccinationFromData.class)
				.setInfectionModel(AgeAndProgressionDependentInfectionModelWithSeasonality.class)
		);
	}

	public CologneStrainScenario() {
		this(1.9);
	}

}
