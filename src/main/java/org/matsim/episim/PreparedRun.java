package org.matsim.episim;

import org.matsim.core.config.Config;

import java.util.List;

/**
 * Class holding the result of {@link BatchRun#prepare(Class, Class)} with all information of the run.
 */
public final class PreparedRun {

	/**
	 * Instance of the setup class.
	 */
	public final BatchRun<?> setup;

	/**
	 * Parameter names.
	 */
	public final List<String> parameter;

	/**
	 * All generated runs.
	 */
	public final List<Run> runs;

	public PreparedRun(BatchRun<?> setup, List<String> parameter, List<Run> runs) {
		this.setup = setup;
		this.parameter = parameter;
		this.runs = runs;
	}

	/**
	 * One individual parameter set of a run.
	 */
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
