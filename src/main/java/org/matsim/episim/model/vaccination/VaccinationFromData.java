package org.matsim.episim.model.vaccination;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static tech.tablesaw.api.ColumnType.*;

/**
 * Read vaccination from file for each age group.
 */
public class VaccinationFromData extends VaccinationByAge {

	private static final Logger log = LogManager.getLogger(org.matsim.episim.model.vaccination.VaccinationFromData.class);

	/**
	 * All known age groups.
	 */
	private List<VaccinationFromData.AgeGroup> ageGroups = null;

	/**
	 * Entries for each day.
	 */
	private TreeMap<LocalDate, DoubleList> entries = null;

	/**
	 * Entries with booster vaccinations for each day.
	 */
	private TreeMap<LocalDate, DoubleList> booster = null;

	/**
	 * Refresher vaccination for each day.
	 */
	private TreeMap<LocalDate, DoubleList> refresher = null;

	/**
	 * Config for this class.
	 */
	private final VaccinationFromData.Config config;

	/**
	 * Fallback to random vaccinations.
	 */
	private final RandomVaccination random;

	@Inject
	public VaccinationFromData(SplittableRandom rnd, VaccinationConfigGroup vaccinationConfig, org.matsim.episim.model.vaccination.VaccinationFromData.Config config) {
		super(rnd, vaccinationConfig);
		this.config = config;
		this.random = new RandomVaccination(rnd, vaccinationConfig);
	}

