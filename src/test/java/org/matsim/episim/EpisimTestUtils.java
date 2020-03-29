package org.matsim.episim;

import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.facilities.Facility;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

public class EpisimTestUtils {

    private static final AtomicLong ID = new AtomicLong(0);

    /**
     * Creates test config with some default interactions.
     */
    public static EpisimConfigGroup createTestConfig() {
        Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        episimConfig.setSampleSize(1);
        episimConfig.setCalibrationParameter(0.001);

        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c0").setContactIntensity(0));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c0.5").setContactIntensity(0.5));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c1").setContactIntensity(1));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c5").setContactIntensity(5));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c10").setContactIntensity(10));

        return episimConfig;
    }

    public static InfectionEventHandler.EpisimFacility createFacility() {
        return new InfectionEventHandler.EpisimFacility(Id.create(ID.getAndIncrement(), Facility.class));
    }

    /**
     * Create facility with n persons in it.
     */
    public static InfectionEventHandler.EpisimFacility createFacility(int n, String act, EpisimPerson.DiseaseStatus status) {
        InfectionEventHandler.EpisimFacility container = createFacility();
        for (int i = 0; i < n; i++) {
            EpisimPerson p = createPerson(act, container);
            p.setDiseaseStatus(status);
        }

        return container;
    }

    /**
     * Create a person and add to container.
     */
    public static EpisimPerson createPerson(String currentAct, @Nullable EpisimContainer<?> container) {
        EpisimPerson p = new EpisimPerson(Id.createPersonId(ID.getAndIncrement()));
        p.getTrajectory().add(currentAct);

        if (container != null) {
            container.addPerson(p, 0);
            p.setLastFacilityId(container.getContainerId().toString());
        }

        return p;
    }

}
