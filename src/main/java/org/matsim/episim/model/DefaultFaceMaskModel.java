package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.policy.Restriction;

import org.matsim.episim.util.EpisimSplittableRandom;

/**
 * Default model only requires people to wear the mask mandated by current {@link Restriction}.
 * Whether a person wears a mask is decided anew everyday.
 */
public class DefaultFaceMaskModel implements FaceMaskModel {

	private final EpisimSplittableRandom rnd;

	@Inject
	public DefaultFaceMaskModel(EpisimSplittableRandom rnd) {
		this.rnd = rnd;
	}

	@Override
	public FaceMask getWornMask(EpisimPerson person, EpisimConfigGroup.InfectionParams act, Restriction restriction) {
		return restriction.determineMask(rnd);
	}
}
