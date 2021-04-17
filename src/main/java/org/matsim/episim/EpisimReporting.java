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
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.episim.events.EpisimContactEvent;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimPersonStatusEvent;
import org.matsim.episim.events.EpisimTracingEvent;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.reporting.EpisimWriter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.matsim.episim.EpisimUtils.readChars;
import static org.matsim.episim.EpisimUtils.writeChars;

/**
 * Reporting and persisting of metrics, like number of infected people etc.
 */
public final class EpisimReporting implements BasicEventHandler, Closeable, Externalizable {

	private static final Logger log = LogManager.getLogger(EpisimReporting.class);
	private static final AtomicInteger specificInfectionsCnt = new AtomicInteger(300);

	private final EpisimWriter writer;
	private final EventsManager manager;

	private final String base;
	private final String outDir;

	/**
	 * Base path for event files.
	 */
	private final Path eventPath;
	private final EpisimConfigGroup.WriteEvents writeEvents;

	/**
	 * Aggregated cumulative cases by status and district. Contains only a subset of relevant {@link org.matsim.episim.EpisimPerson.DiseaseStatus}.
	 */
	private final Map<EpisimPerson.DiseaseStatus, Object2IntMap<String>> cumulativeCases = new EnumMap<>(EpisimPerson.DiseaseStatus.class);

	/**
	 * Number of daily infections per virus strain.
	 */
	public final Object2IntMap<VirusStrain> strains = new Object2IntOpenHashMap<>();

	/**
	 * Number format for logging output. Not static because not thread-safe.
	 */
	private final NumberFormat decimalFormat = DecimalFormat.getInstance(Locale.GERMAN);
	private final double sampleSize;
	private final Config config;
	private final EpisimConfigGroup episimConfig;
	/**
	 * Current day / iteration.
	 */
	private int iteration;
	private BufferedWriter events;
	private BufferedWriter infectionReport;
	private BufferedWriter infectionEvents;
	private BufferedWriter restrictionReport;
	private BufferedWriter timeUse;
	private BufferedWriter diseaseImport;
	private BufferedWriter outdoorFraction;
	private BufferedWriter virusStrains;

	private String memorizedDate = null;


	@Inject
	EpisimReporting(Config config, EpisimWriter writer, EventsManager manager) {
		outDir = config.controler().getOutputDirectory();

		// file names depend on the run name
		if (config.controler().getRunId() != null) {
			base = outDir + "/" + config.controler().getRunId() + ".";
		} else if (!outDir.endsWith("/")) {
			base = outDir + "/";
		} else
			base = outDir;

		episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		try {
			eventPath = Path.of(outDir, "events");
			if (!Files.exists(eventPath))
				Files.createDirectories(eventPath);

		} catch (IOException e) {
			log.error("Could not create output directory", e);
			throw new UncheckedIOException(e);
		}

		this.config = config;
		this.writer = writer;
		this.manager = manager;

		infectionReport = EpisimWriter.prepare(base + "infections.txt", InfectionsWriterFields.class);
		infectionEvents = EpisimWriter.prepare(base + "infectionEvents.txt", InfectionEventsWriterFields.class);
		restrictionReport = EpisimWriter.prepare(base + "restrictions.txt",
				"day", "date", episimConfig.createInitialRestrictions().keySet().toArray());
		timeUse = EpisimWriter.prepare(base + "timeUse.txt",
				"day", "date", episimConfig.createInitialRestrictions().keySet().toArray());
		diseaseImport = EpisimWriter.prepare(base + "diseaseImport.tsv", "day", "date", "nInfected");
		outdoorFraction = EpisimWriter.prepare(base + "outdoorFraction.tsv", "day", "date", "outdoorFraction");
		virusStrains = EpisimWriter.prepare(base + "strains.tsv", "day", "date", (Object[]) VirusStrain.values());

		sampleSize = episimConfig.getSampleSize();
		writeEvents = episimConfig.getWriteEvents();

		// Init cumulative cases
		cumulativeCases.put(EpisimPerson.DiseaseStatus.contagious, new Object2IntOpenHashMap<>());
		cumulativeCases.put(EpisimPerson.DiseaseStatus.showingSymptoms, new Object2IntOpenHashMap<>());
		cumulativeCases.put(EpisimPerson.DiseaseStatus.seriouslySick, new Object2IntOpenHashMap<>());
		cumulativeCases.put(EpisimPerson.DiseaseStatus.critical, new Object2IntOpenHashMap<>());

		writeConfigFiles();
	}

