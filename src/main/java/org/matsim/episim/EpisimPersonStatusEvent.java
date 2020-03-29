package org.matsim.episim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;

import java.util.Map;

public class EpisimPersonStatusEvent extends Event implements HasPersonId {
        private static final String EVENT_TYPE = "episimPersonStatus";
        private static final String DISEASE_STATUS = "diseaseStatus";
        private final EpisimPerson.DiseaseStatus diseaseStatus;
        private final Id<Person> personId;

        public EpisimPersonStatusEvent( double time, Id<Person> personId, EpisimPerson.DiseaseStatus diseaseStatus ){
                super( time );
                this.diseaseStatus = diseaseStatus;
                this.personId = personId;
        }
        @Override public String getEventType(){
                return EVENT_TYPE;
        }
        @Override public Id<Person> getPersonId(){
                return personId;
        }
        public EpisimPerson.DiseaseStatus getDiseaseStatus(){
                return diseaseStatus;
        }
        @Override
        public Map<String, String> getAttributes() {
                Map<String, String> attr = super.getAttributes();
                // person, link, facility done by superclass
                attr.put(DISEASE_STATUS, this.diseaseStatus.name());
                return attr;
        }

}
