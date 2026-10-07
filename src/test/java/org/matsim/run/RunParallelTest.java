package org.matsim.run;

import com.google.inject.AbstractModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.core.config.Config;
import org.matsim.episim.BatchRun;
import org.matsim.episim.SyntheticBatch;
import org.matsim.episim.SyntheticScenario;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.episim.model.DefaultContactModel;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exit code of {@link RunParallel}: callers such as the StarterBatch classes only pack and upload a batch when it is 0.
 */
public class RunParallelTest {

	@TempDir
	Path output;

	@Test
	public void successfulBatchExitsWithZero() {
		assertThat(run(PassingBatch.class)).isZero();
	}

	@Test
	public void failedTaskExitsNonZero() {
		assertThat(run(FailingBatch.class)).isEqualTo(1);
	}

	private int run(Class<?> setup) {
		return new CommandLine(new RunParallel<>()).execute(
				"--output", output.toString(),
				RunParallel.OPTION_SETUP, setup.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, "1",
				RunParallel.OPTION_ITERATIONS, "2");
	}

	private static SyntheticBatch.Params synthetic() {
		return new SyntheticBatch.Params(100, 1, 2, 1, 1, DefaultContactModel.class, 3);
	}

	public static final class Params {
		@BatchRun.GenerateSeeds(1)
		public long seed;
	}

	public static class PassingBatch implements BatchRun<Params> {

		@Override
		public AbstractModule getBindings(int id, Params params) {
			return new SyntheticScenario(synthetic());
		}

		@Override
		public Config prepareConfig(int id, Params params) {
			return new SyntheticScenario(synthetic()).config();
		}
	}

	/** The simulation succeeds, a post-processing analysis throws, as HospitalNumbersFromEvents does for an unknown strain. */
	public static class FailingBatch extends PassingBatch {

		@Override
		public Collection<OutputAnalysis> postProcessing() {
			return List.of(new OutputAnalysis() {
				@Override
				public void analyzeOutput(Path output) {
					throw new IllegalStateException("analysis failed");
				}

				@Override
				public Integer call() {
					return 0;
				}
			});
		}
	}
}
