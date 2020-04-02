/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.schools.openBerlinScenario;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.schools.CreateSchoolPopulation;
import playground.vsp.corineLandcover.CORINELandCoverCoordsModifier;
import playground.vsp.openberlinscenario.cemdap.output.CemdapOutput2MatsimPlansConverter;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CreateSchoolPopulationFromCorineLandCoverCoords {

	private static final double SAMPLE_SIZE = 0.1;

	public static void main(String[] args) {
	    String inputPlansFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/be_5/cemdap_input/500/plans_children.xml.gz";
		String inputPlansReadyForCorine = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/be_5/cemdap_input/500/plans_children_readyForCorine_10pct.xml.gz";
		String corineLandCoverFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/input/shapefiles/corine_landcover/corine_lancover_berlin-brandenburg_GK4.shp";

		String inputBerlinAdultPlans = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-10pct/input/berlin-v5.4-10pct.plans.xml.gz";
		String inputSchoolFacilities = "../shared-svn/projects/episim/matsim-files/snz/Berlin/educFacilities_optimated.txt";

		String zoneShapeFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/input/shapefiles/2016/gemeinden_Planungsraum_GK4.shp";
		String zoneIdTag = "NR";

		String outputPlansEntireBerlin = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/be_5/population/plans_500_includingChildren_corineCoords_10pct.xml.gz";
		String outputSchoolPlans = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/be_5/population/plans_500_onlyChildren_corineCoords_10pct.xml.gz";

	    assignDummyHomeActsAndWritePlans(inputPlansFile, inputPlansReadyForCorine);

	    boolean simplifyGeom = false;
	    boolean combiningGeoms = false;
	    boolean sameHomeActivity = true;
	    String homeActivityPrefix = "home";

	    Map<String, String> shapeFileToFeatureKey = new HashMap<>();
	    shapeFileToFeatureKey.put(zoneShapeFile, zoneIdTag);

		CORINELandCoverCoordsModifier plansFilterForCORINELandCover = new CORINELandCoverCoordsModifier(inputPlansReadyForCorine, shapeFileToFeatureKey,
	            corineLandCoverFile, simplifyGeom, combiningGeoms, sameHomeActivity, homeActivityPrefix);

		plansFilterForCORINELandCover.process();
		Population population = plansFilterForCORINELandCover.getPopulation();
		PopulationWriter writer = new PopulationWriter(population);
		preparePlansForSchooPopulationCreation(population);
		writer.write(outputSchoolPlans);

		//now run CreateSchoolPopulation which will read facilities, assign schools and build plans and finally will merge the adult population with the school population
		try {
			//we already sampled so sample size is set to 1 in this step
			CreateSchoolPopulation.run(population, 1, inputBerlinAdultPlans, inputSchoolFacilities, outputPlansEntireBerlin );

			//delete the file created in middle of the process - it is not needed any more
			new File(inputPlansReadyForCorine).delete();
		} catch (IOException e) {
			e.printStackTrace();
		}


	}


    private static void assignDummyHomeActsAndWritePlans(String inputPlans, String outputPlans){
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(inputPlans);

		PopulationUtils.sampleDown(scenario.getPopulation(), SAMPLE_SIZE);
		
		PopulationFactory pf = scenario.getPopulation().getFactory();
		scenario.getPopulation().getPersons().values().forEach(p -> createPlanAndDummyHomeAct(p, pf));

		new PopulationWriter(scenario.getPopulation()).write(outputPlans);
	}

	private static void createPlanAndDummyHomeAct(Person p, PopulationFactory pf){
		p.getPlans().clear();
		Plan plan = pf.createPlan();
		Activity act = pf.createActivityFromCoord("home_child", new Coord(-1, -1));
		act.getAttributes().putAttribute(CemdapOutput2MatsimPlansConverter.activityZoneId_attributeKey, p.getAttributes().getAttribute("municipalityId"));
		plan.addActivity(act);

		p.addPlan(plan);
	}

	private static void preparePlansForSchooPopulationCreation(Population population){
		for (Person person : population.getPersons().values()) {
			Activity act = (Activity) person.getSelectedPlan().getPlanElements().get(0);
			if(! act.getType().startsWith("home")) throw new IllegalStateException("first act type is not home for person " + person);
			if(act.getCoord() == null) throw new IllegalStateException("can not retrie coord info for home act of person " + person);
			person.getAttributes().putAttribute("homeX", act.getCoord().getX());
			person.getAttributes().putAttribute("homeY", act.getCoord().getY());
			person.setSelectedPlan(null);
			person.getPlans().clear();
		}
	}
}