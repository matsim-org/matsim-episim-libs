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
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.input.ActivityParticipation;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Snz scenario for Munich.
 *
 * @see AbstractSnzScenario
 */
public final class SnzMunichScenario25pct2020 extends AbstractSnzScenario2020 {
	// classes should either be final or package-private if not explicitly designed for inheritance.  kai, dec'20

	/**
	 * Path pointing to the input folder. Can be configured at runtime with EPISIM_INPUT variable.
	 */
	public static Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/MunichV2/episim-input");

	/**
	 * The base policy based on actual restrictions in the past and mobility data
	 */
	public static FixedPolicy.ConfigBuilder basePolicy(ActivityParticipation activityParticipation, Map<String, Double> ciCorrections,
													   long introductionPeriod, Double maskCompliance, boolean restrictSchoolsAndDayCare,
													   boolean restrictUniversities) throws IOException {


		ConfigBuilder restrictions;

		if (activityParticipation == null) restrictions = FixedPolicy.config();
		else restrictions = activityParticipation.createPolicy();

		if (restrictSchoolsAndDayCare) {
		restrictions.restrict("2020-03-14", 0.1, "educ_primary", "educ_kiga")
				.restrict("2020-03-14", 0., "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
//				.restrict("2020-04-27", Restriction.ofMask(Map.of(FaceMask.CLOTH, clothMaskCompliance, FaceMask.SURGICAL, surgicalMaskCompliance)), AbstractSnzScenario2020.DEFAULT_ACTIVITIES)
//				.restrict("2020-04-27", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.6, FaceMask.SURGICAL, 0.3)), "pt", "shop_daily", "shop_other")
				.restrict("2020-05-11", 0.3, "educ_primary")
				.restrict("2020-05-11", 0.2, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
				.restrict("2020-05-25", 0.3, "educ_kiga")

				//TODO kläre, was genau in der Zwischenzeit bis zu Ferienbeginn in den Schulen passiert ist
//				.restrict("2020-06-08", 1., "educ_primary", "educ_kiga", "educ_secondary",  "educ_tertiary", "educ_other")

//				https://www.schulferien.org/Bayern/bayern.html

//				//Sommerferien
				.restrict("2020-07-25", 0.2, "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other")

				//Ende der Sommerferien
				.restrict("2020-09-08", 1., "educ_primary", "educ_kiga", "educ_secondary", "educ_tertiary", "educ_other")

				//ab hier gilt ein 3-Stufen-Plan für Kiga und Schulen. Stufe 4 würde restriction = 0.5 bedeuten. Tritt in Kraft nach Ermessen des Gesundheitsamtes
				// https://www.muenchen.de/rathaus/Stadtverwaltung/Referat-fuer-Bildung-und-Sport/Schule/corona.html#drei-stufen-plan_1
				// https://www.muenchen.de/rathaus/Serviceangebote/familie/kinderbetreuung/corona.html


				//Herbstferien
				.restrict("2020-10-31", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
				.restrict("2020-11-08", 1., "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")

				.restrict("2020-11-18", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
				.restrict("2020-11-19", 1., "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")

				//Weihnachtsferien
				.restrict("2020-12-23", 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
				.restrict("2021-01-10", 1., "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")

				//Winterferien
				.restrict("2021-02-15", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
				.restrict("2021-02-21", 1., "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")

				//Osterferien
				.restrict("2021-03-29", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
				.restrict("2021-04-11", 1., "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
		;


		}
		if (restrictUniversities ) {
			//Uni startet wieder mit Präsenzveranstaltungen
			// https://www.uni-muenchen.de/aktuelles/corona_informationen/studium_lehre/index.html#Vorlesungszeit
			// TUM kombiniert Präsenz- und digitale VEranstaltungen
			// https://www.tum.de/die-tum/aktuelles/coronavirus/studium/

			//but maybe that is just different wording for the same thing that laso Berlin does
//			.restrict("2020-11-02", 0.5, "educ_higher")
			restrictions.restrict("2020-11-02", 0.5, "educ_higher")
			;
		}
		for (Map.Entry<String, Double> e : ciCorrections.entrySet()) {

			String date = e.getKey();
			Double ciCorrection = e.getValue();
			restrictions.restrict(date, Restriction.ofCiCorrection(ciCorrection), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
			restrictions.restrict(date, Restriction.ofCiCorrection(ciCorrection), "quarantine_home");
			restrictions.restrict(date, Restriction.ofCiCorrection(ciCorrection), "pt");
		}


		if (maskCompliance == 0) return restrictions;

		LocalDate masksCenterDate = LocalDate.of(2020, 4, 27);
		double clothFraction = maskCompliance * 0.9;
		double surgicalFraction = maskCompliance * 0.1;
		// this is the date when it was officially introduced in Berlin, so for the time being we do not make this configurable.  Might be different
		// in MUC and elsewhere!
		//MUC started on the same date https://www.muenchen.de/aktuell/2020-04/corona-einkaufen-maerkte-muenchen-mit-maske.html

		for (int ii = 0; ii <= introductionPeriod; ii++) {
			LocalDate date = masksCenterDate.plusDays(-introductionPeriod / 2 + ii);
			restrictions.restrict(date, Restriction.ofMask(Map.of(FaceMask.CLOTH, clothFraction * ii / introductionPeriod,
					FaceMask.SURGICAL, surgicalFraction * ii / introductionPeriod)), "pt", "shop_daily", "shop_other");
		}

		// mask compliance according to bvg
		restrictions.restrict("2020-06-01", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.8 * 0.9, FaceMask.SURGICAL, 0.8 * 0.1)), "pt", "shop_daily", "shop_other", "errands");
		restrictions.restrict("2020-07-01", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.85 * 0.9, FaceMask.SURGICAL, 0.85 * 0.1)), "pt", "shop_daily", "shop_other", "errands");
		restrictions.restrict("2020-08-01", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.9 * 0.9, FaceMask.SURGICAL, 0.9 * 0.1)), "pt", "shop_daily", "shop_other", "errands");
		restrictions.restrict("2020-10-25", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.8 * 0.9, FaceMask.SURGICAL, 0.8 * 0.1)), "educ_higher", "educ_tertiary", "educ_other");

		return restrictions;

	}

	@Provides
	@Singleton
	public Config config() {

		Config config = getBaseConfig();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInitialInfections(500);
		episimConfig.setInitialInfectionDistrict("München");//TODO
		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000_002_6);
		episimConfig.setMaxContacts(3);
		String startDate = "2020-02-08";
		episimConfig.setStartDate(startDate);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		int offset = (int) (ChronoUnit.DAYS.between(episimConfig.getStartDate(), LocalDate.parse("2020-04-01")) + 1);
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(offset);
		double tracingProbability = 0.5;
		tracingConfig.setTracingProbability(tracingProbability);
		tracingConfig.setTracingPeriod_days(14);
		tracingConfig.setMinContactDuration_sec(15 * 60.);
		tracingConfig.setQuarantineHouseholdMembers(true);
		tracingConfig.setEquipmentRate(1.);
		tracingConfig.setTracingDelay_days(2);
		tracingConfig.setTracingCapacity_pers_per_day(Integer.MAX_VALUE ); //TODO

		double alpha = 1.;
		double ciCorrection = 1.;

		File csv = new File(INPUT.resolve("MunichSnzData_daily_until20200919.csv").toString()); //TODO
		String dateOfCiChange = "2020-03-08";

		episimConfig.setProgressionConfig(SnzBerlinScenario25pct2020.baseProgressionConfig(Transition.config()).build());
		episimConfig.setHospitalFactor(1.6);


		BasePolicyBuilder basePolicyBuilder = new BasePolicyBuilder(episimConfig);

		episimConfig.setPolicy(FixedPolicy.class, basePolicyBuilder.build().build());
		config.controler().setOutputDirectory("./output-munich-25pct-SNZrestrictsFromCSV-newprogr-tracing-linearExtra-inf-500init-schoolsAfterSummer-nn-" + tracingProbability + "-" + alpha + "-" + ciCorrection + "-" + dateOfCiChange + "-" + episimConfig.getStartDate() + "-" + episimConfig.getCalibrationParameter() + "-2"); //TODO
//		config.controler().setOutputDirectory("./output-berlin-25pct-unrestricted-calibr-" + episimConfig.getCalibrationParameter());

		return config;
	}

