package org.matsim.episim.model.listener;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.model.SimulationStartListener;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.util.Map;
import java.util.SplittableRandom;

public class HouseholdSusceptibility implements SimulationStartListener {

	/**
	 * Susceptibility for each household.
	 */
	private final Object2DoubleMap<String> houseHoldSusceptibility = new Object2DoubleOpenHashMap<>();

	@Override
	public void init(SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {

		for (EpisimPerson p : persons.values()) {

			String homeId = getHomeId(p);
			p.setSusceptibility(houseHoldSusceptibility.computeIfAbsent(homeId, (k) -> sample(rnd)));
		}
	}

	/**
	 * Samples susceptibility for a household.
	 */
	private double sample(SplittableRandom rnd) {

		// TODO: implement
		return 1.0;
	}

	private String getHomeId(EpisimPerson person) {
		String home = (String) person.getAttributes().getAttribute("homeId");
		// fallback to person id if there is no home
		return home != null ? home : person.getPersonId().toString();
	}

	@Override
	public String toString() {
		return "HouseholdSusceptibility{}";
	}
}
