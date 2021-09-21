package org.matsim.episim.model.activity;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.policy.Restriction;

import java.util.BitSet;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Default participation model that restricts participation based on remaining fraction.
 */
public class DefaultParticipationModel implements ActivityParticipationModel {

	private final SplittableRandom rnd;
	private final EpisimConfigGroup episimConfig;
	private final VaccinationConfigGroup vaccinationConfig;
	private ImmutableMap<String, Restriction> im;
	private int iteration;

	@Inject
	public DefaultParticipationModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, VaccinationConfigGroup vaccinationConfig) {
		this.rnd = rnd;
		this.episimConfig = episimConfig;
		this.vaccinationConfig = vaccinationConfig;

		if (episimConfig.getActivityHandling() == EpisimConfigGroup.ActivityHandling.duringContact)
			throw new IllegalStateException("Participation model can only be used with activityHandling startOfDay");
	}

	@Override
	public void setRestrictionsForIteration(int iteration, ImmutableMap<String, Restriction> im) {
		this.im = im;
		this.iteration = iteration;
	}

	@Override
	public void updateParticipation(EpisimPerson person, BitSet trajectory, int offset, List<EpisimPerson.PerformedActivity> activities) {
		for (int i = 0; i < activities.size(); i++) {
			Restriction context = im.get(activities.get(i).params.getContainerName());
			double r = context.getRemainingFraction();

			// reduce fraction for persons that are not vaccinated
			if (context.getSusceptibleRf() != null && context.getSusceptibleRf() != 1d)
				if (!(person.isRecentlyRecovered(iteration) || (person.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes &&
						person.daysSince(EpisimPerson.VaccinationStatus.yes, iteration) > vaccinationConfig.getParams(person.getVaccinationType()).getDaysBeforeFullEffect())))
					r *= context.getSusceptibleRf();

			if (r == 1.0)
				trajectory.set(offset + i, true);
			else if (r == 0.0)
				trajectory.set(offset + i, false);
			else
				trajectory.set(offset + i, rnd.nextDouble() < r);


		}
	}
}
