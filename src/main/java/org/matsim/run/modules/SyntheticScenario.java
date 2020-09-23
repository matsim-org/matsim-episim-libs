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
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.ReplayHandler;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.facilities.ActivityFacility;
import org.matsim.run.batch.SyntheticModel;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Scenario is created in code, no input files needed.
 */
public class SyntheticScenario extends AbstractModule {

	private final SyntheticModel.Params params;

	public SyntheticScenario() {
		this.params = new SyntheticModel.Params();
	}

	public SyntheticScenario(SyntheticModel.Params params) {
		this.params = params;
	}

	/**
	 * Adds default parameters that should be valid for most scenarios.
	 */
	public static void addDefaultParams(EpisimConfigGroup config) {
		config.getOrAddContainerParams("home").setContactIntensity(1.);
		config.getOrAddContainerParams("outside").setContactIntensity(1.);
		config.getOrAddContainerParams("quarantine_home").setContactIntensity(0.3);
		config.getOrAddContainerParams("tr").setContactIntensity(1.);

	}

	@Override
	protected void configure() {
		bind(ContactModel.class).to(params.contactModel).in(Singleton.class);
		bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(AgeDependentInfectionModelWithSeasonality.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	public Scenario scenario(Config config) {

		final Scenario scenario = ScenarioUtils.createScenario(config);
		PopulationFactory popFac = scenario.getPopulation().getFactory();

		for (int i = 0; i < params.persons; i++) {
			//population is needed for age dependent models
			Id<Person> id = Id.createPersonId("person" + i);
			Person p = popFac.createPerson(id);
			p.getAttributes().putAttribute("age", params.age);
			scenario.getPopulation().addPerson(p);
		}

		return scenario;
	}

	@Provides
	@Singleton
	public ReplayHandler replayHandler() {
		Map<DayOfWeek, List<Event>> all = new EnumMap<>(DayOfWeek.class);

		Id<Link> link = Id.createLinkId("link");

		List<Event> events = new ArrayList<>();

		int homeId = 0;
		int homeSize = 0;

		for (int i = 0; i < params.persons; i++) {
			Id<Person> id = Id.createPersonId("person" + i);

			ActivityEndEvent homeEvent1 = new ActivityEndEvent(8 * 3600, id, link, Id.create("home" + homeId, ActivityFacility.class), "home");

			// outside from 9 to 17 o clock
			int length = (17 - 9) / params.numActivitiesPerDay;

			for (int j = 0; j < params.numActivitiesPerDay; j++) {

				ActivityStartEvent outside1 = new ActivityStartEvent((9 + length * j) * 3600 + 1, id, link, Id.create("outside" + i % params.numFacilities, ActivityFacility.class), "outside", null);
				ActivityEndEvent outside2 = new ActivityEndEvent((9 + (j + 1) * length) * 3600., id, link, Id.create("outside" + i % params.numFacilities, ActivityFacility.class), "outside");

				events.add(outside1);
				events.add(outside2);
			}

			ActivityStartEvent homeEvent2 = new ActivityStartEvent(18 * 3600., id, link, Id.create("home" + homeId, ActivityFacility.class), "home", null);

			events.add(homeEvent1);
			events.add(homeEvent2);

			homeSize++;
			if (homeSize == params.homeSize) {
				homeSize = 0;
				homeId++;
			}
		}

		events.sort(Comparator.comparingDouble(Event::getTime));
		for (DayOfWeek day : DayOfWeek.values()) {
			all.put(day, events);
		}

		return new ReplayHandler(all);
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		config.global().setRandomSeed(params.seed);
		config.controler().setOutputDirectory(String.format("./output/synthetic-%s/", params.contactModel));

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setInitialInfections(1);
		episimConfig.setSampleSize(1);
		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		episimConfig.setCalibrationParameter(getCalibrationParam());

		episimConfig.setStartDate("2020-02-18");
		episimConfig.setMaxContacts(params.maxContacts);
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

	private double getCalibrationParam() {

		double param = 0;

		switch (params.contactModel.getSimpleName()) {

		}

		return param;
	}

}