	public static class BasePolicyBuilder {

		private final EpisimConfigGroup episimConfig;

		private Map<String, Double> ciCorrections = Map.of("2020-03-07", 0.32);
		private long introductionPeriod = 14;
		private double maskCompliance = 0.95;
		private boolean restrictSchoolsAndDayCare = true;
		private boolean restrictUniversities = true;
		private ActivityParticipation activityParticipation;

		public BasePolicyBuilder(EpisimConfigGroup episimConfig) {
			this.episimConfig = episimConfig;
			this.activityParticipation = new CreateRestrictionsFromCSV(episimConfig);
			// TODO: 18.04.2021
			this.activityParticipation.setInput(INPUT.resolve("BerlinSnzData_daily_until20210404.csv"));
		}
		public void setIntroductionPeriod(long introductionPeriod) {
			this.introductionPeriod = introductionPeriod;
		}

		public void setMaskCompliance(double maskCompliance) {
			this.maskCompliance = maskCompliance;
		}

		public void setActivityParticipation(ActivityParticipation activityParticipation) {
			this.activityParticipation = activityParticipation;
		}

		public ActivityParticipation getActivityParticipation() {
			return activityParticipation;
		}

		public void setCiCorrections(Map<String, Double> ciCorrections) {
			this.ciCorrections = ciCorrections;
		}

		public Map<String, Double> getCiCorrections() {
			return ciCorrections;
		}

		public boolean getRestrictSchoolsAndDayCare() {
			return restrictSchoolsAndDayCare;
		}

		public void setRestrictSchoolsAndDayCare(boolean restrictSchoolsAndDayCare) {
			this.restrictSchoolsAndDayCare = restrictSchoolsAndDayCare;
		}
		public boolean getRestrictUniversities() {
			return restrictUniversities;
		}

		public void setRestrictUniversities(boolean restrictUniversities) {
			this.restrictUniversities = restrictUniversities;
		}

		public ConfigBuilder build() {
			ConfigBuilder configBuilder = null;
			try {
				configBuilder = basePolicy(activityParticipation, ciCorrections,introductionPeriod,
						maskCompliance, restrictSchoolsAndDayCare, restrictUniversities);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return configBuilder;
		}

	}

}
