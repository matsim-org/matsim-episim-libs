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
public final class DirectContactModel extends AbstractContactModel {

	private static final Logger log = LogManager.getLogger(DirectContactModel.class);

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

	private final Map<EpisimContainer<?>, EpisimPerson> singlePersons = new IdentityHashMap<>();
	private final Map<EpisimContainer<?>, List<Group>> groups = new IdentityHashMap<>();

	@Inject
		/*package*/ DirectContactModel(SplittableRandom rnd, Config config, TracingConfigGroup tracingConfig,
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

		// this can happen because persons are not removed during initialization
		if (findGroup(container, personEnteringContainer) != null)
			return;

		// for same reason a person currently at home will enter again
		if (!singlePersons.containsKey(container) || singlePersons.get(container) == personEnteringContainer) {
			singlePersons.put(container, personEnteringContainer);
		} else {
			groups.computeIfAbsent(container, k -> new ArrayList<>())
					.add(Group.of(personEnteringContainer, singlePersons.get(container), now));
			singlePersons.remove(container);
		}
	}

	private Group findGroup(EpisimContainer<?> container, EpisimPerson person) {

		if (!groups.containsKey(container))
			return null;

		for (Group group : groups.get(container)) {
			if (group.contains(person)) {
				return group;
			}
		}

		return null;
	}

	private void infectionDynamicsGeneralized(EpisimPerson personLeavingContainer, EpisimContainer<?> container, double now) {
		throw new UnsupportedOperationException();
	}

	/**
	 * A group of two persons and time when the group was formed.
	 */
	private static final class Group {

		private final EpisimPerson a;
		private final EpisimPerson b;
		private final double time;

		private Group(EpisimPerson a, EpisimPerson b, double time) {
			this.a = a;
			this.b = b;
			this.time = time;
		}

		private static Group of(EpisimPerson a, EpisimPerson b, double time) {
			return new Group(a, b, time);
		}

		public boolean contains(EpisimPerson person) {
			return a == person || b == person;
		}

		/**
		 * Return the left over person.
		 */
		public EpisimPerson remove(EpisimPerson p) {
			if (p == a) return b;
			else if (p == b) return a;
			throw new IllegalStateException("Leaving person not in group.");
		}
	}

}
