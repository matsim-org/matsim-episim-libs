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
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsReaderXMLv1;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.episim.EpisimContainer;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.Map;
import java.util.Stack;

import static org.matsim.episim.events.EpisimInfectionEvent.*;

public class EpisimEventsReader extends MatsimXmlParser {

	private EventsReaderXMLv1 delegate;

	/**
	 * EventsReader for EpisimEvents. Currently, only EpisimInfectionEvents and EpisimPersonStatusEvents are supported.
	 * TODO: read other episimevents (EpisimContactEvent)
	 *
	 * @param events
	 */
	public EpisimEventsReader(EventsManager events) {
		delegate = new EventsReaderXMLv1(events);
		this.setValidating(false);
		delegate.addCustomEventMapper(EVENT_TYPE, getEpisimInfectionEventMapper());
		delegate.addCustomEventMapper(EpisimPersonStatusEvent.EVENT_TYPE, getEpisimPersonStatusEventMapper());
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		delegate.characters(ch, start, length);
	}

	private MatsimEventsReader.CustomEventMapper<EpisimInfectionEvent> getEpisimInfectionEventMapper() {
		return event -> {

			Map<String, String> attributes = event.getAttributes();

			double time = Double.parseDouble(attributes.get(ATTRIBUTE_TIME));
			Id<Person> person = Id.createPersonId(attributes.get(ATTRIBUTE_PERSON));
			Id<Person> infector = Id.createPersonId(attributes.get(INFECTOR));
			Id<?> container = Id.create(attributes.get(CONTAINER), EpisimContainer.class);
			String type = attributes.get(INFECTION_TYPE);

			return new EpisimInfectionEvent(time,person,infector,container,type);
		};
	}
	
	private MatsimEventsReader.CustomEventMapper<EpisimPersonStatusEvent> getEpisimPersonStatusEventMapper() {
		return event -> {

			Map<String, String> attributes = event.getAttributes();

			double time = Double.parseDouble(attributes.get(ATTRIBUTE_TIME));
			Id<Person> person = Id.createPersonId(attributes.get(ATTRIBUTE_PERSON));
			DiseaseStatus diseaseStatus = DiseaseStatus.valueOf(attributes.get(EpisimPersonStatusEvent.DISEASE_STATUS));

			return new EpisimPersonStatusEvent(time,person,diseaseStatus);
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
