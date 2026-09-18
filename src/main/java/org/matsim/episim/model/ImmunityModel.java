package org.matsim.episim.model;

import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.Immunizable;

/**
 * Protection that a person's immune history gives against a disease-status transition.
 *
 * <p>This is the single source of immunity for the whole simulation. The infection model asks for protection
 * against {@link DiseaseStatus#infectedButNotContagious}, the disease-status transition model asks for
 * protection against {@link DiseaseStatus#seriouslySick}, and so on. Neither of them knows whether the answer
 * comes from the effectiveness curves of {@code VaccinationConfigGroup} or from antibody levels: the source is
 * one binding for the whole run instead of one per channel, which is what previously let susceptibility and
 * severity be computed from different, unrelated sources.</p>
 *
 * <p>Infection and disease progression are transitions of the same state machine, which is why one method keyed
 * by the target {@link DiseaseStatus} covers both. It is also the shape in which the evidence arrives:
 * effectiveness is reported against infection, against symptomatic disease, against hospitalisation and against
 * death.</p>
 *
 * <p>Not to be confused with {@link AntibodyModel}, which evolves antibody levels day by day. This model changes
 * no state at all, it only reports how well protected a person currently is; an implementation based on
 * antibodies reads what {@code AntibodyModel} has written.</p>
 *
 * <p><b>The returned factor is remaining risk, not protection.</b> {@code 1.0} means no protection, so the
 * transition keeps its unmodified probability; {@code 0.0} means complete protection. Callers multiply it into
 * the probability. This is the opposite of the {@code effectiveness} convention of
 * {@code VaccinationConfigGroup}, where {@code 0.9} means 90&nbsp;% protection.</p>
 */
public interface ImmunityModel {

	/**
	 * Remaining risk that {@code person} makes the transition to {@code target} when challenged by {@code strain}.
	 *
	 * <p>Meaningful targets are the states a person can be protected against:</p>
	 * <ul>
	 *     <li>{@link DiseaseStatus#infectedButNotContagious} &mdash; protection against infection;</li>
	 *     <li>{@link DiseaseStatus#showingSymptoms} &mdash; against symptomatic disease;</li>
	 *     <li>{@link DiseaseStatus#seriouslySick} &mdash; against hospitalisation;</li>
	 *     <li>{@link DiseaseStatus#critical} &mdash; against intensive care.</li>
	 * </ul>
	 *
	 * <p>{@link DiseaseStatus#deceased} is not a supported target yet, although effectiveness against death is
	 * reported in the literature: no configuration holds a curve for it, and the transition to it is currently not
	 * modified by immunity at all.</p>
	 *
	 * @param person immune history to evaluate. Deliberately not an {@code EpisimPerson}: analysis code replays
	 *               events into its own {@link Immunizable} and has to be able to ask the same question.
	 * @param strain strain the person is challenged by, i.e. the strain protection is asked <i>against</i>. Which
	 *               past infection or vaccination the protection came from is up to the implementation.
	 * @param target disease status the person would transition to
	 * @param day    day the question is asked for. Not necessarily the current iteration: analysis code replays
	 *               past days. An implementation that caches keys its cache on this value; there is deliberately
	 *               no per-iteration lifecycle method, because a replaying caller has no iteration to announce.
	 * @return remaining risk in [0, 1]; {@code 1.0} means no protection
	 * @throws IllegalArgumentException if {@code target} is not one of the states listed above
	 */
	double getFactor(Immunizable person, VirusStrain strain, DiseaseStatus target, int day);

	/**
	 * Remaining infectivity of an infectious person, i.e. how much of their shedding is left after their own
	 * immunity. This is not a transition of {@code infector} but their contribution to somebody else's
	 * transition, which is why it does not fit {@link #getFactor}.
	 *
	 * @param infector the infectious person
	 * @param strain   strain the person is infected with
	 * @param day      current day / iteration
	 * @return remaining infectivity in [0, 1]; {@code 1.0} means not reduced
	 */
	double getInfectivityFactor(Immunizable infector, VirusStrain strain, int day);
}
