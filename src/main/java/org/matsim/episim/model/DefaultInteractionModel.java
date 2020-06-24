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
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.matsim.episim.EpisimPerson.DiseaseStatus;

/**
 * Default interaction model executed, when a person ends his activity.
 * Infections probabilities calculations are delegated to a {@link InfectionModel}.
 */
public final class DefaultInteractionModel extends AbstractInteractionModel {

	private static final Logger log = LogManager.getLogger(DefaultInteractionModel.class);

	/**
	 * Flag to enable tracking, which is considerably slower.
	 */
	private final int trackingAfterDay;

	/**
	 * See {@link TracingConfigGroup#getMinDuration()}
	 */
	private final double trackingMinDuration;

	/**
	 * Face mask model, which decides which masks the persons are wearing.
	 */
	private final InfectionModel infectionModel;

	/**
	 * In order to avoid recreating a the list of other persons in the container every time it is stored as instance variable.
	 */
	private final List<EpisimPerson> otherPersonsInContainer = new ArrayList<>();
	/**
	 * This buffer is used to store the infection type.
	 */
	private final StringBuilder buffer = new StringBuilder();

	@Inject
	/* package */
	DefaultInteractionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig,
											  EpisimReporting reporting, InfectionModel infectionModel) {
		// (make injected constructor non-public so that arguments can be changed without repercussions.  kai, jun'20)


		super(rnd, episimConfig, reporting);
		this.infectionModel = infectionModel;
		this.trackingAfterDay = tracingConfig.getPutTraceablePersonsInQuarantineAfterDay();
		this.trackingMinDuration = tracingConfig.getMinDuration();
	}

	/**
	 * Constructor when no injection is used.
	 */
	public DefaultInteractionModel(SplittableRandom rnd, Config config, EpisimReporting reporting, InfectionModel infectionModel) {
		// (make public constructor more general (full config as argument) so that argument changes are reduced.  also, do not pass multiple number
		// types in sequence since they can get confused (as I just did). pass full config so that we do not have to retrofit constructor every
		// time additional config info is needed.  kai, jun'20)

		// (use injected constructor from here since args can be adapted. kai, jun'20)
		this(rnd, ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class), ConfigUtils.addOrGetModule(config, TracingConfigGroup.class),
				reporting, infectionModel);
	}

	/**
	 * Attention: In order to re-use the underlying object, this function returns a buffer.
	 * Be aware that the old result will be overwritten, when the function is called multiple times.
	 */
	private static StringBuilder getInfectionType(StringBuilder buffer, EpisimContainer<?> container, String leavingPersonsActivity, String otherPersonsActivity) {
		buffer.setLength(0);
		if (container instanceof InfectionEventHandler.EpisimFacility) {
			buffer.append(leavingPersonsActivity).append("_").append(otherPersonsActivity);
			return buffer;
		} else if (container instanceof InfectionEventHandler.EpisimVehicle) {
			buffer.append("pt");
			return buffer;
		} else {
			throw new RuntimeException("Infection situation is unknown");
		}
	}

	@Override
	public void infectionDynamicsVehicle(EpisimPerson personLeavingVehicle, InfectionEventHandler.EpisimVehicle vehicle, double now) {
		infectionDynamicsGeneralized(personLeavingVehicle, vehicle, now);
	}

	@Override
	public void infectionDynamicsFacility(EpisimPerson personLeavingFacility, InfectionEventHandler.EpisimFacility facility, double now, String actType) {
		infectionDynamicsGeneralized(personLeavingFacility, facility, now);
	}

	@Override
	public void setRestrictionsForIteration(int iteration, Map<String, Restriction> restrictions) {
		super.setRestrictionsForIteration(iteration, restrictions);
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

		otherPersonsInContainer.addAll(container.getPersons());
		otherPersonsInContainer.remove(personLeavingContainer);

		// For the time being, will just assume that the first 10 persons are the ones we interact with.  Note that because of
		// shuffle, those are 10 different persons every day.

		// persons are scaled to number of agents with sample size, but at least 3 for the small development scenarios
//		int contactWith = Math.min(otherPersonsInContainer.size(), Math.max((int) (episimConfig.getSampleSize() * 10), 3));
		int contactWith = Math.min(otherPersonsInContainer.size(), episimConfig.getMaxInteractions());
		for (int ii = 0; ii < contactWith; ii++) {

			// we are essentially looking at the situation when the person leaves the container.  Interactions with other persons who have
			// already left the container were treated then.  In consequence, we have some "circle of persons around us" (yyyy which should
			//  depend on the density), and then a probability of infection in either direction.

			// Draw the contact person and remove it -> we don't want to draw it multiple times
			EpisimPerson contactPerson = otherPersonsInContainer.remove(rnd.nextInt(otherPersonsInContainer.size()));


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

			if (!AbstractInteractionModel.personsCanInfectEachOther(personLeavingContainer, contactPerson)) {
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

			if (jointTimeInContainer < 0 || jointTimeInContainer > 86400) {
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
			
			//determines whether activity is inside or outside. For now we assume that only leisure activities can occur outside
			double indoorOutdoorFactor = getIndoorOutdoorFactor(infectionType);

			// need to differentiate which person might be the infector
			if (personLeavingContainer.getDiseaseStatus() == DiseaseStatus.susceptible) {
				double prob = infectionModel.calcInfectionProbability(personLeavingContainer, contactPerson, getRestrictions(),
						leavingParams, contactParams, jointTimeInContainer, indoorOutdoorFactor);
				if (rnd.nextDouble() < prob)
					infectPerson(personLeavingContainer, contactPerson, now, infectionType, container);

			} else {
				double prob = infectionModel.calcInfectionProbability(contactPerson, personLeavingContainer, getRestrictions(),
						contactParams, leavingParams, jointTimeInContainer, indoorOutdoorFactor);

				if (rnd.nextDouble() < prob)
					infectPerson(contactPerson, personLeavingContainer, now, infectionType, container);
			}
		}

		// Clear cached container
		otherPersonsInContainer.clear();
	}


	/**
	 * Get the relevant infection parameter based on container and activity and person.
	 */
	private EpisimConfigGroup.InfectionParams getInfectionParams(EpisimContainer<?> container, EpisimPerson person, String activity) {
		if (container instanceof InfectionEventHandler.EpisimVehicle) {
			return episimConfig.selectInfectionParams(container.getContainerId().toString());
		} else if (container instanceof InfectionEventHandler.EpisimFacility) {
			EpisimConfigGroup.InfectionParams params = episimConfig.selectInfectionParams(activity);

			// Select different infection params for home quarantined persons
			if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.atHome && params.getContainerName().equals("home")) {
				return qhParams.params;
			}

			return params;
		} else
			throw new IllegalStateException("Don't know how to deal with container " + container);

	}

	private void trackContactPerson(EpisimPerson personLeavingContainer, EpisimPerson otherPerson, double now, double jointTimeInContainer, StringBuilder infectionType) {

		// Don't track certain activities
		if (infectionType.indexOf("pt") >= 0 || infectionType.indexOf("shop") >= 0) {
			return;
		}

		// don't track below threshold
		if (jointTimeInContainer < trackingMinDuration) {
			return;
		}

		personLeavingContainer.addTraceableContactPerson(otherPerson, now);
		otherPerson.addTraceableContactPerson(personLeavingContainer, now);
	}
	
	private double getIndoorOutdoorFactor(StringBuilder infectionType) {
		if (!(infectionType.indexOf("leisure_leisure") >= 0)) return 1.;
		java.time.LocalDate date = episimConfig.getStartDate().plusDays(iteration);
		int dayOfYear = date.getDayOfYear();
		double proba = -1;
		if (dayOfYear < 365./4.) {
			proba = 12.44 / 100.;
		}
		else if (dayOfYear < 2*365./4.) {
			proba = 23.60 / 100.;
		}
		else if (dayOfYear < 3*365./4.) {
			proba = 28.63 / 100.;
		}
		else if (dayOfYear <= 366) {
			proba = 21.15 / 100.;
		}
		else {
			throw new RuntimeException("Something went wrong. The day of the year is =" + dayOfYear);
		}
		double indoorOutdoorFactor = 1.;
		if (rnd.nextDouble() < proba) {
			indoorOutdoorFactor = 1./20.;
		}
		return indoorOutdoorFactor;
		
	}

}
