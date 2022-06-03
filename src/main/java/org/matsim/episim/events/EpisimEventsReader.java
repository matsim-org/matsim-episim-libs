/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.episim.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsReaderXMLv1;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.episim.EpisimContainer;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.facilities.ActivityFacility;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.Map;
import java.util.Stack;

public class EpisimEventsReader extends MatsimXmlParser {

	private EventsReaderXMLv1 delegate;

	/**
	 * EventsReader for EpisimEvents.
	 */
	public EpisimEventsReader(EventsManager events) {
		delegate = new EventsReaderXMLv1(events);
		this.setValidating(false);
		delegate.addCustomEventMapper(EpisimInfectionEvent.EVENT_TYPE, getEpisimInfectionEventMapper());
		delegate.addCustomEventMapper(EpisimPotentialInfectionEvent.EVENT_TYPE, getEpisimPotentialInfectionEventMapper());
		delegate.addCustomEventMapper(EpisimInitialInfectionEvent.EVENT_TYPE, getEpisimInitialInfectionEventMapper());
		delegate.addCustomEventMapper(EpisimPersonStatusEvent.EVENT_TYPE, getEpisimPersonStatusEventMapper());
		delegate.addCustomEventMapper(EpisimContactEvent.EVENT_TYPE, getEpisimContactEventMapper());
		delegate.addCustomEventMapper(EpisimVaccinationEvent.EVENT_TYPE, getEpisimVaccinationEventMapper());
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		delegate.characters(ch, start, length);
	}

	private MatsimEventsReader.CustomEventMapper getEpisimInfectionEventMapper() {
		return event -> {

			Map<String, String> attributes = event.getAttributes();

			double time = Double.parseDouble(attributes.get(EpisimInfectionEvent.ATTRIBUTE_TIME));
			Id<Person> person = Id.createPersonId(attributes.get(EpisimInfectionEvent.ATTRIBUTE_PERSON));
			Id<Person> infector = Id.createPersonId(attributes.get(EpisimInfectionEvent.INFECTOR));
			Id<?> container = Id.create(attributes.get(EpisimInfectionEvent.CONTAINER), EpisimContainer.class);
			String type = attributes.get(EpisimInfectionEvent.INFECTION_TYPE);

			// check for the presence of these attributes
			double probability = -1;
			String attr = attributes.get(EpisimInfectionEvent.PROBABILITY);
			if (attr != null)
				probability = Double.parseDouble(attr);

			int groupSize = -1;
			attr = attributes.get(EpisimInfectionEvent.GROUP_SIZE);
			if (attr != null)
				groupSize = Integer.parseInt(attr);

			VirusStrain virusStrain = null;
			attr = attributes.get(EpisimInfectionEvent.VIRUS_STRAIN);
			if (attr != null)
				virusStrain = VirusStrain.valueOf(attr);

			double antibodies = -1;
			if (attributes.containsKey(EpisimInfectionEvent.ANTIBODIES)) {
				antibodies = Double.parseDouble(attributes.get(EpisimInfectionEvent.ANTIBODIES));
			}


			return new EpisimInfectionEvent(time, person, infector, container, type, groupSize, virusStrain, probability, antibodies);
		};
	}

	private MatsimEventsReader.CustomEventMapper getEpisimPotentialInfectionEventMapper() {
		return event -> {

			Map<String, String> attributes = event.getAttributes();

			double time = Double.parseDouble(attributes.get(EpisimInfectionEvent.ATTRIBUTE_TIME));
			Id<Person> person = Id.createPersonId(attributes.get(EpisimInfectionEvent.ATTRIBUTE_PERSON));
			Id<Person> infector = Id.createPersonId(attributes.get(EpisimInfectionEvent.INFECTOR));
			Id<?> container = Id.create(attributes.get(EpisimInfectionEvent.CONTAINER), EpisimContainer.class);
			String type = attributes.get(EpisimInfectionEvent.INFECTION_TYPE);

			double probability = Double.parseDouble(attributes.get(EpisimInfectionEvent.PROBABILITY));
			double unVacProb = Double.parseDouble(attributes.get(EpisimPotentialInfectionEvent.UNVAC_PROBABILITY));

			int groupSize = Integer.parseInt(attributes.get(EpisimInfectionEvent.GROUP_SIZE));
			VirusStrain virusStrain = VirusStrain.valueOf( attributes.get(EpisimInfectionEvent.VIRUS_STRAIN));
			double rnd = Double.parseDouble(attributes.get(EpisimPotentialInfectionEvent.RND));

			double antibodies = -1;
			if (attributes.containsKey(EpisimInfectionEvent.ANTIBODIES)) {
				antibodies = Double.parseDouble(attributes.get(EpisimInfectionEvent.ANTIBODIES));
			}

			return new EpisimPotentialInfectionEvent(time, person, infector, container, type, groupSize, virusStrain, probability, unVacProb, antibodies, rnd);
		};
	}

