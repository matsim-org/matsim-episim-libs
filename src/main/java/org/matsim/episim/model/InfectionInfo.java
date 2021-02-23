/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2021 matsim-org
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
import org.matsim.core.population.PopulationUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimContainer;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
import org.matsim.episim.EpisimUtils;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo;


public class InfectionInfo {
	private final EpisimPerson personWrapper;
	private final EpisimPerson infector;
	private double now;
	private final StringBuilder infectionType;
	private final double prob;
	private final EpisimContainer<?> container;
	private final EpisimConfigGroup episimConfig;
	private final int iteration;
	private final Scenario scenario;
	private final EpisimReporting reporting;
	private boolean unhandled = false;
	
	InfectionInfo(EpisimPerson personWrapper,
				  EpisimPerson infector,
				  double now,
				  StringBuilder infectionType,
				  double prob,
				  EpisimContainer<?> container,
				  EpisimConfigGroup episimConfig,
				  int iteration,
				  Scenario scenario,
				  EpisimReporting reporting) {
		this.personWrapper = personWrapper;
		this.infector = infector;
		this.now = now;
		this.infectionType = infectionType;
		this.prob = prob;
		this.container = container;
		this.episimConfig = episimConfig;
		this.iteration = iteration;
		this.scenario = scenario;
		this.reporting = reporting;
	}

	public double getNow() {
		return now;
	};
	
	public void checkInfection() {
		if (unhandled == false) {
			unhandled = true;
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
			
			String infType = infectionType.toString();
			
			reporting.reportInfection(personWrapper, infector, now, infType, infector.getVirusStrain(), prob, container);
			personWrapper.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.infectedButNotContagious);
			personWrapper.setVirusStrain(infector.getVirusStrain());
			personWrapper.setInfectionContainer(container);
			personWrapper.setInfectionType(infType);
			
			// TODO: Currently not in use, is it still needed?
			// Necessary for the otfvis visualization (although it is unfortunately not working).  kai, apr'20
			if (scenario != null) {
				final Person person = PopulationUtils.findPerson(personWrapper.getPersonId(), scenario);
				if (person != null) {
					person.getAttributes().putAttribute(AgentSnapshotInfo.marker, true);
				}
			}
		}
	}
}
