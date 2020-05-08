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
package org.matsim.episim;

import com.google.common.annotations.Beta;
import org.eclipse.collections.api.map.primitive.MutableObjectDoubleMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectDoubleHashMap;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.events.EpisimPersonStatusEvent;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persons current state in the simulation.
 */
public final class EpisimPerson implements Attributable {

	/**
	 * Attribute for the ability to be traced.
	 */
	public static final String TRACING_ATTR = "hasTracing";

	private final Id<Person> personId;
	private final EpisimReporting reporting;
	private final Attributes attributes;
	private final ObjectDoubleHashMap<EpisimPerson> traceableContactPersons = new ObjectDoubleHashMap<>();
	private final List<Activity> trajectory = new ArrayList<>();

	/**
	 * Stores first time of status changes to specific type.
	 */
	private final EnumMap<DiseaseStatus, Double> statusChanges = new EnumMap<>(DiseaseStatus.class);

	/**
	 * Total spent time during activities.
	 */
	private final MutableObjectDoubleMap<String> spentTime = new ObjectDoubleHashMap<>();

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

	EpisimPerson(Id<Person> personId, Attributes attrs, EpisimReporting reporting) {
		this.personId = personId;
		this.attributes = attrs;
		this.reporting = reporting;
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

		reporting.reportPersonStatus(this, new EpisimPersonStatusEvent(now, personId, status));
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

		// yyyy since this API is so unstable, I would prefer to have the class non-public.  kai, apr'20
		// -> api now marked as unstable and containing an api note, because it is used by the models it has to be public. chr, apr'20
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

	public void addTraceableContactPerson(EpisimPerson personWrapper, double now) {
		// check if both persons have tracing capability
		if (isTraceable() && personWrapper.isTraceable())
			// Always use the latest tracking date
			traceableContactPersons.put(personWrapper, now);
	}

	/**
	 * Get all traced contacts that happened after certain time.
	 */
	public Set<EpisimPerson> getTraceableContactPersons(double after) {
		return traceableContactPersons.keySet()
				.stream().filter(k -> traceableContactPersons.get(k) >= after)
				.collect(Collectors.toSet());
	}

	/**
	 * Remove old contact tracing data before a certain date.
	 */
	public void clearTraceableContractPersons(double before) {
		traceableContactPersons.keySet().removeIf(k -> traceableContactPersons.get(k) < before);
	}


	/**
	 * Returns whether the person can be traced. When {@link #TRACING_ATTR} is not set it is always true.
	 */
	public boolean isTraceable() {
		Boolean tracing = (Boolean) attributes.getAttribute(TRACING_ATTR);
		if (tracing ==  null) return true;
		return tracing;
	}

	void addToTrajectory(Activity trajectoryElement) {
		trajectory.add(trajectoryElement);
	}

	public List<Activity> getTrajectory() {
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

	/**
	 * Add amount of time to spent time for an activity.
	 */
	public void addSpentTime(String actType, double timeSpent) {
		spentTime.addToValue(actType, timeSpent);
	}

	/**
	 * Spent time of this person by activity.
	 */
	public MutableObjectDoubleMap<String> getSpentTime() {
		return spentTime;
	}

	/**
	 * Disease status of a person.
	 */
	public enum DiseaseStatus {susceptible, infectedButNotContagious, contagious, showingSymptoms, seriouslySick, critical, recovered}

	/**
	 * Quarantine status of a person.
	 */
	public enum QuarantineStatus {full, atHome, no}

	/**
	 * Activity performed by a person. Holds the type and its infection params.
	 */
	public static final class Activity {

		public final String actType;
		public final EpisimConfigGroup.InfectionParams params;

		/**
		 * Constructor.
		 */
		public Activity(String actType, EpisimConfigGroup.InfectionParams params) {
			this.actType = actType;
			this.params = params;
		}
	}
}
