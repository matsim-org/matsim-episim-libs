 /* project: org.matsim.*
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


 import com.google.common.base.Joiner;
 import com.google.inject.Inject;
 import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
 import it.unimi.dsi.fastutil.doubles.DoubleList;
 import it.unimi.dsi.fastutil.ints.*;
 import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
 import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
 import org.apache.logging.log4j.Level;
 import org.apache.logging.log4j.LogManager;
 import org.apache.logging.log4j.Logger;
 import org.apache.logging.log4j.core.config.Configurator;
 import org.matsim.api.core.v01.Coord;
 import org.matsim.api.core.v01.Id;
 import org.matsim.api.core.v01.IdMap;
 import org.matsim.api.core.v01.Scenario;
 import org.matsim.api.core.v01.population.Person;
 import org.matsim.api.core.v01.population.Population;
 import org.matsim.core.config.Config;
 import org.matsim.core.config.ConfigUtils;
 import org.matsim.core.population.PopulationUtils;
 import org.matsim.core.utils.geometry.CoordUtils;
 import org.matsim.core.utils.geometry.CoordinateTransformation;
 import org.matsim.core.utils.geometry.transformations.TransformationFactory;
 import org.matsim.episim.*;
 import org.matsim.episim.events.*;
 import org.matsim.episim.model.VirusStrain;
 import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
 import org.matsim.run.AnalysisCommand;
 import picocli.CommandLine;

 import java.io.BufferedWriter;
 import java.io.IOException;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.time.LocalDate;
 import java.util.*;
 import java.util.stream.Collectors;


 /**
  * @author smueller
  * Calcualte vaccination effectiveness from events
  */
 @CommandLine.Command(
		 name = "infHomeLoc",
		 description = "Finds the home location of infected persons."
 )
 public class InfectionHomeLocation implements OutputAnalysis {

	 @CommandLine.Option(names = "--output", defaultValue = "./output/")
	 private Path output;

	 @CommandLine.Option(names = "--input", defaultValue = "/scratch/projects/bzz0020/episim-input")
	 private String input;

	 @CommandLine.Option(names = "--population-file", defaultValue = "/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz")
	 private String populationFile;

	 @CommandLine.Option(names = "--input-crs", defaultValue = "EPSG:25832")

	 private String inputCRS;

	 private static final Logger log = LogManager.getLogger(HospitalNumbersFromEvents.class);

	 private final String DAY = "daysSinceStart";

	 private final String HOME_LON = "home_lon";

	 private final String HOME_LAT = "home_lat";

	 @Inject
	 private Scenario scenario;

	 private Population population;
	 public static void main(String[] args) {
		 System.exit(new CommandLine(new InfectionHomeLocation()).execute(args));
	 }

	 @Override
	 public Integer call() throws Exception {
		 // logger configuration
		 Configurator.setLevel("org.matsim.core.config", Level.WARN);
		 Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		 Configurator.setLevel("org.matsim.core.events", Level.WARN);
		 Configurator.setLevel("org.matsim.core.utils", Level.WARN);

		 // check if events file exists
		 if (!Files.exists(output)) {
			 log.error("Output path {} does not exist.", output);
			 return 2;
		 }

		 // read population
		 population = PopulationUtils.readPopulation(input + populationFile);


		 // Part 1: calculate home locs for each seed and save as csv
		 AnalysisCommand.forEachScenario(output, pathToScenario -> {
			 try {
				 // analyzeOutput is where the post processing
				 analyzeOutput(pathToScenario);

			 } catch (IOException e) {
				 log.error("Failed processing {}", pathToScenario, e);
			 }
		 });

		 log.info("done");


		 return 0;
	 }

	 @Override
	 public void analyzeOutput(Path pathToScenario) throws IOException {

		 if (scenario != null)
			 population = scenario.getPopulation();

		 String id = AnalysisCommand.getScenarioPrefix(pathToScenario);

		 // builds the path to the output file that is produced by this analysis
		 final Path csvPath = pathToScenario.resolve(id + "infectionLoc.csv");
		 CoordinateTransformation coordinateTransformation = TransformationFactory.getCoordinateTransformation(inputCRS, TransformationFactory.WGS84);;


		 // calculates home locations
		 try (BufferedWriter bw = Files.newBufferedWriter(csvPath)) {
			 Joiner joiner = Joiner.on(",");
			 bw.write(joiner.join(DAY, HOME_LON, HOME_LAT));
			 Handler handler = new Handler(population, coordinateTransformation);

			 List<String> eventFiles = AnalysisCommand.forEachEvent(pathToScenario, s -> {
			 }, true, handler);


			 if (handler.days.size() != handler.lons.size() || handler.days.size() != handler.lats.size()) {
				 throw new RuntimeException("all three datasets should have same size");
			 }

			 for (int i = 0; i < handler.days.size(); i++) {
				 bw.newLine();
				 bw.write(joiner.join(handler.days.getInt(i), handler.lons.get(i), handler.lats.get(i)));
			 }
		 }

		 log.info("Calculated results for output {}", pathToScenario);

	 }


	 public static final class Handler implements EpisimInfectionEventHandler, EpisimInitialInfectionEventHandler{
		 private final Population population;

		 private final IntList days;
		 private final List<Double> lats;
		 private final List<Double> lons;

		 private final CoordinateTransformation coordinateTransformation;


		 Handler(Population population, CoordinateTransformation coordinateTransformation) {

			 this.population = population;
			 this.coordinateTransformation = coordinateTransformation;
			 this.days = new IntArrayList();
			 this.lats = new ArrayList<>();
			 this.lons = new ArrayList<>();

		 }


		 @Override
		 public void handleEvent(EpisimInfectionEvent event) {

			 int day = (int) (event.getTime() / 86_400);
			 days.add(day);

			 double home_x =  (double) population.getPersons().get(event.getPersonId()).getAttributes().getAttribute("homeX");
			 double home_y = (double) population.getPersons().get(event.getPersonId()).getAttributes().getAttribute("homeY");

			 Coord coord = new Coord(home_x, home_y);
			 Coord coordWgs84 = coordinateTransformation.transform(coord);

			 lats.add(Math.round(coordWgs84.getY() * 10000.0) / 10000.0);
			 lons.add(Math.round(coordWgs84.getX() * 10000.0) / 10000.0);

		 }

		 @Override
		 public void handleEvent(EpisimInitialInfectionEvent event) {
			 handleEvent(event.asInfectionEvent());
		 }


	 }
 }
