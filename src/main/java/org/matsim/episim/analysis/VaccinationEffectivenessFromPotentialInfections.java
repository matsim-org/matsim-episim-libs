/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.episim.analysis;


import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.events.*;
import org.matsim.run.AnalysisCommand;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


@CommandLine.Command(
		name = "vaccinationEffectiveness",
		description = "Calculate vaccination effectiveness"
)
public class VaccinationEffectivenessFromPotentialInfections implements OutputAnalysis {

	private static final Logger log = LogManager.getLogger(VaccinationEffectivenessFromPotentialInfections.class);

	@CommandLine.Option(names = "--output", defaultValue = "./output/")
	private Path output;

	@CommandLine.Option(names = "--remove-infected", defaultValue = "false", description = "Remove infected persons from effectiveness calculation")
	private boolean removeInfected;

	@CommandLine.Option(names = "--min-sample", defaultValue = "100", description = "Filter entries with sample size below threshold")
	private int minSample;

	public static void main(String[] args) {
		System.exit(new CommandLine(new VaccinationEffectivenessFromPotentialInfections()).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		Configurator.setLevel("org.matsim.core.config", Level.WARN);
		Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		Configurator.setLevel("org.matsim.core.events", Level.WARN);
		Configurator.setLevel("org.matsim.core.utils", Level.WARN);

		if (!Files.exists(output)) {
			log.error("Output path {} does not exist.", output);
			return 2;
		}

		AnalysisCommand.forEachScenario(output, scenario -> {
			try {
				analyzeOutput(scenario);
			} catch (IOException e) {
				log.error("Failed processing {}", scenario, e);
			}
		});

		log.info("done");

		return 0;
	}

	@Override
	public void analyzeOutput(Path output) throws IOException {

		String id = AnalysisCommand.getScenarioPrefix(output);

		Handler handler = new Handler();

		AnalysisCommand.forEachEvent(output, s -> {}, false, handler);

		// Entries with rarely used vaccines are filtered
		List<String> collect = new ArrayList<>(handler.vac.keySet());

		try (CSVPrinter csv = new CSVPrinter(Files.newBufferedWriter(output.resolve(id + "post.vaccineEff.tsv")), CSVFormat.TDF)) {

			csv.print("day");
			for (String s1 : collect) {
				csv.print(s1);
			}

			csv.println();

			int x = handler.vac.values().stream().flatMapToInt(s -> s.keySet().intStream()).max().orElse(0);

			for (int i = 0; i <= x; i++) {

				csv.print(i);

				for (String k : collect) {

					double a = handler.vac.get(k).get(i);
					double b = handler.unVac.get(k).get(i);

					if (handler.n.get(k).get(i) >= minSample)
						csv.print((b - a) / b);
					else
						csv.print(Double.NaN);
				}

				csv.println();
			}
		}

		log.info("Calculated results for scenario {}", output);
	}

	private final class Handler implements EpisimInfectionEventHandler, EpisimPotentialInfectionEventHandler, EpisimVaccinationEventHandler {

		private final Object2IntMap<Id<Person>> vaccinationDay = new Object2IntOpenHashMap<>();
		private final Map<Id<Person>, String> vaccine = new HashMap<>();
		private final Set<Id<Person>> infected = new HashSet<>();
		private final Set<Id<Person>> threeTimesVaccinated = new HashSet<>();


		private final Map<String, Int2DoubleMap> vac = new HashMap<>();
		private final Map<String, Int2DoubleMap> unVac = new HashMap<>();
		private final Map<String, Int2IntMap> n = new HashMap<>();

		@Override
		public void handleEvent(EpisimInfectionEvent event) {

			if (removeInfected)
				infected.add(event.getPersonId());
		}

		@Override
		public void handleEvent(EpisimVaccinationEvent event) {
			int day = (int) (event.getTime() / 86_400);


			String s = event.getVaccinationType().toString();
			if (event.getN() == 2)
				s += "_Booster";
			if (event.getN() > 2) {
				threeTimesVaccinated.add(event.getPersonId());
				return;
			}


			vaccinationDay.put(event.getPersonId(), day);
			vaccine.put(event.getPersonId(), s);
		}


		@Override
		public void handleEvent(EpisimPotentialInfectionEvent event) {

			int day = (int) (event.getTime() / 86_400);

			int daysSinceVaccination = day - vaccinationDay.getInt(event.getPersonId());

			if (!vaccine.containsKey(event.getPersonId()))
				return;

			if (infected.contains(event.getPersonId()))
				return;

			if (threeTimesVaccinated.contains(event.getPersonId()))
				return;

			String s = vaccine.get(event.getPersonId());
			String strain = event.getStrain().toString();

			String strainVaccine = s + "-" + strain;

			vac.computeIfAbsent(strainVaccine, (k) -> new Int2DoubleOpenHashMap())
					.mergeDouble(daysSinceVaccination, event.getProbability(), Double::sum);

			unVac.computeIfAbsent(strainVaccine, (k) -> new Int2DoubleOpenHashMap())
					.mergeDouble(daysSinceVaccination, event.getUnVacProbability(), Double::sum);

			n.computeIfAbsent(strainVaccine, (k) -> new Int2IntOpenHashMap())
					.mergeInt(daysSinceVaccination, 1, Integer::sum);

		}
	}
}




