package org.matsim.episim.model;

import com.google.inject.ImplementedBy;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.policy.Restriction;

/**
 * Model which decides which mask a person is wearing during activity.
 */
@ImplementedBy(DefaultFaceMaskModel.class)
public interface FaceMaskModel {

	FaceMask getWornMask(EpisimPerson person, EpisimConfigGroup.InfectionParams act, Restriction restriction);

}
