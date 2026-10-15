package org.matsim.episim.model.vaccination;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.SeasonalVaccinationConfigGroup;
import org.matsim.episim.model.VaccinationType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Yearly vaccination campaigns, such as the seasonal influenza vaccination, which the built-in models (one dose and one booster per
 * person) cannot express. The campaigns are in {@link SeasonalVaccinationConfigGroup}; the model is added to the set of
 * additional {@link VaccinationModel}s, not bound as the single model.
 *
 * <p>On every day of a campaign an agent who has not yet got the product of the season, whose share by age is {@code c}, is
 * vaccinated with the probability</p>
 *
 * <pre>h(d) = c * w(d) / (1 - c * W(d-1))</pre>
 *
 * <p>where {@code w(d)} is the part of the campaign that falls on the day and {@code W(d-1)} the part before it. At the end of the
 * campaign the share of the vaccinated of each age is {@code c}. The model keeps no state: "has got the product" is read from the
 * vaccination history of the agent, so a run from a snapshot needs nothing but what the snapshot holds.</p>
 *
 * <p>The draw is not taken from the random number generator of the run. It is a hash of the seed of the run, the day and the
 * agent, so a run from a snapshot gives the vaccinations of a continuous run, and the vaccinations do not shift the random stream
 * of the rest of the simulation. An agent in {@code seriouslySick} or {@code critical} is not vaccinated; every other disease
 * status is, because the protection acts on later infections only.</p>
 *
 * <p>Without a season in the config the model does nothing.</p>
 */
public final class SeasonalVaccinationModel implements VaccinationModel {

	private static final Logger log = LogManager.getLogger(SeasonalVaccinationModel.class);

	private final SeasonalVaccinationConfigGroup config;
	private final long seed;

	/** Vaccinations given so far per season, for the log only. */
	private final Map<String, Integer> given = new HashMap<>();

	@Inject
	public SeasonalVaccinationModel(Config config) {
		this.config = ConfigUtils.addOrGetModule(config, SeasonalVaccinationConfigGroup.class);
		this.seed = config.global().getRandomSeed();
	}

	@Override
	public void handleVaccination(Map<Id<Person>, EpisimPerson> persons, LocalDate date, int iteration, double now) {

		for (SeasonalVaccinationConfigGroup.Season season : config.getSeasons()) {
			if (date.isBefore(season.firstDay()) || date.isAfter(season.lastDay()))
				continue;

			double before = season.cumulativeShare(date.minusDays(1));
			double today = season.cumulativeShare(date) - before;
			if (today <= 0.0)
				continue;

			VaccinationType product = season.getProduct();
			int vaccinated = 0;
			for (EpisimPerson person : persons.values()) {

				double share = season.shareAt(person.getAge());
				if (share <= 0.0 || !person.isVaccinable() || isSeverelyIll(person) || hasProduct(person, product))
					continue;

				double remaining = 1.0 - share * before;
				double probability = remaining <= 1.0e-12 ? 1.0 : Math.min(1.0, share * today / remaining);
				if (uniform(seed, iteration, person.getPersonId()) < probability) {
					vaccinate(person, iteration, product);
					vaccinated++;
				}
			}

			int total = given.merge(season.getSeason(), vaccinated, Integer::sum);
			if (date.equals(season.firstDay()) || date.equals(season.lastDay()))
				log.info("Seasonal vaccination {}: {} vaccinated on {}, {} of the campaign so far ({})", season.getSeason(), vaccinated, date,
						total, product);
		}
	}

	private static boolean isSeverelyIll(EpisimPerson person) {
		EpisimPerson.DiseaseStatus status = person.getDiseaseStatus();
		return status == EpisimPerson.DiseaseStatus.seriouslySick || status == EpisimPerson.DiseaseStatus.critical;
	}

	private static boolean hasProduct(EpisimPerson person, VaccinationType product) {
		for (int i = 0; i < person.getNumVaccinations(); i++)
			if (person.getVaccinationType(i).equals(product))
				return true;
		return false;
	}

	/**
	 * A number in [0, 1) that depends on the seed, the day and the person only: the mixing function of SplitMix64 applied to their
	 * combination. The hash of the id is that of its text, which does not depend on the order in which ids are created.
	 */
	static double uniform(long seed, int iteration, Id<Person> person) {
		long z = seed + 0x9E3779B97F4A7C15L * (iteration + 1L) + 0xD1B54A32D192ED03L * person.toString().hashCode();
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}
}
