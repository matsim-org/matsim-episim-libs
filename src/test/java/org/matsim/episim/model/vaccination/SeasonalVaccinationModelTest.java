package org.matsim.episim.model.vaccination;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.ImmunityConfigGroup;
import org.matsim.episim.SeasonalVaccinationConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.VaccinationType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SeasonalVaccinationModelTest {

	private static final VaccinationType FLU_1 = VaccinationType.of("flu_test_1");
	private static final VaccinationType FLU_2 = VaccinationType.of("flu_test_2");
	private static final LocalDate START = LocalDate.of(2022, 9, 26);

	/** Share 0.4 from age 60, 0.6 from age 80; campaign 1 October to 31 December, half of it by 31 October. */
	private static Config config(long seed) {
		Config config = ConfigUtils.createConfig();
		config.global().setRandomSeed(seed);
		SeasonalVaccinationConfigGroup group = ConfigUtils.addOrGetModule(config, SeasonalVaccinationConfigGroup.class);
		group.addSeason("2022/23", FLU_1, Map.of(0, 0.0, 59, 0.0, 60, 0.4, 79, 0.4, 80, 0.6), campaign(2022));
		group.addSeason("2023/24", FLU_2, Map.of(0, 0.0, 59, 0.0, 60, 0.4, 79, 0.4, 80, 0.6), campaign(2023));
		return config;
	}

	private static TreeMap<LocalDate, Double> campaign(int year) {
		return new TreeMap<>(Map.of(LocalDate.of(year, 9, 30), 0.0, LocalDate.of(year, 10, 31), 0.5, LocalDate.of(year, 12, 31), 1.0));
	}

	private static Map<Id<Person>, EpisimPerson> persons(int perAge, int... ages) {
		Map<Id<Person>, EpisimPerson> persons = new LinkedHashMap<>();
		for (int age : ages)
			for (int i = 0; i < perAge; i++) {
				EpisimPerson p = EpisimTestUtils.createPerson(true, age);
				persons.put(p.getPersonId(), p);
			}
		return persons;
	}

	private static void run(SeasonalVaccinationModel model, Map<Id<Person>, EpisimPerson> persons, LocalDate from, LocalDate to) {
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			int iteration = (int) ChronoUnit.DAYS.between(START, d) + 1;
			model.handleVaccination(persons, d, iteration, 86400.0 * iteration);
		}
	}

	private static double share(Map<Id<Person>, EpisimPerson> persons, int age, VaccinationType product) {
		List<EpisimPerson> of = persons.values().stream().filter(p -> p.getAge() == age).toList();
		return of.stream().filter(p -> has(p, product)).count() / (double) of.size();
	}

	private static boolean has(EpisimPerson p, VaccinationType product) {
		for (int i = 0; i < p.getNumVaccinations(); i++)
			if (p.getVaccinationType(i).equals(product))
				return true;
		return false;
	}

	@Test
	public void reachesTheShareOfEachAgeAndNobodyElse() {

		var persons = persons(10_000, 30, 59, 60, 80);
		run(new SeasonalVaccinationModel(config(1)), persons, START, LocalDate.of(2023, 1, 15));

		assertThat(share(persons, 30, FLU_1)).isEqualTo(0.0);
		assertThat(share(persons, 59, FLU_1)).isEqualTo(0.0);
		assertThat(share(persons, 60, FLU_1)).isBetween(0.38, 0.42);
		assertThat(share(persons, 80, FLU_1)).isBetween(0.58, 0.62);
		assertThat(persons.values()).allMatch(p -> p.getNumVaccinations() <= 1);
		assertThat(persons.values()).noneMatch(p -> has(p, FLU_2));
	}

	@Test
	public void followsTheTimingOfTheCampaign() {

		var persons = persons(20_000, 70);
		run(new SeasonalVaccinationModel(config(1)), persons, START, LocalDate.of(2022, 9, 30));
		assertThat(share(persons, 70, FLU_1)).isEqualTo(0.0);

		run(new SeasonalVaccinationModel(config(1)), persons, START, LocalDate.of(2022, 10, 31));
		// half of the campaign by 31 October: half of 0.4
		assertThat(share(persons, 70, FLU_1)).isBetween(0.19, 0.21);
	}

	@Test
	public void vaccinatesOncePerSeasonAndAgainInTheNext() {

		var persons = persons(5_000, 70);
		SeasonalVaccinationModel model = new SeasonalVaccinationModel(config(1));
		run(model, persons, START, LocalDate.of(2023, 12, 31));

		assertThat(persons.values()).allMatch(p -> p.getNumVaccinations() <= 2);
		long both = persons.values().stream().filter(p -> has(p, FLU_1) && has(p, FLU_2)).count();
		assertThat(both / (double) persons.size()).isBetween(0.12, 0.20);
	}

	@Test
	public void skipsAgentsWhoAreSeriouslySickOrNotVaccinable() {

		var persons = persons(1_000, 70);
		List<EpisimPerson> list = new ArrayList<>(persons.values());
		list.subList(0, 300).forEach(p -> p.setDiseaseStatus(0., EpisimPerson.DiseaseStatus.seriouslySick));
		list.subList(300, 600).forEach(p -> p.setVaccinable(false));
		list.subList(600, 700).forEach(p -> p.setDiseaseStatus(0., EpisimPerson.DiseaseStatus.showingSymptoms));

		run(new SeasonalVaccinationModel(config(1)), persons, START, LocalDate.of(2023, 1, 15));

		assertThat(list.subList(0, 600)).noneMatch(p -> p.getNumVaccinations() > 0);
		assertThat(list.subList(600, 700).stream().filter(p -> p.getNumVaccinations() > 0).count()).isGreaterThan(20);
	}

	/** The same agents (ids and ages) as a new population, in the given order of the ids. */
	private static Map<Id<Person>, EpisimPerson> population(List<String> ids) {
		Map<Id<Person>, EpisimPerson> persons = new LinkedHashMap<>();
		for (String id : ids) {
			org.matsim.utils.objectattributes.attributable.AttributesImpl attrs = new org.matsim.utils.objectattributes.attributable.AttributesImpl();
			attrs.putAttribute("microm:modeled:age", 70);
			Id<Person> personId = Id.createPersonId(id);
			persons.put(personId, new EpisimPerson(personId, attrs, EpisimTestUtils.getReporting()));
		}
		return persons;
	}

	private static java.util.Set<String> vaccinated(Map<Id<Person>, EpisimPerson> persons) {
		java.util.Set<String> ids = new java.util.TreeSet<>();
		persons.values().stream().filter(p -> p.getNumVaccinations() > 0).forEach(p -> ids.add(p.getPersonId().toString()));
		return ids;
	}

	@Test
	public void theDrawDependsOnTheSeedNotOnTheOrderOfTheAgents() {

		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 3_000; i++)
			ids.add("person" + i);
		List<String> reversed = new ArrayList<>(ids);
		java.util.Collections.reverse(reversed);

		var first = population(ids);
		var second = population(reversed);
		var otherSeed = population(ids);
		LocalDate end = LocalDate.of(2023, 1, 15);
		run(new SeasonalVaccinationModel(config(7)), first, START, end);
		run(new SeasonalVaccinationModel(config(7)), second, START, end);
		run(new SeasonalVaccinationModel(config(8)), otherSeed, START, end);

		assertThat(vaccinated(first)).isNotEmpty().isEqualTo(vaccinated(second));
		assertThat(vaccinated(otherSeed)).isNotEqualTo(vaccinated(first));
	}

	@Test
	public void theUniformNumberIsInTheUnitIntervalAndSpread() {

		double sum = 0;
		int n = 100_000;
		for (int i = 0; i < n; i++) {
			double u = SeasonalVaccinationModel.uniform(42, 1 + i % 50, Id.createPersonId("p" + i));
			assertThat(u).isGreaterThanOrEqualTo(0.0).isLessThan(1.0);
			sum += u;
		}
		assertThat(sum / n).isBetween(0.49, 0.51);
	}

	@Test
	public void doesNothingWithoutASeason() {

		var persons = persons(100, 70);
		run(new SeasonalVaccinationModel(ConfigUtils.createConfig()), persons, START, LocalDate.of(2023, 1, 15));
		assertThat(persons.values()).allMatch(p -> p.getNumVaccinations() == 0);
	}

	@Test
	public void cumulativeShareIsLinearBetweenTheDates() {

		SeasonalVaccinationConfigGroup.Season season = ConfigUtils.addOrGetModule(config(1), SeasonalVaccinationConfigGroup.class)
				.getSeasons().iterator().next();

		assertThat(season.cumulativeShare(LocalDate.of(2022, 9, 30))).isEqualTo(0.0);
		assertThat(season.cumulativeShare(LocalDate.of(2022, 10, 1))).isCloseTo(0.5 / 31, org.assertj.core.data.Offset.offset(1e-12));
		assertThat(season.cumulativeShare(LocalDate.of(2022, 10, 31))).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-12));
		assertThat(season.cumulativeShare(LocalDate.of(2022, 12, 31))).isEqualTo(1.0);
		assertThat(season.cumulativeShare(LocalDate.of(2023, 3, 1))).isEqualTo(1.0);
		assertThat(season.firstDay()).isEqualTo(LocalDate.of(2022, 10, 1));
		assertThat(season.lastDay()).isEqualTo(LocalDate.of(2022, 12, 31));
	}

	@Test
	public void theGroupChecksItsConsistency() {

		// a product without a source in the immunity group and without an entry in the vaccination group
		Config config = config(1);
		ConfigUtils.addOrGetModule(config, ImmunityConfigGroup.class);
		ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		assertThatThrownBy(config::checkConsistency).hasMessageContaining("no source").hasMessageContaining("flu_test_1");

		// timing that does not reach 1, and campaigns that overlap
		Config bad = ConfigUtils.createConfig();
		SeasonalVaccinationConfigGroup group = ConfigUtils.addOrGetModule(bad, SeasonalVaccinationConfigGroup.class);
		group.addSeason("a", FLU_1, Map.of(60, 0.4), new TreeMap<>(Map.of(LocalDate.of(2022, 9, 30), 0.0, LocalDate.of(2022, 12, 31), 0.9)));
		group.addSeason("b", FLU_2, Map.of(60, 0.4), new TreeMap<>(Map.of(LocalDate.of(2022, 11, 30), 0.0, LocalDate.of(2023, 1, 31), 1.0)));
		assertThatThrownBy(bad::checkConsistency).hasMessageContaining("share 1").hasMessageContaining("overlap");

		assertThatThrownBy(() -> group.addSeason("a", FLU_1, Map.of(60, 0.4), new TreeMap<>()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("twice");
		assertThatThrownBy(() -> group.getSeasons().iterator().next().setShareByAge(Map.of(60, 1.2)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
