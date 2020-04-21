package org.matsim.episim.reporting;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.EventTranslatorTwoArg;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.io.BufferedWriter;
import java.io.Writer;

/**
 * Overwrites the default episim write to do all IO in an extra thread.
 */
public final class AsyncEpisimWriter extends EpisimWriter implements EventHandler<AsyncEpisimWriter.LogEvent>,
		EventTranslatorTwoArg<AsyncEpisimWriter.LogEvent, Writer, String[]> {

	private final Disruptor<LogEvent> disruptor;
	private final StringEventTranslator translator = new StringEventTranslator();

	public AsyncEpisimWriter() {

		// Specify the size of the ring buffer, must be power of 2.
		int bufferSize = 10240;

		disruptor = new Disruptor<>(LogEvent::new, bufferSize, DaemonThreadFactory.INSTANCE);

		// Connect the handler
		disruptor.handleEventsWith(this);
	}

	@Override
	public void append(BufferedWriter writer, String[] array) {
		disruptor.publishEvent(this, writer, array);
	}

	@Override
	public void append(BufferedWriter writer, String content) {
		disruptor.publishEvent(translator, writer, content, false);
	}

	@Override
	public void close(BufferedWriter writer) {
		disruptor.publishEvent(translator, writer, null, true);
	}

	@Override
	public void onEvent(LogEvent event, long sequence, boolean endOfBatch) throws Exception {

		if (event.close) {
			event.writer.close();
		} else {
			event.writer.append(event.content);
			if (event.flush)
				event.writer.flush();
		}

		event.reset();
	}

	/**
	 * Write one line with content separated by separator.
	 */
	@Override
	public void translateTo(LogEvent event, long sequence, Writer arg0, String[] arg1) {
		event.writer = arg0;

		for (int i = 0; i < arg1.length; i++) {
			event.content.append(arg1[i]);
			if (i < arg1.length - 1) event.content.append(SEPARATOR);
		}

		event.content.append("\n");
	}

	protected static class LogEvent {

		private static final int BUFFER_SIZE = 120;

		private final StringBuilder content = new StringBuilder(BUFFER_SIZE);
		private Writer writer;
		private boolean close = false;
		private boolean flush = true;

		private void reset() {
			close = false;

			if (content.capacity() > BUFFER_SIZE) {
				content.setLength(BUFFER_SIZE);
				content.trimToSize();
			}

			content.setLength(0);
		}
	}

	/**
	 * Copy the string to buffer and also set close attribute.
	 */
	public static class StringEventTranslator implements EventTranslatorThreeArg<LogEvent, Writer, String, Boolean> {

		@Override
		public void translateTo(LogEvent event, long sequence, Writer arg0, String arg1, Boolean arg2) {

			event.writer = arg0;
			if (arg2) {
				event.close = true;
			} else {
				event.content.append(arg1);
				event.flush = false;
			}
		}
	}
}
