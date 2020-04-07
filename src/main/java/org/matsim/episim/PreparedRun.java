package org.matsim.episim;

import org.matsim.core.config.Config;

import java.util.List;

public final class PreparedRun {

	public final BatchRun<?> setup;
	public final List<String> parameter;
	public final List<Run> runs;

	public PreparedRun(BatchRun<?> setup, List<String> parameter, List<Run> runs) {
		this.setup = setup;
		this.parameter = parameter;
		this.runs = runs;
	}

	public static final class Run {

		public final int id;
		public final List<Object> params;
		public final Config config;

		public Run(int id, List<Object> params, Config config) {
			this.id = id;
			this.params = params;
			this.config = config;
		}
	}

}
