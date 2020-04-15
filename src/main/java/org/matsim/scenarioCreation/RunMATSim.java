package org.matsim.scenarioCreation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;

/**
 * @author smueller
 */
public class RunMATSim {

	private static final String INPUT_SCHOOL_PLANS =  "../shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_u14population_schoolPlans.xml.gz";


	public static void main(String[] args) {
		String plansfile = INPUT_SCHOOL_PLANS;

		if(args.length > 0){
			plansfile = args[1];
		}

		Config config = ConfigUtils.createConfig();

		prepareConfig(config, plansfile);

		runMATSim(config);
	}

	private static void prepareConfig(Config config, String plansFile) {
		config.controler().setLastIteration(0);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.failIfDirectoryExists);
		config.controler().setOutputDirectory( "./outputU14");

		config.qsim().setStartTime(0);
		config.qsim().setEndTime(36. * 3600);

		config.network().setInputFile("../shared-svn/projects/avoev/matsim-input-files/berlin/v0/optimizedNetwork.xml.gz");

		config.transit().setUseTransit(true);
		config.transit().setTransitScheduleFile("../shared-svn/projects/avoev/matsim-input-files/berlin/v0/optimizedSchedule.xml.gz");
		config.transit().setVehiclesFile("../shared-svn/projects/avoev/matsim-input-files/berlin/v0/optimizedVehicles.xml.gz");
		config.transitRouter().setMaxBeelineWalkConnectionDistance(300);

		config.plans().setInputFile(plansFile);
		config.planCalcScore().addActivityParams(new ActivityParams("home").setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(new ActivityParams("educ_kiga").setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(new ActivityParams("educ_primary").setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(new ActivityParams("educ_secondary").setScoringThisActivityAtAll(false));
	}

	private static void runMATSim(Config config) {

		Scenario scenario = ScenarioUtils.loadScenario(config);

		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new SwissRailRaptorModule());

		controler.run();
	}

}
