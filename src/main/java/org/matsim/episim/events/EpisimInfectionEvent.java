package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.episim.model.VirusStrain;

import java.util.Map;


/**
 * Notifies when a person got infected by another person.
 */
public final class EpisimInfectionEvent extends Event implements HasPersonId, Comparable<EpisimInfectionEvent> {

	// TODO: hasLink or hasCoord?

	static final String EVENT_TYPE = "episimInfection";
	static final String INFECTOR = "infector";
	static final String CONTAINER = "container";
	static final String INFECTION_TYPE = "infectionType";
	static final String VIRUS_STRAIN = "virusStrain";
	static final String PROBABILITY = "probability";
	static final String GROUP_SIZE = "groupSize";

	private final Id<Person> personId;
	private final Id<Person> infectorId;
	private final Id<?> containerId;
	private final String infectionType;
	private final int groupSize;
	private final VirusStrain virusStrain;
	private final double probability;


	/**
	 * Constructor.
	 */
	public EpisimInfectionEvent(double time, Id<Person> personId, Id<Person> infectorId, Id<?> containerId, String infectionType,
								int groupSize, VirusStrain strain, double probability) {
		super(time);

		this.personId = personId;
		this.infectorId = infectorId;
		this.containerId = containerId;
		this.infectionType = infectionType;
		this.groupSize = groupSize;
		this.virusStrain = strain;
		this.probability = probability;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
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

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();

		attr.put(CONTAINER, containerId.toString());
		attr.put(INFECTOR, infectorId.toString());
		attr.put(INFECTION_TYPE, infectionType);
		attr.put(GROUP_SIZE, Integer.toString(groupSize));
		attr.put(PROBABILITY, Double.toString(probability));
		attr.put(VIRUS_STRAIN, virusStrain.toString());

		return attr;
	}

	@Override
	public int compareTo(EpisimInfectionEvent o) {

		// Defines a stable ordering for events

		if (getTime() != o.getTime())
			return Double.compare(getTime(), o.getTime());

		if (infectorId != o.infectorId)
			return infectorId.compareTo(o.infectorId);

		if (containerId != o.containerId)
			return containerId.toString().compareTo(o.containerId.toString());

		if (probability != o.probability)
			return Double.compare(probability, o.probability);

		return 0;
	}
}
