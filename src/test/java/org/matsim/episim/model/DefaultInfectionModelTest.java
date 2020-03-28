package org.matsim.episim.model;

import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.*;

import java.time.Duration;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DefaultInfectionModelTest {

    private static final Offset<Double> OFFSET = Offset.offset(0.001);

    private DefaultInfectionModel model;

    @Before
    public void setup() {
        EpisimReporting reporting = mock(EpisimReporting.class);
        EpisimConfigGroup config = EpisimTestUtils.createTestConfig();
        model = new DefaultInfectionModel(new Random(1), config, reporting);
        model.setRestrictionsForIteration(1, config.createInitialRestrictions());

    }

    /**
     * Samples how many time person {@code p} gets infected over many runs.
     *
     * @param jointTime leaving time of person p
     * @param actType   activity type
     * @param f         provider for facility
     * @param p         provider for person
     * @return sampled infection rate
     */
    private double sampleInfectionRate(Duration jointTime, String actType, Supplier<InfectionEventHandler.EpisimFacility> f,
                                       Function<InfectionEventHandler.EpisimFacility, EpisimPerson> p) {

        int infections = 0;

        for (int i = 0; i < 10000; i++) {
            InfectionEventHandler.EpisimFacility container = f.get();
            EpisimPerson person = p.apply(container);
            model.infectionDynamicsFacility(person, container, jointTime.getSeconds(), actType);
            if (person.getDiseaseStatus() == EpisimPerson.DiseaseStatus.infectedButNotContagious)
                infections++;
        }

        return infections / 10000d;
    }

    @Test
    public void highContactRate() {
        double rate = sampleInfectionRate(Duration.ofMinutes(15), "c10",
                () -> EpisimTestUtils.createFacility(1, "c10", EpisimPerson.DiseaseStatus.contagious),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );

        assertThat(rate).isCloseTo(1, OFFSET);
        rate = sampleInfectionRate(Duration.ZERO, "c10",
                () -> EpisimTestUtils.createFacility(1, "c10", EpisimPerson.DiseaseStatus.contagious),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );

        assertThat(rate).isCloseTo(0, OFFSET);
    }

    @Test
    public void alone() {
        double rate = sampleInfectionRate(Duration.ofMinutes(10), "c10",
                () -> EpisimTestUtils.createFacility(0, "c10", EpisimPerson.DiseaseStatus.contagious),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );

        assertThat(rate).isCloseTo(0, OFFSET);
    }

    @Test
    public void noCrossInfection() {
        double rate = sampleInfectionRate(Duration.ofMinutes(30), "c10",
                () -> EpisimTestUtils.createFacility(1, "c1", EpisimPerson.DiseaseStatus.contagious),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );

        assertThat(rate).isCloseTo(0, OFFSET);
    }

}