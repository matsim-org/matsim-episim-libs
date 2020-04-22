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

import org.matsim.core.config.Config;

import java.util.*;

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
	 * Parameter values for all parameter defined in {@link #parameter}.
	 */
	public final List<List<Object>> parameterValues;

	/**
	 * All generated runs.
	 */
	public final List<Run> runs;

	public PreparedRun(BatchRun<?> setup, List<String> parameter, List<List<Object>> parameterValues, List<Run> runs) {
		this.setup = setup;
		this.parameter = parameter;
		this.parameterValues = parameterValues;
		this.runs = runs;
	}


	/**
	 * Returns metadata information of this run that can be written in desired format.
	 */
	public Map<String, Object> getMetadata() {

		Map<String, Object> data = new LinkedHashMap<>();

		data.put("dayZero", setup.startDay());

		int index = parameter.indexOf("offset");
		if (index > -1)
			data.put("offset", parameterValues.get(index));

		List<Object> opts = new ArrayList<>();

		for (Map.Entry<Integer, List<String>> day : setup.getMeasures().entrySet()) {

			Map<String, Object> byDay = new LinkedHashMap<>();


			byDay.put("day", day.getKey());
			byDay.put("heading", "<Please providing heading>");
			byDay.put("subheading", "<Please provide subheading>");

			List<Map<String, Object>> measures = new ArrayList<>();
			for (String m : day.getValue()) {
				measures.add(
						Map.of("measure", m, "title", "<Please provide a title>")
				);
			}

			byDay.put("measures", measures);

			opts.add(byDay);
		}

		data.put("optionGroups", opts);

		return data;
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
