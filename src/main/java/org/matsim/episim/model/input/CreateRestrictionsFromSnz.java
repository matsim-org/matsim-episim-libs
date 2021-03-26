package org.matsim.episim.model.input;

import com.google.common.base.Joiner;
import com.google.common.io.Resources;
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
import java.util.stream.Collectors;

/**
 * Class for reading and analyzing snz activity data.
 */
public class CreateRestrictionsFromSnz implements ActivityParticipation {

	private static final Logger log = LogManager.getLogger(CreateRestrictionsFromSnz.class);
	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final Joiner JOIN = Joiner.on("\t");

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

		writeDataForCertainArea(tempFile, areaCodes, true, false);
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
	 * Read durations from a single input file for a day.
	 */
	static Object2DoubleMap<String> readDurations(File file, IntSet zipCodes) throws IOException {
		Object2DoubleMap<String> sums = new Object2DoubleOpenHashMap<>();

		try (BufferedReader reader = IOUtils.getBufferedReader(file.toString())) {
			CSVParser parse = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()
					.parse(reader);

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
		return new TreeMap<>(findInputFiles(input.toFile()).stream().parallel().collect(Collectors.toMap(
				file -> {
					String dateString = file.getName().split("_")[0];
					return LocalDate.parse(dateString, FMT);
				},
				file -> {
					try {
						return readDurations(file, zipCodes);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
		)));
	}

	/**
	 * Analyze data and write result to {@code outputFile}.
	 */
	public void writeDataForCertainArea(Path outputFile, IntSet zipCodes, boolean getPercentageResults, boolean setBaseIn2018) throws IOException {

		List<File> filesWithData = findInputFiles(inputFolder.toFile());
		Collections.sort(filesWithData);
		log.info("Searching for files in the folder: " + inputFolder);
		log.info("Amount of found files: " + filesWithData.size());

		Set<LocalDate> holidays = Resources.readLines(Resources.getResource("bankHolidays.txt"), StandardCharsets.UTF_8)
				.stream().map(LocalDate::parse).collect(Collectors.toSet());

		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile.toString());
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

			// set base from days in 2018
			if (setBaseIn2018) {
				Path baseFile = Paths.get("../shared-svn/projects/episim/data/Bewegungsdaten/Vergleich2017/");
				String weekdayBase = "20180131";
				String saturdayBase = "20180127";
				String sundayBase = "20180114";
				List<String> baseDays = Arrays.asList(weekdayBase, saturdayBase, sundayBase);

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

				// week days are compared to Sunday if they are holidays. NYE and Dec. 24th are compared to Saturday because some stores are still opened.
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
						row.add(String.valueOf(sums.getDouble(actType)));
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
				finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString+"_duration"));
			else
				finalPath = Path.of(outputFile.toString().replace("until", "until" + dateString));
			Files.move(outputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

			log.info("Write analyze of " + countingDays + " is writen to " + finalPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private enum Types {
		date, accomp, business, education, errands, home, leisure, shop_daily, shop_other, traveling, undefined, visit, work, notAtHome, notAtHomeExceptLeisureAndEdu, notAtHomeExceptEdu
	}

}
