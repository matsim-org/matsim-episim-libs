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
package org.matsim.episim;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.ControlerUtils;

/**
 * Main entry point and runner of one epidemic simulation.
 */
public class EpisimRunner {

	private final Config config;
	private final EventsManager manager;
	private final Provider<InfectionEventHandler> handlerProvider;
	private final Provider<ReplayHandler> replayProvider;
	private final Provider<EpisimReporting> reportingProvider;

	@Inject
	public EpisimRunner(Config config, EventsManager manager, Provider<InfectionEventHandler> handlerProvider,
						Provider<ReplayHandler> replay, Provider<EpisimReporting> reportingProvider) {
		this.config = config;
		this.handlerProvider = handlerProvider;
		this.manager = manager;
		this.replayProvider = replay;
		this.reportingProvider = reportingProvider;
	}

	/**
	 * Main loop that performs the iterations of the simulation.
	 *
	 * @param maxIterations maximum number of iterations (inclusive)
	 */
	public void run(int maxIterations) {

		ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "Just before starting iterations");

		// Construct these dependencies as late as possible, so all other configs etc have been fully configured
		final ReplayHandler replay = replayProvider.get();
		final InfectionEventHandler handler = handlerProvider.get();
		final EpisimReporting reporting = reportingProvider.get();

		manager.addHandler(handler);
		manager.addHandler(reporting);

		for (int iteration = 0; iteration <= maxIterations; iteration++) {

			if (!doStep(replay, handler, iteration))
				break;

		}

		reporting.close();
	}

	/**
	 * Perform one iteration of simulation.
	 *
	 * @return false, when the simulation should end
	 */
	boolean doStep(final ReplayHandler replay, InfectionEventHandler handler, int iteration) {

		manager.resetHandlers(iteration);
		if (handler.isFinished())
			return false;


		// Process all events
		replay.replayEvents(manager, iteration);

		return true;
	}

}
