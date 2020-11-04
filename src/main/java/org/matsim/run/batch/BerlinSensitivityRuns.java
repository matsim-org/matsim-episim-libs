package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.DiseaseImport;
import org.matsim.run.modules.SnzBerlinProductionScenario.Masks;
import org.matsim.run.modules.SnzBerlinProductionScenario.Restrictions;
import org.matsim.run.modules.SnzBerlinProductionScenario.Snapshot;
import org.matsim.run.modules.SnzBerlinProductionScenario.Tracing;

import javax.annotation.Nullable;


/**
 * Runs for symmetric Berlin week model
 */
public class BerlinSensitivityRuns implements BatchRun<BerlinSensitivityRuns.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		if (params == null)
			return new SnzBerlinWeekScenario2020();

		Class<? extends InfectionModel> infectionModel = AgeDependentInfectionModelWithSeasonality.class;
		DiseaseImport diseaseImport = DiseaseImport.yes;
		Restrictions restrictions = Restrictions.yes;
		Masks masks = Masks.yes;
		Tracing tracing = Tracing.yes;

		if (params.run.contains("noDiseaseImport")) diseaseImport = DiseaseImport.no;
		if (params.run.contains("noRestrictions")) restrictions = Restrictions.no;
		if (params.run.contains("noNonEduRestrictions")) restrictions = Restrictions.onlyEdu;
		if (params.run.contains("noEduRestrictions")) restrictions = Restrictions.allExceptEdu;
		if (params.run.contains("noSchoolAndDayCareRestrictions")) restrictions = Restrictions.allExceptSchoolsAndDayCare;
		if (params.run.contains("noUniversitiyRestrictions")) restrictions = Restrictions.allExceptUniversities;

		if (params.run.contains("noOutOfHomeRestrictionsExceptEdu")) restrictions = Restrictions.onlyEdu;
		if (params.run.contains("noMasks")) masks = Masks.no;
		if (params.run.contains("noTracing")) tracing = Tracing.no;
		if (params.run.contains("noAgeDepInfModel")) infectionModel = InfectionModelWithSeasonality.class;
				
		return new SnzBerlinProductionScenario(25, diseaseImport, restrictions, masks, tracing, Snapshot.no, infectionModel);
//		return new SnzBerlinProductionScenario(25, DiseaseImport.yes, Restrictions.yes, Masks.yes, Tracing.yes, Snapshot.no, InfectionModelWithSeasonality.class);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "basePaper");
	}

	@Override
	public Config prepareConfig(int id, Params params) {
		
		Class<? extends InfectionModel> infectionModel = AgeDependentInfectionModelWithSeasonality.class;
		DiseaseImport diseaseImport = DiseaseImport.yes;
		Restrictions restrictions = Restrictions.yes;
		Masks masks = Masks.yes;
		Tracing tracing = Tracing.yes;

		if (params.run.contains("noDiseaseImport")) diseaseImport = DiseaseImport.no;
		if (params.run.contains("noRestrictions")) restrictions = Restrictions.no;
		if (params.run.contains("noNonEduRestrictions")) restrictions = Restrictions.onlyEdu;
		if (params.run.contains("noEduRestrictions")) restrictions = Restrictions.allExceptEdu;
		if (params.run.contains("noSchoolAndDayCareRestrictions")) restrictions = Restrictions.allExceptSchoolsAndDayCare;
		if (params.run.contains("noUniversitiyRestrictions")) restrictions = Restrictions.allExceptUniversities;

		if (params.run.contains("noOutOfHomeRestrictionsExceptEdu")) restrictions = Restrictions.onlyEdu;
		if (params.run.contains("noMasks")) masks = Masks.no;
		if (params.run.contains("noTracing")) tracing = Tracing.no;
		if (params.run.contains("noAgeDepInfModel")) infectionModel = InfectionModelWithSeasonality.class;
		
		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario(25, diseaseImport, restrictions, masks, tracing, Snapshot.no, infectionModel);
		Config config = module.config();
		config.global().setRandomSeed(params.seed);
		
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		if (params.run.equals("noDiseaseImport") || params.run.equals("noAgeDepInfModel")) episimConfig.setCalibrationParameter(1.7E-5);
		
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		
//		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
//		builder.restrict("2020-03-06", Restriction.ofCiCorrection(params.ciCorrection), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
//		
//		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		public long seed;
		
//		@Parameter({1.8E-5, 1.7E-5, 1.6E-5, 1.5E-5, 1.4E-5, 1.3E-5, 1.27E-5, 1.1E-5, 1.E-5})
//		double theta;
		
		@StringParameter({"base", "noDiseaseImport", "noDiseaseImportAdaptedTheta", "noRestrictions", "noNonEduRestrictions", "noEduRestrictions", "noSchoolAndDayCareRestrictions", "noUniversitiyRestrictions", "noOutOfHomeRestrictionsExceptEdu", "noMasks", "noTracing", "noAgeDepInfModel", "noAgeDepInfModelAdaptedTheta"})
		public String run;
		
//		@StringParameter({"yes", "no"})
//		public String diseaseimport;
//		
//		@StringParameter({"yes", "no", "onlyEdu", "allExceptEdu"})
//		public String restrictions;
//		
//		@StringParameter({"yes", "no"})
//		public String masks;
//		
//		@StringParameter({"yes", "no"})
//		public String tracing;
//		
//		@StringParameter({"yes", "no"})
//		public String AgeDepInfModel;
		
		@Parameter({1.0, 1.05, 1.1})
		double thetaFactor;

	}


}
