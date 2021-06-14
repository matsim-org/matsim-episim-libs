package org.matsim.episim.model.input;

import com.google.common.base.Joiner;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.episim.policy.FixedPolicy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Class for reading and analyzing snz activity data.
 */
public class CreateRestrictionsFromSnz implements ActivityParticipation {

	private static final Logger log = LogManager.getLogger(CreateRestrictionsFromSnz.class);
	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final DateTimeFormatter FMT_holiday = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final Joiner JOIN = Joiner.on("\t");
	private static final Joiner JOIN_LK = Joiner.on(";");

	/**
	 * Delegate to create the actual restriction
	 */
	private CreateRestrictionsFromCSV delegate;

	/**
	 * Input folder with data.
	 */
	private Path inputFolder;

	/**
	 * Area codes to extract.
	 */
	private IntSet areaCodes;

	public void setDelegate(CreateRestrictionsFromCSV delegate) {
		this.delegate = delegate;
	}

	@Override
	public ActivityParticipation setInput(Path input) {
		this.inputFolder = input;
		return this;
	}

	public void setAreaCodes(IntSet areaCodes) {
		this.areaCodes = areaCodes;
	}

	@Override
	public FixedPolicy.ConfigBuilder createPolicy() throws IOException {

		Path tempFile = Files.createTempFile("episim", "csv");
		tempFile.toFile().deleteOnExit();

		writeDataForCertainArea(tempFile, areaCodes, true, null);
		delegate.setInput(tempFile);

		return delegate.createPolicy();
	}

	/**
	 * This method searches all files with an certain name in a given folder.
	 */
	static List<File> findInputFiles(File inputFolder) {
		List<File> fileData = new ArrayList<>();

		for (File folder : Objects.requireNonNull(inputFolder.listFiles())) {
			if (folder.isDirectory()) {
				for (File file : Objects.requireNonNull(folder.listFiles())) {
					if (file.getName().contains("_zipCode.csv.gz"))
						fileData.add(file);
				}
			}
		}
		return fileData;
	}

	/**
	 * This method searches a files with personStat.
	 */
	static File findPersonStatInputFile(File inputFolder) {
		File personStatFile = null;
		for (File folder : Objects.requireNonNull(inputFolder.listFiles())) {
			if (folder.isDirectory()) {
				for (File file : Objects.requireNonNull(folder.listFiles())) {
					String string = file.getName().split("_")[0];
					if (file.getName().contains("_personStats.csv.gz") && string.contains("20210531")) {
						personStatFile = file;
						break;
					}
				}
			}
			if (personStatFile != null)
				break;
		}
		return personStatFile;
	}

