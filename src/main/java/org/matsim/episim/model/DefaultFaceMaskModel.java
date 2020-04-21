package org.matsim.episim.model;

import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.policy.Restriction;

/**
 * Default model only requires people to wear the mask mandated by current {@link Restriction}.
 */
public class DefaultFaceMaskModel implements FaceMaskModel {

	@Override
	public FaceMask getWornMask(EpisimPerson person, EpisimConfigGroup.InfectionParams act, int currentDay, Restriction restriction) {
		return restriction.getRequireMask();
	}
}
