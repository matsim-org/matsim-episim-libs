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
import org.matsim.core.config.Config;
import org.matsim.episim.*;

import java.util.*;

import static org.matsim.episim.InfectionEventHandler.EpisimFacility;
import static org.matsim.episim.InfectionEventHandler.EpisimVehicle;

/**
 * Model where persons are only interacting pairwise.
 */
@Deprecated
public final class PairWiseContactModel extends AbstractContactModel {

	private static final Logger log = LogManager.getLogger(PairWiseContactModel.class);

	/**
	 * Flag to enable tracking, which is considerably slower.
	 */
	private final int trackingAfterDay;

	/**
	 * Whether to trace susceptible persons.
	 */
	private final boolean traceSusceptible;

	/**
	 * This buffer is used to store the infection type.
	 */
	private final StringBuilder buffer = new StringBuilder();

	/**
	 * Reusable list for contact persons.
	 */
	private final List<EpisimPerson> contactPersons = new ArrayList<>();

	private final Map<EpisimContainer<?>, Set<EpisimPerson>> contacts = new IdentityHashMap<>();

	@Inject
		/*package*/ PairWiseContactModel(SplittableRandom rnd, Config config, TracingConfigGroup tracingConfig,
										 EpisimReporting reporting, InfectionModel infectionModel) {
		super(rnd, config, infectionModel, reporting);
		this.trackingAfterDay = tracingConfig.getPutTraceablePersonsInQuarantineAfterDay();
		this.traceSusceptible = tracingConfig.getTraceSusceptible();
	}

	@Override
	public void infectionDynamicsVehicle(EpisimPerson personLeavingVehicle, EpisimVehicle vehicle, double now) {
		infectionDynamicsGeneralized(personLeavingVehicle, vehicle, now);
	}

	@Override
	public void infectionDynamicsFacility(EpisimPerson personLeavingFacility, EpisimFacility facility, double now) {
		infectionDynamicsGeneralized(personLeavingFacility, facility, now);
	}

	@Override
	public void notifyEnterVehicle(EpisimPerson personEnteringVehicle, EpisimVehicle vehicle, double now) {
		notifyEnterContainerGeneralized(personEnteringVehicle, vehicle, now);
	}

	@Override
	public void notifyEnterFacility(EpisimPerson personEnteringFacility, EpisimFacility facility, double now) {
		notifyEnterContainerGeneralized(personEnteringFacility, facility, now);
	}

	private void notifyEnterContainerGeneralized(EpisimPerson personEnteringContainer, EpisimContainer<?> container, double now) {
		try {
			if (checkPersonInContainer(now, personEnteringContainer, container, getRestrictions(), rnd)) {
				contacts.computeIfAbsent(container, (k) -> new HashSet<>()).add(personEnteringContainer);
			}
		} catch (IndexOutOfBoundsException | NullPointerException e) {
			// these exceptions happen during init and are ignored
		}
	}

	private void infectionDynamicsGeneralized(EpisimPerson personLeavingContainer, EpisimContainer<?> container, double now) {
		throw new UnsupportedOperationException();
	}
}
