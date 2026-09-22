package org.matsim.episim;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class ProtectionCurveTest {

	@Test
	public void interpolatesLinearlyBetweenPointsAndKeepsTheLastValue() {
		ProtectionCurve curve = ProtectionCurve.parse("0>0.8|100>0.4|200>0.0");

		assertThat(curve.protectionAt(0)).isEqualTo(0.8);
		assertThat(curve.protectionAt(50)).isCloseTo(0.6, within(1e-12));
		assertThat(curve.protectionAt(100)).isEqualTo(0.4);
		assertThat(curve.protectionAt(150)).isCloseTo(0.2, within(1e-12));
		assertThat(curve.protectionAt(200)).isEqualTo(0.0);
		assertThat(curve.protectionAt(10_000)).as("kept after the last point").isEqualTo(0.0);
	}

	/**
	 * The vaccination curves stored a point at day Integer.MAX_VALUE and interpolated towards it, so the default
	 * vaccine never reached its full effect. Here a value is reached on the day it is written for.
	 */
	@Test
	public void reachesAValueOnItsDayWithoutASentinel() {
		ProtectionCurve curve = ProtectionCurve.parse("0>0.0|5>0.45|28>0.9");

		assertThat(curve.protectionAt(28)).isEqualTo(0.9);
		assertThat(curve.protectionAt(365)).isEqualTo(0.9);
	}

	@Test
	public void singlePointIsConstant() {
		assertThat(ProtectionCurve.parse("0>0.47").protectionAt(1000)).isEqualTo(0.47);
		assertThat(ProtectionCurve.NONE.protectionAt(0)).isEqualTo(0.0);
		assertThat(ProtectionCurve.NONE.protectionAt(1000)).isEqualTo(0.0);
	}

	@Test
	public void writesTheFormatItReads() {
		String text = "0>0.47|90>0.4|365>0.1";
		ProtectionCurve curve = ProtectionCurve.parse(text);

		assertThat(curve.toString()).isEqualTo(text);
		assertThat(ProtectionCurve.parse(curve.toString())).isEqualTo(curve);
		assertThat(ProtectionCurve.NONE.toString()).isEqualTo("0>0.0");
	}

	@Test
	public void toleratesSpacesAroundNumbers() {
		assertThat(ProtectionCurve.parse(" 0 > 0.5 | 10 > 0.25 "))
			.isEqualTo(ProtectionCurve.parse("0>0.5|10>0.25"));
	}

	@Test
	public void buildsFromPairsInCode() {
		assertThat(ProtectionCurve.of(0, 0.83, 180, 0.83, 210, 0))
			.isEqualTo(ProtectionCurve.parse("0>0.83|180>0.83|210>0.0"));
	}

	@Test
	public void rejectsCurvesThatBreakTheRules() {
		assertThatThrownBy(() -> ProtectionCurve.parse(""))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty");
		assertThatThrownBy(() -> ProtectionCurve.parse("7>0.5|30>0.2"))
			.hasMessageContaining("has to start at day 0");
		assertThatThrownBy(() -> ProtectionCurve.parse("0>0.5|30>0.2|20>0.1"))
			.as("never reordered silently")
			.hasMessageContaining("day 20 follows day 30");
		assertThatThrownBy(() -> ProtectionCurve.parse("0>0.5|30>0.2|30>0.1"))
			.hasMessageContaining("increase strictly");
		assertThatThrownBy(() -> ProtectionCurve.parse("0>1.2"))
			.as("protection, not a factor").hasMessageContaining("not between 0 and 1");
		assertThatThrownBy(() -> ProtectionCurve.parse("0>-0.1"))
			.hasMessageContaining("not between 0 and 1");
		assertThatThrownBy(() -> ProtectionCurve.parse("0>NaN"))
			.hasMessageContaining("not between 0 and 1");
		assertThatThrownBy(() -> ProtectionCurve.parse("0:0.5"))
			.hasMessageContaining("not of the form day>protection");
		assertThatThrownBy(() -> ProtectionCurve.parse("0>0.5||10>0.2"))
			.hasMessageContaining("not of the form");
		assertThatThrownBy(() -> ProtectionCurve.parse("0.5>0.5"))
			.hasMessageContaining("needs a whole day and a number");
		assertThatThrownBy(() -> ProtectionCurve.of(0, 0.5, 10.5, 0.2))
			.hasMessageContaining("not a whole day");
		assertThatThrownBy(() -> ProtectionCurve.of(0, 0.5, 10))
			.hasMessageContaining("pairs");
	}

	@Test
	public void rejectsDaysBeforeTheEvent() {
		assertThatThrownBy(() -> ProtectionCurve.NONE.protectionAt(-1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("before the immunity event");
	}
}
