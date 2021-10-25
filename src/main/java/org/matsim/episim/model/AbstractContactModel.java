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

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.policy.Restriction;
import org.matsim.facilities.ActivityFacility;

import java.util.HashMap;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.SplittableRandom;

import static org.matsim.episim.InfectionEventHandler.EpisimFacility;
import static org.matsim.episim.InfectionEventHandler.EpisimVehicle;


/**
 * Base implementation for interactions of persons during activities.
 */
public abstract class AbstractContactModel implements ContactModel {
	public static final String QUARANTINE_HOME = "quarantine_home";

	protected final Scenario scenario;
	protected final SplittableRandom rnd;
	protected final EpisimConfigGroup episimConfig;
	protected final EpisimReporting reporting;

	/**
	 * Infections parameter instances for re-use. These are params that are always needed independent of the scenario.
	 */
	protected final EpisimConfigGroup.InfectionParams trParams;
	/**
	 * Home quarantine infection param.
	 */
	protected final EpisimConfigGroup.InfectionParams qhParams;
	/**
	 * See {@link TracingConfigGroup#getMinDuration()}
	 */
	protected final double trackingMinDuration;

	/**
	 * Infection probability calculation.
	 */
	protected final InfectionModel infectionModel;

	protected int iteration;
	protected DayOfWeek day;
	private Map<String, Restriction> restrictions;

	/**
	 * Curfew compliance valid for the day.
	 */
	private double curfewCompliance;

	/**
	 * Map of each ActivityFacility with the corresponding subdistrict
	 */
	private final Map<String, String> subdistrictFacilities;


	AbstractContactModel(SplittableRandom rnd, Config config, InfectionModel infectionModel, EpisimReporting reporting, Scenario scenario) {
		this.rnd = rnd;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.infectionModel = infectionModel;
		this.reporting = reporting;
		this.trParams = episimConfig.selectInfectionParams("tr");
		this.qhParams = episimConfig.selectInfectionParams(QUARANTINE_HOME);
		this.trackingMinDuration = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class).getMinDuration();
		this.scenario = scenario;

