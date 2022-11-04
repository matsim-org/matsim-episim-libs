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

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.episim.model.AntibodyModel;
import org.matsim.episim.model.ProgressionModel;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

/**
 * Main entry point and runner of one epidemic simulation.
 * <p>
 * Using the {@link #run(int)} method, this class will repeatedly loop over {@link InfectionEventHandler} with
 * events provided by the {@link ReplayHandler}.
 */
public final class EpisimRunner {

	private static final Logger log = LogManager.getLogger(EpisimRunner.class);

	private final Config config;
	private final EventsManager manager;
	private final Provider<InfectionEventHandler> handlerProvider;
	private final Provider<ReplayHandler> replayProvider;
	private final Provider<EpisimReporting> reportingProvider;
	private final Provider<ProgressionModel> progressionProvider;
	private final Provider<AntibodyModel> antibodyModelProvider;

	@Inject
	public EpisimRunner(Config config, EventsManager manager, Provider<InfectionEventHandler> handlerProvider, Provider<ReplayHandler> replay,
						Provider<EpisimReporting> reportingProvider, Provider<ProgressionModel> progressionProvider, Provider<AntibodyModel> antibodyModelProvider) {
		this.config = config;
		this.handlerProvider = handlerProvider;
		this.manager = manager;
		this.replayProvider = replay;
		this.reportingProvider = reportingProvider;
		this.progressionProvider = progressionProvider;
		this.antibodyModelProvider = antibodyModelProvider;
	}

	/**
	 * Main loop that performs the iterations of the simulation.
	 *
	 * @param maxIterations maximum number of iterations (inclusive)
	 */
	public void run(int maxIterations) {

		// Construct these dependencies as late as possible, so all other configs etc have been fully configured
		final ReplayHandler replay = replayProvider.get();
		final InfectionEventHandler handler = handlerProvider.get();
		final EpisimReporting reporting = reportingProvider.get();
		final AntibodyModel antibodyModel = antibodyModelProvider.get();

		reporting.reportCpuTime(0, "Init", "start", -1);
		// reporting will write events if necessary
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		if (episimConfig.getWriteEvents() != EpisimConfigGroup.WriteEvents.none)
			manager.addHandler(reporting);

		ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "Just before starting iterations");

		handler.init(replay.getEvents());

		Path output = Path.of(config.controler().getOutputDirectory());

		int iteration = 1;
		if (episimConfig.getStartFromSnapshot() != null && episimConfig.getStartFromImmunization() != null) {
			throw new RuntimeException("Cannot start from snapshot and immunization history simultaneously. Choose one.");
		} else if (episimConfig.getStartFromSnapshot() != null) {
			reporting.close();
			iteration = readSnapshot(output, Path.of(episimConfig.getStartFromSnapshot()));
			try {
				reporting.append(episimConfig.getStartDate().plusDays(iteration - 1).toString());
			} catch (IOException e) {
				log.error("Snapshot output could not be created", e);
				return;
			}

			handler.onSnapshotLoaded(iteration);
		} else if (episimConfig.getStartFromImmunization() != null) {

			handler.initImmunization(Path.of(episimConfig.getStartFromImmunization()));
		}

		// recalculate antibodies for every agent if starting from snapshot.
		// The antibodies profile is generated using the immunity event history in the
		// snapshot; the antibody model config of the snapshot simulation will
		// be superceded by the config of the current simulation. Thus, the antibody development
		// during the snapshot can be rewritten without modifying the immunity event history.

		antibodyModel.init(handler.getPersons(), iteration);

		reporting.reportCpuTime(0, "Init", "finished", -1);

		log.info("Starting from iteration {}...", iteration);

		for (; iteration <= maxIterations; iteration++) {

			if (episimConfig.getSnapshotInterval() > 0 && iteration % episimConfig.getSnapshotInterval() == 0) {
				writeSnapshot(output, iteration);
				writeImmunization(output, iteration);
			}

			if (iteration % 10 == 0)
				Gbl.printMemoryUsage();

			if (!doStep(replay, handler, reporting, iteration))
				break;

		}

		handler.finish();

