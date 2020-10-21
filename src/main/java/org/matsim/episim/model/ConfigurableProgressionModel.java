package org.matsim.episim.model;

import com.google.inject.Inject;
import com.typesafe.config.Config;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.*;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.facilities.ActivityFacility;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.time.LocalDate;
import java.util.*;

import static org.matsim.episim.EpisimUtils.readChars;
import static org.matsim.episim.EpisimUtils.writeChars;
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
			.from(DiseaseStatus.infectedButNotContagious,
					to(DiseaseStatus.contagious, Transition.logNormalWithMedianAndStd(4., 4.))) // 3 3

// Dauer Infektiosität:: Es wurde geschätzt, dass eine relevante Infektiosität bereits zwei Tage vor Symptombeginn vorhanden ist und die höchste Infektiosität am Tag vor dem Symptombeginn liegt
// Dauer Infektiosität: Abstrichproben vom Rachen enthielten vermehrungsfähige Viren bis zum vierten, aus dem Sputum bis zum achten Tag nach Symptombeginn
			.from(DiseaseStatus.contagious,
					to(DiseaseStatus.showingSymptoms, Transition.logNormalWithMedianAndStd(2., 2.)),    //80%
					to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(4., 4.)))            //20%

// Erkankungsbeginn -> Hospitalisierung: Eine Studie aus Deutschland zu 50 Patienten mit eher schwereren Verläufen berichtete für alle Patienten eine mittlere (Median) Dauer von vier Tagen (IQR: 1–8 Tage)
			.from(DiseaseStatus.showingSymptoms,
					to(DiseaseStatus.seriouslySick, Transition.logNormalWithMedianAndStd(4., 4.)),
					to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(8., 8.)))

// Hospitalisierung -> ITS: In einer chinesischen Fallserie betrug diese Zeitspanne im Mittel (Median) einen Tag (IQR: 0–3 Tage)
			.from(DiseaseStatus.seriouslySick,
					to(DiseaseStatus.critical, Transition.logNormalWithMedianAndStd(1., 1.)),
					to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(14., 14.)))

