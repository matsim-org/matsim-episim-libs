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
package org.matsim.run.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.InfectionModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.model.Transition;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.facilities.ActivityFacility;

/**
 * Scenario is created in code, no input files needed.
 */
public class ScenarioFromCode extends AbstractModule {

	/**
	 * Activity names of the default params from {@link #addDefaultParams(EpisimConfigGroup)}.
	 */
	public static final String[] DEFAULT_ACTIVITIES = {
			"home", "work",
	};

	/**
	 * Adds default parameters that should be valid for most scenarios.
	 */
	public static void addDefaultParams(EpisimConfigGroup config) {
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("school").setContactIntensity(1.));
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("home").setContactIntensity(1.));
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("quarantine_home").setContactIntensity(0.3));
		config.addContainerParams(new EpisimConfigGroup.InfectionParams("tr").setContactIntensity(1.));

	}

	@Override
	protected void configure() {
		bind(ContactModel.class).to(OldSymmetricContactModel.class).in(Singleton.class);
		bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(AgeDependentInfectionModelWithSeasonality.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	public Scenario scenario(Config config) {

		final Scenario scenario = ScenarioUtils.loadScenario( config );
		PopulationFactory popFac = scenario.getPopulation().getFactory();

		List<Event> events = new ArrayList<>();

		//create x persons that all visit the same facility at the same time but live alone
		int personsToBeCreated = 100;

		for (int i = 0; i<personsToBeCreated; i++) {
			//population is needed for age dependent models
			Person p = popFac.createPerson(Id.createPersonId("person"+i));
			p.getAttributes().putAttribute("age", 50);
			scenario.getPopulation().addPerson(p);

			ActivityEndEvent homeEvent1 = new ActivityEndEvent(8*3600, p.getId(), Id.createLinkId("link"), Id.create("home"+i, ActivityFacility.class), "home");
			ActivityStartEvent workEvent1 = new ActivityStartEvent(9*3600, p.getId(), Id.createLinkId("link"), Id.create("school", ActivityFacility.class), "school", null);
			ActivityEndEvent workEvent2 = new ActivityEndEvent(17*3600., p.getId(), Id.createLinkId("link"), Id.create("school", ActivityFacility.class), "school");
			ActivityStartEvent homeEvent2 = new ActivityStartEvent(18*3600., p.getId(), Id.createLinkId("link"), Id.create("home"+i, ActivityFacility.class), "home", null);
			events.add(homeEvent1);
			events.add(workEvent2);
			events.add(workEvent1);
			events.add(homeEvent2);
		}

		Collections.sort(events, new Comparator<Event>() {
			@Override
			public int compare(Event e1, Event e2) {
				if (e1.getTime() < e2.getTime()) return -1;
				if (e1.getTime() > e2.getTime()) return 1;
				return 0;

			}
	    });

		//there needs to be a better way of doing this than writing out events and then reading them in again ...
		EventWriterXML writer = new EventWriterXML("./outEvents.xml.gz");

		for (Event e : events) {
			writer.handleEvent(e);
		}

		writer.closeFile();

		return scenario;
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		config.global().setRandomSeed(4711);
		config.controler().setOutputDirectory("./output/scenarioFromCode/");

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setInputEventsFile("./outEvents.xml.gz");
		episimConfig.setInitialInfections(1);
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);
		episimConfig.setSampleSize(.25);
		episimConfig.setCalibrationParameter(9.e-6);
		episimConfig.setStartDate("2020-02-18");
		episimConfig.setMaxContacts(3);
		episimConfig.setHospitalFactor(1.6);
		episimConfig.setProgressionConfig(SnzBerlinScenario25pct2020.baseProgressionConfig(Transition.config()).build());


		addDefaultParams(episimConfig);

		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
				//restrictions ...
				.build()
		);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		// tracing config ...

		return config;
	}

}
