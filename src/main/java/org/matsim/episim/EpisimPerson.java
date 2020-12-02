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
import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.events.EpisimPersonStatusEvent;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicle;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.matsim.episim.EpisimUtils.readChars;
import static org.matsim.episim.EpisimUtils.writeChars;

/**
 * Persons current state in the simulation.
 */
public final class EpisimPerson implements Attributable {

	private final Id<Person> personId;
	private final EpisimReporting reporting;
	// This data structure is quite slow: log n costs, which should be constant...
	private final Attributes attributes;

	/**
	 * Whole trajectory over all days of the week.
	 */
	private final List<Activity> trajectory = new ArrayList<>();

	/**
	 * The position in the trajectory at the start for each day of the week.
	 */
	private final int[] startOfDay = new int[7];

	/**
	 * The position in the trajectory for the end of the day.
	 */
	private final int[] endOfDay = new int[7];

	/**
	 * The first visited {@link org.matsim.facilities.ActivityFacility} for each day.
	 */
	private final Id<ActivityFacility>[] firstFacilityId = new Id[7];

	// Fields above are initialized from the sim and not persisted

	/**
	 * Traced contacts with other persons.
	 */
	private final Object2DoubleMap<EpisimPerson> traceableContactPersons = new Object2DoubleLinkedOpenHashMap<>(4);

	/**
	 * Stores first time of status changes to specific type.
	 */
	private final EnumMap<DiseaseStatus, Double> statusChanges = new EnumMap<>(DiseaseStatus.class);

	/**
	 * Total spent time during activities.
	 */
	private final Object2DoubleMap<String> spentTime = new Object2DoubleOpenHashMap<>(4);

	/**
	 * The {@link EpisimContainer} the person is currently located in.
	 */
	private EpisimContainer<?> currentContainer = null;

	/**
	 * The facility where the person got infected. Can be null if person was initially infected.
	 */
	private Id<ActivityFacility> infectionContainer = null;

	/**
	 * The infection type when the person got infected. Can be null if person was initially infected.
	 */
	private String infectionType = null;

	/**
	 * Current {@link DiseaseStatus}.
	 */
	private DiseaseStatus status = DiseaseStatus.susceptible;
	/**
	 * Current {@link QuarantineStatus}.
	 */
	private QuarantineStatus quarantineStatus = QuarantineStatus.no;

	/**
	 * Current {@link VaccinationStatus}.
	 */
	private VaccinationStatus vaccinationStatus = VaccinationStatus.no;

	/**
	 * Iteration when this person was vaccinated. Negative if person was never vaccinated.
	 */
	private int vaccinationDate = -1;

	/**
	 * Iteration when this person got into quarantine. Negative if person was never quarantined.
	 */
	private int quarantineDate = -1;
	private int currentPositionInTrajectory;

	/**
	 * Whether this person can be traced.
	 */
	private boolean traceable;

	EpisimPerson(Id<Person> personId, Attributes attrs, EpisimReporting reporting) {
		this(personId, attrs, true, reporting);
	}

	EpisimPerson(Id<Person> personId, Attributes attrs, boolean traceable, EpisimReporting reporting) {
		this.personId = personId;
		this.attributes = attrs;
		this.traceable = traceable;
		this.reporting = reporting;
	}

