/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.episim.model;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.gbl.Gbl;
import org.matsim.episim.*;

import java.util.SplittableRandom;

import static org.matsim.episim.EpisimPerson.DiseaseStatus;

/**
 * Default interaction model executed, when a person ends his activity.
 * Infections probabilities calculations are delegated to a {@link InfectionModel}.
 */
@Deprecated
public final class SqrtContactModel extends AbstractContactModel {

	private static final Logger log = LogManager.getLogger( SqrtContactModel.class );

	/**
	 * Flag to enable tracking, which is considerably slower.
	 */
	private final int trackingAfterDay;

	/**
	 * This buffer is used to store the infection type.
	 */
	private final StringBuilder buffer = new StringBuilder();

	@Inject
	/* package */
	SqrtContactModel( SplittableRandom rnd, Config config, TracingConfigGroup tracingConfig,
			  EpisimReporting reporting, InfectionModel infectionModel ) {
		// (make injected constructor non-public so that arguments can be changed without repercussions.  kai, jun'20)
		super(rnd, config, infectionModel, reporting);
		this.trackingAfterDay = tracingConfig.getPutTraceablePersonsInQuarantineAfterDay();
	}

	@Override
	public void infectionDynamicsVehicle(EpisimPerson personLeavingVehicle, InfectionEventHandler.EpisimVehicle vehicle, double now) {
		infectionDynamicsGeneralized(personLeavingVehicle, vehicle, now);
	}

	@Override
	public void infectionDynamicsFacility(EpisimPerson personLeavingFacility, InfectionEventHandler.EpisimFacility facility, double now) {
		infectionDynamicsGeneralized(personLeavingFacility, facility, now);
	}

	private void infectionDynamicsGeneralized(EpisimPerson personLeavingContainer, EpisimContainer<?> container, double now) {
		throw new UnsupportedOperationException();
	}

}
