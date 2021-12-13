package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.model.VirusStrain;

import java.util.Map;


/**
 * Notifies when there is a potential infection with one infectious person.
 */
public final class EpisimPotentialInfectionEvent extends EpisimInfectionEvent {

	static final String EVENT_TYPE = "episimPotentialInfection";

	static final String UNVAC_PROBABILITY = "unVacProbability";
	static final String RND = "rnd";

	private final double unVacProbability;
	private final double rnd;

	/**
	 * Constructor.
	 */
	public EpisimPotentialInfectionEvent(double time, Id<Person> personId, Id<Person> infectorId, Id<?> containerId, String infectionType,
	                                     int groupSize, VirusStrain strain, double probability, double unVacProbability, double rng) {
		super(time, personId, infectorId, containerId, infectionType, groupSize, strain, probability);
		this.unVacProbability = unVacProbability;
		this.rnd = rng;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}


	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();

		attr.put(UNVAC_PROBABILITY, Double.toString(unVacProbability));
		attr.put(RND, Double.toString(rnd));

		return attr;
	}
}
