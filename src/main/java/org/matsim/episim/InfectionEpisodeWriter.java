/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2026 matsim-org
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

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimInitialInfectionEvent;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.reporting.EpisimWriter;
import org.matsim.facilities.ActivityFacility;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes the universal preprocessed output layer, consisting of {@code persons.tsv} and {@code infectionEpisodes.tsv}.
 * <p>
 * Both tables contain only facts of the model, independent of any metric and pathogen. Study-specific metrics like
 * household secondary attack rates, serial and generation intervals, the share of infections at home or reinfections
 * are derived from them in post-processing by joining and filtering. No such definitions are part of this class.
 * <p>
 * {@code persons.tsv} has one row per person and is written once after initialization:
 * <ul>
 *     <li>{@code personId}</li>
 *     <li>{@code homeIdAttribute}: attribute {@code homeId} of the population, empty if not present</li>
 *     <li>{@code homeContainerId}: container in which the person performs {@code home} activities during the simulation,
 *     see {@link #findHomeContainer(EpisimPerson)}</li>
 *     <li>{@code homeContainerCount}: number of distinct home containers in the trajectory (expected 1)</li>
 *     <li>{@code age}: empty if unknown</li>
 *     <li>{@code district}: empty if not present</li>
 * </ul>
 * <p>
 * {@code infectionEpisodes.tsv} has one row per infection of a person (person x episode). A row is written when the
 * episode is closed, i.e. when the person becomes {@code susceptible} again, dies or the run ends:
 * <ul>
 *     <li>{@code personId}</li>
 *     <li>{@code episode}: number of the infection of this person, starting at 1</li>
 *     <li>{@code source}: {@code contact} ({@link EpisimInfectionEvent}) or {@code initial} ({@link EpisimInitialInfectionEvent}).
 *     Initial infections come from two sources, the random disease import and the replay of an immunization history
 *     (where days can be negative); both are written as {@code initial} and are not distinguished.</li>
 *     <li>{@code pathogen}, {@code strain}</li>
 *     <li>{@code infectorId}, {@code infectorEpisode}: infector and its episode number at the time of infection</li>
 *     <li>{@code infectorInHousehold}: {@code 1}/{@code 0} whether infector and infected have the same home container</li>
 *     <li>{@code infectedAtHome}: {@code 1}/{@code 0} whether the infection container is the home container of the infected</li>
 *     <li>{@code infectionType}, {@code facility}: as in {@code infectionEvents.txt}</li>
 *     <li>{@code date_infected}: date of the iteration of infection</li>
 *     <li>{@code day_<status>}: one column per {@link DiseaseStatus} except {@code susceptible}, containing the day
 *     (iteration) this status was first reached within the episode</li>
 *     <li>{@code day_susceptibleAgain}: day of the transition back to {@code susceptible}</li>
 *     <li>{@code closedBy}: {@code susceptible}, {@code deceased} or {@code endOfRun}. {@code reinfected} is only written
 *     if a person receives an initial infection while the previous episode is still open, which can only happen when
 *     replaying an immunization history.</li>
 *     <li>{@code restored}: {@code 1} if the episode was open when a snapshot was loaded. The infector of such episodes
 *     can not be restored and the source is derived from the stored infection container.</li>
 * </ul>
 * All infector related columns, {@code infectionType} and {@code facility} are empty for initial infections.
 * The household columns are empty when a home container is unknown. Unreached stages are empty.
 */
final class InfectionEpisodeWriter {

	private static final Logger log = LogManager.getLogger(InfectionEpisodeWriter.class);

	/**
	 * Marker for an empty day.
	 */
	private static final int NONE = Integer.MIN_VALUE;

	/**
	 * All disease states that are written as day column.
	 */
	private static final DiseaseStatus[] STAGES = Arrays.stream(DiseaseStatus.values())
			.filter(s -> s != DiseaseStatus.susceptible)
			.toArray(DiseaseStatus[]::new);

	private final EpisimWriter writer;
	private final String filename;
	private final LocalDate startDate;

	/**
	 * Currently open episodes.
	 */
	private final Map<Id<Person>, Episode> open = new ConcurrentHashMap<>();

	/**
	 * Number of infections per person.
	 */
	private final Map<Id<Person>, AtomicInteger> numEpisodes = new ConcurrentHashMap<>();

	/**
	 * Home containers as determined during initialization.
	 */
	private final Map<Id<Person>, HomeContainer> homeContainers = new IdMap<>(Person.class);

	/**
	 * All persons of the simulation, used to look up the infector.
	 */
	private Map<Id<Person>, EpisimPerson> persons = Map.of();

	private BufferedWriter out;

	InfectionEpisodeWriter(EpisimWriter writer, String base, LocalDate startDate) {
		this.writer = writer;
		this.filename = base + "infectionEpisodes.tsv";
		this.startDate = startDate;
	}

	/**
	 * Header of {@code infectionEpisodes.tsv}.
	 */
	static String[] episodeHeader() {
		List<String> header = new ArrayList<>(List.of("personId", "episode", "source", "pathogen", "strain", "infectorId",
				"infectorEpisode", "infectorInHousehold", "infectedAtHome", "infectionType", "facility", "date_infected"));

		for (DiseaseStatus stage : STAGES) {
			header.add("day_" + stage.name());
		}

		header.addAll(List.of("day_susceptibleAgain", "closedBy", "restored"));
		return header.toArray(String[]::new);
	}

	/**
	 * Determine the home container of a person from its trajectory. These are the facilities of all activities, whose
	 * container name starts with {@code home}. If there are multiple ones, the most frequent is used and ties are resolved
	 * by the order in the trajectory.
	 * <p>
	 * For agents with events this is the facility id from the events, which is not necessarily equal to the {@code homeId}
	 * attribute. Agents without events are put into a container created from {@code homeId} or {@code home_of_<personId>}.
	 *
	 * @return home container, with null id if the person has no home activity
	 */
	static HomeContainer findHomeContainer(EpisimPerson person) {

		Object2IntMap<Id<ActivityFacility>> counts = new Object2IntLinkedOpenHashMap<>(2);
		for (EpisimPerson.PerformedActivity act : person.getTrajectory()) {
			if (act.params != null && act.facilityId != null && act.params.getContainerName().startsWith("home"))
				counts.mergeInt(act.facilityId, 1, Integer::sum);
		}

		Id<ActivityFacility> best = null;
		int max = 0;
		for (Object2IntMap.Entry<Id<ActivityFacility>> e : counts.object2IntEntrySet()) {
			if (e.getIntValue() > max) {
				best = e.getKey();
				max = e.getIntValue();
			}
		}

		return new HomeContainer(best, counts.size());
	}

	/**
	 * Open the output file.
	 *
	 * @param append whether to append to an existing file, otherwise a new file with header is created
	 */
	void open(boolean append) {
		if (append)
			out = EpisimWriter.prepare(filename);
		else {
			String[] header = episodeHeader();
			out = EpisimWriter.prepare(filename, header[0], header[1], (Object[]) Arrays.copyOfRange(header, 2, header.length));
		}
	}

	/**
	 * Write {@code persons.tsv} and remember the persons and their home containers.
	 */
	void writePersons(String personsFile, Map<Id<Person>, EpisimPerson> persons) {

		this.persons = persons;
		homeContainers.clear();

		BufferedWriter personsOut = EpisimWriter.prepare(personsFile, "personId", "homeIdAttribute",
				"homeContainerId", "homeContainerCount", "age", "district");

		for (EpisimPerson person : persons.values()) {
			HomeContainer home = findHomeContainer(person);
			homeContainers.put(person.getPersonId(), home);

			int age = person.getAgeOrDefault(-1);
			writer.append(personsOut, new String[]{
					person.getPersonId().toString(),
					toString(person.getAttributes().getAttribute("homeId")),
					toString(home.id),
					String.valueOf(home.count),
					age == -1 ? "" : String.valueOf(age),
					toString(person.getAttributes().getAttribute("district"))
			});
		}

		writer.close(personsOut);
	}

	/**
	 * Open an episode for a contact infection.
	 */
	void openContact(EpisimInfectionEvent event, int day) {

		Episode ep = new Episode(event.getPersonId(), nextEpisode(event.getPersonId()), "contact", event.getVirusStrain(), day);

		Id<ActivityFacility> infectedHome = homeContainer(event.getPersonId());
		String facility = event.getContainerId().toString();

		ep.infectorId = event.getInfectorId().toString();
		ep.infectionType = event.getInfectionType();
		ep.facility = facility;
		ep.infectedAtHome = infectedHome == null ? "" : bool(infectedHome.toString().equals(facility));

		EpisimPerson infector = persons.get(event.getInfectorId());
		if (infector != null)
			ep.infectorEpisode = String.valueOf(infector.getNumInfections());

		Id<ActivityFacility> infectorHome = homeContainer(event.getInfectorId());
		if (infectedHome != null && infectorHome != null)
			ep.infectorInHousehold = bool(infectedHome.equals(infectorHome));

		put(ep);
	}

	/**
	 * Open an episode for an initial infection.
	 */
	void openInitial(EpisimInitialInfectionEvent event, int day) {
		put(new Episode(event.getPersonId(), nextEpisode(event.getPersonId()), "initial", event.getVirusStrain(), day));
	}

	/**
	 * Record a status change of a person and close the episode if needed.
	 */
	void reportStatus(Id<Person> personId, DiseaseStatus status, int day) {

		// the episode is opened by the infection event, which is reported after this status in case of contact infections
		if (status == DiseaseStatus.infectedButNotContagious)
			return;

		if (status == DiseaseStatus.susceptible || status == DiseaseStatus.deceased) {
			Episode ep = open.remove(personId);
			if (ep == null)
				return;

			synchronized (ep) {
				if (status == DiseaseStatus.susceptible)
					ep.susceptibleAgain = day;
				else
					ep.reach(status, day);

				ep.closedBy = status.name();
			}

			write(ep);
			return;
		}

		Episode ep = open.get(personId);
		if (ep != null) {
			synchronized (ep) {
				ep.reach(status, day);
			}
		}
	}

	/**
	 * Restore the open episodes from the person state after a snapshot has been loaded.
	 * Stages are taken from the stored status changes, the infector is unknown.
	 *
	 * @param iteration iteration at which the simulation continues
	 */
	void restore(Collection<EpisimPerson> persons, int iteration) {

		open.clear();
		numEpisodes.clear();

		for (EpisimPerson person : persons) {

			int n = person.getNumInfections();
			if (n == 0)
				continue;

			numEpisodes.put(person.getPersonId(), new AtomicInteger(n));

			DiseaseStatus status = person.getDiseaseStatus();
			if (status == DiseaseStatus.susceptible || status == DiseaseStatus.deceased)
				continue;

			int infected = person.hadDiseaseStatus(DiseaseStatus.infectedButNotContagious) ?
					startDay(person, DiseaseStatus.infectedButNotContagious, iteration) :
					(int) Math.floor(person.getInfectionDates().getDouble(n - 1) / EpisimUtils.DAY);

			Id<ActivityFacility> container = person.getInfectionContainer();
			Episode ep = new Episode(person.getPersonId(), n, container != null ? "contact" : "initial", person.getVirusStrain(), infected);
			ep.restored = true;

			if (container != null) {
				Id<ActivityFacility> home = homeContainer(person.getPersonId());
				ep.facility = container.toString();
				ep.infectionType = person.getInfectionType() == null ? "" : person.getInfectionType();
				ep.infectedAtHome = home == null ? "" : bool(home.equals(container));
			}

			for (DiseaseStatus stage : STAGES) {
				// recovered is kept from previous episodes, it only belongs to this one if it is the current status
				if (stage == DiseaseStatus.infectedButNotContagious || !person.hadDiseaseStatus(stage) ||
						(stage == DiseaseStatus.recovered && status != DiseaseStatus.recovered))
					continue;

				int day = startDay(person, stage, iteration);
				if (day >= infected)
					ep.reach(stage, day);
			}

			open.put(person.getPersonId(), ep);
		}

		log.info("Restored {} open infection episodes", open.size());
	}

	/**
	 * Write all episodes that are still open and close the output.
	 */
	void close() {

		if (out == null)
			return;

		List<Episode> remaining = new ArrayList<>(open.values());
		open.clear();
		remaining.sort(Comparator.comparing(ep -> ep.personId));

		for (Episode ep : remaining) {
			synchronized (ep) {
				ep.closedBy = "endOfRun";
			}
			write(ep);
		}

		writer.close(out);
		out = null;
	}

	private void put(Episode ep) {
		Episode previous = open.put(ep.personId, ep);
		if (previous != null) {
			log.warn("Person {} was infected while episode {} was still open", ep.personId, previous.episode);
			synchronized (previous) {
				previous.closedBy = "reinfected";
			}
			write(previous);
		}
	}

	private int nextEpisode(Id<Person> personId) {
		return numEpisodes.computeIfAbsent(personId, k -> new AtomicInteger()).incrementAndGet();
	}

	@Nullable
	private Id<ActivityFacility> homeContainer(Id<Person> personId) {
		HomeContainer home = homeContainers.get(personId);
		if (home == null) {
			// person was not present during initialization
			EpisimPerson person = persons.get(personId);
			return person != null ? findHomeContainer(person).id : null;
		}

		return home.id;
	}

	private void write(Episode ep) {
		String[] row;
		synchronized (ep) {
			row = ep.toRow(startDate);
		}
		writer.append(out, row);
	}

	/**
	 * Day at which a status was reached.
	 */
	private static int startDay(EpisimPerson person, DiseaseStatus status, int iteration) {
		return iteration - person.daysSince(status, iteration);
	}

	private static String bool(boolean value) {
		return value ? "1" : "0";
	}

	private static String toString(@Nullable Object value) {
		return value == null ? "" : value.toString();
	}

	/**
	 * Home container of a person and the number of distinct home containers.
	 */
	static final class HomeContainer {

		@Nullable
		final Id<ActivityFacility> id;
		final int count;

		HomeContainer(@Nullable Id<ActivityFacility> id, int count) {
			this.id = id;
			this.count = count;
		}
	}

	/**
	 * State of one infection episode.
	 */
	private static final class Episode {

		private final Id<Person> personId;
		private final int episode;
		private final String source;
		private final VirusStrain strain;
		private final int infected;
		private final int[] days = new int[DiseaseStatus.values().length];

		private String infectorId = "";
		private String infectorEpisode = "";
		private String infectorInHousehold = "";
		private String infectedAtHome = "";
		private String infectionType = "";
		private String facility = "";
		private int susceptibleAgain = NONE;
		private String closedBy;
		private boolean restored;

		private Episode(Id<Person> personId, int episode, String source, VirusStrain strain, int day) {
			this.personId = personId;
			this.episode = episode;
			this.source = source;
			this.strain = strain;
			this.infected = day;
			Arrays.fill(days, NONE);
			days[DiseaseStatus.infectedButNotContagious.ordinal()] = day;
		}

		/**
		 * Stores the day a status was reached, if it was not reached before.
		 */
		private void reach(DiseaseStatus status, int day) {
			if (days[status.ordinal()] == NONE)
				days[status.ordinal()] = day;
		}

		private String[] toRow(LocalDate startDate) {
			List<String> row = new ArrayList<>(15 + STAGES.length);
			row.add(personId.toString());
			row.add(String.valueOf(episode));
			row.add(source);
			row.add(strain.getPathogen().getName());
			row.add(strain.toString());
			row.add(infectorId);
			row.add(infectorEpisode);
			row.add(infectorInHousehold);
			row.add(infectedAtHome);
			row.add(infectionType);
			row.add(facility);
			row.add(startDate.plusDays(infected - 1).toString());

			for (DiseaseStatus stage : STAGES) {
				row.add(day(days[stage.ordinal()]));
			}

			row.add(day(susceptibleAgain));
			row.add(closedBy);
			row.add(restored ? "1" : "0");
			return row.toArray(String[]::new);
		}

		private static String day(int day) {
			return day == NONE ? "" : String.valueOf(day);
		}
	}
}
