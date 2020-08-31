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

import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.commons.lang3.tuple.Triple;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.*;

import java.util.Map;
import java.util.SplittableRandom;

import static org.matsim.episim.EpisimUtils.nextLogNormalFromMeanAndSigma;
import static org.matsim.episim.model.InfectionModelWithViralLoad.SUSCEPTIBILITY;
import static org.matsim.episim.model.InfectionModelWithViralLoad.VIRAL_LOAD;

/**
 * Snz scenario for Berlin with enabled viral load infection model.
 *
 * @see AbstractSnzScenario
 */
public class SnzBerlinSuperSpreaderScenario extends AbstractSnzScenario2020 {

	/**
	 * Calibration parameter for triples of max contacts, sigma inf. and sigma susp.
	 * These are for {@link SymmetricContactModel}.
	 */
	private static final Map<Triple<Integer, Double, Double>, Double> calibrationSym = Map.of(

			// Different maxContacts with no individual variation
			Triple.of(1, 0d, 0d), 10.5e-5,
			Triple.of(3, 0d, 0d), 2.09e-5,
			Triple.of(10, 0d, 0d), 0.68e-5,
			Triple.of(30, 0d, 0d), 0.28e-5,

			// Different sigmas for 30 maxContacts TODO not valid anymore because of changes in contacts model
			Triple.of(30, 0.5, 0.5), 1.12e-5,
			Triple.of(30, 0.75, 0.75), 1.25e-5,
			Triple.of(30, 1d, 1d), 1.69e-5,
			Triple.of(30, 1.5d, 1.5d), 2.81e-5
	);

	/**
	 * Calibration parameter for {@link DefaultContactModel}
	 * @see #calibrationSym
	 */
	private static final Map<Triple<Integer, Double, Double>, Double> calibrationDefault = Map.of(
			Triple.of(1, 0d, 0d), Double.NaN,
			Triple.of(3, 0d, 0d), Double.NaN,
			Triple.of(10, 0d, 0d), Double.NaN,
			Triple.of(30, 0d, 0d), Double.NaN
	);

	private final boolean symmetric;
	private final int maxContacts;
	private final double sigmaInf;
	private final double sigmaSusp;

	public SnzBerlinSuperSpreaderScenario() {
		this(true, 30, 0, 0);
	}

	public SnzBerlinSuperSpreaderScenario(boolean symmetric, int maxContacts, double sigmaInf, double sigmaSusp) {
		this.symmetric = symmetric;
		this.maxContacts = maxContacts;
		this.sigmaInf = sigmaInf;
		this.sigmaSusp = sigmaSusp;
	}


	@Override
	protected void configure() {
		super.configure();

		bind(InfectionModel.class).to(InfectionModelWithViralLoad.class).in(Singleton.class);

		if (symmetric)
			bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
		else
			bind(ContactModel.class).to(DefaultContactModel.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	public Config config() {


		Config config = new SnzBerlinWeekScenario2020(25).config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		config.vehicles().setVehiclesFile(null);
		episimConfig.setMaxContacts(maxContacts);

		Triple<Integer, Double, Double> p = Triple.of(maxContacts, sigmaInf, sigmaSusp);

		double calibration;

		if (symmetric)
			calibration = calibrationSym.get(p);
		else
			calibration = calibrationDefault.get(p);

		episimConfig.setCalibrationParameter(calibration);

		// set start
		episimConfig.setStartDate("2020-02-16");

		/*
		// For calibration reduction
		ConfigUtils.addOrGetModule(config, TracingConfigGroup.class).setPutTraceablePersonsInQuarantineAfterDay(0);
		episimConfig.setWriteEvents(EpisimConfigGroup.WriteEvents.tracing);
		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
				.restrict(1, Restriction.of(0.5), "work", "leisure", "visit", "errands")
				.build()
		);*/


		// maybe ci calibration needed
		config.controler().setOutputDirectory("./output-berlin-25pct-superSpreader-calibrParam-" + episimConfig.getCalibrationParameter());

		return config;
	}

	@Provides
	@Singleton
	public Scenario scenario(Config config) {

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore);

		// save some time for not needed inputs
		config.facilities().setInputFile(null);

		ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "before loading scenario");

		final Scenario scenario = ScenarioUtils.loadScenario(config);

		SplittableRandom rnd = new SplittableRandom(4715);
		for (Person person : scenario.getPopulation().getPersons().values()) {
			person.getAttributes().putAttribute(VIRAL_LOAD, nextLogNormalFromMeanAndSigma(rnd, 1, sigmaInf));
			person.getAttributes().putAttribute(SUSCEPTIBILITY, nextLogNormalFromMeanAndSigma(rnd, 1, sigmaSusp));
		}

		return scenario;
	}

}
