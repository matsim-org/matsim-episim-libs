package org.matsim.episim.model;

import com.google.inject.Inject;
import com.typesafe.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;

import java.util.SplittableRandom;

import static org.matsim.episim.model.Transition.to;

/**
 * Progression model with configurable state transitions.
 * This class in designed to for subclassing to support defining different transition probabilities.
 */
public class ConfigurableProgressionModel extends AbstractProgressionModel {

	/**
	 * Default config of the progression model.
	 */
	public static final Config DEFAULT_CONFIG = Transition.config()
			// Inkubationszeit: Die Inkubationszeit [ ... ] liegt im Mittel (Median) bei 5–6 Tagen (Spannweite 1 bis 14 Tage)
			.from(EpisimPerson.DiseaseStatus.infectedButNotContagious,
					to(EpisimPerson.DiseaseStatus.contagious, Transition.logNormalWithMedianAndStd(4., 4.))) // 3 3

// Dauer Infektiosität:: Es wurde geschätzt, dass eine relevante Infektiosität bereits zwei Tage vor Symptombeginn vorhanden ist und die höchste Infektiosität am Tag vor dem Symptombeginn liegt
// Dauer Infektiosität: Abstrichproben vom Rachen enthielten vermehrungsfähige Viren bis zum vierten, aus dem Sputum bis zum achten Tag nach Symptombeginn
			.from(EpisimPerson.DiseaseStatus.contagious,
					to(EpisimPerson.DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndStd(2., 2.)),    //80%
					to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(4., 4.)))            //20%

// Erkankungsbeginn -> Hospitalisierung: Eine Studie aus Deutschland zu 50 Patienten mit eher schwereren Verläufen berichtete für alle Patienten eine mittlere (Median) Dauer von vier Tagen (IQR: 1–8 Tage)
			.from(EpisimPerson.DiseaseStatus.showingSymptoms,
					to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndStd(4., 4.)),
					to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))

// Hospitalisierung -> ITS: In einer chinesischen Fallserie betrug diese Zeitspanne im Mittel (Median) einen Tag (IQR: 0–3 Tage)
			.from(EpisimPerson.DiseaseStatus.seriouslySick,
					to(EpisimPerson.DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1., 1.)),
					to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(14., 14.)))