		reporting.close();
	}

	/**
	 * Update events data and internal person data structure.
	 *
	 * @param events
	 */
	public void updateEvents(Map<DayOfWeek, List<Event>> events) {

		ReplayHandler replay = replayProvider.get();

		replay.setEvents(events);

		InfectionEventHandler handler = handlerProvider.get();
		handler.updateEvents(events);
	}

	/**
	 * Reads and updates events as defined in given config.
	 */
	public void updateEvents(EpisimConfigGroup config) {
		ReplayHandler replay = replayProvider.get();
		Map<DayOfWeek, List<Event>> events = replay.readEvents(config);
		updateEvents(events);
	}

	/**
	 * Perform one iteration of simulation.
	 *
	 * @return false, when the simulation should end
	 */
	boolean doStep(final ReplayHandler replay, final InfectionEventHandler handler, final EpisimReporting reporting, int iteration) {

		manager.resetHandlers(iteration);
		handler.reset(iteration);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(this.config, EpisimConfigGroup.class);

		if (episimConfig.isEndEarly() && handler.isFinished())
			return false;

		DayOfWeek day = EpisimUtils.getDayOfWeek(episimConfig, iteration);

		// Process all events
		replay.replayEvents(handler, day);

		reporting.flushEvents();

		return true;
	}


	/**
	 * Write snapshot into output directory.
	 *
	 * @param output    target output directory
	 * @param iteration current iteration
	 */
	private void writeSnapshot(Path output, int iteration) {

		InfectionEventHandler handler = handlerProvider.get();
		EpisimReporting reporting = reportingProvider.get();
		ProgressionModel progressionModel = progressionProvider.get();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		String date = episimConfig.getStartDate().plusDays(iteration - 1).toString();

		Path path = output.resolve(episimConfig.getSnapshotPrefix() + String.format("-%03d-%s.zip", iteration, date));

		log.info("Writing snapshot to {}", path);

		try (var out = Files.newOutputStream(path)) {

			ArchiveOutputStream archive = new ArchiveStreamFactory()
					.createArchiveOutputStream("zip", out);

			// Copy whole output to the snapshot
			EpisimUtils.compressDirectory(output.toString(), output.toString(), config.controler().getRunId(), archive);

			archive.putArchiveEntry(new ZipArchiveEntry("iteration"));
			ObjectOutputStream oos = new ObjectOutputStream(archive);
			oos.writeInt(iteration);
			oos.flush();
			archive.closeArchiveEntry();

			writeObject(handler, "state", archive);
			writeObject(reporting, "reporting", archive);

			if (progressionModel instanceof Externalizable)
				writeObject((Externalizable) progressionModel, "progression", archive);

			archive.finish();
			archive.close();

		} catch (IOException | ArchiveException e) {
			log.error("Could not write snapshot", e);
		}

		log.info("Snapshot for day {} written successfully", iteration);

	}

	/**
	 * Write immunization history.
	 *
	 * @param output    output path
	 * @param iteration current iteration
	 */
	private void writeImmunization(Path output, int iteration) {

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		String date = episimConfig.getStartDate().plusDays(iteration - 1).toString();

		Path path = output.resolve(episimConfig.getImmunizationPrefix() + String.format("-%03d-%s.tsv.gz", iteration, date));

		log.info("Writing immunization history to {}", path);

		InfectionEventHandler handler = handlerProvider.get();

		try (CSVPrinter out = new CSVPrinter(new OutputStreamWriter(new GzipCompressorOutputStream(Files.newOutputStream(path))), CSVFormat.TDF)) {

			out.printRecord("personId", "date", "virus_or_vaccine", "type");

			for (EpisimPerson person : handler.getPersons()) {
				for (int i = 0; i < person.getNumInfections(); i++) {
					String occurrence = episimConfig.getStartDate().plusDays((long) ((person.getInfectionDates().getDouble(i) / EpisimUtils.DAY) - 1)).toString();
					out.printRecord(person.getPersonId(), occurrence, "virus", person.getVirusStrain(i));
				}

				for (int i = 0; i < person.getNumVaccinations(); i++) {
					String occurrence = episimConfig.getStartDate().plusDays(person.getVaccinationDates().getInt(i) - 1).toString();
					out.printRecord(person.getPersonId(), occurrence, "vaccine", person.getVaccinationType(i));
				}
			}

		} catch (IOException e) {
			throw new UncheckedIOException("Could not write immunization history", e);
		}
	}

	/**
	 * Read snapshot from disk and initialize simulation state
	 *
	 * @param path path to snapshot archive
	 * @return starting iteration
	 */
	private int readSnapshot(Path output, Path path) {

		if (!Files.exists(path))
			throw new IllegalArgumentException("Snapshot " + path + " does not exist.");

		InfectionEventHandler handler = handlerProvider.get();
		EpisimReporting reporting = reportingProvider.get();
		ProgressionModel progressionModel = progressionProvider.get();

		int iteration = -1;
		try (var in = Files.newInputStream(path)) {

			ArchiveInputStream archive = new ArchiveStreamFactory()
					.createArchiveInputStream("zip", in);

			log.info("Copying output from {} into {}", path, output);

			ArchiveEntry entry;
			while ((entry = archive.getNextEntry()) != null) {
				String name = entry.getName();

				// copy to output
				if (name.startsWith("output"))
					Files.copy(archive, output.resolve(name.replace("output/", "")), StandardCopyOption.REPLACE_EXISTING);

				if (name.equals("iteration")) {
					ObjectInputStream ois = new ObjectInputStream(archive);
					iteration = ois.readInt();
				}

				if (name.equals("state")) {
					ObjectInputStream ois = new ObjectInputStream(archive);
					handler.readExternal(ois);
				}

				if (name.equals("reporting")) {
					ObjectInputStream ois = new ObjectInputStream(archive);
					reporting.readExternal(ois);
				}

				if (name.equals("progression")) {
					ObjectInputStream ois = new ObjectInputStream(archive);
					if (progressionModel instanceof Externalizable)
						((Externalizable) progressionModel).readExternal(ois);
					else
						log.warn("Progression state present, but model is not Externalizable");
				}
			}

			archive.close();

			return iteration;

		} catch (IOException | ArchiveException | ClassNotFoundException e) {
			throw new IllegalStateException("Could not read snapshot", e);
		}

	}

	/**
	 * Helper method to write object into archive,
	 */
	private void writeObject(Externalizable obj, String name, ArchiveOutputStream archive) throws IOException {
		archive.putArchiveEntry(new ZipArchiveEntry(name));
		ObjectOutputStream oos = new ObjectOutputStream(archive);
		obj.writeExternal(oos);
		oos.flush();
		archive.closeArchiveEntry();
	}

}
