package org.matsim.episim;

import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.ImmunityModel;
import org.matsim.episim.model.VirusStrain;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;
import org.mockito.Mockito;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class EpisimTestUtils {

	public static final Consumer<EpisimPerson> CONTAGIOUS = person -> person.setDiseaseStatus(0., EpisimPerson.DiseaseStatus.contagious);
	public static final Consumer<EpisimPerson> SYMPTOMS = person -> person.setDiseaseStatus(0., EpisimPerson.DiseaseStatus.showingSymptoms);
	public static final Consumer<EpisimPerson> VACCINATED = person -> person.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, VaccinationType.generic, 0);

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
		episimConfig.setThreads(1);

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
	 * Create facility with just an ID
	 */
	public static InfectionEventHandler.EpisimFacility createFacility(String facId) {
		return new InfectionEventHandler.EpisimFacility(Id.create(facId, ActivityFacility.class));
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
	 * Create a facility with certain group size and ID.
	 */
	public static InfectionEventHandler.EpisimFacility createFacility(String facId, int n, String act, int groupSize, Consumer<EpisimPerson> init) {
		InfectionEventHandler.EpisimFacility container = createFacility(facId);
		container.setMaxGroupSize(groupSize);
		return addPersons(container, n, act, init);
	}

	/**
	 * Create a person and add to container.
	 */
	public static EpisimPerson createPerson(String currentAct, @Nullable EpisimContainer<?> container) {
		EpisimPerson p = new EpisimPerson(Id.createPersonId(ID.getAndIncrement()), new AttributesImpl(), reporting);

		Arrays.stream(DayOfWeek.values()).forEach(p::setStartOfDay);
		EpisimPerson.PerformedActivity act = p.addToTrajectory(0, TEST_CONFIG.selectInfectionParams(currentAct),null);
		Arrays.stream(DayOfWeek.values()).forEach(p::setEndOfDay);

		if (container != null) {
			container.addPerson(p, 0, act);
		}

		return p;
	}


	/**
	 * Create person with activity trajectory.
	 */
	public static EpisimPerson createPerson(String... activities) {

		EpisimPerson p = new EpisimPerson(Id.createPersonId(ID.getAndIncrement()), new AttributesImpl(), reporting);

		Arrays.stream(DayOfWeek.values()).forEach(p::setStartOfDay);

		for (String act : activities) {
			p.addToTrajectory(0, TEST_CONFIG.selectInfectionParams(act),null);
		}

		Arrays.stream(DayOfWeek.values()).forEach(p::setEndOfDay);

		p.initParticipation();

		return p;

	}

	/**
	 * Create a person with specific reporting.
	 */
	public static EpisimPerson createPerson(EpisimReporting reporting) {
		return new EpisimPerson(Id.createPersonId(ID.getAndIncrement()), new AttributesImpl(), reporting);
	}


	/**
	 * Create uninitialized person without trajectory.
	 */
	/**
	 * Immunity model for tests that do not care about immunity: nothing is ever reduced. Equivalent to what the
	 * curve-based immunity returns while {@code VaccinationType.natural} is not configured, which is the state
	 * every one of these tests was written in.
	 */
	public static ImmunityModel noImmunity() {
		return new ImmunityModel() {
			@Override
			public double getFactor(Immunizable person, VirusStrain strain, EpisimPerson.DiseaseStatus target, int day) {
				return 1.0;
			}

			@Override
			public double getInfectivityFactor(Immunizable infector, VirusStrain strain, int day) {
				return 1.0;
			}
		};
	}

	public static EpisimPerson createPerson() {
		return new EpisimPerson(Id.createPersonId(ID.getAndIncrement()), new AttributesImpl(), reporting);
	}

	/**
	 * Create person with vaccinable status.
	 */
	public static EpisimPerson createPerson(boolean vaccinable, int age) {
		Attributes attr = new AttributesImpl();
		attr.putAttribute("age", age);

		EpisimPerson p = new EpisimPerson(Id.createPersonId(ID.getAndIncrement()), attr, reporting);
		p.setVaccinable(vaccinable);
		return p;
	}

	/**
	 * Helper method to infect a person.
	 */
	public static EpisimPerson infectPerson(EpisimPerson p, VirusStrain strain, double now) {
		p.possibleInfection(new EpisimInfectionEvent(now, p.getPersonId(), p.getPersonId(), null, "undefined", 1, strain, 1.0, -1, -1, p.getNumVaccinations()));
		p.checkInfection();
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
	public static EpisimReporting.InfectionReport createReport(LocalDate date, long day, int showingSymptoms) {
		EpisimReporting.InfectionReport report = new EpisimReporting.InfectionReport("test", 0, date.toString(), day);

		report.nSusceptible = 100_000;
		report.nShowingSymptomsCumulative = showingSymptoms;

		return report;
	}

	/**
	 * Report with incidence per 100k inhabitants.
	 */
	public static EpisimReporting.InfectionReport createReportHospital(LocalDate date, long day, int hospital) {
		EpisimReporting.InfectionReport report = new EpisimReporting.InfectionReport("test", 0, date.toString(), day);

		report.nSusceptible = 100_000 - hospital;
		report.nSeriouslySick = hospital;

		return report;
	}

	/**
	 * Writes a config file with the given modules, as a user would write it, and loads it into the given config groups.
	 * Use this for content that {@link ConfigUtils#writeConfig(Config, String)} can not produce, e.g. invalid values.
	 *
	 * @param modules xml of the modules, see {@link #xmlModule(String, String...)}
	 */
	public static Config loadConfigXml(String modules, ConfigGroup... groups) throws IOException {
		File tmp = File.createTempFile("matsim", "config.xml");
		tmp.deleteOnExit();

		Files.writeString(tmp.toPath(), String.join("\n",
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
				"<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v2.dtd\">",
				"<config>",
				modules,
				"</config>"));

		return ConfigUtils.loadConfig(tmp.toString(), groups);
	}

	/**
	 * Xml of a config module.
	 */
	public static String xmlModule(String name, String... content) {
		return "<module name=\"" + name + "\" >\n" + String.join("\n", content) + "\n</module>";
	}

	/**
	 * Xml of a parameter set.
	 */
	public static String xmlSet(String type, String... content) {
		return "<parameterset type=\"" + type + "\" >\n" + String.join("\n", content) + "\n</parameterset>";
	}

	/**
	 * Xml of a single parameter.
	 */
	public static String xmlParam(String name, String value) {
		return "<param name=\"" + name + "\" value=\"" + value + "\" />";
	}

}
