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
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import playground.vsp.corineLandcover.CORINELandCoverCoordsModifier;
import playground.vsp.openberlinscenario.cemdap.output.CemdapOutput2MatsimPlansConverter;

import java.util.*;

public class RunCorineLandCoverCoordsModifier {

	private static final double SAMPLE_SIZE = 0.1;

	public static void main(String[] args) {
	    String inputPlansFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/be_5/cemdap_input/500/plans_children.xml.gz";
		String inputPlansreadyForCorine = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/be_5/cemdap_input/500/plans_children_readyForCorine_10pct.xml.gz";
		String outputPlansFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/be_5/population/500/plans_children_CorineHomeActs.xml.gz";
		String corineLandCoverFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/input/shapefiles/corine_landcover/corine_lancover_berlin-brandenburg_GK4.shp";

	    String zoneShapeFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/input/shapefiles/2016/gemeinden_Planungsraum_GK4.shp";
	    String zoneIdTag = "NR";

	    assignDummyHomeActsAndWritePlans(inputPlansFile, inputPlansreadyForCorine);

	    boolean simplifyGeom = false;
	    boolean combiningGeoms = false;
	    boolean sameHomeActivity = true;
	    String homeActivityPrefix = "home";

	    Map<String, String> shapeFileToFeatureKey = new HashMap<>();
	    shapeFileToFeatureKey.put(zoneShapeFile, zoneIdTag);

		CORINELandCoverCoordsModifier plansFilterForCORINELandCover = new CORINELandCoverCoordsModifier(inputPlansreadyForCorine, shapeFileToFeatureKey,
	            corineLandCoverFile, simplifyGeom, combiningGeoms, sameHomeActivity, homeActivityPrefix);
	    plansFilterForCORINELandCover.process();
	    plansFilterForCORINELandCover.writePlans(outputPlansFile);
    }


    private static void assignDummyHomeActsAndWritePlans(String inputPlans, String outputPlans){
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(inputPlans);
		
		samplePopulation(scenario.getPopulation());
		
		PopulationFactory pf = scenario.getPopulation().getFactory();
		scenario.getPopulation().getPersons().values().forEach(p -> createPlanAndDummyHomeAct(p, pf));

		new PopulationWriter(scenario.getPopulation()).write(outputPlans);
	}

	private static void samplePopulation(Population population) {
		Random rnd = MatsimRandom.getLocalInstance();
		List<Id<Person>> personsToDelete = new ArrayList<>();
		for (Id<Person> person : population.getPersons().keySet()) {
				if(rnd.nextDouble() > SAMPLE_SIZE) personsToDelete.add(person);
		}
		personsToDelete.forEach(p -> population.removePerson(p));
	}

	private static void createPlanAndDummyHomeAct(Person p, PopulationFactory pf){
		p.getPlans().clear();
		Plan plan = pf.createPlan();
		Activity act = pf.createActivityFromCoord("home_child", new Coord(-1, -1));
		act.getAttributes().putAttribute(CemdapOutput2MatsimPlansConverter.activityZoneId_attributeKey, p.getAttributes().getAttribute("municipalityId"));
		plan.addActivity(act);

		p.addPlan(plan);
	}
}