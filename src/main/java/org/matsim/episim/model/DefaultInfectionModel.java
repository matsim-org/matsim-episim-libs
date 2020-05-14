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
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.matsim.episim.EpisimPerson.DiseaseStatus;

/**
 * Default infection model executed, when a person ends his activity.
 * This infection model calculates the joint time two persons have been at the same place and calculates a infection probability according to:
 * <pre>
 *    1 - e^(calibParam * contactIntensity * jointTimeInContainer * intake * shedding * exposure)
 * </pre>
 */
public final class DefaultInfectionModel extends AbstractInfectionModel {

	private static final Logger log = LogManager.getLogger(DefaultInfectionModel.class);

	/**
	 * Flag to enable tracking, which is considerably slower.
	 */
	private final int trackingAfterDay;

	/**
	 * Face mask model, which decides which masks the persons are wearing.
	 */
	private final FaceMaskModel maskModel;

	/**
	 * In order to avoid recreating a the list of other persons in the container every time it is stored as instance variable.
	 */
	private final List<EpisimPerson> otherPersonsInContainer = new ArrayList<>();
	/**
	 * This buffer is used to store the infection type.
	 */
	private final StringBuilder buffer = new StringBuilder();

	@Inject
	public DefaultInfectionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig,
								 EpisimReporting reporting, FaceMaskModel maskModel) {
		this(rnd, episimConfig, reporting, maskModel, tracingConfig.getPutTraceablePersonsInQuarantineAfterDay());
	}

	/**
	 * Constructor when no injection is used.
	 */
	public DefaultInfectionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, EpisimReporting reporting, FaceMaskModel maskModel, int trackingAfterDay) {
		super(rnd, episimConfig, reporting);
		this.maskModel = maskModel;
		this.trackingAfterDay = trackingAfterDay;
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
		maskModel.setIteration(iteration);
	}

	private void infectionDynamicsGeneralized(EpisimPerson personLeavingContainer, EpisimContainer<?> container, double now) {

		// no infection possible if there is only one person
		if (iteration == 0 || container.getPersons().size() == 1) {
			return;
		}

		if (!personRelevantForTrackingOrInfectionDynamics(personLeavingContainer, container, episimConfig, getRestrictions(), rnd)) {
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
		int contactWith = Math.min(otherPersonsInContainer.size(), Math.max((int) (episimConfig.getSampleSize() * 10), 3));
		for (int ii = 0; ii < contactWith; ii++) {

			// we are essentially looking at the situation when the person leaves the container.  Interactions with other persons who have
			// already left the container were treated then.  In consequence, we have some "circle of persons around us" (yyyy which should
			//  depend on the density), and then a probability of infection in either direction.

			// Draw the contact person and remove it -> we don't want to draw it multiple times
			EpisimPerson contactPerson = otherPersonsInContainer.remove(rnd.nextInt(otherPersonsInContainer.size()));


			if (!personRelevantForTrackingOrInfectionDynamics(contactPerson, container, episimConfig, getRestrictions(), rnd)) {
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
					trackContactPerson(personLeavingContainer, contactPerson, now, infectionType);
				}
			}

			if (!AbstractInfectionModel.personsCanInfectEachOther(personLeavingContainer, contactPerson)) {
				continue;
			}

			double containerEnterTimeOfPersonLeaving = container.getContainerEnteringTime(personLeavingContainer.getPersonId());
			double containerEnterTimeOfOtherPerson = container.getContainerEnteringTime(contactPerson.getPersonId());

			// persons leaving their first-ever activity have no starting time for that activity.  Need to hedge against that.  Since all persons
			// start healthy (the first seeds are set at enterVehicle), we can make some assumptions.
			if (containerEnterTimeOfPersonLeaving < 0 && containerEnterTimeOfOtherPerson < 0) {
				throw new IllegalStateException("should not happen");
				// should only happen at first activity.  However, at first activity all persons are susceptible.  So the only way we
				// can get here is if an infected person entered the container and is now leaving again, while the other person has been in the
				// container from the beginning.  ????  kai, mar'20
			}

			double jointTimeInContainer = now - Math.max(containerEnterTimeOfPersonLeaving, containerEnterTimeOfOtherPerson);
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

			// need to differentiate which person might be the infector
			if (personLeavingContainer.getDiseaseStatus() == DiseaseStatus.susceptible) {

				double prob = calcInfectionProbability(personLeavingContainer, contactPerson, leavingParams, contactParams, jointTimeInContainer);
				// Only a subset of contacts are reported at the moment
				// TODO: should be invoked earlier if performance penalty is not too high
				reporting.reportContact(now, personLeavingContainer, contactPerson, container, infectionType, jointTimeInContainer, prob);

				if (rnd.nextDouble() < prob)
					infectPerson(personLeavingContainer, contactPerson, now, infectionType);

			} else {
				double prob = calcInfectionProbability(contactPerson, personLeavingContainer, contactParams, leavingParams, jointTimeInContainer);
				reporting.reportContact(now, personLeavingContainer, contactPerson, container, infectionType, jointTimeInContainer, prob);

				if (rnd.nextDouble() < prob)
					infectPerson(contactPerson, personLeavingContainer, now, infectionType);
			}
		}

		// Clear cached container
		otherPersonsInContainer.clear();
	}

	/**
	 * Calculates the probability that person {@code infector} infects {@code target}.
	 *
	 * @param target               The potentially infected person
	 * @param infector             The infectious person
	 * @param act1                 Activity of target
	 * @param act2                 Activity of infector
	 * @param jointTimeInContainer joint time doing these activity in seconds
	 * @return probability between 0 and 1
	 */
	protected double calcInfectionProbability(EpisimPerson target, EpisimPerson infector,
											  EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2,
											  double jointTimeInContainer) {

		Map<String, Restriction> r = getRestrictions();

		double exposure = Math.min(r.get(act1.getContainerName()).getExposure(), r.get(act2.getContainerName()).getExposure());
		double contactIntensity = Math.min(act1.getContactIntensity(), act2.getContactIntensity());

		// note that for 1pct runs, calibParam is of the order of one, which means that for typical times of 100sec or more,
		// exp( - 1 * 1 * 100 ) \approx 0, and thus the infection proba becomes 1.  Which also means that changes in contactIntensity has
		// no effect.  kai, mar'20

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * contactIntensity * jointTimeInContainer * exposure
				* maskModel.getWornMask(infector, act2, iteration, r.get(act2.getContainerName())).shedding
				* maskModel.getWornMask(target, act1, iteration, r.get(act1.getContainerName())).intake
		);
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

	private void trackContactPerson(EpisimPerson personLeavingContainer, EpisimPerson otherPerson, double now, StringBuilder infectionType) {

		// Don't track certain activities
		if (infectionType.indexOf("pt") >= 0 || infectionType.indexOf("shop") >= 0) {
			return;
		}

		personLeavingContainer.addTraceableContactPerson(otherPerson, now);
		otherPerson.addTraceableContactPerson(personLeavingContainer, now);
	}

}