	/**
	 * Reads persons state from stream.
	 *
	 * @param persons map of all persons in the simulation
	 */
	void read(ObjectInput in, Map<Id<Person>, EpisimPerson> persons,
			  Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities,
			  Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) throws IOException {

		int n = in.readInt();
		traceableContactPersons.clear();
		for (int i = 0; i < n; i++) {
			Id<Person> id = Id.create(readChars(in), Person.class);
			traceableContactPersons.put(persons.get(id), in.readDouble());
		}

		n = in.readInt();
		statusChanges.clear();
		for (int i = 0; i < n; i++) {
			int status = in.readInt();
			statusChanges.put(DiseaseStatus.values()[status], in.readDouble());
		}

		// Current container is set
		if (in.readBoolean()) {
			boolean isVehicle = in.readBoolean();
			String name = readChars(in);
			if (isVehicle) {
				currentContainer = vehicles.get(Id.create(name, Vehicle.class));
			} else
				currentContainer = facilities.get(Id.create(name, ActivityFacility.class));

			if (currentContainer == null)
				throw new IllegalStateException("Could not reconstruct container: " + name);
		} else
			currentContainer = null;

		if (in.readBoolean()){
			infectionContainer = Id.create(readChars(in), ActivityFacility.class);
		}

		if (in.readBoolean()) {
			infectionType = readChars(in);
		}

		n = in.readInt();
		spentTime.clear();
		for (int i = 0; i < n; i++) {
			String act = readChars(in);
			spentTime.put(act, in.readDouble());
		}

		status = DiseaseStatus.values()[in.readInt()];
		quarantineStatus = QuarantineStatus.values()[in.readInt()];
		quarantineDate = in.readInt();
		vaccinationStatus = VaccinationStatus.values()[in.readInt()];
		vaccinationDate = in.readInt();
		currentPositionInTrajectory = in.readInt();
		traceable = in.readBoolean();
	}

	/**
	 * Writes person state to stream.
	 */
	void write(ObjectOutput out) throws IOException {

		out.writeInt(traceableContactPersons.size());
		for (Map.Entry<EpisimPerson, Double> kv : traceableContactPersons.entrySet()) {
			writeChars(out, kv.getKey().getPersonId().toString());
			out.writeDouble(kv.getValue());
		}

		out.writeInt(statusChanges.size());
		for (Map.Entry<DiseaseStatus, Double> e : statusChanges.entrySet()) {
			out.writeInt(e.getKey().ordinal());
			out.writeDouble(e.getValue());
		}

		out.writeBoolean(currentContainer != null);
		if (currentContainer != null) {
			out.writeBoolean(currentContainer instanceof InfectionEventHandler.EpisimVehicle);
			writeChars(out, currentContainer.getContainerId().toString());
		}

		out.writeBoolean(infectionContainer != null);
		if (infectionContainer != null) {
			writeChars(out, infectionContainer.toString());
		}

		out.writeBoolean(infectionType != null);
		if (infectionType != null) {
			writeChars(out, infectionType);
		}

		out.writeInt(spentTime.size());

		for (Object2DoubleMap.Entry<String> kv : spentTime.object2DoubleEntrySet()) {
			writeChars(out, kv.getKey());
			out.writeDouble(kv.getDoubleValue());
		}

		out.writeInt(status.ordinal());
		out.writeInt(quarantineStatus.ordinal());
		out.writeInt(quarantineDate);
		out.writeInt(vaccinationStatus.ordinal());
		out.writeInt(vaccinationDate);
		out.writeInt(currentPositionInTrajectory);
		out.writeBoolean(traceable);
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

		// this function should receive now instead of iteration
		// only for testing currently
		//reporting.reportPersonStatus(this, new EpisimPersonStatusEvent(iteration * 86400d, personId, quarantineStatus));
	}

	public VaccinationStatus getVaccinationStatus() {
		return vaccinationStatus;
	}

