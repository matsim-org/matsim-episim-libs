package org.matsim.episim.model.activity;

import com.google.common.collect.ImmutableMap;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.policy.Restriction;

import java.util.BitSet;
import java.util.List;

/**
 * The original default model that does not restrict activities in any way.
 */
public final class AllParticipationModel implements ActivityParticipationModel {

	@Override
	public void setRestrictionsForIteration(int iteration, ImmutableMap<String, Restriction> im) {

	}

	@Override
	public void updateParticipation(EpisimPerson person, BitSet trajectory, int index,
									List<EpisimPerson.PerformedActivity> activities) {

	}

	@Override
	public void applyQuarantine(EpisimPerson person, BitSet trajectory, int offset, List<EpisimPerson.PerformedActivity> activities) {
		// Nothing to do
	}
}
