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
package org.matsim.episim.model;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.episim.EpisimConfigGroup;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.matsim.episim.model.SymmetricContactModelWithOdeCoupling.CommuterQuarantineMode;

/**
 * Immutable, simulation-scoped selection for special contact isolation. Explicitly
 * bound in EpisimModule so copied trajectory injectors share the same instance.
 * Reconstructed from seed/config/input on snapshot restore; no person cache or
 * snapshot-format change is required.
 */
public final class InterregionalIsolationPolicy {
	private static final Logger log = LogManager.getLogger(InterregionalIsolationPolicy.class);

	// ASCII "ISOLATIO": domain separation from epidemic RNGs. Never consume their state.
	private static final long POLICY_SEED_SALT = 0x49534f4c4154494fL;

	private final long firstIsolationIteration;
	private final Set<Id<Person>> isolatedPersons;

	@Inject
	public InterregionalIsolationPolicy(Config config, EpisimConfigGroup episimConfig, Scenario scenario) {
		double share = episimConfig.getCommuterQuarantineShare();
		if (!Double.isFinite(share) || share < 0 || share > 1) {
			throw new IllegalArgumentException("commuterQuarantineShare must be finite and in [0, 1]: " + share);
		}
		CommuterQuarantineMode mode = Objects.requireNonNull(episimConfig.getCommuterQuarantineMode(),
			"commuterQuarantineMode");
		if (mode == CommuterQuarantineMode.OFF || share == 0) {
			firstIsolationIteration = Long.MAX_VALUE;
			isolatedPersons = Collections.emptySet();
			return;
		}

		LocalDate isolationDate = LocalDate.parse(Objects.requireNonNull(episimConfig.getCommuterQuarantineDate(),
			"commuterQuarantineDate is required for active isolation"));
		// Convert the inclusive calendar date once, avoiding date allocations per contact.
		firstIsolationIteration = ChronoUnit.DAYS.between(episimConfig.getStartDate(), isolationDate) + 1;
		Map<String, Id<Person>> populationIds = new HashMap<>();
		for (Id<Person> id : scenario.getPopulation().getPersons().keySet()) {
			populationIds.put(id.toString(), id);
		}

		Set<String> seen = new HashSet<>();
		List<Id<Person>> travelers = new ArrayList<>();
		int duplicates = 0;
		int unmatched = 0;
		Path input = Path.of(Objects.requireNonNull(episimConfig.getCommuterQuarantineInput(),
			"commuterQuarantineInput is required for active isolation"));
		try (BufferedReader reader = Files.newBufferedReader(input)) {
			if (reader.readLine() == null) {
				throw new IllegalArgumentException("Traveler input is missing its header: " + input);
			}
			String line;
			while ((line = reader.readLine()) != null) {
				String value = line.trim();
				if (!seen.add(value)) {
					duplicates++;
					continue;
				}
				Id<Person> id = populationIds.get(value);
				if (id == null) unmatched++;
				else travelers.add(id);
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read traveler input: " + input, e);
		}

		int count = (int) Math.floor(travelers.size() * share);
		List<Id<Person>> candidates = mode == CommuterQuarantineMode.COMMUTERS
			? travelers : new ArrayList<>(scenario.getPopulation().getPersons().keySet());
		candidates.sort(Comparator.comparing(Id::toString));
		SplittableRandom policyRandom = new SplittableRandom(config.global().getRandomSeed() ^ POLICY_SEED_SALT);
		// Always shuffle the full list; prefixes are nested across policy shares.
		for (int i = 0; i < candidates.size(); i++) {
			Collections.swap(candidates, i, policyRandom.nextInt(i, candidates.size()));
		}
		isolatedPersons = Collections.unmodifiableSet(new HashSet<>(candidates.subList(0, count)));
		log.info("Contact isolation: mode={}, eligible travelers={}, duplicates={}, unmatched={}, selected={}, starts={}",
			mode, travelers.size(), duplicates, unmatched, isolatedPersons.size(), isolationDate);
	}

	/** Iteration 1 is startDate. Iteration 0 only initializes trajectories. */
	public boolean isIsolated(Id<Person> personId, int iteration) {
		return iteration > 0 && iteration >= firstIsolationIteration && isolatedPersons.contains(personId);
	}

	/** Immutable membership, independent of the current date and handler. */
	public Set<Id<Person>> getSelectedPersons() {
		return isolatedPersons;
	}
}
