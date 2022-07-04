package org.matsim.episim.model.listener;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.model.SimulationListener;
import org.matsim.episim.model.VirusStrain;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.io.BufferedWriter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Writes antibody leven into a matrix
 */
public class WriteAntibodies implements SimulationListener {

	private Map<Id<Person>, EpisimPerson> persons;
	@Inject
	private EpisimReporting reporting;

	@Override
	public void init(SplittableRandom rnd, Map<Id<Person>, EpisimPerson> persons, Map<Id<ActivityFacility>, InfectionEventHandler.EpisimFacility> facilities, Map<Id<Vehicle>, InfectionEventHandler.EpisimVehicle> vehicles) {
		this.persons = persons;
	}

	@Override
	public void onIterationEnd(int iteration, LocalDate date) {

		String header = VirusStrain.class.getSimpleName() + "\t" + IntStream.range(0, 120).mapToObj(String::valueOf).collect(Collectors.joining("\t")) + "\n";

		BufferedWriter writer = reporting.registerWriter(String.format("antibodiesPerAge-%s.tsv", date.toString()));

		reporting.writeAsync(writer, header);

		for (VirusStrain strain : VirusStrain.values()) {

			// Rolling mean per age group
			int[] n = new int[120];
			double[] values = new double[120];

			for (EpisimPerson p : persons.values()) {

				int i = p.getAge();
				n[i]++;
				values[i] = values[i] + (p.getAntibodies(strain) - values[i]) / n[i];

			}

			String row = strain.name() + "\t" + Arrays.stream(values).mapToObj(String::valueOf).collect(Collectors.joining("\t")) + "\n";

			reporting.writeAsync(writer, row);
		}

		reporting.closeAsync(writer);
	}

}
