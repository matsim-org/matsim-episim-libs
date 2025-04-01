package org.matsim.run.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;

import javax.inject.Singleton;

import static org.matsim.episim.model.Transition.to;

/**
 * Base class for a module containing the config for a snz scenario.
 * These are based on data provided by snz. Please note that this data is not publicly available.
 */
public abstract class AbstractSnzScenario2020 extends AbstractModule {

	public static final String[] DEFAULT_ACTIVITIES = {
			"work", "leisure", "educ_kiga", "educ_primary", "educ_secondary", "educ_tertiary", "educ_higher", "educ_other", "shop_daily", "shop_other", "visit", "errands", "business"
	};

//	public static void setContactIntensities(EpisimConfigGroup episimConfig) {
//		episimConfig.getOrAddContainerParams("pt")
//				.setContactIntensity(10.0);
//		episimConfig.getOrAddContainerParams("work")
//				.setContactIntensity(3.0);
//		episimConfig.getOrAddContainerParams("leisure")
//				.setContactIntensity(5.0);
//		episimConfig.getOrAddContainerParams("visit")
//				.setContactIntensity(5.0);
//		episimConfig.getOrAddContainerParams("educ_kiga")
//				.setContactIntensity(10.0);
//		episimConfig.getOrAddContainerParams("educ_primary")
//				.setContactIntensity(6.0);
//		episimConfig.getOrAddContainerParams("educ_secondary")
//				.setContactIntensity(4.0);
//		episimConfig.getOrAddContainerParams("educ_tertiary")
//				.setContactIntensity(3.0);
//		episimConfig.getOrAddContainerParams("educ_higher")
//				.setContactIntensity(3.0);
//		episimConfig.getOrAddContainerParams("educ_other")
//				.setContactIntensity(3.0);
//		episimConfig.getOrAddContainerParams("home")
//				.setContactIntensity(3.0 / 4.);
//		episimConfig.getOrAddContainerParams("quarantine_home")
//				.setContactIntensity(0.01);
//	}

	public static void addParams(EpisimConfigGroup episimConfig) {
		episimConfig.getOrAddContainerParams("pt", "tr")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("work")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("leisure")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_kiga")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_primary")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_secondary")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_tertiary")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_higher")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("educ_other")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("shop_daily")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("shop_other")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("visit")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("errands")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("business")
				.setContactIntensity(1.);
		episimConfig.getOrAddContainerParams("home")
				.setContactIntensity(1.)
				.setSpacesPerFacility(1.); // home facilities have already been split
		episimConfig.getOrAddContainerParams("quarantine_home")
				.setContactIntensity(1.)
				.setSpacesPerFacility(1.);
	}

	/**
	 * Adds base progression config to the given builder.
	 */
	public static Transition.Builder baseProgressionConfig(Transition.Builder builder) {
		return builder
				// Inkubationszeit: Die Inkubationszeit [ ... ] liegt im Mittel (Median) bei 5–6 Tagen (Spannweite 1 bis 14 Tage)
				.from(EpisimPerson.DiseaseStatus.infectedButNotContagious,
						to(EpisimPerson.DiseaseStatus.contagious, Transition.logNormalWithMedianAndStd(4., 4.)))

// Dauer Infektiosität:: Es wurde geschätzt, dass eine relevante Infektiosität bereits zwei Tage vor Symptombeginn vorhanden ist und die höchste Infektiosität am Tag vor dem Symptombeginn liegt
// Dauer Infektiosität: Abstrichproben vom Rachen enthielten vermehrungsfähige Viren bis zum vierten, aus dem Sputum bis zum achten Tag nach Symptombeginn
				.from(EpisimPerson.DiseaseStatus.contagious,
						to(EpisimPerson.DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndStd(2., 2.)),    //80%
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(4., 4.)))            //20%

// Erkankungsbeginn -> Hospitalisierung: Eine Studie aus Deutschland zu 50 Patienten mit eher schwereren Verläufen berichtete für alle Patienten eine mittlere (Median) Dauer von vier Tagen (IQR: 1–8 Tage)
				.from(EpisimPerson.DiseaseStatus.showingSymptoms,
						to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndStd(5., 5.)),
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))

// Hospitalisierung -> ITS: In einer chinesischen Fallserie betrug diese Zeitspanne im Mittel (Median) einen Tag (IQR: 0–3 Tage)
				.from(EpisimPerson.DiseaseStatus.seriouslySick,
						to(EpisimPerson.DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1., 1.)),
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(14., 14.)))

// Dauer des Krankenhausaufenthalts: „WHO-China Joint Mission on Coronavirus Disease 2019“ wird berichtet, dass milde Fälle im Mittel (Median) einen Krankheitsverlauf von zwei Wochen haben und schwere von 3–6 Wochen
				.from(EpisimPerson.DiseaseStatus.critical,
						to(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndStd(21., 21.)))

				.from(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical,
						to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7., 7.)))

				.from(EpisimPerson.DiseaseStatus.recovered,
						to(EpisimPerson.DiseaseStatus.susceptible, Transition.logNormalWithMean(360, 15)))

				;

		// yyyy Quellen für alle Aussagen oben??  "Es" oder "Eine Studie aus ..." ist mir eigentlich nicht genug.  kai, aug'20
		// yyyy Der obige Code existiert nochmals in ConfigurableProgressionModel.  Können wir in konsolidieren?  kai, oct'20

	}

	@Override
	protected void configure() {

		// Use age dependent progression model
		bind(DiseaseStatusTransitionModel.class).to(AgeDependentDiseaseStatusTransitionModel.class).in(Singleton.class);
	}

	/**
	 * Provider method that needs to be overwritten to generate fully configured scenario.
	 * Needs to be annotated with {@link Provides} and {@link Singleton}
	 */
	public abstract Config config();

	/**
	 * Creates a config with the default settings for all snz scenarios.
	 */
	protected static Config getBaseConfig() {

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.snz);

		addParams(episimConfig);

		return config;
	}


}
