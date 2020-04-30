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
package org.matsim.scenarioCreation;

import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Utilities for scenario creation.
 */
public final class CreationUtils {

	private CreationUtils() {
	}

	/**
	 * Read all lines of an id file.
	 */
	public static Set<String> readIdFile(Path file) throws IOException {

		Set<String> filterIds = new HashSet<>();
		try (BufferedReader reader = IOUtils.getBufferedReader(IOUtils.getFileUrl(file.toString()))) {
			String line;
			while ((line = reader.readLine()) != null)
				filterIds.add(line);
		}

		return filterIds;
	}
}
