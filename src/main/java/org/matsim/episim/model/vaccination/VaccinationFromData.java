package org.matsim.episim.model.vaccination;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.units.qual.A;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.VaccinationType;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;
import tech.tablesaw.api.*;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.selection.Selection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;

import static tech.tablesaw.api.ColumnType.*;
import static tech.tablesaw.api.ColumnType.INTEGER;

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
	 * Entries with booster vaccinations for each day.
	 */
	private TreeMap<LocalDate, DoubleList> booster = null;

	@Inject
	public VaccinationFromData(SplittableRandom rnd, VaccinationConfigGroup vaccinationConfig) {
		super(rnd, vaccinationConfig);
	}

	@Override
	public void init(SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {
		if (vaccinationConfig.getFromFile() == null)
			throw new IllegalArgumentException("Vaccination file must be set, but was null");

		ageGroups = new ArrayList<>();
		entries = new TreeMap<>();
		booster = new TreeMap<>();

		ColumnType[] types = {LOCAL_DATE, STRING, STRING, INTEGER, INTEGER};

		ageGroups.add(new AgeGroup(12, 17));
		ageGroups.add(new AgeGroup(18, 59));
		ageGroups.add(new AgeGroup(60, MAX_AGE - 1));

		try {
			Table rkiData = Table.read().usingOptions(CsvReadOptions.builder(vaccinationConfig.getFromFile())
					.tableName("rkidata")
					.columnTypes(types));

			StringColumn locationColumn = rkiData.stringColumn("LandkreisId_Impfort");
			Selection location = locationColumn.isEqualTo("05315");
			Table table = rkiData.where(location);

			IntColumn vaccinationno = table.intColumn("Impfschutz");

			Selection firstVaccinations = vaccinationno.isEqualTo(1);
			Table filtered = table.where(firstVaccinations);

			mergeData(filtered, entries, "12-17", 54587.2, 0);
			mergeData(filtered, entries, "18-59", 676995, 1);
			mergeData(filtered, entries, "60+", 250986, 2);


			Selection boosterVaccinations = vaccinationno.isEqualTo(3);
			filtered = table.where(boosterVaccinations);

			mergeData(filtered, booster, "12-17", 54587.2, 0);
			mergeData(filtered, booster, "18-59", 676995, 1);
			mergeData(filtered, booster, "60+", 250986, 2);


		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		// TODO: fill age group and entries


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
		if (availableVaccinations >= 0)
			return super.handleVaccination(persons, reVaccination, availableVaccinations, date, iteration, now);

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
					!p.isRecentlyRecovered(iteration) && p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.no) {
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

	static Table filterData(Table table, String ageGroup, double population) {

		Selection selection = table.stringColumn("Altersgruppe").isEqualTo(ageGroup);
		Table data = table.where(selection);
		data.sortAscendingOn("Impfdatum");
		DoubleColumn cumsumAnzahl = data.intColumn("Anzahl").cumSum();
		DoubleColumn quota = cumsumAnzahl.divide(population);
		quota.setName(ageGroup);
		data.addColumns(quota);
		data.removeColumns("Impfschutz", "LandkreisId_Impfort", "Altersgruppe", "Anzahl");
		data.column("Impfdatum").setName("date");

		return data;
	}

	static void mergeData(Table filtered, TreeMap<LocalDate, DoubleList> entries, String ageGroup, double population, int i) {
		for (Row row : filterData(filtered, ageGroup, population)) {
			LocalDate date = row.getDate(0);
			DoubleList values = entries.computeIfAbsent(date, (k) -> new DoubleArrayList(new double[]{0, 0, 0}));

			values.set(i, row.getDouble(1));
		}
	}

	private static final class AgeGroup {

		private final int from;
		private final int to;

		private int size = 0;
		private int vaccinated = 0;

		private AgeGroup(int from, int to) {
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
