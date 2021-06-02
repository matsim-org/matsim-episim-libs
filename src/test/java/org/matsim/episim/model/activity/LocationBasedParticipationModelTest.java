package org.matsim.episim.model.activity;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.data.Percentage;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.policy.Restriction;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacility;


import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class LocationBasedParticipationModelTest {

	private Config config;
	private SplittableRandom rnd;
	private EpisimConfigGroup episimConfig;

	private final double POPULATION_SIZE = 10000.;
	private final Percentage PERCENTAGE_OFFSET = Percentage.withPercentage(5.);


	@Before
	public void setup() {

		rnd = new SplittableRandom(1);

		config = EpisimTestUtils.createTestConfig();
		episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setDistrictLevelRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.yes);
		episimConfig.setDistrictLevelRestrictionsAttribute("subdistrict");
		episimConfig.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay);
	}

	/**
	 * tests location based functionality in ParticipationModel (i.e. when ActivityHandling==startOfDay).
	 * The restrictions have a global remaining fraction (0.75) and two local remaining fractions for bronx and
	 * queens (0.25 and 0.5).
	 *
	 * A large population with a single activity in bronx was created, and updateParticipation was called for each agent.
	 * The percentage of agents that kept their activity should be similar to the localRf for the bronx. Same goes for
	 * queens. The final population had an activityFacility in StatenIsland, for which there is no localRf. Thus, the
	 * globalRf was applied.
	 */
	@Test
	public void testLocationBasedRestriction() {

		// Add facilities to scenario
		Scenario scenario = ScenarioUtils.loadScenario(config);
		ActivityFacilitiesFactory factory = scenario.getActivityFacilities().getFactory();

		ActivityFacility bronxFacility = factory.createActivityFacility(Id.create("BronxFacility", ActivityFacility.class), new Coord(0, 0));
		bronxFacility.getAttributes().putAttribute("subdistrict", "Bronx");

		ActivityFacility queensFacility = factory.createActivityFacility(Id.create("QueensFacility", ActivityFacility.class), new Coord(0, 0));
		queensFacility.getAttributes().putAttribute("subdistrict", "Queens");

		ActivityFacility statenIslandFacility = factory.createActivityFacility(Id.create("StatenIslandFacility", ActivityFacility.class), new Coord(100, 100));
		statenIslandFacility.getAttributes().putAttribute("subdistrict", "StatenIsland");

		scenario.getActivityFacilities().addActivityFacility(bronxFacility);
		scenario.getActivityFacilities().addActivityFacility(queensFacility);
		scenario.getActivityFacilities().addActivityFacility(statenIslandFacility);

		// Create Restrictions
		Map<String, Restriction> restrictions = new HashMap<>();
		Map<String, Double> nycBoroughs = new HashMap<>();
		double bronxRf = 0.25;
		double queensRf = 0.5;
		double globalRf = 0.75;
		nycBoroughs.put("Bronx", bronxRf);
		nycBoroughs.put("Queens", queensRf);
		Restriction workRestriction = Restriction.of(globalRf);
		workRestriction.setLocationBasedRf(nycBoroughs);
		restrictions.put("work", workRestriction);


		// Create LocationBasedParticipationModel
		LocationBasedParticipationModel activityParticipationModel = new LocationBasedParticipationModel(rnd, episimConfig, scenario);

		ImmutableMap<String, Restriction> restrictionsImmutable = ImmutableMap.copyOf(restrictions);

		activityParticipationModel.setRestrictionsForIteration(1, restrictionsImmutable);

		// Check Bronx
		Map<String, EpisimPerson> personMapBronx = makePopulation(bronxFacility);
		int actTakesPlaceBronx = updateParticipationForAllAgents(activityParticipationModel, personMapBronx);
		assertThat(actTakesPlaceBronx / POPULATION_SIZE).isCloseTo(bronxRf, PERCENTAGE_OFFSET);

		// Check Queens
		Map<String, EpisimPerson> personMapQueens = makePopulation(queensFacility);
		int actTakesPlaceQueens = updateParticipationForAllAgents(activityParticipationModel, personMapQueens);
		assertThat(actTakesPlaceQueens / POPULATION_SIZE).isCloseTo(queensRf, PERCENTAGE_OFFSET);

		// Check Staten Island
		Map<String, EpisimPerson> personMapSI = makePopulation(statenIslandFacility);
		int actTakesPlaceSI = updateParticipationForAllAgents(activityParticipationModel, personMapSI);
		assertThat(actTakesPlaceSI / POPULATION_SIZE).isCloseTo(globalRf, PERCENTAGE_OFFSET);


	}

	/**
	 * updates participation for all agents and counts how many actually complete the activity.
	 */
	private int updateParticipationForAllAgents(LocationBasedParticipationModel activityParticipationModel, Map<String, EpisimPerson> personMapBronx) {
		int actTakesPlace = 0;
		for (EpisimPerson person : personMapBronx.values()) {
			List<EpisimPerson.PerformedActivity> trajectory = person.getTrajectory();
			BitSet activityParticipation = new BitSet(trajectory.size());
			activityParticipationModel.updateParticipation(person, activityParticipation,
					0, trajectory);
			actTakesPlace += activityParticipation.length();
		}
		return actTakesPlace;
	}

	/**
	 * creates large population of identical people who all do their work activity in a specific activityFacility
	 */
	private Map<String, EpisimPerson> makePopulation(ActivityFacility activityFacility) {
		Map<String, EpisimPerson> personMap = new HashMap<>();
		for (int i = 0; i < POPULATION_SIZE; i++) {
			EpisimPerson person = EpisimTestUtils.createPerson();
			person.addToTrajectory(0., episimConfig.getInfectionParam("work"), activityFacility.getId());
			personMap.put("p" + i, person);
		}
		return personMap;
	}

}
