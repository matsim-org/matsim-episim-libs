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

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.typesafe.config.ConfigRenderOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectDoubleHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectIntHashMap;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimPersonStatusEvent;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.reporting.EpisimWriter;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reporting and persisting of metrics, like number of infected people etc.
 */
public final class EpisimReporting implements BasicEventHandler, Closeable {

	private static final Logger log = LogManager.getLogger(EpisimReporting.class);
	private static final AtomicInteger specificInfectionsCnt = new AtomicInteger(300);

	private final EpisimWriter writer;
	private final EventsManager manager;

	/**
	 * Base path for event files.
	 */
	private final Path eventPath;
	private final boolean writeAllEvents;

	private final BufferedWriter infectionReport;
	private final BufferedWriter infectionEvents;
	private final BufferedWriter restrictionReport;
	private final BufferedWriter timeUse;

	/**
	 * Aggregated cumulative hospital cases by district.
	 */
	private final MutableObjectIntMap<String> hospitalCases = new ObjectIntHashMap<>();

	/**
	 * Number format for logging output. Not static because not thread-safe.
	 */
	private final NumberFormat decimalFormat = DecimalFormat.getInstance(Locale.GERMAN);
	private final double sampleSize;
	/**
	 * Current day / iteration.
	 */
	private int iteration;
	private BufferedWriter events;


	@Inject
	EpisimReporting(Config config, EpisimWriter writer, EventsManager manager) {
		String base;
		String outDir = config.controler().getOutputDirectory();

		// file names depend on the run name
		if (config.controler().getRunId() != null) {
			base = outDir + "/" + config.controler().getRunId() + ".";
		} else if (!outDir.endsWith("/")) {
			base = outDir + "/";
		} else
			base = outDir;

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		try {
			eventPath = Path.of(outDir, "events");
			if (!Files.exists(eventPath))
				Files.createDirectories(eventPath);

		} catch (IOException e) {
			log.error("Could not create output directory", e);
			throw new UncheckedIOException(e);
		}

		this.writer = writer;
		this.manager = manager;

		infectionReport = EpisimWriter.prepare(base + "infections.txt", InfectionsWriterFields.class);
		infectionEvents = EpisimWriter.prepare(base + "infectionEvents.txt", InfectionEventsWriterFields.class);
		restrictionReport = EpisimWriter.prepare(base + "restrictions.txt",
				"day", "", episimConfig.createInitialRestrictions().keySet().toArray());
		timeUse = EpisimWriter.prepare(base + "timeUse.txt",
				"day", "", episimConfig.createInitialRestrictions().keySet().toArray());

		sampleSize = episimConfig.getSampleSize();
		writeAllEvents = episimConfig.getWriteEvents() == EpisimConfigGroup.WriteEvents.all;

		try {
			Files.writeString(Paths.get(base + "policy.conf"),
					episimConfig.getPolicy().root().render(ConfigRenderOptions.defaults()
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

		// aggregate hospital cases at last
		long nHospitalCumulative = 0;
		for (String district : reports.keySet()) {
			nHospitalCumulative += hospitalCases.get(district);
			reports.get(district).nHospitalCumulative = hospitalCases.get(district);
		}

		reports.get("total").nHospitalCumulative = nHospitalCumulative;

		reports.forEach((k, v) -> v.scale(1 / sampleSize));

		return reports;
	}

	/**
	 * Writes the infection report to csv.
	 */
	void reporting(Map<String, InfectionReport> reports, int iteration) {
		if (iteration == 0) return;

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
			array[InfectionsWriterFields.nHospitalCumulative.ordinal()] = Long.toString(r.nHospitalCumulative);

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

		manager.processEvent(new EpisimInfectionEvent(now, personWrapper.getPersonId(), infector.getPersonId(),
				personWrapper.getCurrentContainer().getContainerId(), infectionType));


		String[] array = new String[InfectionEventsWriterFields.values().length];
		array[InfectionEventsWriterFields.time.ordinal()] = Double.toString(now);
		array[InfectionEventsWriterFields.infector.ordinal()] = infector.getPersonId().toString();
		array[InfectionEventsWriterFields.infected.ordinal()] = personWrapper.getPersonId().toString();
		array[InfectionEventsWriterFields.infectionType.ordinal()] = infectionType;

		writer.append(infectionEvents, array);
	}

	void reportRestrictions(Map<String, Restriction> restrictions, long iteration) {
		if (iteration == 0) return;

		writer.append(restrictionReport, EpisimWriter.JOINER.join(iteration, "", restrictions.values().toArray()));
		writer.append(restrictionReport, "\n");
	}

	void reportTimeUse(Set<String> activities, Collection<EpisimPerson> persons, long iteration) {

		if (iteration == 0) return;

		ObjectDoubleHashMap<String> avg = new ObjectDoubleHashMap<>();

		int i = 1;
		for (EpisimPerson person : persons) {

			// computing incremental avg.
			// Average += (NewValue - Average) / NewSampleCount;
			for (String act : activities) {
				avg.addToValue(act, (person.getSpentTime().get(act) - avg.get(act)) / i);
			}

			person.getSpentTime().clear();
			i++;
		}

		List<String> order = Lists.newArrayList(activities);
		Object[] array = new String[order.size()];
		Arrays.fill(array, "");

		// report minutes
		avg.forEachKeyValue((k, v) -> array[order.indexOf(k)] = String.valueOf(v / 60d));

		writer.append(timeUse, EpisimWriter.JOINER.join(iteration, "", array));
		writer.append(timeUse, "\n");
	}

	/**
	 * Report that a person status has changed and publish corresponding event.
	 */
	public void reportPersonStatus(EpisimPerson person, EpisimPersonStatusEvent event) {

		if (event.getDiseaseStatus() == EpisimPerson.DiseaseStatus.seriouslySick) {
			String districtName = (String) person.getAttributes().getAttribute("district");
			hospitalCases.addToValue(districtName == null ? "unknown" : districtName, 1);
		}

		manager.processEvent(event);
	}

	public void close() {

		if (events != null) {
			writer.append(events, "</events>");
			writer.close(events);
		}

		writer.close(infectionReport);
		writer.close(infectionEvents);
		writer.close(restrictionReport);
		writer.close(timeUse);

	}

	@Override
	public void handleEvent(Event event) {

		// Events on 0th day are not needed
		if (iteration == 0) return;

		// Crucial episim events are always written
		// other only if enabled

		if (event instanceof EpisimPersonStatusEvent || event instanceof EpisimInfectionEvent || writeAllEvents)
			writer.append(events, event);

	}

	@Override
	public void reset(int iteration) {

		this.iteration = iteration;

		if (iteration == 0) return;

		if (events != null) {
			writer.append(events, "</events>");
			writer.close(events);
		}

		events = IOUtils.getBufferedWriter(eventPath.resolve(String.format("day_%03d.xml.gz", iteration)).toString());
		writer.append(events, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<events version=\"1.0\">\n");
	}

	enum InfectionsWriterFields {
		time, day, nSusceptible, nInfectedButNotContagious, nContagious, nShowingSymptoms, nSeriouslySick, nCritical, nTotalInfected, nInfectedCumulative,
		nHospitalCumulative, nRecovered, nInQuarantine, district
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
		public long nHospitalCumulative = 0;
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
			nHospitalCumulative *= factor;
			nRecovered *= factor;
			nInQuarantine *= factor;
		}
	}
}
