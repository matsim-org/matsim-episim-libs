package org.matsim.episim;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimPersonTest {

    @Test
    public void daysSince() {

        EpisimPerson p =  EpisimTestUtils.createPerson("work", null);
        double now = EpisimUtils.getCorrectedTime(0, 5);

        p.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.infectedButNotContagious);
        assertThat(p.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, 5))
                .isEqualTo(0);

        assertThat(p.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, 10))
                .isEqualTo(5);

        // change during the third day
        now = EpisimUtils.getCorrectedTime(3600, 3);
        p.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.critical);
        assertThat(p.daysSince(EpisimPerson.DiseaseStatus.critical, 4))
                .isEqualTo(1);

    }
}