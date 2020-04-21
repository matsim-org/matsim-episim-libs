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

import com.typesafe.config.ConfigRenderOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.reporting.EpisimWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reporting and persisting of metrics, like number of infected people etc.
 */
public final class EpisimReporting {

	private static final Logger log = LogManager.getLogger(EpisimReporting.class);
	private static final AtomicInteger specificInfectionsCnt = new AtomicInteger(300);

	private final EpisimWriter writer;

	private final BufferedWriter infectionReport;
	private final BufferedWriter infectionEvents;
	private final BufferedWriter restrictionReport;

	/**
	 * Number format for logging output. Not static because not thread-safe.
	 */
	private final NumberFormat decimalFormat = DecimalFormat.getInstance(Locale.GERMAN);
	private final double sampleSize;

	EpisimReporting(Config config, EpisimWriter writer) {
		String base = config.controler().getOutputDirectory();

		if (!Files.exists(Paths.get(base))) {
			try {
				Files.createDirectories(Paths.get(base));
			} catch (IOException e) {
				log.error("Could not create output directory", e);
				throw new UncheckedIOException(e);
			}
		}

		EpisimConfigGroup episimConfigGroup = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		this.writer = writer;

		infectionReport = EpisimWriter.prepare(base + "infections.txt", InfectionsWriterFields.class);
		infectionEvents = EpisimWriter.prepare(base + "infectionEvents.txt", InfectionEventsWriterFields.class);
		restrictionReport = EpisimWriter.prepare(base + "restrictions.txt",
				"day", "", episimConfigGroup.createInitialRestrictions().keySet().toArray());

		sampleSize = episimConfigGroup.getSampleSize();

		try {
			Files.writeString(Paths.get(base + "policy.conf"),
					episimConfigGroup.getPolicy().root().render(ConfigRenderOptions.defaults()
							.setOriginComments(false)
							.setJson(false)));
		} catch (IOException e) {
			log.error("Could not write policy config", e);
		}
	}

	/**
	 * Creates infections reports for the day. Grouped by district, but always containing a "total" entry.
	 */
	Map<String, InfectionReport> createReports(Collection<EpisimPerson> persons, int iteration) {

		Map<String, InfectionReport> reports = new LinkedHashMap<>();
		InfectionReport report = new InfectionReport("total", EpisimUtils.getCorrectedTime(0., iteration), iteration);
		reports.put("total", report);

		for (EpisimPerson person : persons) {
			String districtName = (String) person.getAttributes().getAttribute("district");

			// Also aggregate by district
			InfectionReport district = reports.computeIfAbsent(districtName == null ? "unknown"
					: districtName, name -> new InfectionReport(name, report.time, report.day));
			switch (person.getDiseaseStatus()) {
				case susceptible:
					report.nSusceptible++;
					district.nSusceptible++;
					break;
				case infectedButNotContagious:
					report.nInfectedButNotContagious++;
					district.nInfectedButNotContagious++;
					report.nTotalInfected++;
					district.nTotalInfected++;
					break;
				case contagious:
					report.nContagious++;
					district.nContagious++;
					report.nTotalInfected++;
					district.nTotalInfected++;
					break;
				case showingSymptoms:
					report.nShowingSymptoms++;
					district.nShowingSymptoms++;
					report.nTotalInfected++;
					district.nTotalInfected++;
					break;
				case seriouslySick:
					report.nSeriouslySick++;
					district.nSeriouslySick++;
					report.nTotalInfected++;
					district.nTotalInfected++;
					break;
				case critical:
					report.nCritical++;
					district.nCritical++;
					report.nTotalInfected++;
					district.nTotalInfected++;
					break;
				case recovered:
					report.nRecovered++;
					district.nRecovered++;
					break;
				default:
					throw new IllegalStateException("Unexpected value: " + person.getDiseaseStatus());
			}
			switch (person.getQuarantineStatus()) {
				// For now there is no separation in the report between full and home
				case atHome:
				case full:
					report.nInQuarantine++;
					district.nInQuarantine++;
					break;
				case no:
					break;
				default:
					throw new IllegalStateException("Unexpected value: " + person.getQuarantineStatus());
			}
		}

		reports.forEach((k, v) -> v.scale(1 / sampleSize));


		return reports;
	}

