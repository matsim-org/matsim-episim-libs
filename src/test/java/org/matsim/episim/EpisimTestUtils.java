package org.matsim.episim;

import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.mockito.Mockito;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class EpisimTestUtils {

	public static final Consumer<EpisimPerson> CONTAGIOUS = person -> person.setDiseaseStatus(0., EpisimPerson.DiseaseStatus.contagious);
	public static final Consumer<EpisimPerson> SYMPTOMS = person -> person.setDiseaseStatus(0., EpisimPerson.DiseaseStatus.showingSymptoms);
	public static final Consumer<EpisimPerson> VACCINATED = person -> person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, 0);

	public static final Consumer<EpisimPerson> FULL_QUARANTINE = person -> {
		person.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.contagious);
		person.setQuarantineStatus(EpisimPerson.QuarantineStatus.full, 0);
	};

	public static final Consumer<EpisimPerson> HOME_QUARANTINE = person -> {
		person.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.showingSymptoms);
		person.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, 0);
	};

	private static final AtomicLong ID = new AtomicLong(0);
	private static final EpisimReporting reporting = Mockito.mock(EpisimReporting.class, Mockito.withSettings().stubOnly());

	public static final EpisimConfigGroup TEST_CONFIG = ConfigUtils.addOrGetModule(createTestConfig(), EpisimConfigGroup.class);

	/**
	 * Get the reporting stub.
	 */
	public static EpisimReporting getReporting() {
		return reporting;
	}

	/**
	 * Reset the person id counter.
	 */
	public static void resetIds() {
		ID.set(0);
	}

	/**
	 * Creates test config with some default interactions.
	 *
	 * @return
	 */
	public static Config createTestConfig() {
		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setSampleSize(1);
		episimConfig.setMaxContacts(10);
		episimConfig.setCalibrationParameter(0.001);

		// No container name should be the prefix of another one
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c00").setContactIntensity(0));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c0.1").setContactIntensity(0.1));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c0.5").setContactIntensity(0.5));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c1.0").setContactIntensity(1));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c5").setContactIntensity(5));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("c10").setContactIntensity(10));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("home").setContactIntensity(1));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("quarantine_home").setContactIntensity(1));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("leis").setContactIntensity(1));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("work").setContactIntensity(1));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("edu").setContactIntensity(1));
		episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("tr").setContactIntensity(1));

		return config;
	}

	public static InfectionEventHandler.EpisimFacility createFacility() {
		return new InfectionEventHandler.EpisimFacility(Id.create(ID.getAndIncrement(), ActivityFacility.class));
	}

	/**
	 * Create facility with n persons in it.
	 */
	public static InfectionEventHandler.EpisimFacility createFacility(int n, String act, Consumer<EpisimPerson> init) {
		InfectionEventHandler.EpisimFacility container = createFacility();
		return addPersons(container, n, act, init);
	}

	/**
	 * Create a facility with certain group size.
	 */
	public static InfectionEventHandler.EpisimFacility createFacility(int n, String act, int groupSize, Consumer<EpisimPerson> init) {
		InfectionEventHandler.EpisimFacility container = createFacility();
		container.setMaxGroupSize(groupSize);
		return addPersons(container, n, act, init);
	}

	/**
	 * Create a person and add to container.
	 */
	public static EpisimPerson createPerson(String currentAct, @Nullable EpisimContainer<?> container) {
		EpisimPerson p = new EpisimPerson(Id.createPersonId(ID.getAndIncrement()), new Attributes(), reporting);

		p.getTrajectory().add(new EpisimPerson.Activity(currentAct, TEST_CONFIG.selectInfectionParams(currentAct)));

		if (container != null) {
			container.addPerson(p, 0);
		}

		return p;
	}

	/**
	 * Create a person with specific reporting.
	 */
	public static EpisimPerson createPerson(EpisimReporting reporting) {
		return new EpisimPerson(Id.createPersonId(ID.getAndIncrement()), new Attributes(), reporting);
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
		container.removePerson(p);
	}


	/**
	 * Report with zero values.
	 */
	public static EpisimReporting.InfectionReport createReport(String date, long day) {
		return new EpisimReporting.InfectionReport("test", 0, date, day);
	}

	/**
	 * Report with incidence per 100k inhabitants.
	 */
	public static EpisimReporting.InfectionReport createReport(LocalDate date, long day, int incidence) {
		EpisimReporting.InfectionReport report = new EpisimReporting.InfectionReport("test", 0, date.toString(), day);

		report.nSusceptible = 100_000 - incidence;
		report.nTotalInfected = incidence;
		report.nShowingSymptoms = incidence;

		return report;
	}


}