		subdistrictFacilities = new HashMap<>();
		if (episimConfig.getDistrictLevelRestrictions().equals(EpisimConfigGroup.DistrictLevelRestrictions.yes)
				&& scenario != null
				&& !scenario.getActivityFacilities().getFacilities().isEmpty()) {

			for (ActivityFacility facility : scenario.getActivityFacilities().getFacilities().values()) {
				String subdistrictAttributeName = episimConfig.getDistrictLevelRestrictionsAttribute();
				String subdistrict = (String) facility.getAttributes().getAttribute(subdistrictAttributeName);
				if (subdistrict != null) {
					this.subdistrictFacilities.put(facility.getId().toString(), subdistrict);
				}
			}
		}
	}

	AbstractContactModel(SplittableRandom rnd, Config config, InfectionModel infectionModel, EpisimReporting reporting) {
		this(rnd, config, infectionModel, reporting, null);

	}

	private static boolean hasDiseaseStatusRelevantForInfectionDynamics(EpisimPerson personWrapper) {
		switch (personWrapper.getDiseaseStatus()) {
			case susceptible:
			case contagious:
			case showingSymptoms:
				return true;

			case infectedButNotContagious:
			case recovered:
			case seriouslySick: // assume is in hospital
			case critical:
			case seriouslySickAfterCritical:
				return false;

			default:
				throw new IllegalStateException("Unexpected value: " + personWrapper.getDiseaseStatus());
		}
	}

	/**
	 * This method checks whether person1 and person2 have relevant disease status for infection dynamics.
	 * If not or if both have the same disease status, the return value is false.
	 */
	static boolean personsCanInfectEachOther(EpisimPerson person1, EpisimPerson person2) {
		if (person1.getDiseaseStatus() == person2.getDiseaseStatus()) return false;
		// at least one of the persons must be susceptible
		if (person1.getDiseaseStatus() != EpisimPerson.DiseaseStatus.susceptible && person2.getDiseaseStatus() != EpisimPerson.DiseaseStatus.susceptible)
			return false;
		return (hasDiseaseStatusRelevantForInfectionDynamics(person1) && hasDiseaseStatusRelevantForInfectionDynamics(person2));
	}

	/**
	 * Attention: In order to re-use the underlying object, this function returns a buffer.
	 * Be aware that the old result will be overwritten, when the function is called multiple times.
	 */
	protected static StringBuilder getInfectionType(StringBuilder buffer, EpisimContainer<?> container, String leavingPersonsActivity,
													String otherPersonsActivity) {
		buffer.setLength(0);
		if (container instanceof EpisimFacility) {
			buffer.append(leavingPersonsActivity).append("_").append(otherPersonsActivity);
			return buffer;
		} else if (container instanceof EpisimVehicle) {
			buffer.append("pt");
			return buffer;
		} else {
			throw new RuntimeException("Infection situation is unknown");
		}
	}

	/**
	 * Get the relevant infection parameter based on container and activity and person.
	 */
	protected EpisimConfigGroup.InfectionParams getInfectionParams(EpisimContainer<?> container, EpisimPerson person, EpisimPerson.PerformedActivity activity) {
		if (container instanceof EpisimVehicle) {
			return trParams;
		} else if (container instanceof EpisimFacility) {
			EpisimConfigGroup.InfectionParams params = activity.params;

			// Select different infection params for home quarantined persons
			if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.atHome && params.getContainerName().equals("home")) {
				return qhParams;
			}

			return params;
		} else
			throw new IllegalStateException("Don't know how to deal with container " + container);

	}

	protected void trackContactPerson(EpisimPerson personLeavingContainer, EpisimPerson otherPerson, double now, double jointTimeInContainer,
									  StringBuilder infectionType) {

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

	private boolean activityRelevantForInfectionDynamics(EpisimPerson person, EpisimContainer<?> container, Map<String,
			Restriction> restrictions, SplittableRandom rnd) {

		EpisimPerson.PerformedActivity act = container.getPerformedActivity(person.getPersonId());

		// Check if person is home quarantined
		if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.atHome && !act.actType().startsWith("home"))
			return false;

		// enforce max group sizes
		Restriction r = restrictions.get(act.params.getContainerName());
		if (r.getMaxGroupSize() != null && r.getMaxGroupSize() > -1 && container.getMaxGroupSize() > 0 &&
				container.getMaxGroupSize() > r.getMaxGroupSize())
			return false;

		// reduce group size probabilistically
		Integer reducedGroupSize = r.getReducedGroupSize();
		if (reducedGroupSize != null && reducedGroupSize > -1 && reducedGroupSize != Integer.MAX_VALUE) {
			double current = (container.getPersons().size() * episimConfig.getSampleSize()) / container.getNumSpaces();

			// always false if current < reduced size
			boolean out = rnd.nextDouble() > reducedGroupSize / current;

			// don'T return true, other conditions might be false
			if (out) return false;
		}

		if (r.isClosed(container.getContainerId()))
			return false;

		return actIsRelevant(act.params, restrictions, rnd, container);
	}

	private boolean actIsRelevant(EpisimConfigGroup.InfectionParams params, Map<String, Restriction> restrictions, SplittableRandom rnd,EpisimContainer container) {

		Restriction r = restrictions.get(params.getContainerName());
		Double remainingFraction = r.getRemainingFraction();

		// Applies location based restriction, if applicable
		// So far, they are only applied for EpisimFacilities, not EpisimVehicles
		if (episimConfig.getDistrictLevelRestrictions().equals(EpisimConfigGroup.DistrictLevelRestrictions.yes) && container != null) {
			if (subdistrictFacilities.containsKey(container.getContainerId().toString())) {
				String subdistrict = subdistrictFacilities.get(container.getContainerId().toString());
				if (r.getLocationBasedRf().containsKey(subdistrict)) {
					remainingFraction = r.getLocationBasedRf().get(subdistrict);
				}
			}
		}

		// avoid use of rnd if outcome is known beforehand
		if (remainingFraction == 1)
			return true;
		if (remainingFraction == 0)
			return false;

		return rnd.nextDouble() < remainingFraction;

	}

	private boolean tripRelevantForInfectionDynamics(double time, EpisimPerson person, Map<String, Restriction> restrictions, SplittableRandom rnd) {

		if (person.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no)
			return false;

		EpisimPerson.PerformedActivity lastAct = person.getActivity(day, time % 86400);

		EpisimPerson.PerformedActivity nextAct = person.getNextActivity(day, time % 86400);

		// next activity is only considered if present
		return actIsRelevant(trParams, restrictions, rnd, null) &&
				(nextAct == null || actIsRelevant(nextAct.params, restrictions, rnd, null)) &&
				(actIsRelevant(lastAct.params, restrictions, rnd, null));

	}

	/**
	 * Checks whether person is relevant for tracking or for infection dynamics.  Currently, "relevant for infection dynamics" is a subset of "relevant for
	 * tracking".  However, I am not sure if this will always be the case.  kai, apr'20
	 *
	 * @noinspection BooleanMethodIsAlwaysInverted
	 */
	protected final boolean personRelevantForTrackingOrInfectionDynamics(double time, EpisimPerson person, EpisimContainer<?> container,
																		 Map<String, Restriction> restrictions, SplittableRandom rnd) {

		return personHasRelevantStatus(person) && checkPersonInContainer(time, person, container, restrictions, rnd);
	}

	protected final boolean personHasRelevantStatus(EpisimPerson person) {
		// Infected but not contagious persons are considered additionally
		return hasDiseaseStatusRelevantForInfectionDynamics(person) ||
				person.getDiseaseStatus() == EpisimPerson.DiseaseStatus.infectedButNotContagious;
	}

	/**
	 * Checks whether a person would be present in the container.
	 */
	protected final boolean checkPersonInContainer(double time, EpisimPerson person, EpisimContainer<?> container, Map<String, Restriction> restrictions, SplittableRandom rnd) {
		if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.full) {
			return false;
		}

		// if activity participation was already handled, everything else is not relevant
		if (episimConfig.getActivityHandling() != EpisimConfigGroup.ActivityHandling.duringContact)
			return true;

		if (container instanceof EpisimFacility && activityRelevantForInfectionDynamics(person, container, restrictions, rnd)) {
			return true;
		}
		return container instanceof EpisimVehicle && tripRelevantForInfectionDynamics(time, person, restrictions, rnd);
	}

	/**
	 * Calculate the joint time persons have been in a container.
	 * This takes possible closing hours into account.
	 */
	protected double calculateJointTimeInContainer(double now, EpisimConfigGroup.InfectionParams act, double containerEnterTimeOfPersonLeaving, double containerEnterTimeOfOtherPerson) {
		Restriction r = getRestrictions().get(act.getContainerName());

		double max = Math.max(containerEnterTimeOfPersonLeaving, containerEnterTimeOfOtherPerson);

		// no closing hour set, or no compliance
		if (!r.hasClosingHours() || curfewCompliance == 0) {
			return now - max;
		} else if (episimConfig.getCalibrationParameter() != 1 && rnd.nextDouble() >= curfewCompliance) {
			return now - max;
		}

		double overlap = r.overlapWithClosingHour(max, now);
		if (overlap > 0) {
			double jointTime = now - max - overlap;
			// joint time can now be negative and will be set to 0
			return jointTime > 0 ? jointTime : 0;

		} else {
			return now - max;
		}
	}

	/**
	 * Set the iteration number and restrictions that are in place.
	 */
	@Override
	public void setRestrictionsForIteration(int iteration, Map<String, Restriction> restrictions) {
		this.iteration = iteration;
		this.day = EpisimUtils.getDayOfWeek(episimConfig, iteration);
		this.restrictions = restrictions;
		this.infectionModel.setIteration(iteration);
		this.curfewCompliance = EpisimUtils.findValidEntry(episimConfig.getCurfewCompliance(), 1.0,
				episimConfig.getStartDate().plusDays(iteration - 1));
	}

	/**
	 * Sets the infection status of a person and reports the event.
	 */
	protected void infectPerson(EpisimPerson personWrapper, EpisimPerson infector, double now, StringBuilder infectionType,
								double prob, EpisimContainer<?> container) {

		if (personWrapper.getDiseaseStatus() != EpisimPerson.DiseaseStatus.susceptible) {
			throw new IllegalStateException("Person to be infected is not susceptible. Status is=" + personWrapper.getDiseaseStatus());
		}
		if (infector.getDiseaseStatus() != EpisimPerson.DiseaseStatus.contagious && infector.getDiseaseStatus() != EpisimPerson.DiseaseStatus.showingSymptoms) {
			throw new IllegalStateException("Infector is not contagious. Status is=" + infector.getDiseaseStatus());
		}
		if (personWrapper.getQuarantineStatus() == EpisimPerson.QuarantineStatus.full) {
			throw new IllegalStateException("Person to be infected is in full quarantine.");
		}
		if (infector.getQuarantineStatus() == EpisimPerson.QuarantineStatus.full) {
			throw new IllegalStateException("Infector is in ful quarantine.");
		}
		//		if (!personWrapper.getCurrentContainer().equals(infector.getCurrentContainer())) {
		//			throw new IllegalStateException("Person and infector are not in same container!");
		//		}

		// TODO: during iteration persons can get infected after 24h
		// this can lead to strange effects / ordering of events, because it is assumed one iteration is one day
		// now is overwritten to be at the end of day
		if (now >= EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 24 * 60 * 60, iteration)) {
			now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 24 * 60 * 60 - 1, iteration);
		}

		personWrapper.possibleInfection(
				new EpisimInfectionEvent(now, personWrapper.getPersonId(), infector.getPersonId(),
				container.getContainerId(), infectionType.toString(), container.getPersons().size(), infector.getVirusStrain(), prob)
		);

		// check infection immediately if there is only one thread
		if (episimConfig.getThreads() == 1)
			reporting.reportInfection(personWrapper.checkInfection());

	}

	public Map<String, Restriction> getRestrictions() {
		return restrictions;
	}

	@Override
	public void notifyEnterVehicle(EpisimPerson personEnteringVehicle, EpisimVehicle vehicle, double now) {
	}

	@Override
	public void notifyEnterFacility(EpisimPerson personEnteringFacility, EpisimFacility facility, double now) {
	}


}
