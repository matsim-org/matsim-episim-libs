package org.matsim.episim.model;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.*;
import org.matsim.episim.policy.ShutdownPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DefaultInfectionModelTest {

    private static final Offset<Double> OFFSET = Offset.offset(0.001);

    private DefaultInfectionModel model;
    private Map<String, ShutdownPolicy.Restriction> restrictions;


    @Before
    public void setup() {
        EpisimReporting reporting = mock(EpisimReporting.class);
        EpisimConfigGroup config = EpisimTestUtils.createTestConfig();
        model = new DefaultInfectionModel(new Random(1), config, reporting);
        restrictions = config.createInitialRestrictions();
        model.setRestrictionsForIteration(1, restrictions);

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
                () -> EpisimTestUtils.createFacility(1, "c10", EpisimTestUtils.CONTAGIOUS),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );
        assertThat(rate).isCloseTo(1, OFFSET);

        rate = sampleInfectionRate(Duration.ZERO, "c10",
                () -> EpisimTestUtils.createFacility(1, "c10", EpisimTestUtils.CONTAGIOUS),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );
        assertThat(rate).isCloseTo(0, OFFSET);
    }

    @Test
    public void alone() {
        double rate = sampleInfectionRate(Duration.ofMinutes(10), "c10",
                () -> EpisimTestUtils.createFacility(0, "c10", EpisimTestUtils.CONTAGIOUS),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );
        assertThat(rate).isCloseTo(0, OFFSET);
    }

    @Test
    public void noInfection() {

        double rate = sampleInfectionRate(Duration.ofHours(2), "c10",
                () -> EpisimTestUtils.createFacility(5, "c10", p -> p.setDiseaseStatus(EpisimPerson.DiseaseStatus.infectedButNotContagious)),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );
        assertThat(rate).isCloseTo(0, OFFSET);

        rate = sampleInfectionRate(Duration.ofHours(2), "c10",
                () -> EpisimTestUtils.createFacility(5, "c10", p -> p.setDiseaseStatus(EpisimPerson.DiseaseStatus.recovered)),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );
        assertThat(rate).isCloseTo(0, OFFSET);

        rate = sampleInfectionRate(Duration.ofHours(2), "c10",
                () -> EpisimTestUtils.createFacility(5, "c10", EpisimTestUtils.QUARANTINED),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );
        assertThat(rate).isCloseTo(0, OFFSET);

        rate = sampleInfectionRate(Duration.ofHours(2), "c00",
                () -> EpisimTestUtils.createFacility(5, "c00", EpisimTestUtils.QUARANTINED),
                (f) -> EpisimTestUtils.createPerson("c00", f)
        );
        assertThat(rate).isCloseTo(0, OFFSET);
    }

    @Test
    public void sameInfectedInContainer() {

        double rate = sampleInfectionRate(Duration.ofMinutes(30), "c0.1",
                () -> EpisimTestUtils.createFacility(5, "c0.1", EpisimTestUtils.CONTAGIOUS),
                (f) -> EpisimTestUtils.createPerson("c0.1", f)
        );

        double rateWithQuarantined = sampleInfectionRate(Duration.ofMinutes(30), "c0.1",
                () -> EpisimTestUtils.addPersons(EpisimTestUtils.createFacility(5, "c0.1", EpisimTestUtils.CONTAGIOUS),
                        5, "c0.1", EpisimTestUtils.QUARANTINED),
                (f) -> EpisimTestUtils.createPerson("c0.1", f)
        );

        assertThat(rate).as("Infection rate")
                .isGreaterThanOrEqualTo(rateWithQuarantined);

    }

    @Test
    public void restrictions() {

        String type = "c1.0";
        double rate = sampleInfectionRate(Duration.ofMinutes(30), type,
                () -> EpisimTestUtils.createFacility(6, type, EpisimTestUtils.CONTAGIOUS),
                (f) -> EpisimTestUtils.createPerson(type, f)
        );

        restrictions.put(type, ShutdownPolicy.Restriction.newInstance(0.5));

        double rateRestricted = sampleInfectionRate(Duration.ofMinutes(30), type,
                () -> EpisimTestUtils.createFacility(6, type, EpisimTestUtils.CONTAGIOUS),
                (f) -> EpisimTestUtils.createPerson(type, f)
        );

        // Assume reduction between 0.3 and 0.5
        assertThat(rateRestricted)
                .isGreaterThan(rate * 0.3)
                .isLessThan(rate * 0.5);
    }

    @Test
    public void noCrossInfection() {
        double rate = sampleInfectionRate(Duration.ofMinutes(30), "c10",
                () -> EpisimTestUtils.createFacility(1, "c1", EpisimTestUtils.CONTAGIOUS),
                (f) -> EpisimTestUtils.createPerson("c10", f)
        );

        assertThat(rate).isCloseTo(0, OFFSET);
    }

    @Test
    public void infectionRates() {

        // This test fails for changes in infection rates
        // Please check if they are intended and update the values below

        ArrayList<Pair<Integer, Double>> expectation = Lists.newArrayList(
                // Number of persons & expected infection rate
                Pair.of(1, 0.39),
                Pair.of(3, 0.78),
                Pair.of(6, 0.92),
                Pair.of(10, 0.92),
                Pair.of(50, 0.92)
        );

        for (Pair<Integer, Double> p : expectation) {

            double rate = sampleInfectionRate(Duration.ofMinutes(20), "c0.5",
                    () -> EpisimTestUtils.addPersons(EpisimTestUtils.createFacility(
                            p.getLeft(), "c0.5", EpisimTestUtils.CONTAGIOUS),
                            p.getLeft(), "c0.5", EpisimTestUtils.QUARANTINED),
                    (f) -> EpisimTestUtils.createPerson("c0.5", f)
            );

            assertThat(rate).as("Infection rate for %d persons", p.getLeft())
                    .isCloseTo(p.getRight(), Offset.offset(0.01));

        }

    }

}