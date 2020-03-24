package org.matsim.episim;

import org.apache.log4j.Logger;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EpisimConfigGroup extends ReflectiveConfigGroup {
    private static final Logger log = Logger.getLogger(EpisimConfigGroup.class);
    private static final String GROUPNAME = "episim";

    public EpisimConfigGroup() {
        super(GROUPNAME);
    }

    // alle anderen Bestandteile des "disease progression models" sind auch "in code"!  kai, mar'20
    // ---
    public static final String INPUT_EVENTS_FILE = "inputEventsFile";
    private String inputEventsFile = null;

    @StringGetter(INPUT_EVENTS_FILE)
    public String getInputEventsFile() {
        return this.inputEventsFile;
    }

    @StringSetter(INPUT_EVENTS_FILE)
    public void setInputEventsFile(String inputEventsFile) {
        this.inputEventsFile = inputEventsFile;
    }

    // ---
    public static final String CALIBRATION_PARAMETER = "calibrationParameter";
    private double calibrationParameter = 0.0000012;

    @StringGetter(CALIBRATION_PARAMETER)
    public double getCalibrationParameter() {
        return this.calibrationParameter;
    }

    @StringSetter(CALIBRATION_PARAMETER)
    public void setCalibrationParameter(double calibrationParameter) {
        this.calibrationParameter = calibrationParameter;
    }

    // ---
    public static final String PUT_TRACABLE_PERSONS_IN_QUARANTINE = "pubTracablePersonsInQuarantine";

    public enum PutTracablePersonsInQuarantine {yes, no}

    private PutTracablePersonsInQuarantine putTracablePersonsInQuarantine = PutTracablePersonsInQuarantine.no;

    @StringGetter(PUT_TRACABLE_PERSONS_IN_QUARANTINE)
    public PutTracablePersonsInQuarantine getPutTracablePersonsInQuarantine() {
        return this.putTracablePersonsInQuarantine;
    }

    @StringSetter(PUT_TRACABLE_PERSONS_IN_QUARANTINE)
    public void setPutTracablePersonsInQuarantine(PutTracablePersonsInQuarantine putTracablePersonsInQuarantine) {
        this.putTracablePersonsInQuarantine = putTracablePersonsInQuarantine;
    }

    // ---
    public enum FacilitiesHandling {bln, snz}

    private FacilitiesHandling facilitiesHandling = FacilitiesHandling.snz;

    @StringGetter("facilitiesHandling")
    public FacilitiesHandling getFacilitiesHandling() {
        return facilitiesHandling;
    }

    @StringSetter("facilitiesHandling")
    public void setFacilitiesHandling(FacilitiesHandling facilitiesHandling) {
        this.facilitiesHandling = facilitiesHandling;
    }

    public static class InfectionParams extends ReflectiveConfigGroup {
        static final String SET_TYPE = "infectionParams";

        public InfectionParams(final String containerName) {
            this();
            this.containerName = containerName;
        }

        private InfectionParams() {
            super(SET_TYPE);
        }

        public static final String ACTIVITY_TYPE = "activityType";
        private String containerName;

        @StringGetter(ACTIVITY_TYPE)
        public String getContainerName() {
            return containerName;
        }

        @StringSetter(ACTIVITY_TYPE)
        public void setContainerName(String actType) {
            this.containerName = actType;
        }

        public static final String SHUTDOWN_DAY = "shutdownDay";
        private long shutdownDay = Long.MAX_VALUE;

        @StringGetter(SHUTDOWN_DAY)
        public long getShutdownDay() {
            return shutdownDay;
        }

        @StringSetter(SHUTDOWN_DAY)
        public InfectionParams setShutdownDay(long shutdownDay) {
            this.shutdownDay = shutdownDay;
            return this;
        }

        // ---
        public static final String REMAINING_FRACTION = "remainingFraction";
        private double remainingFraction = 0.;

        @StringGetter(REMAINING_FRACTION)
        public double getRemainingFraction() {
            return remainingFraction;
        }

        @StringSetter(REMAINING_FRACTION)
        public InfectionParams setRemainingFraction(double remainingFraction) {
            this.remainingFraction = remainingFraction;
            return this;
        }

        // ---
        public static final String CONTACT_INTENSITY = "contactIntensity";
        private double contactIntensity = 1.;

        /**
         * this is from iteration 0!
         */
        @StringGetter(CONTACT_INTENSITY)
        public double getContactIntensity() {
            return contactIntensity;
        }

        /**
         * this is from iteration 0!
         **/
        @StringSetter(CONTACT_INTENSITY)
        public InfectionParams setContactIntensity(double contactIntensity) {
            this.contactIntensity = contactIntensity;
            return this;
        }
    }

    @Override
    public void addParameterSet(final ConfigGroup set) {
        // this is, I think, necessary for the automatic reading from file, and possibly for the commandline stuff.
        switch (set.getName()) {
            case InfectionParams.SET_TYPE:
                addContainerParams((InfectionParams) set);
                break;
            default:
                throw new IllegalArgumentException(set.getName());
        }
    }

    @Override
    public ConfigGroup createParameterSet(final String type) {
        switch (type) {
            case InfectionParams.SET_TYPE:
                return new InfectionParams();
            default:
                throw new IllegalArgumentException(type);
        }
    }

    @Override
    protected void checkParameterSet(final ConfigGroup module) {
        switch (module.getName()) {
            case InfectionParams.SET_TYPE:
                if (!(module instanceof InfectionParams)) {
                    throw new RuntimeException("unexpected class for module " + module);
                }
                break;
            default:
                throw new IllegalArgumentException(module.getName());
        }
    }

    /**
     * Adds given params to the parameter set, replacing existing ones.
     */
    public void addContainerParams(final InfectionParams params) {
        final InfectionParams previous = this.getContainerParams().get(params.getContainerName());

        if (previous != null) {
            log.info("scoring parameters for activityType=" + previous.getContainerName() + " were just replaced.");

            final boolean removed = removeParameterSet(previous);
            if (!removed)
                throw new RuntimeException("problem replacing params ");
        }

        super.addParameterSet(params);
    }

    /**
     * Returns a container from the parameter set if it exists or creates a new one.
     */
    public InfectionParams getOrAddContainerParams(final String containerName) {
        InfectionParams params = this.getContainerParams().get(containerName);

        if (params != null)
            return params;

        params = new InfectionParams(containerName);

        super.addParameterSet(params);
        return params;
    }

    public Map<String, InfectionParams> getContainerParams() {
        @SuppressWarnings("unchecked") final Collection<InfectionParams> parameters = (Collection<InfectionParams>) getParameterSets(InfectionParams.SET_TYPE);
        final Map<String, InfectionParams> map = new LinkedHashMap<>();

        for (InfectionParams pars : parameters) {
            if (this.isLocked()) {
                pars.setLocked();
            }
            map.put(pars.getContainerName(), pars);
        }

        return Collections.unmodifiableMap(map);
    }

}