	private void writeConfigFiles() {
		try {
			Files.writeString(Paths.get(base + "policy.conf"),
					episimConfig.getPolicy().root().render(ConfigRenderOptions.defaults()
							.setFormatted(true)
							.setComments(false)
							.setOriginComments(false)
							.setJson(false)));

			Files.writeString(Paths.get(base + "progression.conf"),
					episimConfig.getProgressionConfig().root().render(ConfigRenderOptions.defaults()
							.setFormatted(true)
							.setComments(false)
							.setOriginComments(false)
							.setJson(false)));

		} catch (IOException e) {
			log.error("Could not write policy config", e);
		}

		ConfigUtils.writeConfig(config, base + "config.xml");
	}

	/**
	 * Opens files for output in append mode.
	 */
	void append(String date) throws IOException {

		// Copy non prefixed files to base output
		if (!base.equals(outDir))
			for (String file : List.of("infections.txt", "infectionEvents.txt", "restrictions.txt", "timeUse.txt", "diseaseImport.tsv",
					"outdoorFraction.tsv", "strains.tsv")) {
				Path path = Path.of(outDir, file);
				if (Files.exists(path)) {
					Files.move(path, Path.of(base + file), StandardCopyOption.REPLACE_EXISTING);
				}
			}

		infectionReport = EpisimWriter.prepare(base + "infections.txt");
		infectionEvents = EpisimWriter.prepare(base + "infectionEvents.txt");
		restrictionReport = EpisimWriter.prepare(base + "restrictions.txt");
		timeUse = EpisimWriter.prepare(base + "timeUse.txt");
		diseaseImport = EpisimWriter.prepare(base + "diseaseImport.tsv");
		outdoorFraction = EpisimWriter.prepare(base + "outdoorFraction.tsv");
		virusStrains = EpisimWriter.prepare(base + "strains.tsv");
		memorizedDate = date;

		// Write config files again to overwrite these from snapshot
		writeConfigFiles();
	}

	/**
	 * Creates infections reports for the day. Grouped by district, but always containing a "total" entry.
	 */
	Map<String, InfectionReport> createReports(Collection<EpisimPerson> persons, int iteration) {

		Map<String, InfectionReport> reports = new LinkedHashMap<>();

		double time = EpisimUtils.getCorrectedTime(EpisimUtils.getStartOffset(episimConfig.getStartDate()), 0., iteration);
		String date = episimConfig.getStartDate().plusDays(iteration - 1).toString();

		InfectionReport report = new InfectionReport("total", time, date, iteration);
		reports.put("total", report);

		for (EpisimPerson person : persons) {
			String districtName = (String) person.getAttributes().getAttribute("district");

			// Also aggregate by district
			InfectionReport district = reports.computeIfAbsent(districtName == null ? "unknown"
					: districtName, name -> new InfectionReport(name, report.time, report.date, report.day));
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
				case seriouslySickAfterCritical:
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
					report.nInQuarantineHome++;
					district.nInQuarantineHome++;
					break;
				case full:
					report.nInQuarantineFull++;
					district.nInQuarantineFull++;
					break;
				case no:
					break;
				default:
					throw new IllegalStateException("Unexpected value: " + person.getQuarantineStatus());
			}

			switch (person.getVaccinationStatus()) {
				case yes:
					report.nVaccinated++;
					district.nVaccinated++;
				case no:
					break;
				default:
					throw new IllegalArgumentException("Unexpected value: " + person.getVaccinationStatus());
			}

			if (person.daysSinceTest(iteration) == 0) {
				report.nTested++;
				district.nTested++;
			}
		}

		for (String district : reports.keySet()) {

			int nContagious = cumulativeCases.get(EpisimPerson.DiseaseStatus.contagious).getOrDefault(district, 0);
			int nShowingSymptoms = cumulativeCases.get(EpisimPerson.DiseaseStatus.showingSymptoms).getOrDefault(district, 0);
			int nSeriouslySick = cumulativeCases.get(EpisimPerson.DiseaseStatus.seriouslySick).getOrDefault(district, 0);
			int nCritical = cumulativeCases.get(EpisimPerson.DiseaseStatus.critical).getOrDefault(district, 0);

			reports.get(district).nContagiousCumulative = nContagious;
			reports.get(district).nShowingSymptomsCumulative = nShowingSymptoms;
			reports.get(district).nSeriouslySickCumulative = nSeriouslySick;
			reports.get(district).nCriticalCumulative = nCritical;

			// Sum for total report
			report.nContagiousCumulative += nContagious;
			report.nShowingSymptomsCumulative += nShowingSymptoms;
			report.nSeriouslySickCumulative += nSeriouslySick;
			report.nCriticalCumulative += nCritical;
		}

