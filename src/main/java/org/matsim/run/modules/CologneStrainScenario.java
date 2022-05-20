package org.matsim.run.modules;

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.AgeAndProgressionDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.model.vaccination.NoVaccination;
import org.matsim.episim.model.vaccination.VaccinationFromData;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.scenarioCreation.RunTrial;

/**
 * Cologne scenario for calibration of different strains.
 */
public class CologneStrainScenario extends SnzCologneProductionScenario {

	public CologneStrainScenario(double leisureCorrection, Vaccinations vaccinations, Class<? extends VaccinationModel> vacModel, boolean testing ) {
		super((Builder) new Builder()
				.setTesting(testing)
				.setScaleForActivityLevels(1.3)
				.setSuscHouseholds_pct(0.0)
				.setLeisureCorrection(RunTrial.parseParam("leisureCorrection", leisureCorrection))
				.setAlphaOffsetDays((int) RunTrial.parseParam("alphaOffsetDays", 0))
				.setVaccinations(vaccinations)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setVaccinationModel(vacModel)
				.setInfectionModel(AgeAndProgressionDependentInfectionModelWithSeasonality.class)
		);
	}

	public CologneStrainScenario() {
		this(1.95, Vaccinations.yes, VaccinationFromData.class, true);
	}

}
