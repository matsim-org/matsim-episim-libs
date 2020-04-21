package org.matsim.episim.reporting;

import com.google.common.base.Joiner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Utility class to write reported data into csv files.
 */
public class EpisimWriter {

	protected static final Logger log = LogManager.getLogger(EpisimWriter.class);

	protected static final String SEPARATOR = "\t";

	/**
	 * Create one row in csv files.
	 */
	public static final Joiner JOINER = Joiner.on(SEPARATOR);

	/**
	 * Creates a csv writer and write the header using enum definition.s
	 */
	public static BufferedWriter prepare(String filename, Class<? extends Enum<?>> enumClass) {
		BufferedWriter writer = IOUtils.getBufferedWriter(filename);
		try {
			writer.write(JOINER.join(enumClass.getEnumConstants()));
			writer.write("\n");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return writer;
	}

	/**
	 * Creates a csv writer and writes the header according to {@link Joiner#join(Object, Object, Object...)}
	 */
	public static BufferedWriter prepare(String filename, Object first, Object second, Object... rest) {
		BufferedWriter writer = IOUtils.getBufferedWriter(filename);
		try {
			writer.write(JOINER.join(first, second, rest));
			writer.write("\n");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return writer;
	}

	/**
	 * Append a new row to the writer, columns separated by separator.
	 */
	public void append(BufferedWriter writer, String[] array) {
		try {
			writer.write(JOINER.join(array));
			writer.write("\n");
			writer.flush();
		} catch (IOException e) {
			log.error("Could not write content", e);
		}
	}

	/**
	 * Appends plain string to the writer.
	 */
	public void append(BufferedWriter writer, String string) {
		try {
			writer.write(string);
			writer.flush();
		} catch (IOException e) {
			log.error("Could not write content", e);
		}
	}

	/**
	 * Close a writer for writing.
	 */
	public void close(BufferedWriter writer) {
		try {
			writer.close();
		} catch (IOException e) {
			log.error("Could not close writer", e);
			throw new UncheckedIOException(e);
		}
	}

}
