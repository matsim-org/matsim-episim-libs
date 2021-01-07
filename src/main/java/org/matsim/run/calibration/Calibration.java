package org.matsim.run.calibration;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Calibration {

	public static void main(String[] args) {

//		String inputBerlinCases = "‪D:\\Arbeit\\Episim\\berlin-cases.csv";
		String inputBerlinCases = "berlin-cases.csv";
//		String inputBerlinCasesMeldedatum = "‪D:/Arbeit/Episim/berlin-cases-meldedatum.csv";
//		String inputBerlinHospital = "‪D:/Arbeit/Episim/berlin-hospital.csv";
		String inputBerlinCasesMeldedatum = "berlin-cases-meldedatum.csv";
		String inputBerlinHospital = "berlin-hospital.csv";
		String inputSz0Infections = "D:/Arbeit/Episim/sz0.infections.txt";

		LinkedList<BerlinCases> berlinCases = new LinkedList<>();
		LinkedList<BerlinCasesMeldedatum> berlinCasesMeldedatum = new LinkedList<>();
		LinkedList<BerlinHospital> berlinHospital = new LinkedList<>();
		LinkedList<BerlinSimulation> berlinSimulation = new LinkedList<>();

		readBerlinCases(inputBerlinCases, berlinCases);
		readBerlinCasesMeldedatum(inputBerlinCasesMeldedatum, berlinCasesMeldedatum);
		readBerlinHospital(inputBerlinHospital, berlinHospital);
		readSz0Infections(inputSz0Infections, berlinSimulation);

		int diffCases = compareAllCases(berlinCases, berlinSimulation);
		int diffCritical = compareIntensivmedizin(berlinHospital, berlinSimulation);
		int diffnSeriouslySick = comparenSeriouslySick(berlinHospital, berlinSimulation);
		System.out.println("All cases: " + diffCases);
		System.out.println("nCritical cases: " + diffCritical);
		System.out.println("SeriouslySick cases: " + diffnSeriouslySick);
		System.out.println("Done");

	}

	private static int comparenSeriouslySick(LinkedList<BerlinHospital> berlinHospitals, LinkedList<BerlinSimulation> berlinSimulations) {
		int cases = 0;
		int sameDays = 0;
		Iterator<BerlinHospital> iBH = berlinHospitals.iterator();
		Iterator<BerlinSimulation> iBS = berlinSimulations.iterator();
		BerlinHospital berlinHospital = iBH.next();
		BerlinSimulation berlinSimulation = iBS.next();

		while (iBH.hasNext() && iBS.hasNext()) {
			if (berlinHospital.date.toString().equals(berlinSimulation.date.toString())) {
				sameDays++;
				cases = cases - berlinHospital.stationaereBehandlung + berlinSimulation.nCritical + berlinSimulation.nSeriouslySick;
				berlinHospital = iBH.next();
				berlinSimulation = iBS.next();
			} else if (berlinHospital.date.before(berlinSimulation.date)) {
				berlinHospital = iBH.next();
			} else {
				berlinSimulation = iBS.next();
			}
		}
//		return cases/sameDays;
		return cases;
	}

	private static int compareIntensivmedizin(LinkedList<BerlinHospital> berlinHospitals, LinkedList<BerlinSimulation> berlinSimulations) {
		int cases = 0;
		int sameDays = 0;
		Iterator<BerlinHospital> iBH = berlinHospitals.iterator();
		Iterator<BerlinSimulation> iBS = berlinSimulations.iterator();
		BerlinHospital berlinHospital = iBH.next();
		BerlinSimulation berlinSimulation = iBS.next();

		while (iBH.hasNext() && iBS.hasNext()) {
			if (berlinHospital.date.toString().equals(berlinSimulation.date.toString())) {
				sameDays++;
				cases = cases - berlinHospital.intensivemedizin + berlinSimulation.nCritical;
				berlinHospital = iBH.next();
				berlinSimulation = iBS.next();
			} else if (berlinHospital.date.before(berlinSimulation.date)) {
				berlinHospital = iBH.next();
			} else {
				berlinSimulation = iBS.next();
			}
		}
//		return cases/sameDays;
		return cases;
	}

	private static int compareAllCases(LinkedList<BerlinCases> berlinCases, LinkedList<BerlinSimulation> berlinSimulations) {
		int sameDays = 0;
		int cases = 0;
		Iterator<BerlinCases> iBC = berlinCases.iterator();
		Iterator<BerlinSimulation> iBS = berlinSimulations.iterator();
		BerlinCases berlinCase = iBC.next();
		BerlinSimulation berlinSimulation = iBS.next();

		while (iBC.hasNext() && iBS.hasNext()) {
			if (berlinCase.date.toString().equals(berlinSimulation.date.toString())) {
				sameDays++;
				cases = cases - berlinCase.cases + berlinSimulation.nShowingSymptoms;
				berlinCase = iBC.next();
				berlinSimulation = iBS.next();
			} else if (berlinCase.date.before(berlinSimulation.date)) {
				berlinCase = iBC.next();
			} else {
				berlinSimulation = iBS.next();
			}
		}
//		return cases/sameDays;
		return cases;
	}

	private static void readSz0Infections(String inputSz0Infections, List<BerlinSimulation> berlinSimulation) {
		try (BufferedReader br = new BufferedReader(new FileReader(inputSz0Infections))) {
			String line;
			String headerLine = br.readLine();
			int nShowingSymptomsLastDay = 0;
			int nCriticalLastDay = 0;
			int nSeriouslySickLastDay = 0;
			while ((line = br.readLine()) != null) {
				String[] matrix = line.split("\\t");
				if (matrix[13].equals("Berlin")) {
					berlinSimulation.add(new BerlinSimulation(matrix[1], matrix[5], matrix[7], matrix[6], nShowingSymptomsLastDay, nCriticalLastDay, nSeriouslySickLastDay));
					nShowingSymptomsLastDay = Integer.parseInt(matrix[5]);
					nCriticalLastDay = Integer.parseInt(matrix[7]);
					nSeriouslySickLastDay = Integer.parseInt(matrix[6]);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void readBerlinCases(String inputBerlinCases, List<BerlinCases> berlinCases) {
		try (CSVReader csvReaderr = new CSVReaderBuilder(new FileReader(inputBerlinCases)).withSkipLines(1).build()) {
			List<String[]> r = csvReaderr.readAll();
			for (String[] line : r) {
				berlinCases.add(new BerlinCases(line[0], line[1], line[2], line[3]));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CsvException e) {
			e.printStackTrace();
		}
	}

	private static void readBerlinCasesMeldedatum(String inputBerlinCasesMeldedatum, List<BerlinCasesMeldedatum> berlinCasesMeldedatum) {
		try (CSVReader csvReaderr = new CSVReaderBuilder(new FileReader(inputBerlinCasesMeldedatum)).withSkipLines(1).build()) {
			List<String[]> r = csvReaderr.readAll();
			for (String[] line : r) {
				berlinCasesMeldedatum.add(new BerlinCasesMeldedatum(line[0], line[1], line[2], line[3]));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CsvException e) {
			e.printStackTrace();
		}
	}

	private static void readBerlinHospital(String inputBerlinHospital, List<BerlinHospital> berlinHospital) {
		try (CSVReader csvReaderr = new CSVReaderBuilder(new FileReader(inputBerlinHospital)).withSkipLines(1).build()) {
			List<String[]> r = csvReaderr.readAll();
			for (String[] line : r) {
				berlinHospital.add(new BerlinHospital(line[0], line[1], line[2], line[3], line[4]));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CsvException e) {
			e.printStackTrace();
		}
	}

	private static class BerlinCases {
		final private int year;
		final private int month;
		final private int day;
		final private int cases;
		final private Date date;

		BerlinCases(String year, String month, String day, String cases) {
			this.year = Integer.parseInt(year);
			this.month = Integer.parseInt(month);
			this.day = Integer.parseInt(day);
			this.cases = Integer.parseInt(cases);
			this.date = Date.valueOf(year + "-" + month + "-" + day);
		}
	}

	private static class BerlinCasesMeldedatum {
		final private int year;
		final private int month;
		final private int day;
		final private int cases;
		final private Date date;

		BerlinCasesMeldedatum(String year, String month, String day, String cases) {
			this.year = Integer.parseInt(year);
			this.month = Integer.parseInt(month);
			this.day = Integer.parseInt(day);
			this.cases = Integer.parseInt(cases);
			this.date = Date.valueOf(year + "-" + month + "-" + day);
		}
	}

	private static class BerlinHospital {
		final private int year;
		final private int month;
		final private int day;
		final private int gemeldeteFaelle;
		final private int stationaereBehandlung;
		final private int intensivemedizin;
		final private int gestorben;
		final private Date date;

		BerlinHospital(String datum, String gemeldeteFaelle, String stationaereBehandlung, String intensivemedizin, String gestorben) {
			if (gemeldeteFaelle.equals("")) {
				this.gemeldeteFaelle = 0;
			} else {
				this.gemeldeteFaelle = Integer.parseInt(gemeldeteFaelle);
			}
			this.stationaereBehandlung = Integer.parseInt(stationaereBehandlung);
			this.intensivemedizin = Integer.parseInt(intensivemedizin);
			if (gestorben.equals("")) {
				this.gestorben = 0;
			} else {
				this.gestorben = Integer.parseInt(gestorben);
			}
			String[] date = datum.split("\\.");
			this.year = Integer.parseInt(date[2]);
			this.month = Integer.parseInt(date[1]);
			this.day = Integer.parseInt(date[0]);
			this.date = Date.valueOf(year + "-" + month + "-" + day);
		}
	}

	private static class BerlinSimulation {
		final private int day;
		final private int nShowingSymptoms;
		final private int nCritical;
		final private int nSeriouslySick ;
		final private Date date;

		BerlinSimulation(String day, String nShowingSymtoms, String nCritical, String nSeriouslySick, int nShowingSymptomsLastDay, int nCriticalLastDay, int nSeriouslySickLastDay) {
			this.day = Integer.parseInt(day);
			this.nShowingSymptoms = Integer.parseInt(nShowingSymtoms) - nShowingSymptomsLastDay;
			this.nCritical = Integer.parseInt(nCritical) - nCriticalLastDay;
			this.nSeriouslySick = Integer.parseInt(nSeriouslySick) - nSeriouslySickLastDay;
			this.date = Date.valueOf(LocalDate.parse("2020-02-16").plusDays(Integer.parseInt(day) -1).toString());
		}
	}

}