// Dauer des Krankenhausaufenthalts: „WHO-China Joint Mission on Coronavirus Disease 2019“ wird berichtet, dass milde Fälle im Mittel (Median) einen Krankheitsverlauf von zwei Wochen haben und schwere von 3–6 Wochen
			.from(DiseaseStatus.critical,
					to(DiseaseStatus.seriouslySickAfterCritical, Transition.logNormalWithMedianAndStd(21., 21.)))

			.from(DiseaseStatus.seriouslySickAfterCritical,
					to(DiseaseStatus.recovered, Transition.logNormalWithMedianAndStd(7., 7.)))

			.build();

	// yyyy Quellen für alle Aussagen oben??  "Es ..." oder "Eine Studie aus ..." ist mir eigentlich nicht genug.  kai, aug'20
	// yyyy Der obige Code existiert nochmals in AbstractSnzScenario2020.  Können wir in konsolidieren?  kai, oct'20



	private static final Logger log = LogManager.getLogger(ConfigurableProgressionModel.class);

	private static final double DAY = 24. * 3600;

	/**
	 * Definition of state transitions from x -> y
	 * Indices are the ordinal values of {@link DiseaseStatus} (as 2d matrix form)
	 */
	private final Transition[] tMatrix;
	private final TracingConfigGroup tracingConfig;
	/**
	 * Counts how many infections occurred at each location.
	 */
	private final Object2IntMap<Id<ActivityFacility>> locations = new Object2IntOpenHashMap<>();

	/**
	 * Person ids already traced.
	 */
	private final Set<Id<Person>> traced = Collections.newSetFromMap(new IdentityHashMap<>());

	/**
	 * Persons marked for contact tracing.
	 */
	private final Set<Id<Person>> tracingQueue = new LinkedHashSet<>();

	/**
	 * Tracing capacity left for the day.
	 */
	private int tracingCapacity = Integer.MAX_VALUE;

	/**
	 * Tracing probability for current day.
	 */
	private double tracingProb = 1;

	/**
	 * Tracing delay for current day.
	 */
	private int tracingDelay = 0;

	/**
	 * Used to track how many new people started showing symptoms.
	 */
	private long prevShowingSymptoms;

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

		LocalDate date = episimConfig.getStartDate().plusDays(day - 1);

		// Default capacity if none is set
		tracingCapacity = EpisimUtils.findValidEntry(tracingConfig.getTracingCapacity(), Integer.MAX_VALUE, date);

		// scale by sample size
		if (tracingCapacity != Integer.MAX_VALUE)
			tracingCapacity *= episimConfig.getSampleSize();

		tracingProb = EpisimUtils.findValidEntry(tracingConfig.getTracingProbability(), 1.0, date);
		tracingDelay = EpisimUtils.findValidEntry(tracingConfig.getTracingDelay(), 0, date);
	}

	@Override
	public final void updateState(EpisimPerson person, int day) {
		super.updateState(person, day);


		// A healthy quarantined person is dismissed from quarantine after some time
		if (releasePerson(person) && person.daysSinceQuarantine(day) > tracingConfig.getQuarantineDuration()) {
			person.setQuarantineStatus(EpisimPerson.QuarantineStatus.no, day);
		}

		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, day);

		// Delay 0 is already handled
		if (person.hadDiseaseStatus(DiseaseStatus.showingSymptoms) && tracingDelay > 0 &&
				person.daysSince(DiseaseStatus.showingSymptoms, day) >= tracingDelay) {

			performTracing(person, now - tracingDelay * DAY, day);
		}

		// clear tracing if not relevant anymore
		person.clearTraceableContractPersons(now - (tracingDelay + tracingConfig.getTracingDayDistance() + 1) * DAY);
	}

	/**
	 * Checks whether person can be released from quarantine.
	 */
	private boolean releasePerson(EpisimPerson person) {

		// person not in quarantine can not be released
		if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no)
			return false;

		TracingConfigGroup.QuarantineRelease release = tracingConfig.getQuarantineRelease();
		DiseaseStatus status = person.getDiseaseStatus();

		if (release == TracingConfigGroup.QuarantineRelease.SUSCEPTIBLE && status == DiseaseStatus.susceptible)
			return true;

		if (release == TracingConfigGroup.QuarantineRelease.NON_SYMPTOMS &&
				(status == DiseaseStatus.susceptible || status == DiseaseStatus.contagious || status == DiseaseStatus.infectedButNotContagious))
			return true;

		return false;
	}

	@Override
	protected final void onTransition(EpisimPerson person, double now, int day, DiseaseStatus from, DiseaseStatus to) {

		if (to == DiseaseStatus.showingSymptoms) {

			person.setQuarantineStatus(EpisimPerson.QuarantineStatus.full, day);
			// Perform tracing immediately if there is no delay, otherwise needs to be done when person shows symptoms
			if (tracingDelay == 0) {
				performTracing(person, now, day);
			}

			// count infections at locations
			if (tracingConfig.getStrategy() == TracingConfigGroup.Strategy.LOCATION ||
					tracingConfig.getStrategy() == TracingConfigGroup.Strategy.LOCATION_WITH_TESTING) {
				// persons with no infection container have been initially infected
				if (person.getInfectionContainer() != null && person.getInfectionType() != null) {
					String container = person.getInfectionContainer().toString();
					if (!container.startsWith("home") && !container.startsWith("tr") &&
							!person.getInfectionType().contains("shop") && !person.getInfectionType().contains("pt")) {
						locations.mergeInt(person.getInfectionContainer(), 1, Integer::sum);
					}
				}
			}
		}
	}

	@Override
	public final void beforeStateUpdates(Map<Id<Person>, EpisimPerson> persons, int day, EpisimReporting.InfectionReport report) {

		double now = EpisimUtils.getCorrectedTime(episimConfig.getStartOffset(), 0, day);

		// perform the location based tracing
		// there is always a delay of 1 day
		ObjectIterator<Object2IntMap.Entry<Id<ActivityFacility>>> it = locations.object2IntEntrySet().iterator();
		while (it.hasNext()) {

			Object2IntMap.Entry<Id<ActivityFacility>> e = it.next();

			// trace facilities that are above the threshold
			if (e.getIntValue() >= tracingConfig.getLocationThreshold()) {

				log.debug("Trace location {}", e.getKey());

				for (EpisimPerson p : persons.values()) {

					if (p.getInfectionContainer() == e.getKey()) {

						quarantinePerson(p, day);

						if (tracingConfig.getStrategy() == TracingConfigGroup.Strategy.LOCATION) {
							tracingCapacity--;
						} else if (tracingConfig.getStrategy() == TracingConfigGroup.Strategy.LOCATION_WITH_TESTING) {
							// assumes that all contact persons get tested
							// then quarantines all of their contacts
							performTracing(p, now, day);
						}
					}
				}

				it.remove();
			}
		}

		if (tracingConfig.getStrategy() == TracingConfigGroup.Strategy.IDENTIFY_SOURCE) {

			// Persons that will be traced for the day
			Queue<Id<Person>> queue = new ArrayDeque<>(tracingQueue);

			while (tracingCapacity > 0 && !queue.isEmpty()) {

				Id<Person> id = queue.poll();
				EpisimPerson person = persons.get(id);
				tracingQueue.remove(id);

				// Assume that each contact got tested
				// if the test is positive contacts will be quarantined as well and also tested at the next day
				if (person.hadDiseaseStatus(DiseaseStatus.infectedButNotContagious)) {
					performTracing(person, now, day);
				}
			}

		} else if (tracingConfig.getStrategy() == TracingConfigGroup.Strategy.RANDOM) {

			double newCases = report.nShowingSymptomsCumulative - prevShowingSymptoms;
			prevShowingSymptoms = report.nShowingSymptomsCumulative;

			LocalDate date = episimConfig.getStartDate().plusDays(day - 1);
			Double prob = EpisimUtils.findValidEntry(tracingConfig.getTracingProbability(), 1.0, date);

			// scale probability with config value
			double p = prob * newCases / report.nTotal();

			// put persons randomly into quarantine
			for (EpisimPerson person : persons.values()) {
				if (rnd.nextDouble() < p)
					quarantinePerson(person, day);

			}
		}
	}

	@Override
	protected final DiseaseStatus decideNextState(EpisimPerson person) {

		switch (person.getDiseaseStatus()) {
			case infectedButNotContagious:
				return DiseaseStatus.contagious;

			case contagious:
				if (rnd.nextDouble() < getProbaOfTransitioningToShowingSymptoms(person ))
					return DiseaseStatus.showingSymptoms;
				else
					return DiseaseStatus.recovered;

			case showingSymptoms:
				if (rnd.nextDouble() < getProbaOfTransitioningToSeriouslySick(person))
					return DiseaseStatus.seriouslySick;
				else
					return DiseaseStatus.recovered;

			case seriouslySick:
				if (!person.hadDiseaseStatus(DiseaseStatus.critical)
						&& rnd.nextDouble() < getProbaOfTransitioningToCritical(person))
					return DiseaseStatus.critical;
				else
					return DiseaseStatus.recovered;

			case critical:
				return DiseaseStatus.seriouslySickAfterCritical;

			case seriouslySickAfterCritical:
				return DiseaseStatus.recovered;


			default:
				throw new IllegalStateException("No state transition defined for " + person.getDiseaseStatus());
		}
	}

	@Override
	protected final int decideTransitionDay(EpisimPerson person, DiseaseStatus from, DiseaseStatus to) {
		Transition t = tMatrix[from.ordinal() * DiseaseStatus.values().length + to.ordinal()];
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

	protected double getProbaOfTransitioningToContagious(EpisimPerson person) {
		return 1.;
	}

	protected double getProbaOfTransitioningToShowingSymptoms( EpisimPerson person ) {
		return 0.8;
	}

	/**
	 * Perform the tracing procedure for a person. Also ensures if enabled for current day.
	 */
	private void performTracing(EpisimPerson person, double now, int day) {

		if (day < tracingConfig.getPutTraceablePersonsInQuarantineAfterDay()
				|| tracingConfig.getStrategy() == TracingConfigGroup.Strategy.RANDOM) {
			return;
		}

		if (tracingCapacity <= 0) {
			return;
		}

		// check if already traced
		if (traced.contains(person.getPersonId()))
			return;

		traced.add(person.getPersonId());

		String homeId = null;

		// quarantine household flag controls direct household and 2nd order household
		if (tracingConfig.getQuarantineHousehold())
			homeId = (String) person.getAttributes().getAttribute("homeId");

		for (EpisimPerson pw : person.getTraceableContactPersons(now - tracingConfig.getTracingDayDistance() * DAY)) {

			if (tracingConfig.getCapacityType() == TracingConfigGroup.CapacityType.PER_CONTACT_PERSON) {
				tracingCapacity--;
				if (tracingCapacity <= 0)
					break;
			}

			// don't draw random number when tracing is practically off
			if (tracingProb == 0 && homeId == null)
				continue;

			// Persons of the same household are always traced successfully
			if ((homeId != null && homeId.equals(pw.getAttributes().getAttribute("homeId")))
					|| tracingProb == 1d || rnd.nextDouble() < tracingProb) {
				quarantinePerson(pw, day);
				log.debug("sending person={} into quarantine because of contact to person={}", pw.getPersonId(), person.getPersonId());
			}

		}

		if (tracingConfig.getCapacityType() == TracingConfigGroup.CapacityType.PER_PERSON)
			tracingCapacity--;

		if (tracingCapacity == 0) {
			log.debug("tracing capacity exhausted for day={}", now);
		}
	}

	private void quarantinePerson(EpisimPerson p, int day) {

		if (p.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no && p.getDiseaseStatus() != DiseaseStatus.recovered) {
			p.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);

			if (tracingConfig.getStrategy() == TracingConfigGroup.Strategy.IDENTIFY_SOURCE)
				tracingQueue.add(p.getPersonId());
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		super.readExternal(in);

		prevShowingSymptoms = in.readLong();

		int n = in.readInt();
		for (int i = 0; i < n; i++) {
			Id<ActivityFacility> id = Id.create(readChars(in), ActivityFacility.class);
			locations.put(id, in.readInt());
		}

		n = in.readInt();
		for (int i = 0; i < n; i++) {
			Id<Person> id = Id.createPersonId(readChars(in));
			tracingQueue.add(id);
		}

		n = in.readInt();
		for (int i = 0; i < n; i++) {
			Id<Person> id = Id.createPersonId(readChars(in));
			traced.add(id);
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);

		out.writeLong(prevShowingSymptoms);

		out.writeInt(locations.size());
		for (Object2IntMap.Entry<Id<ActivityFacility>> e : locations.object2IntEntrySet()) {
			writeChars(out, e.getKey().toString());
			out.writeInt(e.getIntValue());
		}

		out.writeInt(tracingQueue.size());
		for (Id<Person> personId : tracingQueue) {
			writeChars(out, personId.toString());
		}

		out.writeInt(traced.size());
		for (Id<Person> personId : traced) {
			writeChars(out, personId.toString());
		}
	}
}
