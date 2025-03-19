package org.matsim.episim;

import org.apache.commons.csv.CSVRecord;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.episim.model.VirusStrain;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import static org.assertj.core.api.Assertions.assertThat;
import javax.annotation.Nullable;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

public class BatchRunTest {


	@Test
	public void prepare() {
		PreparedRun preparedRun = BatchRun.prepare(TestBatch.class, TestBatch.TestParams.class);

		// 140 runs should be created
		assertThat(preparedRun.runs.size()).isEqualTo(140);

	}


	@Test
	public void lookup(){

		List<CSVRecord> records = new ArrayList<>();
		BatchRun.lookup(TestBatch.TestParams.class, records);


	}
}


class TestBatch implements BatchRun<TestBatch.TestParams> {

	private SnzBerlinProductionScenario getBindings() {
		return new SnzBerlinProductionScenario.Builder().build();
	}

	@Nullable
	@Override
	public Config prepareConfig(int id, TestParams params) {
		Config config = getBindings().config();

		return config;
	}

	/*
	 * Specify parameter combinations that will be run.
	 */
	public static final class TestParams {
		// general
		@GenerateSeeds(5)
		public long seedParam;

		@StringParameter({"yes", "no"})
		public String stringParam;

		@EnumParameter(DayOfWeek.class)
		public DayOfWeek enumParam;

		@Parameter({1.0, 2.0})
		public double doubleParam;
	}
	}

