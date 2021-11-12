package org.matsim.episim.model.vaccination;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.VaccinationType;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.*;

/**
 * Read vaccination from file for each age group.
 */
public class VaccinationFromData extends VaccinationByAge {

	private static final Logger log = LogManager.getLogger(VaccinationFromData.class);

	/**
	 * All known age groups.
	 */
	private List<AgeGroup> ageGroups = null;

	/**
	 * Entries for each day.
	 */
	private TreeMap<LocalDate, DoubleList> entries = null;

	/**
	 * Fallback to random vaccinations.
	 */
	private final RandomVaccination random;

	@Inject
	public VaccinationFromData(SplittableRandom rnd, VaccinationConfigGroup vaccinationConfig) {
		super(rnd, vaccinationConfig);

		random = new RandomVaccination(rnd, vaccinationConfig);
	}

	@Override
	public void init(SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {

		if (vaccinationConfig.getFromFile() == null)
			throw new IllegalArgumentException("Vaccination file must be set, but was null");

		ageGroups = new ArrayList<>();
		entries = new TreeMap<>();

		try (CSVParser parser = new CSVParser(IOUtils.getBufferedReader(vaccinationConfig.getFromFile()), CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

			int i = 0;
			for (String header : parser.getHeaderNames()) {

				if (header.contains("-")) {

					String[] split = header.split("-");

					ageGroups.add(new AgeGroup(i, Integer.parseInt(split[0]), Integer.parseInt(split[1])));

				} else if (header.endsWith("+")) {

					String s = header.substring(0, header.length() - 1);

					ageGroups.add(new AgeGroup(i, Integer.parseInt(s), MAX_AGE - 1));
				}

				i++;
			}

			for (CSVRecord record : parser) {

				LocalDate date = LocalDate.parse(record.get("date"));
				DoubleList values = new DoubleArrayList(ageGroups.size());

				for (AgeGroup ag : ageGroups) {
					values.add(Double.parseDouble(record.get(ag.index)));
				}

				entries.put(date, values);
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		// collect population sizes
		for (EpisimPerson p : persons.values()) {
			AgeGroup ag = findAgeGroup(p.getAge());
			if (ag != null)
				ag.size++;
		}

		log.info("Using age-groups: {}", ageGroups);
	}

	private AgeGroup findAgeGroup(int age) {
		for (AgeGroup ag : ageGroups) {
			if (age >= ag.from && age <= ag.to)
				return ag;
		}

		return null;
	}

	@Override
	public int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, LocalDate date, int iteration, double now) {

		// If available vaccination is given, data will be ignored and vaccination by age executed
		// TODO: handle revaccination after update
		if (availableVaccinations >= 0 || reVaccination)
			if (reVaccination)
				return super.handleVaccination(persons, reVaccination, availableVaccinations, date, iteration, now);
			else
				return random.handleVaccination(persons, reVaccination, availableVaccinations, date, iteration, now);

		DoubleList entry = EpisimUtils.findValidEntry(entries, null, date);

		// No vaccinations today
		if (entry == null)
			return 0;

		// reset count
		for (AgeGroup ag : ageGroups) {
			ag.vaccinated = 0;
		}

		final List<EpisimPerson>[] perAge = new List[MAX_AGE];

		for (int i = 0; i < MAX_AGE; i++)
			perAge[i] = new ArrayList<>();

		for (EpisimPerson p : persons.values()) {

			AgeGroup ag = findAgeGroup(p.getAge());

			if (ag == null) continue;

			if (p.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes) {
				ag.vaccinated++;
				continue;
			}

			if (p.isVaccinable() && p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible &&
//					!p.isRecentlyRecovered(iteration) && 
					p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.no) {
				perAge[p.getAge()].add(p);
			}
		}

		Map<VaccinationType, Double> prob = vaccinationConfig.getVaccinationTypeProb(date);

		int totalVaccinations = 0;

		for (int ii = 0; ii < ageGroups.size(); ii++) {

			AgeGroup ag = ageGroups.get(ii);
			double share = entry.getDouble(ii);

			int vaccinationsLeft = (int) ((ag.size * share) - ag.vaccinated);

			int age = ag.to;

			while (vaccinationsLeft > 0 && age >= ag.from) {

				List<EpisimPerson> candidates = perAge[age];

				// list is shuffled to avoid eventual bias
				if (candidates.size() > vaccinationsLeft)
					Collections.shuffle(perAge[age], new Random(EpisimUtils.getSeed(rnd)));

				for (int i = 0; i < Math.min(candidates.size(), vaccinationsLeft); i++) {
					EpisimPerson person = candidates.get(i);
					vaccinate(person, iteration, VaccinationModel.chooseVaccinationType(prob, rnd), reVaccination);
					vaccinationsLeft--;
					totalVaccinations++;
				}

				age--;
			}

		}


		return totalVaccinations;
	}

	private static final class AgeGroup {

		private final int index;

		private final int from;
		private final int to;

		private int size = 0;
		private int vaccinated = 0;

		private AgeGroup(int index, int from, int to) {
			this.index = index;
			this.from = from;
			this.to = to;
		}

		@Override
		public String toString() {
			return "AgeGroup{" +
					"from=" + from +
					", to=" + to +
					", size=" + size +
					'}';
		}
	}
}
