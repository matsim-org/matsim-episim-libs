package org.matsim.episim.model;

import org.matsim.episim.EpisimPerson;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Updates the antibody level of a person after each iteration.
 */
public interface AntibodyModel {

	/**
	 * Executed each day in order to update the antibody level of a person.
	 * @param person person to update.
	 * @param day current day / iteration
	 */
	void updateAntibodies(EpisimPerson person, int day);

	/**
	 * Initialize antibody model.
	 * @param persons persons whose antibodies are initialized
	 * @param iteration current simulation iteration
	 */
	void init(Collection<EpisimPerson> persons, int iteration);

	/**
	 * Recalculates antibody levels after loading a snapshot.
	 */
	void recalculateAntibodiesAfterSnapshot(Collection<EpisimPerson> persons, int iteration);


}
