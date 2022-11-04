package org.matsim.run.modules;

import com.google.inject.multibindings.Multibinder;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.AgeAndProgressionDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.InitRecoveredPersons;
import org.matsim.episim.model.SimulationListener;
import org.matsim.episim.model.vaccination.VaccinationFromData;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.scenarioCreation.RunTrial;

/**
 * Cologne scenario for calibration of different strains.
 */
public class CologneStrainScenario extends SnzCologneProductionScenario {

	private final double scaleRecovered;

	public CologneStrainScenario(double leisureCorrection, int alphaOffsetDays, Vaccinations vaccinations, Class<? extends VaccinationModel> vacModel, boolean testing, double scaleRecovered) {
		super((Builder) new Builder()
				.setTesting(testing)
				.setScaleForActivityLevels(1.3)
				.setSuscHouseholds_pct(0.0)
				.setLeisureCorrection(RunTrial.parseParam("leisureCorrection", leisureCorrection))
				.setAlphaOffsetDays((int) RunTrial.parseParam("alphaOffsetDays", alphaOffsetDays))
				.setVaccinations(vaccinations)
				.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
				.setVaccinationModel(vacModel)
				.setInfectionModel(AgeAndProgressionDependentInfectionModelWithSeasonality.class)
		);
		this.scaleRecovered = RunTrial.parseParam("scaleRecovered", scaleRecovered);
	}

	public CologneStrainScenario() {
		this(1.95, 0, Vaccinations.yes, VaccinationFromData.class, true, 1);
	}

	@Override
	protected void configure() {
		super.configure();

		if (scaleRecovered > 1) {
			Multibinder.newSetBinder(binder(), SimulationListener.class).addBinding()
					.toInstance(new InitRecoveredPersons(scaleRecovered));
		}

	}
}
