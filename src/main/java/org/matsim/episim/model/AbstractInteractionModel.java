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
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo;

import java.util.Map;
import java.util.SplittableRandom;


/**
 * Base implementation for interactions of persons during activities.
 */
public abstract class AbstractInteractionModel implements InteractionModel {
	public static final String QUARANTINE_HOME = "quarantine_home";

	protected final Scenario scenario = null;
	protected final SplittableRandom rnd;
	protected final EpisimConfigGroup episimConfig;
	protected final EpisimReporting reporting;

	/**
	 * Infections parameter instances for re-use. These are params that are always needed independent of the scenario.
	 */
	protected final EpisimPerson.Activity trParams;
	/**
	 * Home quarantine infection param.
	 */
	protected final EpisimPerson.Activity qhParams;
	/**
	 * See {@link TracingConfigGroup#getMinDuration()}
	 */
	protected final double trackingMinDuration;
	protected int iteration;

	private Map<String, Restriction> restrictions;


	AbstractInteractionModel( SplittableRandom rnd, Config config, EpisimReporting reporting ) {
		this.rnd = rnd;
		this.episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
		this.reporting = reporting;
		this.trParams = new EpisimPerson.Activity("tr", episimConfig.selectInfectionParams("tr" ));
		this.qhParams = new EpisimPerson.Activity( QUARANTINE_HOME, episimConfig.selectInfectionParams( QUARANTINE_HOME ));
		this.trackingMinDuration = ConfigUtils.addOrGetModule( config, TracingConfigGroup.class ).getMinDuration();
	}
	/**
	 * Get the relevant infection parameter based on container and activity and person.
	 */
	protected EpisimConfigGroup.InfectionParams getInfectionParams( EpisimContainer<?> container, EpisimPerson person, String activity ) {
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
	protected void trackContactPerson( EpisimPerson personLeavingContainer, EpisimPerson otherPerson, double now, double jointTimeInContainer,
					   StringBuilder infectionType ) {

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

	private boolean activityRelevantForInfectionDynamics(EpisimPerson person, EpisimContainer<?> container, Map<String, Restriction> restrictions, SplittableRandom rnd) {
		EpisimPerson.Activity act = person.getTrajectory().get(person.getCurrentPositionInTrajectory());

		// Check if person is home quarantined
		if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.atHome && !act.actType.startsWith("home"))
			return false;


		// enforce max group sizes
		Restriction r = restrictions.get(act.params.getContainerName());
		if (r.getMaxGroupSize() != null && r.getMaxGroupSize() > -1 && container.getMaxGroupSize() > 0 &&
				container.getMaxGroupSize() > r.getMaxGroupSize())
			return false;

		return actIsRelevant(act, restrictions, rnd);
	}

	private boolean actIsRelevant(EpisimPerson.Activity act, Map<String, Restriction> restrictions, SplittableRandom rnd) {

		Restriction r = restrictions.get(act.params.getContainerName());
		// avoid use of rnd if outcome is known beforehand
		if (r.getRemainingFraction() == 1)
			return true;
		if (r.getRemainingFraction() == 0)
			return false;

		return rnd.nextDouble() < r.getRemainingFraction();

	}

	private boolean tripRelevantForInfectionDynamics(EpisimPerson person, Map<String, Restriction> restrictions, SplittableRandom rnd) {
		EpisimPerson.Activity lastAct = null;
		if (person.getCurrentPositionInTrajectory() != 0) {
			lastAct = person.getTrajectory().get(person.getCurrentPositionInTrajectory() - 1);
		}

		if (person.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no)
			return false;

		EpisimPerson.Activity nextAct = person.getTrajectory().get(person.getCurrentPositionInTrajectory());

		// last activity is only considered if present
		return actIsRelevant(trParams, restrictions, rnd) && actIsRelevant(nextAct, restrictions, rnd)
				&& (lastAct == null || actIsRelevant(lastAct, restrictions, rnd));

	}

	/**
	 * Checks whether person is relevant for tracking or for infection dynamics.  Currently, "relevant for infection dynamics" is a subset of "relevant for
	 * tracking".  However, I am not sure if this will always be the case.  kai, apr'20
	 *
	 * @noinspection BooleanMethodIsAlwaysInverted
	 */
	protected final boolean personRelevantForTrackingOrInfectionDynamics(EpisimPerson person, EpisimContainer<?> container,
																		 Map<String, Restriction> restrictions, SplittableRandom rnd) {

		// Infected but not contagious persons are considered additionally
		if (!hasDiseaseStatusRelevantForInfectionDynamics(person) &&
				person.getDiseaseStatus() != EpisimPerson.DiseaseStatus.infectedButNotContagious)
			return false;

		if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.full) {
			return false;
		}

		if (container instanceof InfectionEventHandler.EpisimFacility && activityRelevantForInfectionDynamics(person, container, restrictions, rnd)) {
			return true;
		}
		return container instanceof InfectionEventHandler.EpisimVehicle && tripRelevantForInfectionDynamics(person, restrictions, rnd);
	}

	/**
	 * Attention: In order to re-use the underlying object, this function returns a buffer.
	 * Be aware that the old result will be overwritten, when the function is called multiple times.
	 */
	protected static StringBuilder getInfectionType( StringBuilder buffer, EpisimContainer<?> container, String leavingPersonsActivity,
							 String otherPersonsActivity ) {
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
	/**
	 * Set the iteration number and restrictions that are in place.
	 */
	@Override
	public void setRestrictionsForIteration(int iteration, Map<String, Restriction> restrictions) {
		this.iteration = iteration;
		this.restrictions = restrictions;
	}

	/**
	 * Sets the infection status of a person and reports the event.
	 */
	protected void infectPerson( EpisimPerson personWrapper, EpisimPerson infector, double now, StringBuilder infectionType,
				     EpisimContainer<?> container ) {

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
		if (!personWrapper.getCurrentContainer().equals(infector.getCurrentContainer())) {
			throw new IllegalStateException("Person and infector are not in same container!");
		}

		// TODO: during iteration persons can get infected after 24h
		// this can lead to strange effects / ordering of events, because it is assumed one iteration is one day
		// now is overwritten to be at the end of day
		if (now >= EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 24 * 60 * 60, iteration)) {
			now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 24 * 60 * 60 - 1, iteration);
		}

		reporting.reportInfection(personWrapper, infector, now, infectionType.toString(), container );
		personWrapper.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.infectedButNotContagious);

		// TODO: Currently not in use, is it still needed?
		// Necessary for the otfvis visualization (although it is unfortunately not working).  kai, apr'20
		if (scenario != null) {
			final Person person = PopulationUtils.findPerson(personWrapper.getPersonId(), scenario);
			if (person != null) {
				person.getAttributes().putAttribute(AgentSnapshotInfo.marker, true);
			}
		}
	}

	public Map<String, Restriction> getRestrictions() {
		return restrictions;
	}
}