	@Override
	public void init(SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {
		if (vaccinationConfig.getFromFile() == null)
			throw new IllegalArgumentException("Vaccination file must be set, but was null");

		ageGroups = new ArrayList<>();
		entries = new TreeMap<>();
		booster = new TreeMap<>();
		refresher = new TreeMap<>();

		ColumnType[] types = {LOCAL_DATE, STRING, STRING, INTEGER, INTEGER};

		ageGroups.add(new VaccinationFromData.AgeGroup(5, 11));
		ageGroups.add(new VaccinationFromData.AgeGroup(12, 17));
		ageGroups.add(new VaccinationFromData.AgeGroup(18, 59));
		ageGroups.add(new VaccinationFromData.AgeGroup(60, MAX_AGE - 1));

		try {
			Table rkiData = Table.read().usingOptions(CsvReadOptions.builder(vaccinationConfig.getFromFile())
					.tableName("rkidata")
					.columnTypes(types));

			LocalDate endDate = rkiData.dateColumn("Impfdatum").max();

			StringColumn locationColumn = rkiData.stringColumn("LandkreisId_Impfort");
			Selection location = locationColumn.isEqualTo(config.locationId);
			Table table = rkiData.where(location);

			IntColumn vaccinationno = table.intColumn("Impfschutz");

			Selection firstVaccinations = vaccinationno.isEqualTo(1);
			Table filtered = table.where(firstVaccinations);

			mergeData(filtered, entries, endDate, "05-11", config.groups.getDouble("05-11"), 0);
			mergeData(filtered, entries, endDate, "12-17", config.groups.getDouble("12-17"), 1);
			mergeData(filtered, entries, endDate, "18-59", config.groups.getDouble("18-59"), 2);
			mergeData(filtered, entries, endDate, "60+", config.groups.getDouble("60+"), 3);


			Selection boosterVaccinations = vaccinationno.isEqualTo(3);
			filtered = table.where(boosterVaccinations);

			mergeData(filtered, booster, endDate, "05-11", config.groups.getDouble("05-11"), 0);
			mergeData(filtered, booster, endDate, "12-17", config.groups.getDouble("12-17"), 1);
			mergeData(filtered, booster, endDate, "18-59", config.groups.getDouble("18-59"), 2);
			mergeData(filtered, booster, endDate, "60+", config.groups.getDouble("60+"), 3);


			Selection refresherVaccinations = vaccinationno.isEqualTo(4);
			filtered = table.where(refresherVaccinations);

			mergeData(filtered, refresher, endDate, "05-11", config.groups.getDouble("05-11"), 0);
			mergeData(filtered, refresher, endDate, "12-17", config.groups.getDouble("12-17"), 1);
			mergeData(filtered, refresher, endDate, "18-59", config.groups.getDouble("18-59"), 2);
			mergeData(filtered, refresher, endDate, "60+", config.groups.getDouble("60+"), 3);


		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		// collect population sizes
		for (EpisimPerson p : persons.values()) {
			Integer ag = findAgeGroup(p.getAge());
			if (ag != null)
				ageGroups.get(ag).size++;
		}

		log.info("Using age-groups: {}", ageGroups);
	}

	@Nullable
	private Integer findAgeGroup(int age) {

		for (int i = 0; i < ageGroups.size(); i++) {
			AgeGroup ag = ageGroups.get(i);
			if (age >= ag.from && age <= ag.to)
				return i;
		}

		return null;
	}

	@Override
	public int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, LocalDate date, int iteration, double now) {

		// If available vaccination is given, data will be ignored and vaccination by age executed
		if (availableVaccinations >= 0)
			return random.handleVaccination(persons, reVaccination, availableVaccinations, date, iteration, now);

		// booster and refresher shot
		// TODO: upstream API and config would not an update to better differentiate
		if (reVaccination)
			return vaccinate(persons, booster, 2, date, iteration, now) + vaccinate(persons, refresher,3, date, iteration, now);
		else
			return vaccinate(persons, entries, 1, date, iteration, now);

	}

	/**
	 * Vaccinate all persons with the nth vaccination
	 * @param vaccinationN the nth vaccination
	 */
	private int vaccinate(Map<Id<Person>, EpisimPerson> persons, TreeMap<LocalDate, DoubleList> entries, int vaccinationN, LocalDate date, int iteration, double now) {

		DoubleList entry = EpisimUtils.findValidEntry(entries, null, date);

		// No vaccinations today
		if (entry == null)
			return 0;

		// reset count
		for (VaccinationFromData.AgeGroup ag : ageGroups) {
			ag.vaccinated = 0;
		}

		final List<EpisimPerson>[] perAge = new List[ageGroups.size()];

		for (int i = 0; i < ageGroups.size(); i++)
			perAge[i] = new ArrayList<>();

		for (EpisimPerson p : persons.values()) {

			Integer idx = findAgeGroup(p.getAge());
			if (idx == null) continue;

			VaccinationFromData.AgeGroup ag = ageGroups.get(idx);

			if (p.getNumVaccinations() >= vaccinationN) {
				ag.vaccinated++;
				continue;
			}

			if (p.isVaccinable() && p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible &&
					//!p.isRecentlyRecovered(iteration) &&
					(p.getNumVaccinations() == vaccinationN - 1) &&
					(vaccinationN > 1 ? p.daysSince(EpisimPerson.VaccinationStatus.yes, iteration) >= vaccinationConfig.getParams(p.getVaccinationType(0)).getBoostWaitPeriod() : true)
			) {
				perAge[idx].add(p);
			}
		}

		Map<VaccinationType, Double> prob = vaccinationConfig.getVaccinationTypeProb(date);

		int totalVaccinations = 0;

		for (int ii = 0; ii < ageGroups.size(); ii++) {

			VaccinationFromData.AgeGroup ag = ageGroups.get(ii);
			double share = entry.getDouble(ii);

			// vaccinations left per age group
			int vaccinationsLeft = (int) ((ag.size * share) - ag.vaccinated);

			List<EpisimPerson> candidates = perAge[ii];

			// list is shuffled to avoid eventual bias
			if (candidates.size() != 0)
				Collections.shuffle(perAge[ii], new Random(EpisimUtils.getSeed(rnd)));

			int n = Math.min(candidates.size(), vaccinationsLeft);
			for (int i = 0; i < n; i++) {
				EpisimPerson person = candidates.get(i);
				vaccinate(person, iteration, vaccinationN > 1 ? VaccinationType.mRNA : VaccinationModel.chooseVaccinationType(prob, rnd));
				vaccinationsLeft--;
				totalVaccinations++;
			}
		}


		return totalVaccinations;
	}

	static Table filterData(Table table, LocalDate endDate, String ageGroup, double population) {

		Selection selection = table.stringColumn("Altersgruppe").isEqualTo(ageGroup);
		Table data = table.where(selection);

		DateColumn dateColumn = data.dateColumn("Impfdatum");

		LocalDate startDate = dateColumn.min();

		// This column is empty
		if (startDate == null) {
			return Table.create();
		}

		List<LocalDate> dates = startDate.datesUntil(endDate.plusDays(1)).collect(Collectors.toList());

		for (LocalDate date : dates) {
			Selection thisDateSelection = dateColumn.isEqualTo(date);
			Table thisDateTable = data.where(thisDateSelection);
			if (thisDateTable.rowCount() == 0) {
				Table singlerow = data.emptyCopy(1);
				for (Row row : singlerow) {
					row.setDate("Impfdatum", date);
					row.setString("Altersgruppe", ageGroup);
					row.setInt("Anzahl", 0);
				}
				data.append(singlerow);
			}
		}
		data = data.sortAscendingOn("Impfdatum");
		DoubleColumn cumsumAnzahl = data.intColumn("Anzahl").cumSum();
		DoubleColumn quota = cumsumAnzahl.divide(population);
		quota.setName(ageGroup);
		data.addColumns(quota);
		data.removeColumns("Impfschutz", "LandkreisId_Impfort", "Altersgruppe", "Anzahl");
		data.column("Impfdatum").setName("date");

		return data;
	}

	static void mergeData(Table filtered, TreeMap<LocalDate, DoubleList> entries, LocalDate endDate, String ageGroup, double population, int i) {
		for (Row row : filterData(filtered, endDate, ageGroup, population)) {
			LocalDate date = row.getDate(0);
			DoubleList values = entries.computeIfAbsent(date, (k) -> new DoubleArrayList(new double[]{0, 0, 0, 0}));

			values.set(i, row.getDouble(1));
		}
	}

	/**
	 * Create a new configuration, that needs to be bound with guice.
	 */
	public static VaccinationFromData.Config newConfig(String locationId) {
		return new VaccinationFromData.Config(locationId);
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

	/**
	 * Holds config options for this class.
	 */
	public static final class Config {

		final String locationId;
		final Object2DoubleMap<String> groups = new Object2DoubleLinkedOpenHashMap<>();

		public Config(String locationId) {
			this.locationId = locationId;
		}

		/**
		 * Define an age group and reference size in the population.
		 *
		 * @param ageGroup      string that must be exactly like in the data
		 * @param referenceSize unscaled reference size of this age group.
		 */
		public VaccinationFromData.Config withAgeGroup(String ageGroup, double referenceSize) {
			groups.put(ageGroup, referenceSize);
			return this;
		}
	}
}
