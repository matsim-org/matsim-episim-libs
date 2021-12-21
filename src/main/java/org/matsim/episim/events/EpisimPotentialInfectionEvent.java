package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.episim.model.VirusStrain;

import static org.matsim.episim.events.EpisimInfectionEvent.*;

import java.util.Map;


/**
 * Notifies when a person got infected by another person.
 */
public class EpisimPotentialInfectionEvent extends Event implements HasPersonId, Comparable<Event> {

	static final String EVENT_TYPE = "episimPotentialInfection";
	static final String UNVAC_PROBABILITY = "unVacProbability";
	static final String RND = "rnd";

	private final Id<Person> personId;
	private final Id<Person> infectorId;
	private final Id<?> containerId;
	private final String infectionType;
	private final int groupSize;
	private final VirusStrain virusStrain;
	private final double probability;

	private final double unVacProbability;
	private final double rnd;

	/**
	 * Constructor.
	 */
	public EpisimPotentialInfectionEvent(double time, Id<Person> personId, Id<Person> infectorId, Id<?> containerId, String infectionType,
	                                     int groupSize, VirusStrain strain, double probability, double unVacProbability, double rnd) {

		super(time);

		this.personId = personId;
		this.infectorId = infectorId;
		this.containerId = containerId;
		this.infectionType = infectionType;
		this.groupSize = groupSize;
		this.virusStrain = strain;
		this.probability = probability;

		this.unVacProbability = unVacProbability;
		this.rnd = rnd;
	}

	@Override
	public String getEventType() {
		return EpisimPotentialInfectionEvent.EVENT_TYPE;
	}

	@Override
	public Id<Person> getPersonId() {
		return personId;
	}

	public Id<?> getContainerId() {
		return containerId;
	}

	/**
	 * Person who infected {@code #getPersonId}.
	 */
	public Id<Person> getInfectorId() {
		return infectorId;
	}

	/**
	 * How this infection happened. Activity of person and infector separated by underscore.
	 */
	public String getInfectionType() {
		return infectionType;
	}

	/**
	 * Variant which the person was infected with.
	 */
	public VirusStrain getStrain() {
		return virusStrain;
	}

	public int getGroupSize() {
		return groupSize;
	}

	public VirusStrain getVirusStrain() {
		return virusStrain;
	}

	public double getProbability() {
		return probability;
	}

	public double getUnVacProbability() {
		return unVacProbability;
	}

	public double getRnd() {
		return rnd;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();

		attr.put(CONTAINER, containerId.toString());
		attr.put(INFECTOR, infectorId.toString());
		attr.put(INFECTION_TYPE, infectionType);
		attr.put(GROUP_SIZE, Integer.toString(groupSize));
		attr.put(PROBABILITY, Double.toString(probability));
		attr.put(VIRUS_STRAIN, virusStrain.toString());

		attr.put(UNVAC_PROBABILITY, Double.toString(unVacProbability));
		attr.put(RND, Double.toString(rnd));

		return attr;
	}


	@Override
	public int compareTo(Event obj) {

		// Defines a stable ordering for events
		if (getTime() != obj.getTime())
			return Double.compare(getTime(), obj.getTime());

		EpisimPotentialInfectionEvent o;
		if (obj instanceof EpisimPotentialInfectionEvent)
			o = (EpisimPotentialInfectionEvent) obj;
		else
			return -1;

		if (infectorId != o.infectorId)
			return infectorId.compareTo(o.infectorId);

		if (containerId != o.containerId)
			return containerId.toString().compareTo(o.containerId.toString());

		if (probability != o.probability)
			return Double.compare(probability, o.probability);

		return 0;
	}
}
