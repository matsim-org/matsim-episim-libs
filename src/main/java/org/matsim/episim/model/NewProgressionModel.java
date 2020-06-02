package org.matsim.episim.model;

import com.google.inject.Inject;
import com.typesafe.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;

import java.time.LocalDate;
import java.util.SplittableRandom;

import static org.matsim.episim.model.Transition.to;

/**
 * Default progression model with configurable state transitions.
 * This class in designed to for subclassing to support defining different transition probabilities.
 */
public class NewProgressionModel extends AbstractProgressionModel {

	/**
	 * Default config of the progression model.
	 */
	public static final Config DEFAULT_CONFIG = Transition.config()
			.from(EpisimPerson.DiseaseStatus.infectedButNotContagious,
					to(EpisimPerson.DiseaseStatus.contagious, Transition.fixed(4)))

			.from(EpisimPerson.DiseaseStatus.contagious,
					to(EpisimPerson.DiseaseStatus.showingSymptoms, Transition.fixed(2)),
					to(EpisimPerson.DiseaseStatus.recovered, Transition.fixed(12)))

			.from(EpisimPerson.DiseaseStatus.showingSymptoms,
					to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.fixed(4)),
					to(EpisimPerson.DiseaseStatus.recovered, Transition.fixed(10)))

			.from(EpisimPerson.DiseaseStatus.seriouslySick,
					to(EpisimPerson.DiseaseStatus.critical, Transition.fixed(1)),
					to(EpisimPerson.DiseaseStatus.recovered, Transition.fixed(13)))

			.from(EpisimPerson.DiseaseStatus.critical,
					to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.fixed(9)))
			.build();

	private static final Logger log = LogManager.getLogger(NewProgressionModel.class);

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
	public NewProgressionModel(SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig) {
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

		LocalDate date = episimConfig.getStartDate().plusDays(day - 1);

		// Hardcoded capacity before 06-01
		if (date.isBefore(LocalDate.parse("2020-06-01"))) {
			tracingCapacity = (int) (30 * episimConfig.getSampleSize());
		} else {
			tracingCapacity = (int) (tracingConfig.getTracingCapacity() * episimConfig.getSampleSize());
		}

	}

	@Override
	public final void updateState(EpisimPerson person, int day) {
		super.updateState(person, day);

		// account for the delay in showing symptoms and tracing
		if (person.getDiseaseStatus() == EpisimPerson.DiseaseStatus.showingSymptoms &&
				person.daysSince(EpisimPerson.DiseaseStatus.showingSymptoms, day) == tracingConfig.getTracingDelay()) {
			double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, day);

			performTracing(person, now - tracingConfig.getTracingDelay() * DAY, day);
		}
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
				return EpisimPerson.DiseaseStatus.seriouslySick;


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

		if (day < tracingConfig.getPutTraceablePersonsInQuarantineAfterDay()) return;

		if (tracingCapacity <= 0) return;

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
					|| rnd.nextDouble() < tracingConfig.getTracingProbability())

				quarantinePerson(pw, day);

		}

		tracingCapacity--;
	}

	private void quarantinePerson(EpisimPerson p, int day) {

		if (p.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no && p.getDiseaseStatus() != EpisimPerson.DiseaseStatus.recovered) {
			p.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);
		}
	}

}
