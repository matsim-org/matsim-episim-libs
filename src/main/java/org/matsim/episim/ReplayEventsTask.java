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

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.List;

/**
 * Replays all events for a single day to one {@link TrajectoryHandler}.
 */
final public class ReplayEventsTask implements Runnable {

	private final List<Event> events;
	private final TrajectoryHandler trajectoryHandler;

	/**
	 * taskId is in [0, numThreads - 1], and each thread must have
	 * a different taskId
	 */
	private final int taskId;
	private final int numThreads;


	public ReplayEventsTask(TrajectoryHandler trajectoryHandler,
							List<Event> events,
							int taskId,
							int numThreads) {
		this.trajectoryHandler = trajectoryHandler;
		this.events = events;
		this.taskId = taskId;
		this.numThreads = numThreads;
	}

	/**
	 * Check whether the handler is responsible for the facility with this id.
	 */
	boolean handlesFacility(Id<ActivityFacility> id) {
		return trajectoryHandler.getEpisimFacility(id).getTaskId() == taskId;
	}

	/**
	 * Check whether the handler is responsible for the vehicle with this id.
	 */
	boolean handlesVehicle(Id<Vehicle> id) {
		return trajectoryHandler.getEpisimVehicle(id).getTaskId() == taskId;
	}

	public void run() {
		trajectoryHandler.reportCpuTime("start", taskId);
		trajectoryHandler.onStartDay(this::handlesFacility, this::handlesVehicle);

		for (final Event e : events) {
			if (e instanceof ActivityStartEvent) {
				ActivityStartEvent ase = (ActivityStartEvent) e;
				if (handlesFacility(ase.getFacilityId()))
					trajectoryHandler.handleEvent(ase);
			} else if (e instanceof ActivityEndEvent) {
				ActivityEndEvent aee = (ActivityEndEvent) e;
				if (handlesFacility(aee.getFacilityId())) {
					trajectoryHandler.handleEvent(aee);
				}
			} else if (e instanceof PersonEntersVehicleEvent) {
				PersonEntersVehicleEvent peve = (PersonEntersVehicleEvent) e;
				if (handlesVehicle(peve.getVehicleId()))
					trajectoryHandler.handleEvent(peve);
			} else {
				PersonLeavesVehicleEvent plve = (PersonLeavesVehicleEvent) e;
				if (handlesVehicle(plve.getVehicleId()))
					trajectoryHandler.handleEvent(plve);
			}
		}

		trajectoryHandler.reportCpuTime("finished", taskId);
	}
}
