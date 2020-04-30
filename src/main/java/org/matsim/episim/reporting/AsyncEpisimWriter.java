package org.matsim.episim.reporting;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.EventTranslatorTwoArg;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.events.Event;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * Overwrites the default episim writer to do all IO in an extra thread using the {@link Disruptor} library.
 */
public final class AsyncEpisimWriter extends EpisimWriter implements EventHandler<AsyncEpisimWriter.LogEvent>,
		EventTranslatorTwoArg<AsyncEpisimWriter.LogEvent, Writer, Event> {

	private static final Logger log = LogManager.getLogger(AsyncEpisimWriter.class);
	private final Disruptor<LogEvent> disruptor;
	private final StringEventTranslator translator = new StringEventTranslator();
	private final StringArrayEventTranslator arrayTranslator = new StringArrayEventTranslator();
	/**
	 * Constructor.
	 *
	 * @param singleProducer can be true when only one thread will publish events.
	 */
	public AsyncEpisimWriter(boolean singleProducer) {

		// Specify the size of the ring buffer, must be power of 2.
		int bufferSize = 16384;

		disruptor = new Disruptor<>(LogEvent::new, bufferSize, DaemonThreadFactory.INSTANCE,
				singleProducer ? ProducerType.SINGLE : ProducerType.MULTI, new SleepingWaitStrategy());


		// Connect the handler
		disruptor.handleEventsWith(this);

		log.info("Using async writer with singleProducer={}", singleProducer);

		disruptor.start();
	}

	@Override
	public void append(BufferedWriter writer, String[] array) {
		disruptor.publishEvent(arrayTranslator, writer, array);
	}

	@Override
	public void append(BufferedWriter writer, String content) {
		disruptor.publishEvent(translator, writer, content, false);
	}

	@Override
	public void append(BufferedWriter writer, Event event) {
		disruptor.publishEvent(this, writer, event);
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

	@Override
	public void translateTo(LogEvent event, long sequence, Writer arg0, Event arg1) {
		event.writer = arg0;
		event.flush = false;
		try {
			EpisimWriter.writeEvent(event.content, arg1);
		} catch (IOException e) {
			log.error("Could not append event");
		}
	}


	protected static class LogEvent {

		private static final int BUFFER_SIZE = 120;

		private final StringBuilder content = new StringBuilder(BUFFER_SIZE);
		private Writer writer;
		private boolean close = false;
		private boolean flush = true;

		private void reset() {
			close = false;
			flush = true;
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
	public static final class StringEventTranslator implements EventTranslatorThreeArg<LogEvent, Writer, String, Boolean> {

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

	/**
	 * Convert MATSim event to log event.
	 */
	public static final class StringArrayEventTranslator implements EventTranslatorTwoArg<LogEvent, Writer, String[]> {

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

	}

}
