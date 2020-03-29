package org.matsim.episim;

import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.facilities.Facility;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class EpisimTestUtils {

    private static final AtomicLong ID = new AtomicLong(0);

    public static Consumer<EpisimPerson> CONTAGIOUS = person -> person.setDiseaseStatus(EpisimPerson.DiseaseStatus.contagious);
    public static Consumer<EpisimPerson> QUARANTINED = person -> person.setQuarantineStatus(EpisimPerson.QuarantineStatus.full);

    /**
     * Creates test config with some default interactions.
     */
    public static EpisimConfigGroup createTestConfig() {
        Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        episimConfig.setSampleSize(1);
        episimConfig.setCalibrationParameter(0.001);

        // No container name should be the prefix of another one
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c00").setContactIntensity(0));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c0.1").setContactIntensity(0.1));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c0.5").setContactIntensity(0.5));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c1.0").setContactIntensity(1));
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
    public static InfectionEventHandler.EpisimFacility createFacility(int n, String act, Consumer<EpisimPerson> init) {
        InfectionEventHandler.EpisimFacility container = createFacility();
        return addPersons(container, n, act, init);
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

    /**
     * Add persons to a facility.
     */
    public static InfectionEventHandler.EpisimFacility addPersons(InfectionEventHandler.EpisimFacility container, int n,
                                                                  String act, Consumer<EpisimPerson> init) {
        for (int i = 0; i < n; i++) {
            EpisimPerson p = createPerson(act, container);
            init.accept(p);
        }

        return container;
    }

    /**
     * Remove person from container.
     */
    public static void removePerson(EpisimContainer<?> container, EpisimPerson p) {
        container.removePerson(p.getPersonId());
    }

}
