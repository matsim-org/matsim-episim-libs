package org.matsim.episim;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.ContactTransmissionConfigGroup.ContactPairParams;
import org.matsim.episim.ContactTransmissionConfigGroup.Resolver;
import org.matsim.episim.model.ContactTransmissionType;
import org.matsim.episim.model.TransmissionWeights;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContactTransmissionConfigGroupTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void config() throws IOException {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.setAgeBands(Arrays.asList(0, 6, 18, 30));
		group.setDefaultTransmissionWeights(TransmissionWeights.parse(
				"respiratory=0.7;directContact=0.2"));
		group.getOrAddContactPair("work", "home").setTransmissionWeights(
				TransmissionWeights.parse("respiratory=0.4;directContact=0.3"));
		group.getOrAddContactPair("school", "leisure").setFile("age-matrix.csv");

		Config config = ConfigUtils.createConfig(group);
		File configFile = temporaryFolder.newFile("config.xml");
		ConfigUtils.writeConfig(config, configFile.toString());

		ContactTransmissionConfigGroup copy = new ContactTransmissionConfigGroup();
		ConfigUtils.loadConfig(configFile.toString(), copy);

		assertThat(copy.getAgeBands()).containsExactly(0, 6, 18, 30);
		assertThat(copy.getDefaultTransmissionWeights().asMap())
				.containsEntry(ContactTransmissionType.RESPIRATORY, 0.7)
				.containsEntry(ContactTransmissionType.DIRECT_CONTACT, 0.2);
		assertThat(copy.getContactPairs()).hasSize(2);
		assertThat(copy.getContactPair("home", "work").getTransmissionWeights().toToken())
				.isEqualTo("respiratory=0.4;directContact=0.3");
		assertThat(copy.getContactPair("leisure", "school").getFile()).isEqualTo("age-matrix.csv");
	}

	@Test
	public void duplicateContactPairReplacesPrevious() {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();

		for (String weights : List.of("respiratory=0.4", "respiratory=0.9")) {
			ContactPairParams pair = (ContactPairParams) group.createParameterSet(ContactPairParams.SET_TYPE);
			pair.setActivityA("work");
			pair.setActivityB("home");
			pair.setTransmissionWeights(TransmissionWeights.parse(weights));
			group.addParameterSet(pair);
		}

		assertThat(group.getParameterSets(ContactPairParams.SET_TYPE)).hasSize(1);
		assertThat(group.getContactPair("home", "work").getTransmissionWeights().getRespiratory()).isEqualTo(0.9);
	}

	@Test
	public void resolverPriority() throws IOException {
		File matrix = writeCsv("matrix.csv", completeMatrix(Arrays.asList(0, 6, 15, 20, 30),
				"# route weights by age band\n"
						+ "6,30,0.55,0.35\n"
						+ "15,20,0.65,0.30\n"));
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.setAgeBands(Arrays.asList(0, 6, 15, 20, 30));
		group.getOrAddContactPair("home", "work").setTransmissionWeights(
				TransmissionWeights.parse("respiratory=0.25;DIRECT_CONTACT=0.5"));
		group.getOrAddContactPair("school", "leisure").setFile(matrix.toString());

		Resolver resolver = group.createResolver();

		TransmissionWeights defaults = resolver.resolve("unknown", "home", 10, 20);
		assertThat(defaults.get(ContactTransmissionType.RESPIRATORY)).isEqualTo(1.0);
		assertThat(defaults.sum()).isEqualTo(1.0);

		TransmissionWeights inline = resolver.resolve("work", "home", 99, 1);
		assertThat(inline.get(ContactTransmissionType.RESPIRATORY)).isEqualTo(0.25);
		assertThat(inline.get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.5);

		TransmissionWeights covered = resolver.resolve("leisure", "school", 33, 8);
		assertThat(covered.get(ContactTransmissionType.RESPIRATORY)).isEqualTo(0.55);
		assertThat(covered.get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.35);

		TransmissionWeights zeroCell = resolver.resolve("school", "leisure", 16, 34);
		assertThat(zeroCell.isZero()).isTrue();
		assertThat(resolver.bandIndex(0)).isEqualTo(0);
		assertThat(resolver.bandIndex(5)).isEqualTo(0);
		assertThat(resolver.bandIndex(6)).isEqualTo(1);
		assertThat(resolver.bandIndex(19)).isEqualTo(2);
		assertThat(resolver.bandIndex(100)).isEqualTo(4);
	}

	@Test
	public void relativeFileIsResolvedAgainstContext() throws IOException {
		File matrix = writeCsv("relative-matrix.csv",
				"ageA,ageB,respiratory,directContact\n"
						+ "0,0,0.1,0.9\n");
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.getOrAddContactPair("edu", "edu").setFile(matrix.getName());
		group.setContext(new File(matrix.getParentFile(), "config.xml").toURI().toURL());

		Resolver resolver = group.createResolver();

		assertThat(resolver.resolve("edu", "edu", 5, 5).getDirectContact()).isEqualTo(0.9);
	}

	@Test
	public void incompleteMatrixIsRejected() throws IOException {
		File matrix = writeCsv("sparse.csv", "ageA,ageB,respiratory\n0,0,1.0\n0,20,1.0\n");
		ContactTransmissionConfigGroup group = fileGroup(matrix);

		assertThatThrownBy(group::createResolver)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("(20,20)");
	}

	@Test
	public void unknownAgeUsesDefaultWeights() throws IOException {
		File matrix = writeCsv("ages.csv", "ageA,ageB,respiratory,directContact\n0,0,0.0,0.0\n0,20,0.0,0.0\n20,20,1.0,0.9\n");
		ContactTransmissionConfigGroup group = fileGroup(matrix);
		group.setDefaultTransmissionWeights(TransmissionWeights.parse("respiratory=0.5;directContact=0.1"));

		Resolver resolver = group.createResolver();

		assertThat(resolver.resolve("home", "work", 30, 30).getDirectContact()).isEqualTo(0.9);
		assertThat(resolver.resolve("home", "work", -1, 30)).isSameAs(group.getDefaultTransmissionWeights());
		assertThat(resolver.resolve("home", "work", 30, -1)).isSameAs(group.getDefaultTransmissionWeights());
	}

	@Test
	public void contactPairsMatchContainerNamesByPrefix() {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.getOrAddContactPair("edu", "home").setTransmissionWeights(
				TransmissionWeights.parse("respiratory=1.0;directContact=0.3"));

		Resolver resolver = group.createResolver();

		assertThat(resolver.resolve("educ_primary", "home", 10, 40).get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.3);
		assertThat(resolver.resolve("home", "educ_kiga", 40, 4).get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.3);
		// not covered by the pair -> default weights
		assertThat(resolver.resolve("work", "home", 40, 40).get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.0);
	}

	@Test
	public void mostSpecificContactPairWins() {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.getOrAddContactPair("leis", "work").setTransmissionWeights(
				TransmissionWeights.parse("respiratory=1.0;directContact=0.1"));
		group.getOrAddContactPair("leisPublic", "work").setTransmissionWeights(
				TransmissionWeights.parse("respiratory=1.0;directContact=0.2"));
		group.getOrAddContactPair("shop", "work").setForbidden(true);
		group.getOrAddContactPair("shop_daily", "work").setTransmissionWeights(
				TransmissionWeights.parse("respiratory=1.0;directContact=0.5"));

		Resolver resolver = group.createResolver();

		assertThat(resolver.resolve("leisPublic", "work", 30, 30).get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.2);
		assertThat(resolver.resolve("work", "leisPrivate", 30, 30).get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.1);
		assertThat(resolver.resolve("leisure", "work", 30, 30).get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.1);

		// a broad forbidden pair blocks everything it covers ...
		assertThat(resolver.isContactAllowed("shop_other", "work")).isFalse();
		assertThat(resolver.resolve("shop_other", "work", 30, 30)).isSameAs(TransmissionWeights.ZERO);
		// ... unless a more specific pair overrides it
		assertThat(resolver.isContactAllowed("shop_daily", "work")).isTrue();
		assertThat(resolver.resolve("work", "shop_daily", 30, 30).get(ContactTransmissionType.DIRECT_CONTACT)).isEqualTo(0.5);
	}

	@Test
	public void reportsContactPairsThatMatchNoContainer() {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.getOrAddContactPair("edu", "home").setTransmissionWeights(TransmissionWeights.parse("respiratory=1.0"));
		group.getOrAddContactPair("school", "leisure").setTransmissionWeights(TransmissionWeights.parse("respiratory=1.0"));

		assertThat(group.unmatchedContactPairs(Arrays.asList("home", "educ_primary", "leisure", "work")))
				.containsExactly("leisure/school");
	}

	@Test
	public void validatesNonAscendingAgeBands() {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.setAgeBands(Arrays.asList(0, 20, 10));

		assertThatThrownBy(group::createResolver)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("10");
	}

	@Test
	public void csvWeightsAreNotNormalised() throws IOException {
		// routes are independent, additive channels -> a per-cell weight sum above 1 is allowed
		File matrix = writeCsv("unnormalised.csv",
				"ageA,ageB,respiratory,directContact\n0,0,1.0,0.5\n0,20,1.0,0.5\n20,20,1.0,0.5\n");
		ContactTransmissionConfigGroup group = fileGroup(matrix);

		Resolver resolver = group.createResolver();

		TransmissionWeights w = resolver.resolve("home", "work", 5, 10);
		assertThat(w.getRespiratory()).isEqualTo(1.0);
		assertThat(w.getDirectContact()).isEqualTo(0.5);
		assertThat(w.sum()).isEqualTo(1.5);
	}

	@Test
	public void validatesExactlyOnePairSource() throws IOException {
		File matrix = writeCsv("both.csv", "ageA,ageB,respiratory\n0,0,1.0\n");
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		ContactPairParams pair = group.getOrAddContactPair("home", "work");
		pair.setFile(matrix.toString());
		pair.setTransmissionWeights(TransmissionWeights.parse("respiratory=1.0"));

		assertThatThrownBy(group::createResolver)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("exactly one");
	}

	@Test
	public void validatesCsvAgesAreDeclaredBands() throws IOException {
		File matrix = writeCsv("invalid-age.csv", "ageA,ageB,respiratory\n6,20,1.0\n");
		ContactTransmissionConfigGroup group = fileGroup(matrix);

		assertThatThrownBy(group::createResolver)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("6");
	}

	@Test
	public void defaultWhitelistReproducesHardcodedRules() {
		Resolver resolver = new ContactTransmissionConfigGroup().createResolver();

		// home only mixes with home / leisure / work
		assertThat(resolver.isContactAllowed("home", "home")).isTrue();
		assertThat(resolver.isContactAllowed("home", "leisure")).isTrue();
		assertThat(resolver.isContactAllowed("home", "leisPrivate")).isTrue();
		assertThat(resolver.isContactAllowed("home", "work")).isTrue();
		assertThat(resolver.isContactAllowed("home", "educ_primary")).isFalse();
		assertThat(resolver.isContactAllowed("home", "shop")).isFalse();

		// education only mixes with education / work
		assertThat(resolver.isContactAllowed("educ_kiga", "educ_higher")).isTrue();
		assertThat(resolver.isContactAllowed("educ_primary", "work")).isTrue();
		assertThat(resolver.isContactAllowed("educ_primary", "leisure")).isFalse();
		assertThat(resolver.isContactAllowed("edu", "leis")).isFalse();

		// unconstrained activities may always meet
		assertThat(resolver.isContactAllowed("work", "shop")).isTrue();
		assertThat(resolver.isContactAllowed("shop", "leisure")).isTrue();
	}

	@Test
	public void customContactGroupOverridesDefault() {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.getOrAddContactGroup("home").setAllowedPartners(Arrays.asList("home"));

		Resolver resolver = group.createResolver();

		assertThat(resolver.isContactAllowed("home", "work")).isFalse();
		assertThat(resolver.isContactAllowed("home", "home")).isTrue();
	}

	@Test
	public void forbiddenPairBlocksContactAndResolvesToZero() {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.getOrAddContactPair("work", "leisure").setForbidden(true);

		Resolver resolver = group.createResolver();

		assertThat(resolver.isContactAllowed("leisure", "work")).isFalse();
		assertThat(resolver.resolve("work", "leisure", 20, 40)).isSameAs(TransmissionWeights.ZERO);
		// unaffected pair still allowed
		assertThat(resolver.isContactAllowed("work", "shop")).isTrue();
	}

	@Test
	public void forbiddenPairRejectsWeights() {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		ContactPairParams pair = group.getOrAddContactPair("home", "work");
		pair.setForbidden(true);
		pair.setTransmissionWeights(TransmissionWeights.parse("respiratory=1.0"));

		assertThatThrownBy(group::createResolver)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not set transmissionWeights");
	}

	@Test
	public void serializesContactGroupsAndForbiddenFlag() throws IOException {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.getOrAddContactGroup("shop").setAllowedPartners(Arrays.asList("shop", "work"));
		group.getOrAddContactPair("home", "errands").setForbidden(true);

		Config config = ConfigUtils.createConfig(group);
		File configFile = temporaryFolder.newFile("ct-config.xml");
		ConfigUtils.writeConfig(config, configFile.toString());

		ContactTransmissionConfigGroup copy = new ContactTransmissionConfigGroup();
		ConfigUtils.loadConfig(configFile.toString(), copy);

		// defaults are not duplicated on reload
		assertThat(copy.getContactGroups()).hasSize(3);
		assertThat(copy.hasContactGroup("home")).isTrue();
		assertThat(copy.getOrAddContactGroup("shop").getAllowedPartners()).containsExactly("shop", "work");
		assertThat(copy.getContactPair("errands", "home").isForbidden()).isTrue();

		Resolver resolver = copy.createResolver();
		assertThat(resolver.isContactAllowed("home", "errands")).isFalse();
		assertThat(resolver.isContactAllowed("shop", "leisure")).isFalse();
	}

	private ContactTransmissionConfigGroup fileGroup(File matrix) {
		ContactTransmissionConfigGroup group = new ContactTransmissionConfigGroup();
		group.setAgeBands(Arrays.asList(0, 20));
		group.getOrAddContactPair("home", "work").setFile(matrix.toString());
		return group;
	}

	/**
	 * CSV header plus the given rows, filled up with zero-weight rows for every other pair of age bands.
	 */
	private static String completeMatrix(List<Integer> bands, String rows) {
		StringBuilder csv = new StringBuilder("ageA,ageB,respiratory,directContact\n").append(rows);
		for (int i = 0; i < bands.size(); i++) {
			for (int j = i; j < bands.size(); j++) {
				String cell = bands.get(i) + "," + bands.get(j) + ",";
				if (!rows.contains("\n" + cell) && !rows.startsWith(cell)) {
					csv.append(cell).append("0.0,0.0\n");
				}
			}
		}
		return csv.toString();
	}

	private File writeCsv(String name, String content) throws IOException {
		File file = temporaryFolder.newFile(name);
		Files.writeString(file.toPath(), content);
		return file;
	}
}
