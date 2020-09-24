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

import com.google.common.collect.Sets;
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
	private final Map<EpisimContainer<?>, List<Set<EpisimPerson>>> groups = new IdentityHashMap<>();

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
	public void infectionDynamicsFacility(EpisimPerson personLeavingFacility, EpisimFacility facility, double now, String actType) {
		infectionDynamicsGeneralized(personLeavingFacility, facility, now);
	}

	@Override
	public void notifyEnterVehicle(EpisimPerson personEnteringVehicle, EpisimVehicle vehicle, double now) {
		notifyEnterContainerGeneralized(personEnteringVehicle, vehicle, now);
	}

	@Override
	public void notifyEnterFacility(EpisimPerson personEnteringFacility, EpisimFacility facility, double now, String actType) {
		notifyEnterContainerGeneralized(personEnteringFacility, facility, now);
	}

	private void notifyEnterContainerGeneralized(EpisimPerson personEnteringContainer, EpisimContainer<?> container, double now) {
		if (!singlePersons.containsKey(container)) {
			singlePersons.put(container, personEnteringContainer);
		} else {
			groups.computeIfAbsent(container, k -> new ArrayList<>())
					.add(Sets.newHashSet(personEnteringContainer, singlePersons.get(container)));
			singlePersons.remove(container);
		}
	}

	private Set<EpisimPerson> findGroup(EpisimContainer<?> container, EpisimPerson person) {
		// will crash if there are not groups for container
		for (Set<EpisimPerson> group : groups.get(container)) {
			if (group.contains(person)) {
				return group;
			}
		}
		return null;
	}

	private void infectionDynamicsGeneralized(EpisimPerson personLeavingContainer, EpisimContainer<?> container, double now) {
		// no infection possible if there is only one person
		if (iteration == 0 || container.getPersons().size() == 1) {
			return;
		}

		if (singlePersons.get(container) == personLeavingContainer) {
			singlePersons.remove(container);
			return;
		}

		if (!personRelevantForTrackingOrInfectionDynamics(personLeavingContainer, container, getRestrictions(), rnd)) {
			removePersonFromGroups(container, personLeavingContainer);
			// yyyyyy hat in diesem Modell die Konsequenz, dass, wenn jemand zu Hause bleibt, die andere Person alleine rumsitzt.  Somewhat plausible in public
			// transport; not plausible in restaurant.
			return;
		}

		// start tracking late as possible because of computational costs
		boolean trackingEnabled = iteration >= trackingAfterDay;

		EpisimConfigGroup.InfectionParams leavingParams = null;

		Set<EpisimPerson> group = findGroup(container, personLeavingContainer);
		groups.get(container).remove(group);
		group.remove(personLeavingContainer);
		EpisimPerson contactPerson = group.iterator().next();

		// TODO: this overwrites other single persons, who won't be assigned to any group
		singlePersons.put(container, contactPerson);
		group.clear();


//		for( EpisimPerson contactPerson : container.getPersons() ){

		// no contact with self, especially no tracing
//			if (personLeavingContainer == contactPerson) {
//				continue;
//			}

//			int maxPersonsInContainer = container.getMaxGroupSize();
//			if ( container instanceof EpisimVehicle ) {
//				maxPersonsInContainer = container.getTypicalCapacity();
//				if ( container.getMaxGroupSize() > container.getTypicalCapacity() ) {
//					log.warn("yyyyyy: vehicleId={}: maxGroupSize={} is larger than typicalCapacity={}; need to find organized answer to this.",
//							container.getContainerId(), container.getMaxGroupSize(), container.getTypicalCapacity() );
//				}
//			}
//			Gbl.assertIf( maxPersonsInContainer>1 );
		// ==1 should not happen because if ever not more than 1 person in container, then method exits already earlier.  ???

//			if ( rnd.nextDouble() >= episimConfig.getMaxContacts()/(maxPersonsInContainer-1) ) {
//				continue;
//			}
		// since every pair of persons interacts only once, there is now a constant interaction probability per pair
		// if we want superspreading events, then maxInteractions needs to be much larger than 3 or 10.

		if (!personRelevantForTrackingOrInfectionDynamics(contactPerson, container, getRestrictions(), rnd)) {
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


		// Parameter will only be retrieved one time
		if (leavingParams == null)
			leavingParams = getInfectionParams(container, personLeavingContainer, leavingPersonsActivity);

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

	private void removePersonFromGroups(EpisimContainer<?> container, EpisimPerson personLeavingContainer) {
		if (singlePersons.get(container) == personLeavingContainer) {
			singlePersons.remove(container);
		} else {
			Set<EpisimPerson> group = findGroup(container, personLeavingContainer);
			group.remove(personLeavingContainer);
			singlePersons.put(container, group.iterator().next());
			groups.get(container).remove(group);
			group.clear();
		}
	}

}
