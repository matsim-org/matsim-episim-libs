package org.matsim.episim.policy;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.EpisimReporting;


/**
 * Abstract base class for policies which are supposed to modify {@link Restriction}s at the end of each day.
 */
abstract public class ShutdownPolicy {

    private static final Logger log = LogManager.getLogger(ShutdownPolicy.class);

    protected final Config config;

    protected ShutdownPolicy(Config config) {
        this.config = config;
        log.debug("Using policy {} with config: {}", getClass(), config.root().render(ConfigRenderOptions.concise().setJson(false)));
    }

    /**
     * Update the restrictions on the end of the day based on the report. The restrictions will be in effect the following day.
     * The map is immutable, use setters of {@link Restriction}.
     *
     * @param report       infections statistics of the day
     * @param restrictions restrictions in place during the day
     */
    public abstract void updateRestrictions(EpisimReporting.InfectionReport report, ImmutableMap<String, Restriction> restrictions);

    /**
     * Represent the current restrictions on an activity type.
     */
    public static final class Restriction {

        /**
         * Percentage of activities still performed.
         */
        private double remainingFraction = 1.;

        private Restriction() {
        }

        public static Restriction newInstance() {
            return new Restriction();
        }

        @Override
        public String toString() {
            return String.valueOf(remainingFraction);
        }

        public double getRemainingFraction() {
            return remainingFraction;
        }

        void setRemainingFraction(double remainingFraction) {
            this.remainingFraction = remainingFraction;
        }

        void fullShutdown() {
            remainingFraction = 0d;
        }

        void open() {
            remainingFraction = 1d;
        }

    }

}