	/**
	 * Searches the number of persons in all the different areas
	 * 
	 * @param zipCodesForAreas
	 * @param inputPulder
	 * @return
	 */
	static Map<String, Integer> getPersonsInThisZIPCodes(HashMap<String, IntSet> zipCodesForAreas, File inputPulder) {
		File fileWithPersonData = findPersonStatInputFile(inputPulder);
		Map<String, Integer> personsPerArea = new HashMap<>();
		for (String area : zipCodesForAreas.keySet()) {
			personsPerArea.put(area, 0);
		}
		CSVParser parse;
		try {
			parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
					.parse(IOUtils.getBufferedReader(fileWithPersonData.toString()));
			for (CSVRecord record : parse) {
				for (Entry<String, IntSet> certainArea : zipCodesForAreas.entrySet()) {
					if (!record.get("zipCode").contains("NULL")) {
						String nameArea = certainArea.getKey();
						int readZipCode = Integer.parseInt(record.get("zipCode"));
						if (certainArea.getValue().contains(readZipCode))
							personsPerArea.put(nameArea,
									personsPerArea.get(nameArea) + Integer.parseInt(record.get("nPersons")));
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return personsPerArea;
	}

	/**
	 * Searches the number of persons in all this area
	 * 
	 * @param zipCodes
	 * @param inputPulder
	 * @return
	 */
	static int getPersonsInThisZIPCode(IntSet zipCodes, File inputPulder) {
		File fileWithPersonData = findPersonStatInputFile(inputPulder);
		int nPersons = 0;
		CSVParser parse;
		try {
			parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
					.parse(IOUtils.getBufferedReader(fileWithPersonData.toString()));
			for (CSVRecord record : parse) {
				if (!record.get("zipCode").contains("NULL")) {
					int readZipCode = Integer.parseInt(record.get("zipCode"));
					if (zipCodes.contains(readZipCode)) {
						nPersons = nPersons + Integer.parseInt(record.get("nPersons"));
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return nPersons;
	}

	/**
	 * Read durations from a single input file for different areas for a day.
	 * 
	 * @param allSums
	 * @param areasWithBankHoliday
	 * @param anaylzedDaysPerAreaAndPeriod
	 * @param lkAssignemt
	 */
	static HashMap<String, Object2DoubleMap<String>> readDurations(File file, HashMap<String, IntSet> zipCodesForAreas,
			HashMap<String, Object2DoubleMap<String>> allSums, List<String> areasWithBankHoliday,
			HashMap<String, Integer> anaylzedDaysPerAreaAndPeriod, HashMap<String, Set<String>> lkAssignemt)
			throws IOException {

		if (allSums.isEmpty())
			for (String nameArea : zipCodesForAreas.keySet()) {
				Object2DoubleMap<String> sums = new Object2DoubleOpenHashMap<>();
				allSums.put(nameArea, sums);
			}
		if (anaylzedDaysPerAreaAndPeriod.isEmpty())
			for (String nameArea : zipCodesForAreas.keySet()) {
				anaylzedDaysPerAreaAndPeriod.put(nameArea, 0);
			}
		for (String area : anaylzedDaysPerAreaAndPeriod.keySet())
			if (areasWithBankHoliday == null || !areasWithBankHoliday.contains(getRelatedBundesland(area, lkAssignemt)))
				anaylzedDaysPerAreaAndPeriod.put(area, anaylzedDaysPerAreaAndPeriod.get(area) + 1);

		try (BufferedReader reader = IOUtils.getBufferedReader(file.toString())) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				for (Entry<String, IntSet> certainArea : zipCodesForAreas.entrySet()) {
					if (areasWithBankHoliday == null || !areasWithBankHoliday
							.contains(getRelatedBundesland(certainArea.getKey(), lkAssignemt))) {
						if (!record.get("zipCode").contains("NULL")) {
							String nameArea = certainArea.getKey();
							int zipCode = Integer.parseInt(record.get("zipCode"));
							if (certainArea.getValue().contains(zipCode)) {
								Object2DoubleMap<String> sums = allSums.get(nameArea);
								double duration = Double.parseDouble(record.get("durationSum"));
								String actType = record.get("actType");

								sums.mergeDouble(actType, duration, Double::sum);

								if (!actType.equals("home")) {

									sums.mergeDouble("notAtHome", duration, Double::sum);

									if (!actType.equals("education") && !actType.equals("leisure")) {
										sums.mergeDouble("notAtHomeExceptLeisureAndEdu", duration, Double::sum);
									}
									if (!actType.equals("education")) {
										sums.mergeDouble("notAtHomeExceptEdu", duration, Double::sum);
									}
								}
								allSums.put(nameArea, sums);
							}
						}
					}
				}
			}
		}
		return allSums;
	}

	/**
	 * Read durations from a single input file for one area for a day.
	 */
	static Object2DoubleMap<String> readDurations(File file, IntSet zipCodes) throws IOException {
		Object2DoubleMap<String> sums = new Object2DoubleOpenHashMap<>();

		try (BufferedReader reader = IOUtils.getBufferedReader(file.toString())) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				if (!record.get("zipCode").contains("NULL")) {
					int zipCode = Integer.parseInt(record.get("zipCode"));
					if (zipCodes.contains(zipCode)) {

						double duration = Double.parseDouble(record.get("durationSum"));
						String actType = record.get("actType");

						sums.mergeDouble(actType, duration, Double::sum);

						if (!actType.equals("home")) {

							sums.mergeDouble("notAtHome", duration, Double::sum);

							if (!actType.equals("education") && !actType.equals("leisure")) {
								sums.mergeDouble("notAtHomeExceptLeisureAndEdu", duration, Double::sum);
							}
							if (!actType.equals("education")) {
								sums.mergeDouble("notAtHomeExceptEdu", duration, Double::sum);
							}
						}
					}
				}
			}
		}

		return sums;
	}

	/**
	 * Read in all durations from input folder.
	 */
	static NavigableMap<LocalDate, Object2DoubleMap<String>> readAllDurations(Path input, IntSet zipCodes) {
		return new TreeMap<>(findInputFiles(input.toFile()).stream().parallel().collect(Collectors.toMap(file -> {
			String dateString = file.getName().split("_")[0];
			return LocalDate.parse(dateString, FMT);
		}, file -> {
			try {
				return readDurations(file, zipCodes);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		})));
	}

	/**
	 * Analyze data and write result to {@code outputFile}.
	 */
	public void writeDataForCertainArea(Path outputFile, IntSet zipCodes, boolean getPercentageResults,
			List<String> baseDays) throws IOException {

		List<File> filesWithData = findInputFiles(inputFolder.toFile());
		HashMap<String, Set<String>> lkAssignemt = createLKAssignmentToBL();

		int nPersons = 0;
		if (!getPercentageResults)
			nPersons = getPersonsInThisZIPCode(zipCodes, inputFolder.toFile());
		Collections.sort(filesWithData);
		log.info("Searching for files in the folder: " + inputFolder);
		log.info("Amount of found files: " + filesWithData.size());

		HashMap<String, Set<LocalDate>> allHolidays = readBankHolidays();
		Set<LocalDate> holidays = allHolidays
				.get(getRelatedBundesland(outputFile.getFileName().toString().split("Snz")[0], lkAssignemt));
		if (holidays == null)
			holidays = allHolidays.get("Germany");
		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile.toUri().toURL(), StandardCharsets.UTF_8, true);
		try {

			JOIN.appendTo(writer, Types.values());
			writer.write("\n");

			// base activity level for different days
			Object2DoubleMap<String> wd = new Object2DoubleOpenHashMap<>();
			Object2DoubleMap<String> sa = new Object2DoubleOpenHashMap<>();
			Object2DoubleMap<String> so = new Object2DoubleOpenHashMap<>();

			Map<DayOfWeek, Object2DoubleMap<String>> base = new EnumMap<>(DayOfWeek.class);

			// working days share the same base
			base.put(DayOfWeek.MONDAY, wd);
			base.put(DayOfWeek.TUESDAY, wd);
			base.put(DayOfWeek.WEDNESDAY, wd);
			base.put(DayOfWeek.THURSDAY, wd);
			base.put(DayOfWeek.FRIDAY, wd);
			base.put(DayOfWeek.SATURDAY, sa);
			base.put(DayOfWeek.SUNDAY, so);

			int countingDays = 1;

			// will contain the last parsed date
			String dateString = "";

			if (!baseDays.isEmpty()) {
				Path baseFile = null;
				if (baseDays.iterator().next().contains("2018"))
					baseFile = Paths.get("../shared-svn/projects/episim/data/Bewegungsdaten/Vergelich2017/");
				else if (baseDays.iterator().next().contains("2020"))
					baseFile = Paths.get("../shared-svn/projects/episim/data/Bewegungsdaten/");

				log.info("Setting weekday base from: " + baseFile);
				for (File folder : Objects.requireNonNull(baseFile.toFile().listFiles())) {
					if (folder.isDirectory()) {
						for (File file : Objects.requireNonNull(folder.listFiles())) {
							if (file.getName().contains("_zipCode.csv.gz")) {
								dateString = file.getName().split("_")[0];
								if (baseDays.contains(dateString)) {
									LocalDate date = LocalDate.parse(dateString, FMT);

									Object2DoubleMap<String> sums = readDurations(file, zipCodes);

									DayOfWeek day = date.getDayOfWeek();

									// set base
									if (base.get(day).isEmpty())
										base.get(day).putAll(sums);
								}
							}
						}
					}
				}
			}

			// Analyzes all files with the mobility data
			for (File file : filesWithData) {

				Object2DoubleMap<String> sums = readDurations(file, zipCodes);

				dateString = file.getName().split("_")[0];
				LocalDate date = LocalDate.parse(dateString, FMT);

				DayOfWeek day = date.getDayOfWeek();

				// week days are compared to Sunday if they are holidays. NYE and Dec. 24th are
				// compared to Saturday because some stores are still opened.
				if (day != DayOfWeek.SUNDAY) {
					if (holidays.contains(date))
						day = DayOfWeek.SUNDAY;
					if (dateString.contains("20171224") || dateString.contains("20201224")
							|| dateString.contains("20201231"))
						day = DayOfWeek.SATURDAY;
				}
				// set base
				if (base.get(day).isEmpty())
					base.get(day).putAll(sums);

				List<String> row = new ArrayList<>();
				row.add(dateString);

				for (int i = 1; i < Types.values().length; i++) {

					String actType = Types.values()[i].toString();
					if (getPercentageResults)
						row.add(String.valueOf(
								Math.round((sums.getDouble(actType) / base.get(day).getDouble(actType) - 1) * 100)));
					else
						row.add(String.valueOf(round2Decimals(sums.getDouble(actType) / nPersons / 3600)));
				}

				JOIN.appendTo(writer, row);
				writer.write("\n");

				if (countingDays == 1 || countingDays % 5 == 0)
					log.info("Finished day " + countingDays);

				countingDays++;
			}
			writer.close();
			Path finalPath = null;
			if (!getPercentageResults)
				finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_duration"));
			else {
				if (baseDays.isEmpty())
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString));
				else if (baseDays.iterator().next().contains("2018"))
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_base2018"));
				else if (baseDays.iterator().next().contains("202009"))
					finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString + "_baseSep20"));
			}
			Files.move(outputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

			log.info("Write analyze of " + countingDays + " is writen to " + finalPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Analyze data and write result to {@code outputFile}. The result contains data
	 * for all Bundeslaender.
	 * @param outputOption 
	 */
	public void writeBundeslandDataForPublic(Path outputFile, String selectedOutputOption) throws IOException {

		List<File> filesWithData = findInputFiles(inputFolder.toFile());

		Path thisOutputFile = outputFile.resolve("mobilityData_Overview2BL.csv");

		Collections.sort(filesWithData);
		log.info("Searching for files in the folder: " + inputFolder);
		log.info("Amount of found files: " + filesWithData.size());

		HashMap<String, Set<LocalDate>> allHolidays = readBankHolidays();

		BufferedWriter writer = IOUtils.getBufferedWriter(thisOutputFile.toUri().toURL(), StandardCharsets.UTF_8, true);
		try {
			String[] header = new String[] { "date", "BundeslandID", "outOfHomeDuration",
					"percentageChangeComparedToBeforeCorona" };
			JOIN.appendTo(writer, header);
			writer.write("\n");

			HashMap<String, IntSet> zipCodesBL = findZIPCodesForBundeslaender();

			readAndWriteResultsOfAllDays(selectedOutputOption, filesWithData, allHolidays, writer,
					zipCodesBL);
			writer.close();
			log.info("Write analyze of " + filesWithData.size() + " is writen to " + thisOutputFile);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Analyze data and write result to {@code outputFile}. The result contains data
	 * for all Bundeslaender.
	 * 
	 * @param selectedOutputOptions
	 */
	public void writeLandkreisDataForPublic(Path outputFile, String selectedOutputOption) throws IOException {

		List<File> filesWithData = findInputFiles(inputFolder.toFile());

		Path thisOutputFile = outputFile.resolve("mobilityData_OverviewLK_new.csv");

		HashMap<String, Set<LocalDate>> allHolidays = readBankHolidays();

		Collections.sort(filesWithData);
		log.info("Searching for files in the folder: " + inputFolder);
		log.info("Amount of found files: " + filesWithData.size());

		BufferedWriter writer = IOUtils.getBufferedWriter(thisOutputFile.toUri().toURL(), StandardCharsets.UTF_8, true);
		try {
			String[] header = new String[] { "date", "Landkreis", "outOfHomeDuration",
					"percentageChangeComparedToBeforeCorona" };
			JOIN_LK.appendTo(writer, header);
			writer.write("\n");

			HashMap<String, IntSet> zipCodesLK = findZIPCodesForLandkreise();

			readAndWriteResultsOfAllDays(selectedOutputOption, filesWithData, allHolidays, writer,
					zipCodesLK);

			writer.close();
			Path finalPath = null;
			String analysedAreas = null;
			if (selectedOutputOption.contains("LK"))
				analysedAreas = "LK";
			else if (selectedOutputOption.contains("BL"))
				analysedAreas = "BL";
			if (selectedOutputOption.contains("daily"))
				finalPath = Path.of(thisOutputFile.toString().replace(analysedAreas, analysedAreas+"_daily"));
			else if (selectedOutputOption.contains("weekly"))
				finalPath = Path.of(thisOutputFile.toString().replace(analysedAreas, analysedAreas+"_weekly"));
			else if (selectedOutputOption.contains("weekdays"))
				finalPath = Path.of(thisOutputFile.toString().replace(analysedAreas, analysedAreas+"_weekdays"));
			else if (selectedOutputOption.contains("weekends"))
				finalPath = Path.of(thisOutputFile.toString().replace(analysedAreas, analysedAreas+"_weekends"));

			Files.move(thisOutputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

			log.info("Write analyze of " + filesWithData.size() + " is writen to " + finalPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** Reads the data files and writes the output depending on the selected output option.
	 * @param selectedOutputOptions
	 * @param filesWithData
	 * @param allHolidays
	 * @param writer
	 * @param zipCodesAreas
	 * @throws IOException
	 */
	private void readAndWriteResultsOfAllDays(String selectedOutputOptions, List<File> filesWithData,
			HashMap<String, Set<LocalDate>> allHolidays, BufferedWriter writer, HashMap<String, IntSet> zipCodesAreas)
			throws IOException {

		Map<DayOfWeek, Map<String, Object2DoubleMap<String>>> base = new EnumMap<>(DayOfWeek.class);
		Map<String, Integer> personsPerArea = new HashMap<>();
		HashMap<String, Set<String>> lkAssignemt = createLKAssignmentToBL();

		int countingDays = 1;
		HashMap<String, Integer> anaylzedDaysPerAreaAndPeriod = new HashMap<String, Integer>();
		boolean writeOutput = false;
		// will contain the last parsed date
		String dateString = "";
		HashMap<String, Object2DoubleMap<String>> allSums = new HashMap<String, Object2DoubleMap<String>>();
		// Analyzes all files with the mobility data
		for (File file : filesWithData) {

			dateString = file.getName().split("_")[0];
			LocalDate date = LocalDate.parse(dateString, FMT);
			DayOfWeek day = date.getDayOfWeek();

			List<String> areasWithBankHoliday = new ArrayList<>();
			getAreasWithBankHoliday(areasWithBankHoliday, allHolidays, date);

			if (selectedOutputOptions.contains("daily")) {
				readDurations(file, zipCodesAreas, allSums, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
				writeOutput = true;
			} else if (selectedOutputOptions.contains("weekly")) {
				readDurations(file, zipCodesAreas, allSums, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
				if (day.equals(DayOfWeek.SUNDAY))
					writeOutput = true;
			} else if (selectedOutputOptions.contains("weekdays")) {
				if (!day.equals(DayOfWeek.SATURDAY) && !day.equals(DayOfWeek.SUNDAY)) {
					readDurations(file, zipCodesAreas, allSums, areasWithBankHoliday, anaylzedDaysPerAreaAndPeriod,
							lkAssignemt);
					if (day.equals(DayOfWeek.FRIDAY))
						writeOutput = true;
				}
			} else if (selectedOutputOptions.contains("weekends")) {
				if (day.equals(DayOfWeek.SATURDAY) || day.equals(DayOfWeek.SUNDAY)) {
					readDurations(file, zipCodesAreas, allSums, null, anaylzedDaysPerAreaAndPeriod, lkAssignemt);
					if (day.equals(DayOfWeek.SUNDAY))
						writeOutput = true;
				}
			}

			if (writeOutput) {
				writeOutput = false;
				for (String nameOfArea : allSums.keySet()) {
					dateString = file.getName().split("_")[0];
					day = date.getDayOfWeek();

					// mean sum of the analyzed days
					Object2DoubleMap<String> sums = allSums.get(nameOfArea);
					for (String activity : sums.keySet())
						sums.put(activity, sums.getDouble(activity) / anaylzedDaysPerAreaAndPeriod.get(nameOfArea));

					// set base
					if (day != DayOfWeek.SUNDAY)
						if (areasWithBankHoliday.contains(nameOfArea))
							day = DayOfWeek.SUNDAY;

					if (base.containsKey(day)) {
						if (!base.get(day).containsKey(nameOfArea))
							base.get(day).put(nameOfArea, sums);
					} else {
						HashMap<String, Object2DoubleMap<String>> wd = new HashMap<String, Object2DoubleMap<String>>();
						HashMap<String, Object2DoubleMap<String>> sa = new HashMap<String, Object2DoubleMap<String>>();
						HashMap<String, Object2DoubleMap<String>> so = new HashMap<String, Object2DoubleMap<String>>();
						base.put(DayOfWeek.MONDAY, wd);
						base.put(DayOfWeek.TUESDAY, wd);
						base.put(DayOfWeek.WEDNESDAY, wd);
						base.put(DayOfWeek.THURSDAY, wd);
						base.put(DayOfWeek.FRIDAY, wd);
						base.put(DayOfWeek.SATURDAY, sa);
						base.put(DayOfWeek.SUNDAY, so);

						base.get(day).put(nameOfArea, sums);
					}

					// add the number of persons in this area from data
					if (personsPerArea.isEmpty())
						personsPerArea = getPersonsInThisZIPCodes(zipCodesAreas, inputFolder.toFile());

					List<String> row = new ArrayList<>();
					row.add(dateString);
					row.add(nameOfArea);
					row.add(String.valueOf(
							round2Decimals(sums.getDouble("notAtHome") / personsPerArea.get(nameOfArea) / 3600)));
					row.add(String.valueOf(Math.round(
							(sums.getDouble("notAtHome") / base.get(day).get(nameOfArea).getDouble("notAtHome") - 1)
									* 100)));
					JOIN_LK.appendTo(writer, row);
					writer.write("\n");

				}

				anaylzedDaysPerAreaAndPeriod.clear();
				allSums.clear();
			}
			if (countingDays % 7 == 0)
				log.info("Finished week " + countingDays / 7);
			countingDays++;
		}
	}

	/**
	 * Creates an assignment of the landkreise to the Bundeslaender
	 * 
	 * @return
	 * @throws IOException
	 */
	private HashMap<String, Set<String>> createLKAssignmentToBL() throws IOException {

		String zipCodeFile = "../shared-svn/projects/episim/data/PLZ/zuordnung_plz_ort_landkreis.csv";
		HashMap<String, Set<String>> lKAssignment = new HashMap<String, Set<String>>();

		try (BufferedReader reader = IOUtils.getBufferedReader(zipCodeFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				String nameLK = record.get("landkreis");
				if (nameLK.isEmpty())
					nameLK = record.get("ort");
				if (lKAssignment.containsKey(record.get("bundesland"))) {
					if (!lKAssignment.get(record.get("bundesland")).contains(nameLK))
						lKAssignment.get(record.get("bundesland")).add(String.valueOf(nameLK));
				} else
					lKAssignment.put(record.get("bundesland"), new HashSet<>(Arrays.asList(nameLK)));
			}
		}
		return lKAssignment;
	}

	/**
	 * Returns the Bundesland where is Landkreis is located
	 * 
	 * @param area
	 * @param lKAssignment
	 * @return
	 */
	private static String getRelatedBundesland(String area, HashMap<String, Set<String>> lKAssignment) {

		if (lKAssignment.containsKey(area))
			return area;

		for (String bundesland : lKAssignment.keySet())
			if (lKAssignment.get(bundesland).contains(area))
				return bundesland;
		return null;
	}

	/**
	 * Assigns the zip codes to a Bundesland
	 * 
	 * @return
	 * @throws IOException
	 */
	private HashMap<String, IntSet> findZIPCodesForBundeslaender() throws IOException {

		String zipCodeFile = "../shared-svn/projects/episim/data/PLZ/OpenGeoDB_bundesland_plz_ort_de.csv";
		HashMap<String, IntSet> zipCodesBL = new HashMap<String, IntSet>();
		zipCodesBL.put("Deutschland", new IntOpenHashSet());

		try (BufferedReader reader = IOUtils.getBufferedReader(zipCodeFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				if (zipCodesBL.containsKey(record.get("BL"))) {
					zipCodesBL.get(record.get("BL")).add(Integer.parseInt(record.get("PLZ")));
					zipCodesBL.get("Deutschland").add(Integer.parseInt(record.get("PLZ")));

				} else {
					zipCodesBL.put(record.get("BL"), new IntOpenHashSet(List.of(Integer.parseInt(record.get("PLZ")))));
					zipCodesBL.get("Deutschland").add(Integer.parseInt(record.get("PLZ")));
				}
			}
		}
		return zipCodesBL;
	}

	/**
	 * Assigns the zip codes to Landkreise
	 * 
	 * @return
	 * @throws IOException
	 */
	private HashMap<String, IntSet> findZIPCodesForLandkreise() throws IOException {

		String zipCodeFile = "../shared-svn/projects/episim/data/PLZ/zuordnung_plz_ort_landkreis.csv";
		HashMap<String, IntSet> zipCodesLK = new HashMap<String, IntSet>();
		zipCodesLK.put("Deutschland", new IntOpenHashSet());

		try (BufferedReader reader = IOUtils.getBufferedReader(zipCodeFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				String nameLK = record.get("landkreis");
				if (nameLK.isEmpty())
					nameLK = record.get("ort");
				if (zipCodesLK.containsKey(nameLK)) {
					zipCodesLK.get(nameLK).add(Integer.parseInt(record.get("plz")));
					zipCodesLK.get("Deutschland").add(Integer.parseInt(record.get("plz")));

				} else {
					zipCodesLK.put(nameLK, new IntOpenHashSet(List.of(Integer.parseInt(record.get("plz")))));
					zipCodesLK.get("Deutschland").add(Integer.parseInt(record.get("plz")));
				}
			}
		}
		return zipCodesLK;
	}

	/**
	 * Finds the zipCodes for any Area. If more than one area contains the input
	 * String an exception is thrown.
	 * 
	 * @param anyArea
	 * @return
	 * @throws IOException
	 */
	public HashMap<String, IntSet> findZipCodesForAnyArea(String anyArea) throws IOException {
		String zipCodeFile = "../shared-svn/projects/episim/data/PLZ/zuordnung_plz_ort_landkreis.csv";
		HashMap<String, IntSet> zipCodes = new HashMap<String, IntSet>();
		List<String> possibleAreas = new ArrayList<String>();

		try (BufferedReader reader = IOUtils.getBufferedReader(zipCodeFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				String nameLK = record.get("landkreis");
				if (nameLK.isEmpty())
					nameLK = record.get("ort");

				if (nameLK.contains(anyArea)) {

					if (!possibleAreas.contains(nameLK))
						possibleAreas.add(nameLK);
					if (zipCodes.containsKey(nameLK)) {
						zipCodes.get(nameLK).add(Integer.parseInt(record.get("plz")));
					} else
						zipCodes.put(nameLK, new IntOpenHashSet(List.of(Integer.parseInt(record.get("plz")))));
				}
			}
		}
		if (possibleAreas.size() > 1)
			if (possibleAreas.contains(anyArea)) {
				IntSet finalZipCodes = zipCodes.get(anyArea);
				zipCodes.clear();
				zipCodes.put(anyArea, finalZipCodes);
			} else
				throw new RuntimeException(
						"For the choosen area " + anyArea + " more the following districts are possible: "
								+ possibleAreas.toString() + " Choose one and start again.");
		return zipCodes;
	}

	/**
	 * Reads all bank holidays for Germany and each Bundesland
	 * 
	 * @return
	 * @throws IOException
	 */
	private HashMap<String, Set<LocalDate>> readBankHolidays() throws IOException {

		String bankHolidayFile = "src/main/resources/bankHolidays.csv";
		HashMap<String, Set<LocalDate>> bankHolidaysForBL = new HashMap<String, Set<LocalDate>>();

		try (BufferedReader reader = IOUtils.getBufferedReader(bankHolidayFile)) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(reader);

			for (CSVRecord record : parse) {
				String[] BlWithBankHolidy = record.get("Bundesland").split(";");
				LocalDate date = LocalDate.parse(record.get("bankHoliday"), FMT_holiday);
				for (String bundesland : BlWithBankHolidy) {

					if (bankHolidaysForBL.containsKey(bundesland)) {
						if (!bankHolidaysForBL.get(bundesland).contains(date))
							bankHolidaysForBL.get(bundesland).add(date);
					} else
						bankHolidaysForBL.put(bundesland, new HashSet<LocalDate>(Arrays.asList(date)));
				}
			}
			Set<LocalDate> germanyBankHolidays = bankHolidaysForBL.get("Germany");

			for (String bundesland : bankHolidaysForBL.keySet())
				if (!bundesland.contains("Germany"))
					bankHolidaysForBL.get(bundesland).addAll(germanyBankHolidays);
		}
		return bankHolidaysForBL;
	}

	/**
	 * Finds all bundeslaender with bank holiday on this day
	 * 
	 * @param areasWithBankHoliday
	 * @param allHolidays
	 * @param date
	 */
	private void getAreasWithBankHoliday(List<String> areasWithBankHoliday, HashMap<String, Set<LocalDate>> allHolidays,
			LocalDate date) {

		for (String certainArea : allHolidays.keySet()) {
			if (allHolidays.get(certainArea).contains(date))
				areasWithBankHoliday.add(certainArea);
		}
	}

	/**
	 * Rounds the number 2 places after the comma
	 * 
	 */
	static double round2Decimals(double number) {
		return Math.round(number * 100) * 0.01;
	}

	private enum Types {
		date, accomp, business, education, errands, home, leisure, shop_daily, shop_other, traveling, undefined, visit,
		work, notAtHome, notAtHomeExceptLeisureAndEdu, notAtHomeExceptEdu
	}

}
