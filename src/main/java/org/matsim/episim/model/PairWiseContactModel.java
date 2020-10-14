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

import static org.matsim.episim.EpisimPerson.DiseaseStatus;
import static org.matsim.episim.InfectionEventHandler.EpisimFacility;
import static org.matsim.episim.InfectionEventHandler.EpisimVehicle;

/**
 * Model where persons are only interacting pairwise.
 */
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
	public void infectionDynamicsFacility(EpisimPerson personLeavingFacility, EpisimFacility facility, double now, String actType) {
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
			if (checkPersonInContainer(personEnteringContainer, container, getRestrictions(), rnd)) {
				contacts.computeIfAbsent(container, (k) -> new HashSet<>()).add(personEnteringContainer);
			}
		} catch (IndexOutOfBoundsException | NullPointerException e) {
			// these exceptions happen during init and are ignored
		}
	}

	private void infectionDynamicsGeneralized(EpisimPerson personLeavingContainer, EpisimContainer<?> container, double now) {
		// no infection possible if there is only one person
		if (iteration == 0 || container.getPersons().size() == 1) {

			if (contacts.containsKey(container))
				contacts.get(container).remove(personLeavingContainer);

			return;
		}

		// person leaving was already a contact, or never present
		if (!contacts.containsKey(container) || !contacts.get(container).contains(personLeavingContainer)) {
			return;
		}

		// start tracking late as possible because of computational costs
		boolean trackingEnabled = iteration >= trackingAfterDay;

		contacts.get(container).remove(personLeavingContainer);
		contactPersons.addAll(contacts.get(container));

		if (contactPersons.size() == 0)
			return;

		EpisimPerson contactPerson = contactPersons.get(rnd.nextInt(contactPersons.size()));
		contacts.get(container).remove(contactPerson);
		contactPersons.clear();

		if (!personHasRelevantStatus(personLeavingContainer) || !personHasRelevantStatus(contactPerson)) {
			return;
		}

		// we have thrown the random numbers, so we can bail out in some cases if we are not tracking:
		if (!trackingEnabled) {
			if (personLeavingContainer.getDiseaseStatus() == DiseaseStatus.infectedButNotContagious) {
				return;
			}
			if (contactPerson.getDiseaseStatus() == DiseaseStatus.infectedButNotContagious) {
				return;
			}
			if (personLeavingContainer.getDiseaseStatus() == contactPerson.getDiseaseStatus()) {
				return;
			}
		} else if (!traceSusceptible && personLeavingContainer.getDiseaseStatus() == DiseaseStatus.susceptible
				&& contactPerson.getDiseaseStatus() == DiseaseStatus.susceptible)
			return;

		String leavingPersonsActivity = personLeavingContainer.getTrajectory().get(personLeavingContainer.getCurrentPositionInTrajectory()).actType;
		String otherPersonsActivity = contactPerson.getTrajectory().get(contactPerson.getCurrentPositionInTrajectory()).actType;

		StringBuilder infectionType = getInfectionType(buffer, container, leavingPersonsActivity, otherPersonsActivity);

		double containerEnterTimeOfPersonLeaving = container.getContainerEnteringTime(personLeavingContainer.getPersonId());
		double containerEnterTimeOfOtherPerson = container.getContainerEnteringTime(contactPerson.getPersonId());
		double jointTimeInContainer = now - Math.max(containerEnterTimeOfPersonLeaving, containerEnterTimeOfOtherPerson);

		//forbid certain cross-activity interactions, keep track of contacts
		if (container instanceof EpisimFacility) {
			//home can only interact with home, leisure or work
			if (infectionType.indexOf("home") >= 0 && infectionType.indexOf("leis") == -1 && infectionType.indexOf("work") == -1
					&& !(leavingPersonsActivity.startsWith("home") && otherPersonsActivity.startsWith("home"))) {
				// yyyyyy we need to move out of these string convention based rules in code.  kai, aug'20
				return;
			} else if (infectionType.indexOf("edu") >= 0 && infectionType.indexOf("work") == -1 && !(leavingPersonsActivity.startsWith("edu") && otherPersonsActivity.startsWith("edu"))) {
				//edu can only interact with work or edu
				// yyyyyy we need to move out of these string convention based rules in code.  kai, aug'20
				return;
			}
			if (trackingEnabled) {
				trackContactPerson(personLeavingContainer, contactPerson, now, jointTimeInContainer, infectionType);
			}

			// Only a subset of contacts are reported at the moment
			// tracking has to be enabled to report more contacts
			reporting.reportContact(now, personLeavingContainer, contactPerson, container, infectionType, jointTimeInContainer);
		}

		if (!AbstractContactModel.personsCanInfectEachOther(personLeavingContainer, contactPerson)) {
			return;
		}

		// person can only infect others 4 days after being contagious
		if ((personLeavingContainer.hadDiseaseStatus(DiseaseStatus.contagious) &&
				personLeavingContainer.daysSince(DiseaseStatus.contagious, iteration) > 4)
				|| (contactPerson.hadDiseaseStatus(DiseaseStatus.contagious) &&
				contactPerson.daysSince(DiseaseStatus.contagious, iteration) > 4))
			return;

		// persons leaving their first-ever activity have no starting time for that activity.  Need to hedge against that.  Since all persons
		// start healthy (the first seeds are set at enterVehicle), we can make some assumptions.
		if (containerEnterTimeOfPersonLeaving < 0 && containerEnterTimeOfOtherPerson < 0) {
			throw new IllegalStateException("should not happen");
			// should only happen at first activity.  However, at first activity all persons are susceptible.  So the only way we
			// can get here is if an infected person entered the container and is now leaving again, while the other person has been in the
			// container from the beginning.  ????  kai, mar'20
		}

		if (jointTimeInContainer < 0 || jointTimeInContainer > 86400 * 7) {
			log.warn(containerEnterTimeOfPersonLeaving);
			log.warn(containerEnterTimeOfOtherPerson);
			log.warn(now);
			throw new IllegalStateException("joint time in container is not plausible for personLeavingContainer=" + personLeavingContainer.getPersonId() + " and contactPerson=" + contactPerson.getPersonId() + ". Joint time is=" + jointTimeInContainer);
		}


		EpisimConfigGroup.InfectionParams leavingParams = getInfectionParams(container, personLeavingContainer, leavingPersonsActivity);

		// activity params of the contact person and leaving person
		EpisimConfigGroup.InfectionParams contactParams = getInfectionParams(container, contactPerson, otherPersonsActivity);

		// need to differentiate which person might be the infector
		if (personLeavingContainer.getDiseaseStatus() == DiseaseStatus.susceptible) {

			double prob = infectionModel.calcInfectionProbability(personLeavingContainer, contactPerson, getRestrictions(),
					leavingParams, contactParams, jointTimeInContainer);
			if (rnd.nextDouble() < prob)
				infectPerson(personLeavingContainer, contactPerson, now, infectionType, container);

		} else {
			double prob = infectionModel.calcInfectionProbability(contactPerson, personLeavingContainer, getRestrictions(),
					contactParams, leavingParams, jointTimeInContainer);

			if (rnd.nextDouble() < prob)
				infectPerson(contactPerson, personLeavingContainer, now, infectionType, container);
		}
//		}
	}
}
