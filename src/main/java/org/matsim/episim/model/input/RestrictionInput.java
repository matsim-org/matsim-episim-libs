package org.matsim.episim.model.input;

import org.matsim.episim.policy.FixedPolicy;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for providing activity participation data.
 */
public interface RestrictionInput {

	/**
	 * Sets the input for this file.
	 */
	RestrictionInput setInput(Path input);

	/**
	 * Provide policy with activity reduction.
	 */
	FixedPolicy.ConfigBuilder createPolicy() throws IOException;
}
