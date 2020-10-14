package org.matsim.scenarioCreation;


import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "createVehicleFiles",
		description = "Creates vehicle types and vehicles from provided csv file"
)
public class CreateVehicleFiles implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(CreateVehicleFiles.class);

	@CommandLine.Parameters(arity = "1", description = "Path to input csv file.")
	private Path input;

	@CommandLine.Option(names = "--output", description = "Output path", defaultValue = "vehicles.xml")
	private Path output;

	public static void main(String[] args) {
		System.exit(new CommandLine(new CreateVehicleFiles()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		if (!Files.exists(input)) {
			log.error("Input file {} does not exist.", input);
			return 1;
		}

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Vehicles vehicles = scenario.getVehicles();


		try (var reader = IOUtils.getBufferedReader(input.toString())) {

			CSVParser parse = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);

			log.info("Parsed header: {}", parse.getHeaderNames());

			for (CSVRecord record : parse) {
				Id<VehicleType> typeId = Id.create(record.get("ptType"), VehicleType.class);
				Id<Vehicle> vehicleId = Id.createVehicleId(record.get("vehicleId"));

				if (!vehicles.getVehicleTypes().containsKey(typeId)) {
					VehicleType type = vehicles.getFactory().createVehicleType(typeId);
					type.getCapacity().setStandingRoom(0);
					type.getCapacity().setSeats(10);

					vehicles.addVehicleType(type);
				}

				VehicleType type = vehicles.getVehicleTypes().get(typeId);
				Vehicle vehicle = vehicles.getFactory().createVehicle(vehicleId, type);

				vehicles.addVehicle(vehicle);
			}
		}

		log.info("Read {} vehicles with {} types", vehicles.getVehicles().size(), vehicles.getVehicleTypes().size());

		MatsimVehicleWriter writer = new MatsimVehicleWriter(vehicles);

		writer.writeFile(output.toString());

		return 0;
	}
}
