package org.matsim.episim.model.activity;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.policy.Restriction;
import org.matsim.facilities.ActivityFacility;

import java.time.LocalDate;
import java.util.*;

/**
 * Location based participation model restricts activity participation by the local remaining fraction corresponding
 * to the location of the ActivityFacility, if available. Otherwise, the global remaining fraction is used.
 */
public class LocationBasedParticipationModel implements ActivityParticipationModel {

	private final SplittableRandom rnd;
	private final EpisimConfigGroup episimConfig;
	private final VaccinationConfigGroup vaccinationConfig;
	private ImmutableMap<String, Restriction> im;
	private int iteration;
	private LocalDate date;


	/**
	 * Map of each ActivityFacility with the corresponding subdistrict
	 */
	private final Map<String, String> subdistrictFacilities;


	@Inject
	public LocationBasedParticipationModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, Scenario scenario,VaccinationConfigGroup vaccinationConfig) {
		this.rnd = rnd;
		this.episimConfig = episimConfig;
		this.vaccinationConfig = vaccinationConfig;

		if (episimConfig.getActivityHandling() == EpisimConfigGroup.ActivityHandling.duringContact)
			throw new IllegalStateException("Participation model can only be used with activityHandling startOfDay");

		if (episimConfig.getDistrictLevelRestrictions() == EpisimConfigGroup.DistrictLevelRestrictions.no) {
			throw new IllegalStateException("LocationBasedParticipationModel can only be used if location based restrictions are used");
		}

		subdistrictFacilities = new HashMap<>();
		if (episimConfig.getDistrictLevelRestrictions() == EpisimConfigGroup.DistrictLevelRestrictions.yesForActivityLocation) {
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
	}

	@Override
	public void setRestrictionsForIteration(int iteration, ImmutableMap<String, Restriction> im) {
		this.im = im;
		this.iteration = iteration;
		this.date = episimConfig.getStartDate().plusDays(iteration - 1);
	}

	@Override
	public void updateParticipation(EpisimPerson person, BitSet trajectory, int offset, List<EpisimPerson.PerformedActivity> activities) {
		for (int i = 0; i < activities.size(); i++) {
//			String context = activities.get(i).params.getContainerName();
			Id<ActivityFacility> facilityId = activities.get(i).getFacilityId();

			Restriction context = im.get(activities.get(i).params.getContainerName());
			double r = context.getRemainingFraction();

//			Restriction restriction = im.get(context);
//			double remainingFraction = restriction.getRemainingFraction();

			// Replaces global remaining fraction with local one, if applicable
			if (episimConfig.getDistrictLevelRestrictions().equals(EpisimConfigGroup.DistrictLevelRestrictions.yesForActivityLocation)) {
			if (facilityId != null) {
				if (subdistrictFacilities.containsKey(facilityId.toString())) {
					String subdistrict = subdistrictFacilities.get(facilityId.toString());
						if (context.getLocationBasedRf().containsKey(subdistrict)) {
							r = context.getLocationBasedRf().get(subdistrict);
						}
					}
				}
			} else if (episimConfig.getDistrictLevelRestrictions().equals(EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation)) {
				if (person.getAttributes().getAsMap().containsKey(episimConfig.getDistrictLevelRestrictionsAttribute())) {
					String subdistrict = person.getAttributes().getAttribute(episimConfig.getDistrictLevelRestrictionsAttribute()).toString();
					if (context.getLocationBasedRf().containsKey(subdistrict)) {
						r = context.getLocationBasedRf().get(subdistrict);
					}
				}
			}

			// reduce fraction for persons that are not vaccinated
			if (context.getSusceptibleRf() != null && context.getSusceptibleRf() != 1d) {
				if (!vaccinationConfig.hasGreenPass(person, iteration, date))
					r *= context.getSusceptibleRf();
			}

			if (context.getVaccinatedRf() != null && context.getVaccinatedRf() != 1d) {
				if (vaccinationConfig.hasGreenPass(person, iteration, date))
					r *= context.getVaccinatedRf();
			}

			if (r == 1.0)
				trajectory.set(offset + i, true);
			else if (r == 0.0)
				trajectory.set(offset + i, false);
			else
				trajectory.set(offset + i, rnd.nextDouble() < r);

		}
	}
}