// Dauer des Krankenhausaufenthalts: „WHO-China Joint Mission on Coronavirus Disease 2019“ wird berichtet, dass milde Fälle im Mittel (Median) einen Krankheitsverlauf von zwei Wochen haben und schwere von 3–6 Wochen
			.from(EpisimPerson.DiseaseStatus.critical,
					to(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndStd(21., 21.)))

			.from(EpisimPerson.DiseaseStatus.seriouslySickAfterCritical,
				to(EpisimPerson.DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7., 7.)))

			.build();


	private static final Logger log = LogManager.getLogger(ConfigurableProgressionModel.class);

	private static final double DAY = 24. * 3600;

	/**
	 * Definition of state transitions from x -> y
	 * Indices are the ordinal values of {@link EpisimPerson.DiseaseStatus} (as 2d matrix form)
	 */
	private final Transition[] tMatrix;
	private final TracingConfigGroup tracingConfig;

	/**
	 * Tracing capacity left for the day.
	 */
	private int tracingCapacity = Integer.MAX_VALUE;

	@Inject
	public ConfigurableProgressionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig) {
		super(rnd, episimConfig);
		this.tracingConfig = tracingConfig;

		Config config = episimConfig.getProgressionConfig();

		if (config.isEmpty()) {
			config = DEFAULT_CONFIG;
			log.info("Using default disease progression");
		}

		Transition.Builder t = Transition.parse(config);
		log.info("Using disease progression config: {}", t);
		tMatrix = t.asArray();
	}

	@Override
	public void setIteration(int day) {

		// Hardcoded capacity before 06-01
		// LocalDate date = episimConfig.getStartDate().plusDays(day - 1);
		//if (date.isBefore(LocalDate.parse("2020-06-01"))) {
		//	tracingCapacity = (int) (30 * episimConfig.getSampleSize());
		//} else {
		//}

		if (tracingConfig.getTracingCapacity() == Integer.MAX_VALUE)
			tracingCapacity = Integer.MAX_VALUE;
		else
			tracingCapacity = (int) (tracingConfig.getTracingCapacity() * episimConfig.getSampleSize());
	}

	@Override
	public final void updateState(EpisimPerson person, int day) {
		super.updateState(person, day);

		// account for the delay in showing symptoms and tracing
		int tracingDelay = tracingConfig.getTracingDelay();

		// A healthy quarantined person is dismissed from quarantine after some time
		if (person.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible &&
				person.getQuarantineStatus() != EpisimPerson.QuarantineStatus.no && person.daysSinceQuarantine(day) > 14) {
			person.setQuarantineStatus(EpisimPerson.QuarantineStatus.no, day);
		}

		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, day);

		// Delay 0 is already handled
		if (person.hadDiseaseStatus(EpisimPerson.DiseaseStatus.showingSymptoms) && tracingDelay > 0 &&
				person.daysSince(EpisimPerson.DiseaseStatus.showingSymptoms, day) == tracingDelay) {

			performTracing(person, now - tracingDelay * DAY, day);
		}

		// clear tracing if not relevant anymore
		person.clearTraceableContractPersons(now - (tracingConfig.getTracingDelay() + tracingConfig.getTracingDayDistance() + 1) * DAY);
	}

	@Override
	protected final void onTransition(EpisimPerson person, double now, int day, EpisimPerson.DiseaseStatus from, EpisimPerson.DiseaseStatus to) {

		if (to == EpisimPerson.DiseaseStatus.showingSymptoms) {

			person.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);
			// Perform tracing immediately if there is no delay, otherwise needs to be done when person shows symptoms
			if (tracingConfig.getTracingDelay() == 0) {
				performTracing(person, now, day);
			}
		}
	}

	@Override
	protected final EpisimPerson.DiseaseStatus decideNextState(EpisimPerson person) {

		switch (person.getDiseaseStatus()) {
			case infectedButNotContagious:
				return EpisimPerson.DiseaseStatus.contagious;

			case contagious:
				if (rnd.nextDouble() < 0.8)
					return EpisimPerson.DiseaseStatus.showingSymptoms;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case showingSymptoms:
				if (rnd.nextDouble() < getProbaOfTransitioningToSeriouslySick(person))
					return EpisimPerson.DiseaseStatus.seriouslySick;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case seriouslySick:
				if (!person.hadDiseaseStatus(EpisimPerson.DiseaseStatus.critical)
						&& rnd.nextDouble() < getProbaOfTransitioningToCritical(person))
					return EpisimPerson.DiseaseStatus.critical;
				else
					return EpisimPerson.DiseaseStatus.recovered;

			case critical:
				return EpisimPerson.DiseaseStatus.seriouslySickAfterCritical;

			case seriouslySickAfterCritical:
				return EpisimPerson.DiseaseStatus.recovered;


			default:
				throw new IllegalStateException("No state transition defined for " + person.getDiseaseStatus());
		}
	}

	@Override
	protected final int decideTransitionDay(EpisimPerson person, EpisimPerson.DiseaseStatus from, EpisimPerson.DiseaseStatus to) {
		Transition t = tMatrix[from.ordinal() * EpisimPerson.DiseaseStatus.values().length + to.ordinal()];
		if (t == null) throw new IllegalStateException(String.format("No transition from %s to %s defined", from, to));

		return t.getTransitionDay(rnd);
	}

	/**
	 * Probability that a persons transitions from {@code showingSymptoms} to {@code seriouslySick}.
	 */
	protected double getProbaOfTransitioningToSeriouslySick(EpisimPerson person) {
		return 0.05625;
	}

	/**
	 * Probability that a persons transitions from {@code seriouslySick} to {@code critical}.
	 */
	protected double getProbaOfTransitioningToCritical(EpisimPerson person) {
		return 0.25;
	}

	/**
	 * Perform the tracing procedure for a person. Also ensures if enabled for current day.
	 */
	private void performTracing(EpisimPerson person, double now, int day) {

		if (day < tracingConfig.getPutTraceablePersonsInQuarantineAfterDay()) {
			return;
		}

		if (tracingCapacity <= 0) {
			return;
		}

		String homeId = null;

		// quarantine household flag controls direct household and 2nd order household
		if (tracingConfig.getQuarantineHousehold())
			homeId = (String) person.getAttributes().getAttribute("homeId");

		// TODO: tracing household members makes always sense, no app or anything needed..
		// they might not appear as contact persons under certain circumstances

		for (EpisimPerson pw : person.getTraceableContactPersons(now - tracingConfig.getTracingDayDistance() * DAY)) {

			// don't draw random number when tracing is practically off
			if (tracingConfig.getTracingProbability() == 0 && homeId == null)
				continue;

			// Persons of the same household are always traced successfully
			if ((homeId != null && homeId.equals(pw.getAttributes().getAttribute("homeId")))
					|| tracingConfig.getTracingProbability() == 1d || rnd.nextDouble() < tracingConfig.getTracingProbability()) {
				quarantinePerson(pw, day);
				log.debug("sending person={} into quarantine because of contact to person={}", pw.getPersonId(), person.getPersonId());
			}

		}

		tracingCapacity--;
		if (tracingCapacity == 0) {
			log.debug("tracing capacity exhausted for day={}", now);
		}
	}

	private void quarantinePerson(EpisimPerson p, int day) {

		if (p.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no && p.getDiseaseStatus() != EpisimPerson.DiseaseStatus.recovered) {
			p.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);
		}
	}

}
