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
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.Transition;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.matsim.episim.model.Transition.to;

/**
 * Snz scenario for Munich.
 *
 * @see AbstractSnzScenario
 */
public class SnzMunichScenario25pct2020 extends AbstractSnzScenario2020 {

	/**
	 * The base policy based on actual restrictions in the past and mobility data
	 */
	public static FixedPolicy.ConfigBuilder basePolicy(EpisimConfigGroup episimConfig, File csv, double alpha,
													   double ciCorrection, String dateOfCiChange, EpisimUtils.Extrapolation extrapolation) throws IOException {

		ConfigBuilder builder = EpisimUtils.createRestrictionsFromCSV2(episimConfig, csv, alpha, extrapolation);

		builder.restrict(dateOfCiChange, Restriction.ofCiCorrection(ciCorrection), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
		builder.restrict(dateOfCiChange, Restriction.ofCiCorrection(ciCorrection), "pt");

		builder.restrict("2020-03-14", 0.1, "educ_primary", "educ_kiga")
				.restrict("2020-03-14", 0., "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
//				.restrict("2020-04-27", Restriction.ofMask(Map.of(FaceMask.CLOTH, clothMaskCompliance, FaceMask.SURGICAL, surgicalMaskCompliance)), AbstractSnzScenario2020.DEFAULT_ACTIVITIES)
				.restrict("2020-04-27", Restriction.ofMask(Map.of(FaceMask.CLOTH, 0.6, FaceMask.SURGICAL, 0.3)), "pt", "shop_daily", "shop_other")
				.restrict("2020-05-11", 0.3, "educ_primary")
				.restrict("2020-05-11", 0.2, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
				.restrict("2020-05-25", 0.3, "educ_kiga")
				
				.restrict("2020-06-08", 1., "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
				
//				//Sommerferien //TODO
				.restrict("2020-07-25", 0.3, "educ_primary", "educ_kiga")
				.restrict("2020-07-25", 0.2, "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
				
				//Ende der Sommerferien //TODO 
				.restrict("2020-09-08", 1., "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other")
		;

		return builder;
	}

	/**
	 * Adds base progression config to the given builder.
	 */
	public static Transition.Builder baseProgressionConfig(Transition.Builder builder) {
		return builder
				// Inkubationszeit: Die Inkubationszeit [ ... ] liegt im Mittel (Median) bei 5–6 Tagen (Spannweite 1 bis 14 Tage)
				.from(EpisimPerson.DiseaseStatus.infectedButNotContagious,
						to(EpisimPerson.DiseaseStatus.contagious, Transition.logNormalWithMedianAndStd(4., 4.))) // 3 3

// Dauer Infektiosität:: Es wurde geschätzt, dass eine relevante Infektiosität bereits zwei Tage vor Symptombeginn vorhanden ist und die höchste Infektiosität am Tag vor dem Symptombeginn liegt
// Dauer Infektiosität: Abstrichproben vom Rachen enthielten vermehrungsfähige Viren bis zum vierten, aus dem Sputum bis zum achten Tag nach Symptombeginn
				.from(EpisimPerson.DiseaseStatus.contagious,
						to(EpisimPerson.DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndStd(2., 2.)),    //80%
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(4., 4.)))            //20%

// Erkankungsbeginn -> Hospitalisierung: Eine Studie aus Deutschland zu 50 Patienten mit eher schwereren Verläufen berichtete für alle Patienten eine mittlere (Median) Dauer von vier Tagen (IQR: 1–8 Tage)
				.from(EpisimPerson.DiseaseStatus.showingSymptoms,
						to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndStd(4., 4.)),
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))

// Hospitalisierung -> ITS: In einer chinesischen Fallserie betrug diese Zeitspanne im Mittel (Median) einen Tag (IQR: 0–3 Tage)
				.from(EpisimPerson.DiseaseStatus.seriouslySick,
						to(EpisimPerson.DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1., 1.)),
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(14., 14.)))

// Dauer des Krankenhausaufenthalts: „WHO-China Joint Mission on Coronavirus Disease 2019“ wird berichtet, dass milde Fälle im Mittel (Median) einen Krankheitsverlauf von zwei Wochen haben und schwere von 3–6 Wochen
				.from(EpisimPerson.DiseaseStatus.critical,
						to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndStd(21., 21.)));

	}

	@Provides
	@Singleton
	public Config config() {

		Config config = getBaseConfig();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Munich/episim-input/mu_2020_snz_episim_events_25pt.xml.gz"); //TODO

		config.plans().setInputFile("../shared-svn/projects/episim/matsim-files/snz/Munich/episim-input/mu_2020_snz_entirePopulation_noPlans_withDistricts_25pt.xml.gz"); //TODO

		episimConfig.setInitialInfections(500);
		episimConfig.setInitialInfectionDistrict("München");//TODO
		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.000_002_6);
		episimConfig.setMaxInteractions(3);
		String startDate = "2020-02-08";
		episimConfig.setStartDate(startDate);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		int offset = (int) (ChronoUnit.DAYS.between(episimConfig.getStartDate(), LocalDate.parse("2020-04-01")) + 1);
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(offset);
		double tracingProbability = 0.75;
		tracingConfig.setTracingProbability(tracingProbability);
		tracingConfig.setTracingDayDistance(14);
		tracingConfig.setMinDuration(15 * 60.);
		tracingConfig.setQuarantineHouseholdMembers(true);
		tracingConfig.setEquipmentRate(1.);
		tracingConfig.setTracingDelay(2);
		tracingConfig.setTracingCapacity(Integer.MAX_VALUE); //TODO 

		double alpha = 1.4;
		double ciCorrection = 0.3;

		File csv = new File("../shared-svn/projects/episim/matsim-files/snz/Munich/episim-input/MunichSnzData_daily_until20200531.csv"); //TODO
		String dateOfCiChange = "2020-03-08";

		episimConfig.setProgressionConfig(baseProgressionConfig(Transition.config()).build());

		ConfigBuilder configBuilder = null;
		try {
			configBuilder = basePolicy(episimConfig, csv, alpha, ciCorrection, dateOfCiChange, EpisimUtils.Extrapolation.linear);
		} catch (IOException e) {
			e.printStackTrace();
		}

		episimConfig.setPolicy(FixedPolicy.class, configBuilder.build());
		config.controler().setOutputDirectory("./output-munich-25pct-SNZrestrictsFromCSV-newprogr-tracing-linearExtra-inf-500init-schoolsAfterSummer-nn-" + tracingProbability + "-" + alpha + "-" + ciCorrection + "-" + dateOfCiChange + "-" + episimConfig.getStartDate() + "-" + episimConfig.getCalibrationParameter() + "-2"); //TODO
//		config.controler().setOutputDirectory("./output-berlin-25pct-unrestricted-calibr-" + episimConfig.getCalibrationParameter());


		return config;
	}

}
