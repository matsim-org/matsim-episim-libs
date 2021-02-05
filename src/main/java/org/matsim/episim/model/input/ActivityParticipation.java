package org.matsim.episim.model.input;

import org.matsim.episim.policy.ShutdownPolicy;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for providing activity participation data.
 */
public interface ActivityParticipation {

	/**
	 * Sets the input for this file.
	 */
	ActivityParticipation setInput(Path input);

	/**
	 * Parameter to modulates the activity participation.
	 */
	default ActivityParticipation setAlpha(double alpha) {
		return this;
	}

	/**
	 * Provide policy with activity reduction.
	 */
	ShutdownPolicy.ConfigBuilder<?> createPolicy() throws IOException;
}
