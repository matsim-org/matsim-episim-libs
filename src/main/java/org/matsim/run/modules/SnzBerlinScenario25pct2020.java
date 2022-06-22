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
import org.matsim.episim.model.input.RestrictionInput;
import org.matsim.episim.model.input.CreateAdjustedRestrictionsFromCSV;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.model.input.RestrictionInput;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.ShutdownPolicy;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Snz scenario for Berlin.
 *
 * @see AbstractSnzScenario
 */
public final class SnzBerlinScenario25pct2020 extends AbstractSnzScenario2020 {
	// classes should either be final or package-private if not explicitly designed for inheritance.  kai, dec'20

	/**
	 * Path pointing to the input folder. Can be configured at runtime with EPISIM_INPUT variable.
	 */
	public static Path INPUT = EpisimUtils.resolveInputPath("../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input");

	/**
	 * The base policy based on actual restrictions in the past and mobility data
	 */
	private static ShutdownPolicy.ConfigBuilder<?> basePolicy(RestrictionInput activityParticipation, Map<String, Double> ciCorrections,
														   long introductionPeriod, Double maskCompliance, boolean restrictSchoolsAndDayCare,
														   boolean restrictUniversities) throws IOException {
		// note that there is already a builder around this
		ConfigBuilder restrictions;

		// adjusted restrictions must be created after policy was set, currently there is no nicer way to do this
		if (activityParticipation == null || activityParticipation instanceof CreateAdjustedRestrictionsFromCSV) {
			restrictions = FixedPolicy.config();
		} else
			restrictions = (ConfigBuilder) activityParticipation.createPolicy();

		if (restrictSchoolsAndDayCare) {
			restrictions.restrict("2020-03-14", 0.1, "educ_primary", "educ_kiga")
			.restrict("2020-03-14", 0., "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2020-05-11", 0.3, "educ_primary")
			.restrict("2020-05-11", 0.2, "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2020-05-25", 0.3, "educ_kiga")
			.restrict("2020-06-08", 0.5, "educ_kiga")
			.restrict("2020-06-22", 1., "educ_kiga")
			//Sommerferien
			.restrict("2020-06-25", 0.2, "educ_primary")
			//Ende der Sommerferien
			.restrict("2020-08-08", 1., "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			//Lueften nach den Sommerferien
			.restrict("2020-08-08", Restriction.ofCiCorrection(0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
			//Herbstferien
			.restrict("2020-10-12", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2020-10-25", 1., "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			//Weihnachtsferien (vorgezogen)
			.restrict("2020-12-16", 0.2, "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
//			.restrict("2021-01-03", 1., "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2021-01-03", 0.5, "educ_kiga")
			.restrict("2021-01-03", 0.3, "educ_primary")
//			//Winterferien
			.restrict("2021-02-01", 0.2, "educ_primary")
			.restrict("2021-02-07", 0.3, "educ_primary")

//			.restrict("2021-02-22", 1., "educ_primary", "educ_secondary", "educ_tertiary", "educ_other",  "educ_kiga")
			.restrict("2021-02-22", .5, "educ_primary")
			.restrict("2021-02-22", .5, "educ_secondary", "educ_tertiary", "educ_other")

			//Osterferien
			.restrict("2021-03-29", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2021-04-11", 0.5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			//Sommerferien
			.restrict("2021-06-24", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2021-08-09", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga")
			//Herbstferien
			.restrict("2021-10-11", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2021-10-25", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			//Weihnachtsferien
			.restrict("2021-12-21", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2022-01-04", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			// ** 2022 **
			//Winterferien
			.restrict("2022-01-29", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2022-02-05", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			//Osterferien
			.restrict("2022-04-11", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2022-04-23", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			//Sommerferien
			.restrict("2022-07-07", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2022-08-19", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			//Herbstferien
			.restrict("2022-10-24", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2022-11-05", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			//Weihnachtsferien
			.restrict("2022-12-22", 0.2, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")
			.restrict("2023-01-02", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other")

			;
		}

		if (restrictUniversities ) {
			restrictions.restrict("2020-03-14", 0., "educ_higher")
			.restrict("2020-05-11", 0.2, "educ_higher");
			//TODO: what happened to higher education in 2021/2022
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
		double clothFraction = maskCompliance * 1./3.;
		double surgicalFraction = maskCompliance * 1./3.;
		double ffpFraction = maskCompliance * 1./3.;
		// this is the date when it was officially introduced in Berlin, so for the time being we do not make this configurable.  Might be different
		// in MUC and elsewhere!

		for (int ii = 0; ii <= introductionPeriod; ii++) {
			LocalDate date = masksCenterDate.plusDays(-introductionPeriod / 2 + ii);
			restrictions.restrict(date, Restriction.ofMask(Map.of(
					FaceMask.CLOTH, clothFraction * ii / introductionPeriod,
					FaceMask.N95, ffpFraction * ii / introductionPeriod,
					FaceMask.SURGICAL, surgicalFraction * ii / introductionPeriod)),
					"pt", "shop_daily", "shop_other", "errands");
		}

		// mask compliance according to bvg
		restrictions.restrict(LocalDate.parse("2020-06-01"), Restriction.ofMask(Map.of(
				FaceMask.CLOTH, 0.8 * 1./3.,
				FaceMask.N95, 0.8 * 1./3.,
				FaceMask.SURGICAL, 0.8 * 1./3.)),
				"pt", "shop_daily", "shop_other", "errands");
		restrictions.restrict(LocalDate.parse("2020-07-01"), Restriction.ofMask(Map.of(
				FaceMask.CLOTH, 0.85 * 1./3.,
				FaceMask.N95, 0.85 * 1./3.,
				FaceMask.SURGICAL, 0.85 * 1./3.)),
				"pt", "shop_daily", "shop_other", "errands");
		restrictions.restrict(LocalDate.parse("2020-08-01"), Restriction.ofMask(Map.of(
				FaceMask.CLOTH, 0.9 * 1./3.,
				FaceMask.N95, 0.9 * 1./3.,
				FaceMask.SURGICAL, 0.9 * 1./3.)),
				"pt", "shop_daily", "shop_other", "errands");

		//Pflicht für medizinische Masken: https://www.rbb24.de/politik/thema/corona/beitraege/2021/01/verschaerfte-maskenpflicht-berlin-ffp2-oepnv-.html
		restrictions.restrict(LocalDate.parse("2021-01-24"), Restriction.ofMask(Map.of(
				FaceMask.N95, 0.9 * 1./2.,
				FaceMask.SURGICAL, 0.9 * 1./2.)),
				"pt", "shop_daily", "shop_other", "errands");

		//FFP2-Maskenpflicht 2021: https://www.berlin.de/aktuelles/berlin/6489489-958092-ab-mittwoch-an-vielen-orten-ffp2masken-p.html
		restrictions.restrict(LocalDate.parse("2021-03-31"), Restriction.ofMask(Map.of(
				FaceMask.N95, 0.9)),
				"pt", "shop_daily", "shop_other", "errands");

		//Maskenpflicht Lockerung: medizinische Masken im Einzelhandel: https://www.berlin.de/rbmskzl/aktuelles/pressemitteilungen/2021/pressemitteilung.1103648.php
		restrictions.restrict(LocalDate.parse("2021-07-10"), Restriction.ofMask(Map.of(
				FaceMask.N95, 0.9 * 1./2.,
				FaceMask.SURGICAL, 0.9 * 1./2.)),
				"shop_daily", "shop_other", "errands");

		//FFP2-Pflicht Lockerung im ÖPNV mit der Einführung der 3G Regelung: https://gesetze.berlin.de/bsbe/document/jlr-CoronaV4VBEV4P2
		restrictions.restrict(LocalDate.parse("2021-12-18"), Restriction.ofMask(Map.of(
				FaceMask.N95, 0.9 * 1./2.,
				FaceMask.SURGICAL, 0.9 * 1./2.)),
				"pt");

		//FFP2-Maskenpflicht in public transit (2022): https://www.berlin.de/aktuelles/7240429-958090-senat-beschliesst-ffp2maskenpflicht-im-o.html
		restrictions.restrict(LocalDate.parse("2022-01-15"), Restriction.ofMask(Map.of(
				FaceMask.N95, 0.9)),
				"pt");

		//FFP2-Maskenpflicht in pt and businesses (2022) TODO: coming soon:https://www.berlin.de/aktuelles/7299635-958090-ffp2maskenpflicht-statt-2gregel-im-einze.html
//		restrictions.restrict(LocalDate.parse("2021-03-31"), Restriction.ofMask(Map.of(
//				FaceMask.N95, 0.9)),
//				"pt", "shop_daily", "shop_other", "errands");

		restrictions.restrict(LocalDate.parse("2020-10-25"), Restriction.ofMask(Map.of(
				FaceMask.CLOTH, 0.9 * 1./3.,
				FaceMask.N95, 0.9 * 1./3.,
				FaceMask.SURGICAL, 0.9 * 1./3.)),
				"educ_higher", "educ_tertiary", "educ_other");

		if (activityParticipation instanceof CreateAdjustedRestrictionsFromCSV) {
			CreateAdjustedRestrictionsFromCSV adjusted = (CreateAdjustedRestrictionsFromCSV) activityParticipation;

			LocalDate[] period = new LocalDate[] {LocalDate.MIN, LocalDate.MAX};
			adjusted.setPolicy(restrictions);
			LocalDate[] restaurantPeriod = new LocalDate[] {LocalDate.parse("2020-03-22"), LocalDate.parse("2020-05-14"), LocalDate.parse("2020-11-02"), LocalDate.MAX};
			adjusted.setAdministrativePeriods(Map.of(
					"educ_primary", period,
					"educ_secondary", period,
					"educ_tertiary", period,
					"educ_other", period,
					"educ_kiga" , period,
					"restaurant", restaurantPeriod
			));

			return activityParticipation.createPolicy();
		}

		return restrictions;
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = getBaseConfig();

		config.vehicles().setVehiclesFile(INPUT.resolve("de_2020-vehicles.xml").toString());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile(INPUT.resolve("be_2020_snz_episim_events_25pt_split.xml.gz").toString());

		config.plans().setInputFile(INPUT.resolve("be_2020_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz").toString());

		episimConfig.setInitialInfections(500);
		episimConfig.setInitialInfectionDistrict("Berlin");
		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000_011_0);
		episimConfig.setMaxContacts(3);
		String startDate = "2020-02-16";
		episimConfig.setStartDate(startDate);
		episimConfig.setHospitalFactor(1.6);
		episimConfig.setProgressionConfig(baseProgressionConfig(Transition.config()).build());

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
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30
		));

		BasePolicyBuilder basePolicyBuilder = new BasePolicyBuilder(episimConfig);

		episimConfig.setPolicy(FixedPolicy.class, basePolicyBuilder.buildFixed().build());

		config.controler().setOutputDirectory("./output-berlin-25pct-input-" + basePolicyBuilder.getActivityParticipation() + "-ciCorrections-" + basePolicyBuilder.getCiCorrections() + "-startDate-" + episimConfig.getStartDate() + "-hospitalFactor-" + episimConfig.getHospitalFactor() + "-calibrParam-" + episimConfig.getCalibrationParameter() + "-tracingProba-" + tracingProbability);

//		config.controler().setOutputDirectory("./output-berlin-25pct-unrestricted-calibr-" + episimConfig.getCalibrationParameter());

		return config;
	}

	public static class BasePolicyBuilder {
		private final EpisimConfigGroup episimConfig;

		/*
		 *  alpha = 1 -> ci=0.323
		 *  alpha = 1.2 -> ci=0.360
		 *  alpha = 1.4 -> ci=0.437
		 */
		private Map<String, Double> ciCorrections = Map.of("2020-03-07", 0.32);
		private long introductionPeriod = 14;
		private double maskCompliance = 0.95;
		private boolean restrictSchoolsAndDayCare = true;
		private boolean restrictUniversities = true;
		private RestrictionInput activityParticipation;

		public BasePolicyBuilder(EpisimConfigGroup episimConfig) {
			this.episimConfig = episimConfig;
			this.activityParticipation = new CreateRestrictionsFromCSV(episimConfig);
			this.activityParticipation.setInput(INPUT.resolve("BerlinSnzData_daily_until20220204.csv"));
		}

		public void setIntroductionPeriod(long introductionPeriod) {
			this.introductionPeriod = introductionPeriod;
		}

		public void setMaskCompliance(double maskCompliance) {
			this.maskCompliance = maskCompliance;
		}

		public void setActivityParticipation(RestrictionInput activityParticipation) {
			this.activityParticipation = activityParticipation;
		}

		public RestrictionInput getActivityParticipation() {
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

		/**
		 * Build a {@link FixedPolicy}.
		 * @deprecated use {@link #build()}
		 * @throws ClassCastException if the {@link RestrictionInput} is not creating a {@link FixedPolicy}.
		 */
		public ConfigBuilder buildFixed() {
			ConfigBuilder configBuilder;
			try {
				configBuilder = (ConfigBuilder) basePolicy(activityParticipation, ciCorrections,introductionPeriod,
						maskCompliance, restrictSchoolsAndDayCare, restrictUniversities);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return configBuilder;
		}

		public ShutdownPolicy.ConfigBuilder<?> build() {
			ShutdownPolicy.ConfigBuilder<?> configBuilder;
			try {
				configBuilder = basePolicy(activityParticipation, ciCorrections,introductionPeriod,
						maskCompliance, restrictSchoolsAndDayCare, restrictUniversities);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return configBuilder;
		}

	}

}
