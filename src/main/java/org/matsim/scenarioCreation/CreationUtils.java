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
public class CreationUtils {

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
