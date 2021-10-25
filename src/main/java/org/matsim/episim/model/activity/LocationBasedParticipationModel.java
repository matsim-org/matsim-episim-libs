package org.matsim.episim.model.activity;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.policy.Restriction;
import org.matsim.facilities.ActivityFacility;

import java.util.*;

/**
 * Location based participation model restricts activity participation by the local remaining fraction corresponding
 * to the location of the ActivityFacility, if available. Otherwise, the global remaining fraction is used.
 */
public class LocationBasedParticipationModel implements ActivityParticipationModel {

	private final SplittableRandom rnd;
	private final EpisimConfigGroup episimConfig;
	private ImmutableMap<String, Restriction> im;

	/**
	 * Map of each ActivityFacility with the corresponding subdistrict
	 */
	private final Map<String, String> subdistrictFacilities;

	@Inject
	public LocationBasedParticipationModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, Scenario scenario) {
		this.rnd = rnd;
		this.episimConfig = episimConfig;

		if (episimConfig.getActivityHandling() == EpisimConfigGroup.ActivityHandling.duringContact)
			throw new IllegalStateException("Participation model can only be used with activityHandling startOfDay");

		if (episimConfig.getDistrictLevelRestrictions() != EpisimConfigGroup.DistrictLevelRestrictions.yes) {
			throw new IllegalStateException("LocationBasedParticipationModel can only be used if location based restrictions are used");
		}

		subdistrictFacilities = new HashMap<>();
		if (scenario != null && !scenario.getActivityFacilities().getFacilities().isEmpty()) {
			for (ActivityFacility facility : scenario.getActivityFacilities().getFacilities().values()) {
				String subdistrictAttributeName = episimConfig.getDistrictLevelRestrictionsAttribute();
				String subdistrict = (String) facility.getAttributes().getAttribute(subdistrictAttributeName);
				if (subdistrict != null) {
					this.subdistrictFacilities.put(facility.getId().toString(), subdistrict);
				}
			}
		}
	}

	@Override
	public void setRestrictionsForIteration(int iteration, ImmutableMap<String, Restriction> im) {
		this.im = im;
	}

	@Override
	public void updateParticipation(EpisimPerson person, BitSet trajectory, int offset, List<EpisimPerson.PerformedActivity> activities) {
		for (int i = 0; i < activities.size(); i++) {
			String context = activities.get(i).params.getContainerName();
			Id<ActivityFacility> facilityId = activities.get(i).getFacilityId();

			Restriction restriction = im.get(context);
			double remainingFraction = restriction.getRemainingFraction();

			// Replaces global remaining fraction with local one, if applicable
			if (facilityId != null) {
				if (subdistrictFacilities.containsKey(facilityId.toString())) {
					String subdistrict = subdistrictFacilities.get(facilityId.toString());
					if (restriction.getLocationBasedRf().containsKey(subdistrict)) {
						remainingFraction = restriction.getLocationBasedRf().get(subdistrict);
					}
				}
			}

			if (remainingFraction == 1.0)
				trajectory.set(offset + i, true);
			else if (remainingFraction == 0.0)
				trajectory.set(offset + i, false);
			else
				trajectory.set(offset + i, rnd.nextDouble() < remainingFraction);

		}
	}
}