	public void setVaccinationStatus(VaccinationStatus vaccinationStatus, int iteration) {
		if (vaccinationStatus != VaccinationStatus.yes) throw new IllegalArgumentException("Vaccination can only be set to yes.");

		this.vaccinationStatus = vaccinationStatus;
		this.vaccinationDate = iteration;
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
	 * Return whether a person had (or currently has) a certain disease status.
	 */
	public boolean hadDiseaseStatus(DiseaseStatus status) {
		return statusChanges.containsKey(status);
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

	/**
	 * Days elapsed since person got its first vaccination.
	 *
	 * @param currentDay current day (iteration)
	 */
	public int daysSince(VaccinationStatus status, int currentDay) {
		if (status != VaccinationStatus.yes) throw new IllegalArgumentException("Only supports querying when person was vaccinated");
		if (currentDay < 0) throw new IllegalStateException("Person was never vaccinated");

		return currentDay - vaccinationDate;
	}

	int getQuarantineDate() {
		return this.quarantineDate;
	}

	public void addTraceableContactPerson(EpisimPerson personWrapper, double now) {
		// check if both persons have tracing capability
		if (isTraceable() && personWrapper.isTraceable()) {
			// Always use the latest tracking date
			traceableContactPersons.put(personWrapper, now);
			reporting.reportTracing(now, this, personWrapper);
		}
	}

	/**
	 * Get all traced contacts that happened after certain time.
	 */
	public List<EpisimPerson> getTraceableContactPersons(double after) {
		return traceableContactPersons.object2DoubleEntrySet()
				.stream().filter(p -> p.getDoubleValue() >= after)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		// yyyy if the computationally intensive operation is to search by time, we should sort traceableContactPersons by time.  To simplify this, I
		// would argue that it is not a problem to have a person in there multiple times.  kai, may'20

	}

	/**
	 * Remove old contact tracing data before a certain date.
	 */
	public void clearTraceableContractPersons(double before) {

		int oldSize = traceableContactPersons.size();

		if (oldSize == 0) return;

		traceableContactPersons.keySet().removeIf(k -> traceableContactPersons.get(k) < before);
	}

	/**
	 * Returns whether the person can be traced.
	 */
	public boolean isTraceable() {
		return traceable;
	}

	public void setTraceable(boolean traceable) {
		this.traceable = traceable;
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

	void incrementCurrentPositionInTrajectory() {
		this.currentPositionInTrajectory++;
	}

	void resetCurrentPositionInTrajectory(DayOfWeek day) {
		currentPositionInTrajectory = startOfDay[day.getValue() - 1];
	}

	void setStartOfDay(DayOfWeek day, int position) {
		startOfDay[day.getValue() - 1] = position;
	}

	int getStartOfDay(DayOfWeek day) {
		return startOfDay[day.getValue() - 1];
	}

	void setEndOfDay(DayOfWeek day, int position) {
		endOfDay[day.getValue() - 1] = position;
	}

	int getEndOfDay(DayOfWeek day) {
		return endOfDay[day.getValue() - 1];
	}

	/**
	 * Defines that day {@code target} has the same trajectory as {@code source}.
	 */
	void duplicateDay(DayOfWeek target, DayOfWeek source) {
		startOfDay[target.getValue() - 1] = startOfDay[source.getValue() - 1];
		endOfDay[target.getValue() - 1] = endOfDay[source.getValue() - 1];
		firstFacilityId[target.getValue() - 1] = firstFacilityId[source.getValue() - 1];
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

	Id<ActivityFacility> getFirstFacilityId(DayOfWeek day) {
		return firstFacilityId[day.getValue() - 1];
	}

	void setFirstFacilityId(Id<ActivityFacility> firstFacilityId, DayOfWeek day) {
		this.firstFacilityId[day.getValue() - 1] = firstFacilityId;
	}

	public void setInfectionContainer(EpisimContainer<?> container) {
		this.infectionContainer = (Id<ActivityFacility>) container.getContainerId();
	}

	public Id<ActivityFacility> getInfectionContainer() {
		return infectionContainer;
	}

	public void setInfectionType(String infectionType) {
		this.infectionType = infectionType;
	}

	public String getInfectionType() {
		return infectionType;
	}

	/**
	 * Add amount of time to spent time for an activity.
	 */
	public void addSpentTime(String actType, double timeSpent) {
		spentTime.mergeDouble(actType, timeSpent, Double::sum);
	}

	/**
	 * Spent time of this person by activity.
	 */
	public Object2DoubleMap<String> getSpentTime() {
		return spentTime;
	}

	@Override
	public String toString() {
		return "EpisimPerson{" +
				"personId=" + personId +
				'}';
	}

	/**
	 * Disease status of a person.
	 */
	public enum DiseaseStatus {
		susceptible, infectedButNotContagious, contagious, showingSymptoms,
		seriouslySick, critical, seriouslySickAfterCritical, recovered
	}

	/**
	 * Quarantine status of a person.
	 */
	public enum QuarantineStatus {full, atHome, no}

	/**
	 * Status of vaccination.
	 */
	public enum VaccinationStatus {yes, no}

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

		@Override
		public String toString() {
			return "Activity{" +
					"actType='" + actType + '\'' +
					'}';
		}
	}
}
