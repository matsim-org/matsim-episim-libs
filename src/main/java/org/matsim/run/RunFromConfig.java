/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */


package org.matsim.run;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author smueller
 */
public class RunFromConfig {

	public static void main(String[] args) throws IOException {

		if (args.length == 0) {
			throw new IllegalArgumentException("Need config file");
		}

		String[] typedArgs = Arrays.copyOfRange(args, 1, args.length);

		Config config = ConfigUtils.loadConfig(args[0]);

		ConfigUtils.applyCommandline(config, typedArgs);

		RunEpisim.runSimulation(config, 200);
	}

}
