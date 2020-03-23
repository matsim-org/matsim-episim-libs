package org.matsim.episim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class EpisimPerson{
        private final Id<Person> personId;
        private InfectionEventHandler.DiseaseStatus status = InfectionEventHandler.DiseaseStatus.susceptible;
        private InfectionEventHandler.QuarantineStatus quarantineStatus = InfectionEventHandler.QuarantineStatus.no;
        private int infectionDate;
        private int quarantineDate;
        private int currentPositionInTrajectory;
        private String lastFacilityId;
        private String firstFacilityId;
        private Set<EpisimPerson> tracableContactPersons = new LinkedHashSet<>();
        private List<String> trajectory = new ArrayList<>();
        private List<EpisimContainer<?>> currentContainers = new ArrayList<>();
        EpisimPerson( Id<Person> personId ) {
                this.personId = personId;
        }
        void setDiseaseStatus( InfectionEventHandler.DiseaseStatus status ) {
                this.status = status;
        }
        void setQuarantineStatus( InfectionEventHandler.QuarantineStatus quarantineStatus ) {
                this.quarantineStatus = quarantineStatus;
        }
        Id<Person> getPersonId(){
                return personId;
        }
        InfectionEventHandler.DiseaseStatus getDiseaseStatus(){
                return status;
        }
        InfectionEventHandler.QuarantineStatus getQuarantineStatus(){
                return quarantineStatus;
        }
        void setInfectionDate (int date) {
                this.infectionDate = date;
        }
        int getInfectionDate () {
                return this.infectionDate;
        }
        void setQuarantineDate (int date) {
                this.quarantineDate = date;
        }
        int getQuarantineDate () {
                return this.quarantineDate;
        }
        void setLastFacilityId (String lastFacilityId) {
            this.lastFacilityId = lastFacilityId;
        }
        String getLastFacilityId () {
            return this.lastFacilityId;
        }
        void addTracableContactPerson( EpisimPerson personWrapper ) {
                tracableContactPersons.add( personWrapper );
        }
        Set<EpisimPerson> getTracableContactPersons() {
                return tracableContactPersons;
        }
        void addToTrajectory( String trajectoryElement ) {
        	trajectory.add( trajectoryElement );
        }
        List<String> getTrajectory() {
            return trajectory;
        }
        int getCurrentPositionInTrajectory () {
            return this.currentPositionInTrajectory;
        }
        void setCurrentPositionInTrajectory (int currentPositionInTrajectory) {
        	this.currentPositionInTrajectory = currentPositionInTrajectory;
        }
        public List<EpisimContainer<?>> getCurrentContainers() {
			return Collections.unmodifiableList(currentContainers);
		}
		public void addToCurrentContainers(EpisimContainer<?> container) {
			this.currentContainers.add(container);
			if (this.getCurrentContainers().size() > 1) {
				throw new RuntimeException("Person in more than one container at once. Person=" +this.getPersonId().toString() + " is in: " + container.getContainerId().toString() + " and " + this.getCurrentContainers().get(0).getContainerId().toString());
			}
		}
		public void removeFromCurrentContainers(EpisimContainer<?> container) {
			this.currentContainers.remove(container);
		}
		String getFirstFacilityId() {
			return firstFacilityId;
		}
		void setFirstFacilityId(String firstFacilityId) {
			this.firstFacilityId = firstFacilityId;
		}
}
