/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.scenarioCreation.openBerlinScenario;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.*;
import java.util.stream.Collectors;


/**
 * DO NOT USE! THIS CLASS IS UNFINISHED WORK!
 *
 * @author tschlenther
 */
class AssignChildrenToParentHomeLocations {

	private static final Logger log = Logger.getLogger(AssignChildrenToParentHomeLocations.class);

	private static final String emptyChildrenPlansFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/be_5/cemdap_input/500/plans_children.xml.gz";
	//    private static final String openBerlinPopulationFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-10pct/input/berlin-v5.4-10pct.plans.xml.gz";
	private static final String openBerlinPopulationFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/input/berlin-v5.4-1pct.plans.xml.gz";
	private static final String outputChildrenPlansFile = "../../svn/shared-svn/studies/countries/de/open_berlin_scenario/be_5/population/plans_children_assignedToParents_1pct.xml.gz";


	public static void main(String[] args) {

//		Scenario childrenScenario = readPopulation(emptyChildrenPlansFile);
//		Population children = childrenScenario.getPopulation();
//		PopulationUtils.sampleDown(children, 0.01);
//		PopulationFactory childrenFactory = childrenScenario.getPopulation().getFactory();
//		Population parents = readPopulation(openBerlinPopulationFile).getPopulation();
//
//		/*
//		nach dem Statistischen Bundesamt (https://www.destatis.de/DE/Themen/Gesellschaft-Umwelt/Bevoelkerung/Haushalte-Familien/Publikationen/Downloads-Haushalte/haushalte-familien-2010300187004.html)
//		ist die Aufteilung der Haushalte mit Kindern unter 18 Jahren in den neuen Bundesländern einschl. Berlin wie folgt (Jahr 2018, siehe Tabelle 5_2_1 Zeile 35ff):
//
//
//		55,5% 1 Kind unter 18
//		34,5% 2 Kinder unter 18
//		7,7%  3 Kinder unter 18
//		1,7%  4 Kinder unter 18
//		0,6% 5 oder mehr Kinder unter 18
//
// 		*/
//		double oneChildRatio = 0.555;
//		double twoChildrenRatio = 0.345;
//
//		//ratio of three or more children is calculated later..
//
//		Map<String, List<Person>> zone2Parents = mapParentsToHomeZone(parents);
//		log.info("nr of zones in parents map = " + zone2Parents.size());
//		Map<String, List<Person>> zone2Children = mapChildrenToHomeZone(children);
//		log.info("nr of zones in children map = " + zone2Children.size());
//
//		assign(childrenFactory, oneChildRatio, twoChildrenRatio, zone2Parents, zone2Children);
//
//		log.info("finished assigning...");
//
//		log.info("write output");
//
//		new PopulationWriter(children).write(outputChildrenPlansFile);

	}