	private MatsimEventsReader.CustomEventMapper getEpisimInitialInfectionEventMapper() {
		return event -> {

			Map<String, String> attributes = event.getAttributes();

			double time = Double.parseDouble(attributes.get(EpisimInfectionEvent.ATTRIBUTE_TIME));
			Id<Person> person = Id.createPersonId(attributes.get(EpisimInfectionEvent.ATTRIBUTE_PERSON));
			VirusStrain virusStrain = VirusStrain.valueOf( attributes.get(EpisimInfectionEvent.VIRUS_STRAIN));

			return new EpisimInitialInfectionEvent(time, person,virusStrain);
		};
	}

	private MatsimEventsReader.CustomEventMapper getEpisimPersonStatusEventMapper() {
		return event -> {

			Map<String, String> attributes = event.getAttributes();

			double time = Double.parseDouble(attributes.get(EpisimInfectionEvent.ATTRIBUTE_TIME));
			Id<Person> person = Id.createPersonId(attributes.get(EpisimInfectionEvent.ATTRIBUTE_PERSON));
			DiseaseStatus diseaseStatus = DiseaseStatus.valueOf(attributes.get(EpisimPersonStatusEvent.DISEASE_STATUS));

			return new EpisimPersonStatusEvent(time, person, diseaseStatus);
		};
	}

	private MatsimEventsReader.CustomEventMapper getEpisimContactEventMapper() {
		return event -> {

			Map<String, String> attributes = event.getAttributes();

			double time = Double.parseDouble(attributes.get(EpisimContactEvent.ATTRIBUTE_TIME));
			Id<Person> person = Id.createPersonId(attributes.get(EpisimContactEvent.ATTRIBUTE_PERSON));
			Id<Person> contactPerson = Id.createPersonId(attributes.get(EpisimContactEvent.CONTACT_PERSON));
			Id<ActivityFacility> container = Id.create(attributes.get(EpisimContactEvent.CONTAINER), ActivityFacility.class);

			return new EpisimContactEvent(time, person, contactPerson, container, attributes.get(ActivityEndEvent.ATTRIBUTE_ACTTYPE).intern(),
					Double.parseDouble(attributes.get(EpisimContactEvent.DURATION)), Integer.parseInt(attributes.get(EpisimContactEvent.GROUP_SIZE)));
		};
	}

	private MatsimEventsReader.CustomEventMapper getEpisimVaccinationEventMapper() {
		return event -> {

			Map<String, String> attr = event.getAttributes();
			return new EpisimVaccinationEvent(
					Double.parseDouble(attr.get(EpisimVaccinationEvent.ATTRIBUTE_TIME)),
					Id.createPersonId(attr.get(EpisimVaccinationEvent.ATTRIBUTE_PERSON)),
					VaccinationType.valueOf(attr.get(EpisimVaccinationEvent.TYPE)),
					Integer.parseInt(attr.get(EpisimVaccinationEvent.N))
			);
		};
	}


	@Override
	public void startTag(String name, Attributes atts, Stack<String> context) {
		delegate.startTag(name, atts, context);
	}

	@Override
	public void endTag(String name, String content, Stack<String> context) {
		delegate.endTag(name, content, context);
	}
}
