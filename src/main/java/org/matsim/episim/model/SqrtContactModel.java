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
import org.matsim.core.gbl.Gbl;
import org.matsim.episim.*;

import java.util.SplittableRandom;

import static org.matsim.episim.EpisimPerson.DiseaseStatus;

/**
 * Default interaction model executed, when a person ends his activity.
 * Infections probabilities calculations are delegated to a {@link InfectionModel}.
 */
public final class SqrtContactModel extends AbstractContactModel {

	private static final Logger log = LogManager.getLogger( SqrtContactModel.class );

	/**
	 * Flag to enable tracking, which is considerably slower.
	 */
	private final int trackingAfterDay;

	/**
	 * This buffer is used to store the infection type.
	 */
	private final StringBuilder buffer = new StringBuilder();

	@Inject
	/* package */
	SqrtContactModel( SplittableRandom rnd, Config config, TracingConfigGroup tracingConfig,
			  EpisimReporting reporting, InfectionModel infectionModel ) {
		// (make injected constructor non-public so that arguments can be changed without repercussions.  kai, jun'20)
		super(rnd, config, infectionModel, reporting);
		this.trackingAfterDay = tracingConfig.getPutTraceablePersonsInQuarantineAfterDay();
	}

	@Override
	public void infectionDynamicsVehicle(EpisimPerson personLeavingVehicle, InfectionEventHandler.EpisimVehicle vehicle, double now) {
		infectionDynamicsGeneralized(personLeavingVehicle, vehicle, now);
	}

	@Override
	public void infectionDynamicsFacility(EpisimPerson personLeavingFacility, InfectionEventHandler.EpisimFacility facility, double now, String actType) {
		infectionDynamicsGeneralized(personLeavingFacility, facility, now);
	}

	private void infectionDynamicsGeneralized(EpisimPerson personLeavingContainer, EpisimContainer<?> container, double now) {

		// no infection possible if there is only one person
		if (iteration == 0 || container.getPersons().size() == 1) {
			return;
		}

		if (!personRelevantForTrackingOrInfectionDynamics(personLeavingContainer, container, getRestrictions(), rnd)) {
			return;
		}

		// start tracking late as possible because of computational costs
		boolean trackingEnabled = iteration >= trackingAfterDay;

		EpisimConfigGroup.InfectionParams leavingParams = null;

		for( EpisimPerson contactPerson : container.getPersons() ){

			int maxPersonsInContainer = container.getMaxGroupSize();
			Gbl.assertIf( maxPersonsInContainer>1 );
			// ==1 should not happen because if ever not more than 1 person in container, then method exits already earlier.  ???

			if ( rnd.nextDouble() >= episimConfig.getMaxContacts()/Math.sqrt(maxPersonsInContainer-1) ) {
				continue;
			}
			// since every pair of persons interacts only once, there is now a constant interaction probability per pair
			// if we want superspreading events, then maxInteractions needs to be much larger than 3 or 10.

			if (!personRelevantForTrackingOrInfectionDynamics(contactPerson, container, getRestrictions(), rnd)) {
				continue;
			}

			// we have thrown the random numbers, so we can bail out in some cases if we are not tracking:
			if (!trackingEnabled) {
				if (personLeavingContainer.getDiseaseStatus() == DiseaseStatus.infectedButNotContagious) {
					continue;
				}
				if (contactPerson.getDiseaseStatus() == DiseaseStatus.infectedButNotContagious) {
					continue;
				}
				if (personLeavingContainer.getDiseaseStatus() == contactPerson.getDiseaseStatus()) {
					continue;
				}
			}

			String leavingPersonsActivity = personLeavingContainer.getTrajectory().get(personLeavingContainer.getCurrentPositionInTrajectory()).actType;
			String otherPersonsActivity = contactPerson.getTrajectory().get(contactPerson.getCurrentPositionInTrajectory()).actType;

			StringBuilder infectionType = getInfectionType(buffer, container, leavingPersonsActivity, otherPersonsActivity);

			double containerEnterTimeOfPersonLeaving = container.getContainerEnteringTime(personLeavingContainer.getPersonId());
			double containerEnterTimeOfOtherPerson = container.getContainerEnteringTime(contactPerson.getPersonId());
			double jointTimeInContainer = now - Math.max(containerEnterTimeOfPersonLeaving, containerEnterTimeOfOtherPerson);

			//forbid certain cross-activity interactions, keep track of contacts
			if (container instanceof InfectionEventHandler.EpisimFacility) {
				//home can only interact with home, leisure or work
				if (infectionType.indexOf("home") >= 0 && infectionType.indexOf("leis") == -1 && infectionType.indexOf("work") == -1
						&& !(leavingPersonsActivity.startsWith("home") && otherPersonsActivity.startsWith("home"))) {
					continue;
				} else if (infectionType.indexOf("edu") >= 0 && infectionType.indexOf("work") == -1 && !(leavingPersonsActivity.startsWith("edu") && otherPersonsActivity.startsWith("edu"))) {
					//edu can only interact with work or edu
					continue;
				}
				if (trackingEnabled) {
					trackContactPerson(personLeavingContainer, contactPerson, now, jointTimeInContainer, infectionType);
				}

				// Only a subset of contacts are reported at the moment
				// tracking has to be enabled to report more contacts
				reporting.reportContact(now, personLeavingContainer, contactPerson, container, infectionType, jointTimeInContainer);
			}

			if (!AbstractContactModel.personsCanInfectEachOther(personLeavingContainer, contactPerson)) {
				continue;
			}

			// person can only infect others 4 days after being contagious
			if ((personLeavingContainer.hadDiseaseStatus(DiseaseStatus.contagious) &&
					personLeavingContainer.daysSince(DiseaseStatus.contagious, iteration) > 4)
					|| (contactPerson.hadDiseaseStatus(DiseaseStatus.contagious) &&
					contactPerson.daysSince(DiseaseStatus.contagious, iteration) > 4))
				continue;

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

			double contactIntensity = Math.min(leavingParams.getContactIntensity(), contactParams.getContactIntensity());


			// need to differentiate which person might be the infector
			if (personLeavingContainer.getDiseaseStatus() == DiseaseStatus.susceptible) {

				double prob = infectionModel.calcInfectionProbability(personLeavingContainer, contactPerson, getRestrictions(),
						leavingParams, contactParams, contactIntensity, jointTimeInContainer);
				if (rnd.nextDouble() < prob)
					infectPerson(personLeavingContainer, contactPerson, now, infectionType, prob, container);

			} else {
				double prob = infectionModel.calcInfectionProbability(contactPerson, personLeavingContainer, getRestrictions(),
						contactParams, leavingParams, contactIntensity, jointTimeInContainer);

				if (rnd.nextDouble() < prob)
					infectPerson(contactPerson, personLeavingContainer, now, infectionType, prob, container);
			}
		}
	}

}