	private static void assign(PopulationFactory childrenFactory, double oneChildRatio, double twoChildrenRatio, Map<String, List<Person>> zone2Parents, Map<String, List<Person>> zone2Children) {
		double threeOrMoreChildrenRatio = 1 - oneChildRatio - twoChildrenRatio;
		if (threeOrMoreChildrenRatio < 0 || threeOrMoreChildrenRatio > 1) throw new IllegalArgumentException();

		Random rnd = MatsimRandom.getLocalInstance();

		for (String zone : zone2Children.keySet()) {
			List<Person> childrenInZone = zone2Children.get(zone);
			List<Person> lonelyParentsInZone = zone2Parents.get(zone);
			int nrOfChildrenInZone = childrenInZone.size();
			log.info("start assigning " + nrOfChildrenInZone + " children in zone " + zone + ". There are " + lonelyParentsInZone.size() + " parents in this zone.");


			long nrParentsWithOneChild = Math.round(nrOfChildrenInZone / (1 + (2 * twoChildrenRatio + 3 * threeOrMoreChildrenRatio * oneChildRatio) / oneChildRatio));
			long nrParentsWithTwoChildren = Math.round(nrOfChildrenInZone * twoChildrenRatio);

			Map<Person, List<Person>> parents2Children = new HashMap<>();
			for (int ii = 0; ii < nrParentsWithOneChild; ii++) {
				Person fatherMother = lonelyParentsInZone.remove(rnd.nextInt(lonelyParentsInZone.size()));
				List<Person> list = new ArrayList<>();
				list.add(childrenInZone.remove(0));
				parents2Children.put(fatherMother, list);
			}

			List<Person> parentsWith1Child = new ArrayList<>(parents2Children.keySet());
			List<Person> parentsWith2Children = new ArrayList<>();


			for (double ii = 0; ii < nrParentsWithTwoChildren; ii++) {
				Person fatherMother = parentsWith1Child.remove(rnd.nextInt(parentsWith1Child.size()));
				parents2Children.get(fatherMother).add(childrenInZone.remove(0));
				parentsWith2Children.add(fatherMother);
			}

			for (double ii = 0; ii < childrenInZone.size(); ii++) {
				Person fatherMother = parentsWith2Children.remove(rnd.nextInt(parentsWith2Children.size()));
				parents2Children.get(fatherMother).add(childrenInZone.remove(0));
			}

			if (!childrenInZone.isEmpty()) throw new IllegalStateException();

			log.info("nr of parents with 1 child : " + parentsWith1Child.size() + " = " + ((double) parentsWith1Child.size() / nrOfChildrenInZone * 100) + " %");
			log.info("nr of parents with 2 children : " + parentsWith2Children.size() + " = " + ((double) parentsWith2Children.size() / nrOfChildrenInZone * 100) + " %");

			parents2Children.forEach((parent, listOfChildren) -> {
				Activity act = (Activity) parent.getSelectedPlan().getPlanElements().get(0);
				if (!act.getType().startsWith("home")) {
					throw new IllegalStateException("first act of parent " + parent + " is not home!");
				}
				listOfChildren.forEach(child -> {
					child.getAttributes().putAttribute("homeX", act.getCoord().getX());
					child.getAttributes().putAttribute("homeY", act.getCoord().getX());
//                    addPlanWith1Activity(child, act, childrenFactory);
					child.getAttributes().putAttribute("parent", parent.getId());
				});
			});
		}
	}

	private static void addPlanWith1Activity(Person child, Activity act, PopulationFactory populationFactory) {
		Plan plan = populationFactory.createPlan();
		plan.addActivity(act);
		child.addPlan(plan);
	}


	private static Map<String, List<Person>> mapParentsToHomeZone(Population parentsPopulation) {
		Map<String, List<Person>> zoneIdToPersons = new HashMap<>();

		log.info("nrOf persons in parents population before subpopulation filter = " + parentsPopulation.getPersons().size());
		List<Person> nonPersons = parentsPopulation.getPersons().values().stream()
				.filter(p -> p.getAttributes().getAttribute("subpopulation") != null)
				.filter(p -> !p.getAttributes().getAttribute("subpopulation").equals("person"))
				.collect(Collectors.toList());

		nonPersons.forEach(p -> parentsPopulation.removePerson(p.getId()));
		log.info("nrOf persons in parents population after subpopulation filter = " + parentsPopulation.getPersons().size());

		for (Person person : parentsPopulation.getPersons().values()) {

			Plan p = person.getSelectedPlan();

			if (p == null) {
				if (person.getPlans().isEmpty()) {
					log.warn("person " + person + " has no plan at all!");
					continue;
				}
				p = person.getPlans().get(0); //just take the first plan
			}


			Activity homeAct = (Activity) person.getSelectedPlan().getPlanElements().get(0);
			if (!homeAct.getType().startsWith("home")) {
				throw new IllegalArgumentException("person " + person + " starts its plan with an activity other than home");
			}
			String zoneIdStr = homeAct.getAttributes().getAttribute("zoneId").toString();
			if (zoneIdToPersons.containsKey(zoneIdStr)) {
				zoneIdToPersons.get(zoneIdStr).add(person);
			} else {
				List<Person> list = new ArrayList<>();
				list.add(person);
				zoneIdToPersons.put(zoneIdStr, list);
			}
		}
		return zoneIdToPersons;
	}

	private static Map<String, List<Person>> mapChildrenToHomeZone(Population childrenPopulation) {
		Map<String, List<Person>> zoneIdToPersons = new HashMap<>();

		for (Person person : childrenPopulation.getPersons().values()) {
			String zoneIdStr = person.getAttributes().getAttribute("municipalityId").toString();
			if (zoneIdToPersons.containsKey(zoneIdStr)) {
				zoneIdToPersons.get(zoneIdStr).add(person);
			} else {
				List<Person> list = new ArrayList<>();
				list.add(person);
				zoneIdToPersons.put(zoneIdStr, list);
			}
		}
		return zoneIdToPersons;
	}


	private static Scenario readPopulation(String file) {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(file);
		return scenario;
	}

}