		reports.forEach((k, v) -> v.scale(1 / sampleSize));

		return reports;
	}

	/**
	 * Writes the infection report to csv.
	 *
	 * @param date
	 */
	void reporting(Map<String, InfectionReport> reports, int iteration, String date) {

		memorizedDate = date;

		if (iteration == 0) return;

		InfectionReport t = reports.get("total");

		log.warn("===============================");
		log.warn("Beginning day {} ({})", iteration, date);
		log.warn("No of susceptible persons={} / {}%", decimalFormat.format(t.nSusceptible), 100 * t.nSusceptible / t.nTotal());
		log.warn("No of infected persons={} / {}%", decimalFormat.format(t.nTotalInfected), 100 * t.nTotalInfected / t.nTotal());
		log.warn("No of recovered persons={} / {}%", decimalFormat.format(t.nRecovered), 100 * t.nRecovered / t.nTotal());
		log.warn("No of vaccinated persons={} / {}%", decimalFormat.format(t.nVaccinated), 100 * t.nVaccinated / t.nTotal());
		log.warn("---");
		log.warn("No of persons in quarantineFull={} / {}%", decimalFormat.format(t.nInQuarantineFull), 100 * t.nInQuarantineFull / t.nTotal());
		log.warn("No of persons in quarantineHome (through tracing)={} / {}%", decimalFormat.format(t.nInQuarantineHome), 100 * t.nInQuarantineHome / t.nTotal());
		log.warn("100 persons={} agents", sampleSize * 100);
		log.warn("===============================");

		String[] strainOut = new String[VirusStrain.values().length + 2];
		strainOut[0] = String.valueOf(iteration);
		strainOut[1] = date;
		for (int i = 0; i < VirusStrain.values().length; i++) {
			strainOut[i + 2] = String.valueOf(strains.getOrDefault(VirusStrain.values()[i], 0) * (1 / sampleSize));
		}
		writer.append(virusStrains, strainOut);
		strains.clear();

		// Write all reports for each district
		for (InfectionReport r : reports.values()) {
			if (r.name.equals("total")) continue;

			String[] array = new String[InfectionsWriterFields.values().length];
			array[InfectionsWriterFields.time.ordinal()] = Double.toString(r.time);
			array[InfectionsWriterFields.day.ordinal()] = Long.toString(r.day);
			array[InfectionsWriterFields.date.ordinal()] = r.date;
			array[InfectionsWriterFields.nSusceptible.ordinal()] = Long.toString(r.nSusceptible);
			array[InfectionsWriterFields.nInfectedButNotContagious.ordinal()] = Long.toString(r.nInfectedButNotContagious);
			array[InfectionsWriterFields.nContagious.ordinal()] = Long.toString(r.nContagious);
			array[InfectionsWriterFields.nContagiousCumulative.ordinal()] = Long.toString(r.nContagiousCumulative);
			array[InfectionsWriterFields.nShowingSymptoms.ordinal()] = Long.toString(r.nShowingSymptoms);
			array[InfectionsWriterFields.nShowingSymptomsCumulative.ordinal()] = Long.toString(r.nShowingSymptomsCumulative);
			array[InfectionsWriterFields.nRecovered.ordinal()] = Long.toString(r.nRecovered);

			array[InfectionsWriterFields.nTotalInfected.ordinal()] = Long.toString((r.nTotalInfected));
			array[InfectionsWriterFields.nInfectedCumulative.ordinal()] = Long.toString((r.nTotalInfected + r.nRecovered));

			array[InfectionsWriterFields.nInQuarantineFull.ordinal()] = Long.toString(r.nInQuarantineFull);
			array[InfectionsWriterFields.nInQuarantineHome.ordinal()] = Long.toString(r.nInQuarantineHome);

			array[InfectionsWriterFields.nSeriouslySick.ordinal()] = Long.toString(r.nSeriouslySick);
			array[InfectionsWriterFields.nSeriouslySickCumulative.ordinal()] = Long.toString(r.nSeriouslySickCumulative);
			array[InfectionsWriterFields.nCritical.ordinal()] = Long.toString(r.nCritical);
			array[InfectionsWriterFields.nCriticalCumulative.ordinal()] = Long.toString(r.nCriticalCumulative);

			array[InfectionsWriterFields.nVaccinated.ordinal()] = Long.toString(r.nVaccinated);
			array[InfectionsWriterFields.nTested.ordinal()] = Long.toString(r.nTested);

			array[InfectionsWriterFields.district.ordinal()] = r.name;

			writer.append(infectionReport, array);
		}
	}

	/**
	 * Report the occurrence of an infection.
	 *
	 * @param personWrapper infected person
	 * @param infector      infector
	 * @param infectionType activities of both persons
	 */
	public void reportInfection(EpisimPerson personWrapper, EpisimPerson infector, double now, String infectionType,
								VirusStrain strain, double prob, EpisimContainer<?> container) {

		int cnt = specificInfectionsCnt.getOpaque();
		// This counter is used by many threads, for better performance we use very weak memory guarantees here
		// race-conditions will occur, but the state will be eventually where we want it (threads stop logging)
		if (cnt > 0) {
			log.warn("Infection of personId={} by person={} at/in {}", personWrapper.getPersonId(), infector.getPersonId(), infectionType);
			specificInfectionsCnt.setOpaque(cnt - 1);
		}

		strains.mergeInt(strain, 1, Integer::sum);
		manager.processEvent(new EpisimInfectionEvent(now, personWrapper.getPersonId(), infector.getPersonId(),
				personWrapper.getCurrentContainer().getContainerId(), infectionType, container.getPersons().size(), strain, prob));


		String[] array = new String[InfectionEventsWriterFields.values().length];
		array[InfectionEventsWriterFields.time.ordinal()] = Double.toString(now);
		array[InfectionEventsWriterFields.infector.ordinal()] = infector.getPersonId().toString();
		array[InfectionEventsWriterFields.infected.ordinal()] = personWrapper.getPersonId().toString();
		array[InfectionEventsWriterFields.infectionType.ordinal()] = infectionType;
		array[InfectionEventsWriterFields.date.ordinal()] = memorizedDate;
		array[InfectionEventsWriterFields.groupSize.ordinal()] = Long.toString(container.getPersons().size());
		array[InfectionEventsWriterFields.facility.ordinal()] = container.getContainerId().toString();
		array[InfectionEventsWriterFields.virusStrain.ordinal()] = strain.toString();
		array[InfectionEventsWriterFields.probability.ordinal()] = Double.toString(prob);

		writer.append(infectionEvents, array);
	}

	/**
	 * Report the occurrence of an contact between two persons.
	 * TODO Attention: Currently this only includes a subset of contacts (between persons with certain disease status).
	 *
	 * @see EpisimContactEvent
	 */
	public void reportContact(double now, EpisimPerson person, EpisimPerson contactPerson, EpisimContainer<?> container,
							  StringBuilder actType, double duration) {

		if (writeEvents == EpisimConfigGroup.WriteEvents.tracing || writeEvents == EpisimConfigGroup.WriteEvents.all) {
			manager.processEvent(new EpisimContactEvent(now, person.getPersonId(), contactPerson.getPersonId(), container.getContainerId(),
					actType.toString(), duration, container.getPersons().size()));
		}

	}


	/**
	 * Report the successful tracing between two persons.
	 */
	void reportTracing(double now, EpisimPerson person, EpisimPerson contactPerson) {

		if (writeEvents == EpisimConfigGroup.WriteEvents.tracing || writeEvents == EpisimConfigGroup.WriteEvents.all) {
			manager.processEvent(new EpisimTracingEvent(now, person.getPersonId(), contactPerson.getPersonId()));
		}
	}

	void reportRestrictions(Map<String, Restriction> restrictions, long iteration, String date) {
		if (iteration == 0) return;

		writer.append(restrictionReport, EpisimWriter.JOINER.join(iteration, date, restrictions.values().toArray()));
		writer.append(restrictionReport, "\n");
	}

	void reportTimeUse(Set<String> activities, Collection<EpisimPerson> persons, long iteration, String date) {

		if (iteration == 0) return;

		Object2DoubleMap<String> avg = new Object2DoubleOpenHashMap<>();

		int i = 1;
		for (EpisimPerson person : persons) {

			// Average += (NewValue - Average) / NewSampleCount;
			for (String act : activities) {
				// Compute incremental avg.
				avg.mergeDouble(act, (person.getSpentTime().getDouble(act) - avg.getDouble(act)) / i, Double::sum);

				// Compute total instead
//				avg.mergeDouble(act, person.getSpentTime().getDouble(act), Double::sum);
			}

			person.getSpentTime().clear();
			i++;
		}

		List<String> order = Lists.newArrayList(activities);
		Object[] array = new String[order.size()];
		Arrays.fill(array, "");

		// report minutes
		avg.forEach((k, v) -> array[order.indexOf(k)] = String.valueOf(v / 60d));

		writer.append(timeUse, EpisimWriter.JOINER.join(iteration, date, array));
		writer.append(timeUse, "\n");
	}

	/**
	 * Report that a person status has changed and publish corresponding event.
	 */
	public void reportPersonStatus(EpisimPerson person, EpisimPersonStatusEvent event) {

		EpisimPerson.DiseaseStatus newStatus = event.getDiseaseStatus();

		if (newStatus == EpisimPerson.DiseaseStatus.seriouslySick || newStatus == EpisimPerson.DiseaseStatus.contagious ||
				newStatus == EpisimPerson.DiseaseStatus.showingSymptoms || newStatus == EpisimPerson.DiseaseStatus.critical) {
			String districtName = (String) person.getAttributes().getAttribute("district");
			cumulativeCases.get(newStatus).mergeInt(districtName == null ? "unknown" : districtName, 1, Integer::sum);
		}

		manager.processEvent(event);
	}

	/**
	 * Write container statistic to file.
	 */
	public void reportContainerUsage(Object2IntMap<EpisimContainer<?>> maxGroupSize, Object2IntMap<EpisimContainer<?>> totalUsers,
									 Map<EpisimContainer<?>, Object2IntMap<String>> activityUsage) {

		BufferedWriter out = EpisimWriter.prepare(base + "containerUsage.txt.gz", "id", "types", "totalUsers", "maxGroupSize");

		for (Object2IntMap.Entry<EpisimContainer<?>> kv : maxGroupSize.object2IntEntrySet()) {

			double scale = 1 / episimConfig.getSampleSize();

			this.writer.append(out, new String[]{
					kv.getKey().getContainerId().toString(),
					String.valueOf(activityUsage.get(kv.getKey())),
					String.valueOf((int) (totalUsers.getInt(kv.getKey()) * scale)),
					String.valueOf((int) (kv.getIntValue() * scale))
			});
		}

		this.writer.close(out);
	}

	/**
	 * Write number of initially infected persons.
	 */
	public void reportDiseaseImport(int infected, int iteration, String date) {
		writer.append(diseaseImport, new String[]{String.valueOf(iteration), date, String.valueOf(infected * (1 / sampleSize))});
	}

	/**
	 * Write outdoor fraction for each day.
	 */
	public void reportOutdoorFraction(double outdoorFraction, int iteration) {
		String date = episimConfig.getStartDate().plusDays(iteration - 1).toString();

		try {
			this.outdoorFraction.flush();
			writer.append(this.outdoorFraction, new String[]{String.valueOf(iteration), date, String.valueOf(outdoorFraction)});
		} catch (IOException e) {
			// will only write to the writer if it is still open
			// when reading snapshot there may be a situation where it is closed
		}

	}

	@Override
	public void close() {

		writer.close(infectionReport);
		writer.close(infectionEvents);
		writer.close(restrictionReport);
		writer.close(timeUse);
		writer.close(diseaseImport);
		writer.close(outdoorFraction);
		writer.close(virusStrains);

	}

	/**
	 * This method may ever only do event writing, as it can be disabled via config.
	 */
	@Override
	public void handleEvent(Event event) {

		// Events on 0th day are not needed
		if (iteration == 0) return;

		// Crucial episim events are always written, others only if enabled
		if (event instanceof EpisimPersonStatusEvent || event instanceof EpisimInfectionEvent
				|| (writeEvents == EpisimConfigGroup.WriteEvents.tracing && event instanceof EpisimTracingEvent)
				|| (writeEvents == EpisimConfigGroup.WriteEvents.tracing && event instanceof EpisimContactEvent)) {

			writer.append(events, event);

		} else if (writeEvents == EpisimConfigGroup.WriteEvents.all || writeEvents == EpisimConfigGroup.WriteEvents.input) {

			// All non-epism events need a corrected timestamp
			writer.append(events, event,
					EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), event.getTime(), iteration));

		}

	}

	@Override
	public void reset(int iteration) {
		this.iteration = iteration;

		if (iteration == 0 || writeEvents == EpisimConfigGroup.WriteEvents.none)
			return;

		events = IOUtils.getBufferedWriter(eventPath.resolve(String.format("day_%03d.xml.gz", iteration)).toString());
		writer.append(events, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<events version=\"1.0\">\n");
	}


	/**
	 * Flush written events.
	 */
	void flushEvents() {
		if (events != null) {
			writer.append(events, "</events>");
			writer.close(events);
		}
	}


	@Override
	public void writeExternal(ObjectOutput out) throws IOException {

		out.writeInt(cumulativeCases.size());

		for (Map.Entry<EpisimPerson.DiseaseStatus, Object2IntMap<String>> e : cumulativeCases.entrySet()) {
			out.writeInt(e.getKey().ordinal());
			Object2IntMap<String> map = e.getValue();
			out.writeInt(map.size());

			for (Object2IntMap.Entry<String> kv : map.object2IntEntrySet()) {
				writeChars(out, kv.getKey());
				out.writeInt(kv.getIntValue());
			}
		}

		for (VirusStrain value : VirusStrain.values()) {
			out.writeInt(strains.getInt(value));
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {

		int states = in.readInt();
		for (int i = 0; i < states; i++) {
			EpisimPerson.DiseaseStatus state = EpisimPerson.DiseaseStatus.values()[in.readInt()];
			int size = in.readInt();
			for (int j = 0; j < size; j++) {
				String key = readChars(in);
				cumulativeCases.get(state).put(key, in.readInt());
			}
		}

		for (VirusStrain value : VirusStrain.values()) {
			strains.put(value, in.readInt());
		}
	}

	enum InfectionsWriterFields {
		time, day, date, nSusceptible, nInfectedButNotContagious, nContagious, nShowingSymptoms, nSeriouslySick, nCritical, nTotalInfected,
		nInfectedCumulative, nContagiousCumulative, nShowingSymptomsCumulative, nSeriouslySickCumulative, nCriticalCumulative,
		nRecovered, nInQuarantineFull, nInQuarantineHome, nVaccinated, nTested, district
	}

	enum InfectionEventsWriterFields {time, infector, infected, infectionType, date, groupSize, facility, virusStrain, probability}

	/**
	 * Detailed infection report for the end of a day.
	 * Although the fields are mutable, do not change them outside this class.
	 */
	@SuppressWarnings("VisibilityModifier")
	public static class InfectionReport {

		public final String name;
		public final double time;
		public final String date;
		public final long day;
		public long nSusceptible = 0;
		public long nInfectedButNotContagious = 0;
		public long nContagious = 0;
		public long nContagiousCumulative = 0;
		public long nShowingSymptoms = 0;
		public long nShowingSymptomsCumulative = 0;
		public long nSeriouslySick = 0;
		public long nSeriouslySickCumulative = 0;
		public long nCritical = 0;
		public long nCriticalCumulative = 0;
		public long nTotalInfected = 0;
		public long nRecovered = 0;
		public long nInQuarantineFull = 0;
		public long nInQuarantineHome = 0;
		public long nVaccinated = 0;
		public long nTested = 0;

		/**
		 * Constructor.
		 */
		public InfectionReport(String name, double time, String date, long day) {
			this.name = name;
			this.time = time;
			this.date = date;
			this.day = day;
		}

		/**
		 * Total number of persons in the simulation.
		 */
		public long nTotal() {
			return nSusceptible + nTotalInfected + nRecovered;
		}

		void scale(double factor) {
			nSusceptible *= factor;
			nInfectedButNotContagious *= factor;
			nContagious *= factor;
			nContagiousCumulative *= factor;
			nShowingSymptoms *= factor;
			nShowingSymptomsCumulative *= factor;
			nSeriouslySick *= factor;
			nSeriouslySickCumulative *= factor;
			nCritical *= factor;
			nCriticalCumulative *= factor;
			nTotalInfected *= factor;
			nRecovered *= factor;
			nInQuarantineFull *= factor;
			nInQuarantineHome *= factor;
			nVaccinated *= factor;
			nTested *= factor;
		}
	}
}
