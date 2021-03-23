package org.matsim.episim.model.activity;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.doubles.DoubleObjectPair;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.policy.Restriction;

import java.util.BitSet;
import java.util.List;

/**
 * Model to determine at the start of the day if a person is participating at the planned activity.
 */
public interface ActivityParticipationModel {

	/**
	 * Sets iteration and restrictions of the current day.
	 */
	void setRestrictionsForIteration(int iteration, ImmutableMap<String, Restriction> im);

	/**
	 * Method that needs to update the activity participation of a certain day by setting the bit flags in {@code trajectory}.
	 * The person given to this method is just informational. All updates need to be performed in {@code trajectory}.
	 * The trajectory might be for multiple days, the starting point of the current day is given by {@code offset}.
	 * {@code activities} contains only the subset of activities for the current day, thus updates in {@code trajectory} needs
	 * to be set at offset + index.
	 *
	 * @param person     the person that is currently handled.
	 * @param trajectory bit set of the whole trajectory
	 * @param offset     starting index in the bitset for the current day
	 * @param activities activities of the current day
	 */
	void updateParticipation(EpisimPerson person, BitSet trajectory, int offset,
							 List<EpisimPerson.PerformedActivity> activities);

}
