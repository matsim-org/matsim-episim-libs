package org.matsim.episim.model;

import com.google.inject.Singleton;
import org.matsim.episim.EpisimPerson;

import java.util.Collection;

/**
 * Keeps antibody levels at zero, for runs whose immunity does not come from antibodies.
 *
 * <p>{@link DefaultAntibodyModel} walks every agent on every day of the run. Its numbers are calibrated for
 * SARS-CoV-2 and are read by {@link LegacyAntibodyImmunityModel} alone, so under an immunity model that works
 * from curves they cost time and leave antibody columns in the output that mean nothing. This implementation
 * does nothing instead; agents keep the level they start with, which is zero.</p>
 */
@Singleton
public final class NoAntibodyModel implements AntibodyModel {

	@Override
	public void updateAntibodies(EpisimPerson person, int day) {
	}

	@Override
	public void init(Collection<EpisimPerson> persons, int iteration) {
	}

	@Override
	public void recalculateAntibodiesAfterSnapshot(Collection<EpisimPerson> persons, int iteration) {
	}
}
