package org.matsim.episim;

import com.google.common.annotations.Beta;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.episim.events.EpisimPersonStatusEvent;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.*;

/**
 * Persons current state in the simulation.
 */
public final class EpisimPerson implements Attributable {

	private final Id<Person> personId;
	private final EventsManager eventsManager;
	private final Attributes attributes;
	private final Set<EpisimPerson> traceableContactPersons = new LinkedHashSet<>();
	private final List<String> trajectory = new ArrayList<>();

	/**
	 * Stores first time of status changes to specific type.
	 */
	private final EnumMap<DiseaseStatus, Double> statusChanges = new EnumMap<>(DiseaseStatus.class);

	/**
	 * The {@link EpisimContainer} the person is currently located in.
	 */
	private EpisimContainer<?> currentContainer = null;
	/**
	 * Current {@link DiseaseStatus}.
	 */
	private DiseaseStatus status = DiseaseStatus.susceptible;
	/**
	 * Current {@link QuarantineStatus}.
	 */
	private QuarantineStatus quarantineStatus = QuarantineStatus.no;

	/**
	 * Iteration when this person got into quarantine. Negative if person was never quarantined.
	 */
	private int quarantineDate = -1;
	private int currentPositionInTrajectory;
	/**
	 * The last visited {@link org.matsim.facilities.ActivityFacility}.
	 */
	private String lastFacilityId;
	private String firstFacilityId;

	EpisimPerson(Id<Person> personId, Attributes attrs, EventsManager eventsManager) {
		this.personId = personId;
		this.attributes = attrs;
		this.eventsManager = eventsManager;
	}

	public Id<Person> getPersonId() {
		return personId;
	}

	public DiseaseStatus getDiseaseStatus() {
		return status;
	}

	public void setDiseaseStatus(double now, DiseaseStatus status) {
		this.status = status;
		if (!statusChanges.containsKey(status))
			statusChanges.put(status, now);

		eventsManager.processEvent(new EpisimPersonStatusEvent(now, personId, status));
	}

	public QuarantineStatus getQuarantineStatus() {
		return quarantineStatus;
	}

	public void setQuarantineStatus(QuarantineStatus quarantineStatus, int iteration) {
		this.quarantineStatus = quarantineStatus;
		this.quarantineDate = iteration;
	}


	/**
	 * Days elapsed since a certain status was set.
	 * This will always round the change as if it happened on the start of a day.
	 *
	 * @param status     requested status
	 * @param currentDay current day (iteration)
	 * @throws IllegalStateException when the requested status was never set
	 */
	public int daysSince(DiseaseStatus status, int currentDay) {
		if (!statusChanges.containsKey(status)) throw new IllegalStateException("Person was never " + status);

		double day = Math.floor(statusChanges.get(status) / 86400d);

		return currentDay - (int) day;
	}

	/**
	 * Days elapsed since person was put into quarantine.
	 *
	 * @param currentDay current day (iteration)
	 * @apiNote This is currently not used much and may change similar to {@link #daysSince(DiseaseStatus, int)}.
	 */
	@Beta
	public int daysSinceQuarantine(int currentDay) {

		// yyyyyy since this API is so unstable, I would prefer to have the class non-public.  kai, apr'20
		if (quarantineDate < 0) throw new IllegalStateException("Person was never quarantined");

		return currentDay - quarantineDate;
	}

	int getQuarantineDate() {
		return this.quarantineDate;
	}

	String getLastFacilityId() {
		return this.lastFacilityId;
	}

	void setLastFacilityId(String lastFacilityId) {
		this.lastFacilityId = lastFacilityId;
	}

	public void addTraceableContactPerson(EpisimPerson personWrapper) {
		traceableContactPersons.add(personWrapper);
	}

	public Set<EpisimPerson> getTraceableContactPersons() {
		return traceableContactPersons;
	}

	void addToTrajectory(String trajectoryElement) {
		trajectory.add(trajectoryElement);
	}

	public List<String> getTrajectory() {
		return trajectory;
	}

	public int getCurrentPositionInTrajectory() {
		return this.currentPositionInTrajectory;
	}

	void setCurrentPositionInTrajectory(int currentPositionInTrajectory) {
		this.currentPositionInTrajectory = currentPositionInTrajectory;
	}

	public EpisimContainer<?> getCurrentContainer() {
		return currentContainer;
	}

	/**
	 * Set the container the person is currently contained in. {@link #removeCurrentContainer(EpisimContainer)} must be called before a new
	 * container can be set.
	 */
	public void setCurrentContainer(EpisimContainer<?> container) {
		if (this.currentContainer != null)
			throw new IllegalStateException(String.format("Person in more than one container at once. Person=%s in %s and %s",
					this.getPersonId(), container.getContainerId(), this.currentContainer.getContainerId()));


		this.currentContainer = container;
	}

	@Override
	public Attributes getAttributes() {
		return attributes;
	}

	/**
	 * Whether person is currently in a container.
	 */
	public boolean isInContainer() {
		return currentContainer != null;
	}

	public void removeCurrentContainer(EpisimContainer<?> container) {
		if (this.currentContainer != container)
			throw new IllegalStateException(String.format("Person is currently in %s, but not in removed one %s", currentContainer, container));

		this.currentContainer = null;
	}

	String getFirstFacilityId() {
		return firstFacilityId;
	}

	void setFirstFacilityId(String firstFacilityId) {
		this.firstFacilityId = firstFacilityId;
	}

	public enum DiseaseStatus {susceptible, infectedButNotContagious, contagious, showingSymptoms, seriouslySick, critical, recovered}

	public enum QuarantineStatus {full, atHome, no}
}
