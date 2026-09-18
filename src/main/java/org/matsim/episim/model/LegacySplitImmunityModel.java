package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.Immunizable;

/**
 * The immunity split that the simulation had before {@link ImmunityModel} existed: protection against infection
 * from the effectiveness curves, protection against a severe course from antibody levels.
 *
 * <p>This is not a model anybody chose. It is what fell out of two independent Guice bindings, since the source
 * of immunity used to be decided by which class was bound in each channel:</p>
 * <ul>
 *     <li>{@code InfectionModel} was bound to {@code AgeAndProgressionDependentInfectionModelWithSeasonality},
 *     which reads {@code getVaccinationEffectiveness} / {@code getImmunityEffectiveness} and
 *     {@code DefaultInfectionModel.getInfectivity} &mdash; all three are curves;</li>
 *     <li>{@code DiseaseStatusTransitionModel} was bound to {@code AgeDependentDiseaseStatusTransitionModel},
 *     which inherits the antibody overrides of {@code AntibodyDependentTransitionModel}.</li>
 * </ul>
 *
 * <p>Both scenarios in this repository bind exactly that pair, so this class reproduces their results while the
 * source of immunity becomes a single binding. It exists to make that refactoring provably behaviour-preserving
 * and is meant to be deleted: a run should be able to say which immunity model it uses, not inherit an answer
 * that differs between "will I be infected" and "how badly will it go".</p>
 *
 * <p>The routing is fixed on purpose. Making it configurable would turn a historical accident into an option.</p>
 */
public class LegacySplitImmunityModel implements ImmunityModel {

	private final LegacyCurveImmunityModel curves;
	private final LegacyAntibodyImmunityModel antibodies;

	@Inject
	public LegacySplitImmunityModel(LegacyCurveImmunityModel curves, LegacyAntibodyImmunityModel antibodies) {
		this.curves = curves;
		this.antibodies = antibodies;
	}

	@Override
	public double getFactor(Immunizable person, VirusStrain strain, DiseaseStatus target, int day) {
		return delegateFor(target).getFactor(person, strain, target, day);
	}

	@Override
	public double getInfectivityFactor(Immunizable infector, VirusStrain strain, int day) {
		// the infection model read the curve-based infectivity, so this follows the infection side
		return curves.getInfectivityFactor(infector, strain, day);
	}

	private ImmunityModel delegateFor(DiseaseStatus target) {
		switch (target) {
			case infectedButNotContagious:
				return curves;
			case showingSymptoms:
			case seriouslySick:
			case critical:
				return antibodies;
			default:
				throw new IllegalArgumentException("No protection is configured against disease status '" + target
					+ "'. Supported are infectedButNotContagious, showingSymptoms, seriouslySick and critical.");
		}
	}
}
