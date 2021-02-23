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

import java.util.List;
import java.util.SplittableRandom;

import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;

final public class ReplayEventsTask implements Runnable {
	private List<Event> events;

	private InfectionEventHandler infectionHandler;

	/** 
	 * taskId is in [0, numThreads - 1], and each thread must have
	 * a different taskId
	 */
	private int taskId;

	private int numThreads;

	/**
	 * Each thread has an own random generator, which is splitted from
	 * the global random generator. The Constructor must get it already 
	 * in a splitted case, to ensure that the split is always done in
	 * the same order
	 */
	private SplittableRandom splittedGlobalRnd;

	/**
	 * The splittedGlobalRnd is assigned to this ThreadLocal 
	 * random generator when the thread is started
	 */
	private static ThreadLocal<SplittableRandom> threadRnd;

	public ReplayEventsTask(InfectionEventHandler infectionHandler,
							List<Event> events,
							int taskId,
							int numThreads,
							SplittableRandom splittedRnd) {
		this.infectionHandler = infectionHandler;
		this.events = events;
		this.taskId = taskId;
		this.numThreads = numThreads;
		this.splittedGlobalRnd = splittedRnd;
	}

	/**
	 * In the multithreaded part, the random generator must be accessed
	 * using this function. To ensure, that a random generator can be still
	 * accessed from all places, an alternative random generator must be
	 * given as parameter
	 */
	public static SplittableRandom getThreadRnd(SplittableRandom elseRnd) {
		if (threadRnd != null)
			return threadRnd.get();
		return elseRnd;
	}

	// public static boolean inReplayEventsParallel() {
	// 	return threadRnd != null;
	// }

	public void run() {
		ReplayEventsTask.threadRnd = ThreadLocal.withInitial(() -> splittedGlobalRnd);
		
		for (final Event e : events) {
			if (e instanceof ActivityStartEvent) {
				ActivityStartEvent ase = (ActivityStartEvent) e;
				if (Math.abs(ase.getFacilityId().hashCode()) % numThreads == taskId)
					infectionHandler.handleEvent(ase);
			} else if (e instanceof ActivityEndEvent) {
				ActivityEndEvent aee = (ActivityEndEvent) e;
				if (Math.abs(aee.getFacilityId().hashCode()) % numThreads == taskId) {
					infectionHandler.handleEvent(aee);
				}
			} else if (e instanceof PersonEntersVehicleEvent) {
				PersonEntersVehicleEvent peve = (PersonEntersVehicleEvent) e;
				if (Math.abs(peve.getVehicleId().hashCode()) % numThreads == taskId)
					infectionHandler.handleEvent(peve);
			} else {
				PersonLeavesVehicleEvent plve = (PersonLeavesVehicleEvent) e;
				if (Math.abs(plve.getVehicleId().hashCode()) % numThreads == taskId)
					infectionHandler.handleEvent(plve);
			}
		}

		// the thread can be reused, do ensure that the global thread
		// does not use a ThreadLocal rnd we must set it to null
		threadRnd = null;
	}
}
