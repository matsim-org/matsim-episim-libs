/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2021 matsim-org
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
package org.matsim.episim;

import org.matsim.api.core.v01.events.*;

import java.util.List;

/**
 * Replays all events for
 */
final public class ReplayEventsTask implements Runnable {

	private final List<Event> events;
	private final TrajectoryHandler trajectoryHandler;

	/**
	 * taskId is in [0, numThreads - 1], and each thread must have
	 * a different taskId
	 */
	private int taskId;
	private int numThreads;


	public ReplayEventsTask(TrajectoryHandler trajectoryHandler,
							List<Event> events,
							int taskId,
							int numThreads) {
		this.trajectoryHandler = trajectoryHandler;
		this.events = events;
		this.taskId = taskId;
		this.numThreads = numThreads;
	}

	public void run() {
		for (final Event e : events) {
			if (e instanceof ActivityStartEvent) {
				ActivityStartEvent ase = (ActivityStartEvent) e;
				if (Math.abs(ase.getFacilityId().hashCode()) % numThreads == taskId)
					trajectoryHandler.handleEvent(ase);
			} else if (e instanceof ActivityEndEvent) {
				ActivityEndEvent aee = (ActivityEndEvent) e;
				if (Math.abs(aee.getFacilityId().hashCode()) % numThreads == taskId) {
					trajectoryHandler.handleEvent(aee);
				}
			} else if (e instanceof PersonEntersVehicleEvent) {
				PersonEntersVehicleEvent peve = (PersonEntersVehicleEvent) e;
				if (Math.abs(peve.getVehicleId().hashCode()) % numThreads == taskId)
					trajectoryHandler.handleEvent(peve);
			} else {
				PersonLeavesVehicleEvent plve = (PersonLeavesVehicleEvent) e;
				if (Math.abs(plve.getVehicleId().hashCode()) % numThreads == taskId)
					trajectoryHandler.handleEvent(plve);
			}
		}
	}
}
