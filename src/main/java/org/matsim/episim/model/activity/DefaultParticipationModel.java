package org.matsim.episim.model.activity;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
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
	private ImmutableMap<String, Restriction> im;

	@Inject
	public DefaultParticipationModel(SplittableRandom rnd, EpisimConfigGroup episimConfig) {
		this.rnd = rnd;
		this.episimConfig = episimConfig;

		if (episimConfig.getActivityHandling() == EpisimConfigGroup.ActivityHandling.duringContact)
			throw new IllegalStateException("Participation model can only be used with activityHandling startOfDay");
	}

	@Override
	public void setRestrictionsForIteration(int iteration, ImmutableMap<String, Restriction> im) {
		this.im = im;
	}

	@Override
	public void updateParticipation(EpisimPerson person, BitSet trajectory, int offset, List<EpisimPerson.PerformedActivity> activities) {
		for (int i = 0; i < activities.size(); i++) {
			String context = activities.get(i).params.getContainerName();
			double r = im.get(context).getRemainingFraction();

			if (r == 1.0)
				trajectory.set(offset + i, true);
			else if (r == 0.0)
				trajectory.set(offset + i, false);
			else
				trajectory.set(offset + i, rnd.nextDouble() < r);


		}
	}
}