	/**
	 * Writes the infection report to csv.
	 */
	void reporting(Map<String, InfectionReport> reports, int iteration) {
		if (iteration == 0) {
			return;
		}
		InfectionReport t = reports.get("total");

		log.warn("===============================");
		log.warn("Beginning day {}", iteration);
		log.warn("No of susceptible persons={} / {}%", decimalFormat.format(t.nSusceptible), 100 * t.nSusceptible / t.nTotal());
		log.warn("No of infected persons={} / {}%", decimalFormat.format(t.nTotalInfected), 100 * t.nTotalInfected / t.nTotal());
		log.warn("No of recovered persons={} / {}%", decimalFormat.format(t.nRecovered), 100 * t.nRecovered / t.nTotal());
		log.warn("---");
		log.warn("No of persons in quarantine={}", decimalFormat.format(t.nInQuarantine));
		log.warn("100 persons={} agents", sampleSize * 100);
		log.warn("===============================");

		// Write all reports for each district
		for (InfectionReport r : reports.values()) {
			if (r.name.equals("total")) continue;

			String[] array = new String[InfectionsWriterFields.values().length];
			array[InfectionsWriterFields.time.ordinal()] = Double.toString(r.time);
			array[InfectionsWriterFields.day.ordinal()] = Long.toString(r.day);
			array[InfectionsWriterFields.nSusceptible.ordinal()] = Long.toString(r.nSusceptible);
			array[InfectionsWriterFields.nInfectedButNotContagious.ordinal()] = Long.toString(r.nInfectedButNotContagious);
			array[InfectionsWriterFields.nContagious.ordinal()] = Long.toString(r.nContagious);
			array[InfectionsWriterFields.nShowingSymptoms.ordinal()] = Long.toString(r.nShowingSymptoms);
			array[InfectionsWriterFields.nRecovered.ordinal()] = Long.toString(r.nRecovered);

			array[InfectionsWriterFields.nTotalInfected.ordinal()] = Long.toString((r.nTotalInfected));
			array[InfectionsWriterFields.nInfectedCumulative.ordinal()] = Long.toString((r.nTotalInfected + r.nRecovered));

			array[InfectionsWriterFields.nInQuarantine.ordinal()] = Long.toString(r.nInQuarantine);

			array[InfectionsWriterFields.nSeriouslySick.ordinal()] = Long.toString(r.nSeriouslySick);
			array[InfectionsWriterFields.nCritical.ordinal()] = Long.toString(r.nCritical);
			array[InfectionsWriterFields.district.ordinal()] = r.name;

			writer.append(infectionReport, array);
		}
	}

	public void reportInfection(EpisimPerson personWrapper, EpisimPerson infector, double now, String infectionType) {

		int cnt = specificInfectionsCnt.getOpaque();
		// This counter is used by many threads, for better performance we use very weak memory guarantees here
		// race-conditions will occur, but the state will be eventually where we want it (threads stop logging)
		if (cnt > 0) {
			log.warn("Infection of personId={} by person={} at/in {}", personWrapper.getPersonId(), infector.getPersonId(), infectionType);
			specificInfectionsCnt.setOpaque(cnt - 1);
		}

		String[] array = new String[InfectionEventsWriterFields.values().length];
		array[InfectionEventsWriterFields.time.ordinal()] = Double.toString(now);
		array[InfectionEventsWriterFields.infector.ordinal()] = infector.getPersonId().toString();
		array[InfectionEventsWriterFields.infected.ordinal()] = personWrapper.getPersonId().toString();
		array[InfectionEventsWriterFields.infectionType.ordinal()] = infectionType;

		writer.append(infectionEvents, array);
	}

	void reportRestrictions(Map<String, Restriction> restrictions, long iteration) {
		writer.append(restrictionReport, EpisimWriter.JOINER.join(iteration, "", restrictions.values().toArray()));
		writer.append(restrictionReport, "\n");
	}

	enum InfectionsWriterFields {
		time, day, nSusceptible, nInfectedButNotContagious, nContagious, nShowingSymptoms, nSeriouslySick, nCritical, nTotalInfected, nInfectedCumulative,
		nRecovered, nInQuarantine, district
	}

	enum InfectionEventsWriterFields {time, infector, infected, infectionType}

	/**
	 * Detailed infection report for the end of a day.
	 * Although the fields are mutable, do not change them outside this class.
	 */
	public static class InfectionReport {

		public final String name;
		public final double time;
		public final long day;
		public long nSusceptible = 0;
		public long nInfectedButNotContagious = 0;
		public long nContagious = 0;
		public long nShowingSymptoms = 0;
		public long nSeriouslySick = 0;
		public long nCritical = 0;
		public long nTotalInfected = 0;
		public long nRecovered = 0;
		public long nInQuarantine = 0;

		public InfectionReport(String name, double time, long day) {
			this.name = name;
			this.time = time;
			this.day = day;
		}

		public long nTotal() {
			return nSusceptible + nTotalInfected + nRecovered;
		}

		void scale(double factor) {
			nSusceptible *= factor;
			nInfectedButNotContagious *= factor;
			nContagious *= factor;
			nShowingSymptoms *= factor;
			nSeriouslySick *= factor;
			nCritical *= factor;
			nTotalInfected *= factor;
			nRecovered *= factor;
			nInQuarantine *= factor;
		}
	}
}
