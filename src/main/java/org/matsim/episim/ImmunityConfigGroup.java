package org.matsim.episim;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.model.ImmunityEvent;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.time.LocalDate;
import java.util.NavigableMap;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * How much protection an immunity event gives, over time, against which disease status and which strain.
 *
 * <p>One {@link SourceParams} per source of immunity. A source is an {@link ImmunityEvent}: an infection is
 * described by the {@link VirusStrain} that caused it, a product &mdash; a vaccine, a monoclonal antibody such as
 * nirsevimab, a maternal vaccination &mdash; by its {@link VaccinationType}. Which of the two is stated explicitly
 * with {@code sourceKind}, because strains and vaccination types live in separate registries and a name may exist
 * in both. The Java class of a product is still called {@code VaccinationType}, although not every product is a
 * vaccine.</p>
 *
 * <p>Each source holds one {@link ProtectionParams} per target disease status ({@code against}) and target strain:
 * a {@link ProtectionCurve} of protection over the days since the event. Protection against strains of
 * <i>other</i> pathogens is not listed pair by pair but given once by {@code otherPathogensProtection}.</p>
 *
 * <p>{@code model} decides whether this group is read at all. With {@link Model#legacyCovid}, the default, the
 * immunity model is the legacy implementation the scenario module binds, which reads the vaccination curves and
 * antibody levels calibrated for SARS-CoV-2; this group is then ignored. With {@link Model#explicit} the new
 * immunity model reads this group, and it is checked strictly before the simulation starts.</p>
 *
 * <p>This group only describes protection. Who receives a product and when stays in {@link VaccinationConfigGroup}
 * and the vaccination model; the protection curves of {@link VaccinationConfigGroup} are not read by the immunity
 * model that uses this group.</p>
 */
public final class ImmunityConfigGroup extends ReflectiveConfigGroup {

	public static final String GROUPNAME = "immunity";

	private static final Logger log = LogManager.getLogger(ImmunityConfigGroup.class);

	/**
	 * Disease statuses a person can be protected against, in the order of the course of a disease.
	 */
	public static final Set<DiseaseStatus> SUPPORTED_TARGETS = Collections.unmodifiableSet(EnumSet.of(
		DiseaseStatus.infectedButNotContagious, DiseaseStatus.showingSymptoms,
		DiseaseStatus.seriouslySick, DiseaseStatus.critical));

	private static final String MODEL = "model";
	private static final String OTHER_PATHOGENS_PROTECTION = "otherPathogensProtection";
	private static final String COMBINATOR = "combinator";

	/**
	 * Which immunity model a run uses.
	 */
	public enum Model {
		/**
		 * The legacy implementation bound by the scenario module, calibrated for SARS-CoV-2. This group is not read.
		 */
		legacyCovid,
		/**
		 * The immunity model that reads this group; the group is checked strictly.
		 */
		explicit
	}

	/**
	 * How the protection of several immunity events of one person is reduced to one.
	 */
	public enum Combinator {
		/**
		 * The strongest single event wins; repeated events do not add up.
		 */
		min,
		/**
		 * Remaining risks multiply, so repeated events add up.
		 */
		product
	}

	private Model model = Model.legacyCovid;

	/**
	 * Protection against strains of pathogens a source does not list; {@code null} while not set.
	 */
	private Double otherPathogensProtection;

	private Combinator combinator = Combinator.min;

	private final Map<ImmunityEvent, SourceParams> sources = new LinkedHashMap<>();

	/**
	 * An empty group: no source of immunity and {@code otherPathogensProtection} not set.
	 */
	public ImmunityConfigGroup() {
		super(GROUPNAME);
	}

	public Model getModel() {
		return model;
	}

	public void setModel(Model model) {
		this.model = Objects.requireNonNull(model, "Immunity model must not be null");
	}

	@StringGetter(MODEL)
	String getModelString() {
		return model.name();
	}

	@StringSetter(MODEL)
	void setModelString(String value) {
		setModel(parseEnum(Model.class, MODEL, value));
	}

	/**
	 * Protection against strains of pathogens a source does not list, if set.
	 */
	public OptionalDouble getOtherPathogensProtection() {
		return otherPathogensProtection == null ? OptionalDouble.empty() : OptionalDouble.of(otherPathogensProtection);
	}

	public void setOtherPathogensProtection(double protection) {
		if (!(protection >= 0 && protection <= 1))
			throw new IllegalArgumentException("'" + OTHER_PATHOGENS_PROTECTION + "' has to be between 0 and 1, got " + protection);
		this.otherPathogensProtection = protection;
	}

	@StringGetter(OTHER_PATHOGENS_PROTECTION)
	String getOtherPathogensProtectionString() {
		return otherPathogensProtection == null ? null : otherPathogensProtection.toString();
	}

	@StringSetter(OTHER_PATHOGENS_PROTECTION)
	void setOtherPathogensProtectionString(String value) {
		if (value == null || value.isBlank())
			otherPathogensProtection = null;
		else
			setOtherPathogensProtection(Double.parseDouble(value.trim()));
	}

	public Combinator getCombinator() {
		return combinator;
	}

	public void setCombinator(Combinator combinator) {
		this.combinator = Objects.requireNonNull(combinator, "Combinator must not be null");
	}

	@StringGetter(COMBINATOR)
	String getCombinatorString() {
		return combinator.name();
	}

	@StringSetter(COMBINATOR)
	void setCombinatorString(String value) {
		setCombinator(parseEnum(Combinator.class, COMBINATOR, value));
	}

	/**
	 * Parameters of the immunity gained from an infection with this strain, added if missing.
	 */
	public SourceParams getOrAddSource(VirusStrain strain) {
		return getOrAddSource(strain, SourceKind.infection);
	}

	/**
	 * Parameters of the immunity given by this product, added if missing.
	 */
	public SourceParams getOrAddSource(VaccinationType product) {
		return getOrAddSource(product, SourceKind.product);
	}

	private SourceParams getOrAddSource(ImmunityEvent event, SourceKind kind) {
		SourceParams existing = sources.get(event);
		if (existing != null)
			return existing;

		SourceParams p = new SourceParams();
		p.setSource(event.toString(), kind);
		addParameterSet(p);
		return p;
	}

	/**
	 * Parameters of a source, or {@code null} if it is not configured.
	 */
	public SourceParams getSource(ImmunityEvent event) {
		return sources.get(event);
	}

	public Collection<SourceParams> getSources() {
		return Collections.unmodifiableCollection(sources.values());
	}

	@Override
	public ConfigGroup createParameterSet(String type) {
		if (SourceParams.SET_TYPE.equals(type))
			return new SourceParams();

		throw new IllegalArgumentException("Unknown parameter set type: " + type);
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		if (!SourceParams.SET_TYPE.equals(set.getName()))
			throw new IllegalStateException("Unknown parameter set type: " + set.getName());

		SourceParams p = (SourceParams) set;
		ImmunityEvent event = p.getSource();
		if (sources.containsKey(event))
			throw new IllegalArgumentException("Source of immunity '" + event + "' is configured twice in config group '"
				+ GROUPNAME + "'");

		sources.put(event, p);
		super.addParameterSet(set);
	}

	/**
	 * Checks the group before the simulation starts. Only with {@link Model#explicit}: with {@link Model#legacyCovid}
	 * the group is not read, and settings that would be ignored are reported as a warning.
	 */
	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);

		if (model == Model.legacyCovid) {
			if (!sources.isEmpty() || otherPathogensProtection != null)
				log.warn("Config group '{}' configures immunity, but '{}' is {}: the legacy immunity model does not read "
					+ "this group, so these settings have no effect.", GROUPNAME, MODEL, model);
			return;
		}

		List<String> problems = new ArrayList<>();
		for (SourceParams source : sources.values())
			source.collectProblems(problems);

		// coverage needs complete sets; report incomplete ones first
		if (problems.isEmpty())
			checkCoverage(config, problems);

		if (!problems.isEmpty())
			throw new IllegalStateException(String.join("\n", problems));
	}

	/**
	 * Checks that the sources cover what can happen in the run.
	 *
	 * <p>Only <i>circulating</i> strains are required: configured in {@code virusStrains} and imported with a
	 * positive number of infections in {@code episim}. The constructor of {@link VirusStrainConfigGroup} always adds
	 * SARS-CoV-2, so requiring every configured strain would force a placeholder for it into every other run. A strain
	 * that enters a run without being imported, through a custom initial infection handler, is not seen here; the
	 * immunity model fails on its first infection instead.</p>
	 *
	 * <p>Products handed out by the vaccination programme are not checked here: which vaccination model is bound is not
	 * part of the configuration. The immunity model fails on the first vaccination with a product that has no source.</p>
	 */
	private void checkCoverage(Config config, List<String> problems) {

		VirusStrainConfigGroup strainConfig = VirusStrainConfigGroup.moduleOrDefault(config, "virusStrains",
			VirusStrainConfigGroup.class, VirusStrainConfigGroup::new);
		EpisimConfigGroup episimConfig = VirusStrainConfigGroup.moduleOrDefault(config, "episim",
			EpisimConfigGroup.class, EpisimConfigGroup::new);
		if (strainConfig == null || episimConfig == null)
			return;

		Set<VirusStrain> configured = strainConfig.getConfiguredStrains();
		Set<VirusStrain> circulating = new LinkedHashSet<>();
		for (Map.Entry<VirusStrain, NavigableMap<LocalDate, Integer>> e : episimConfig.getInfections_pers_per_day().entrySet()) {
			if (configured.contains(e.getKey()) && e.getValue().values().stream().anyMatch(n -> n > 0))
				circulating.add(e.getKey());
		}

		checkStrainNames(configured, problems);
		checkInfectionSources(circulating, problems);
		checkTargets(circulating, problems);
	}

	/**
	 * Names that do not resolve to a configured strain are most likely typos.
	 */
	private void checkStrainNames(Set<VirusStrain> configured, List<String> problems) {
		for (SourceParams source : sources.values()) {
			if (source.getSourceKind() == SourceKind.infection && !configured.contains((VirusStrain) source.getSource()))
				problems.add("Source of immunity '" + source.getSource() + "' is an infection with a strain that has no '"
					+ "strainParams' in config group 'virusStrains'. Check the name or add the strain.");
			for (ProtectionParams p : source.getProtections()) {
				if (!configured.contains(p.getStrain()))
					problems.add("Source of immunity '" + source.getSource() + "' lists protection for strain '" + p.getStrain()
						+ "', which has no 'strainParams' in config group 'virusStrains'. Check the name or add the strain.");
			}
		}
	}

	/**
	 * Every circulating strain leaves immunity behind, which has to be described, even if it is none.
	 */
	private void checkInfectionSources(Set<VirusStrain> circulating, List<String> problems) {
		for (VirusStrain strain : circulating) {
			SourceParams source = sources.get(strain);
			if (source == null || source.getSourceKind() != SourceKind.infection)
				problems.add("Strain '" + strain + "' circulates, but there is no '" + SourceParams.SET_TYPE + "' for an infection "
					+ "with it. Describe the immunity it leaves behind, with curves '0>0.0' if there is none.");
		}
	}

	/**
	 * A source covers its own pathogen (for an infection) and every pathogen it lists curves for. For circulating strains
	 * of a covered pathogen every target has to be listed; other strains need {@link #OTHER_PATHOGENS_PROTECTION}.
	 */
	private void checkTargets(Set<VirusStrain> circulating, List<String> problems) {
		Set<String> uncovered = new TreeSet<>();
		for (SourceParams source : sources.values()) {

			Set<Pathogen> covered = new HashSet<>();
			if (source.getSourceKind() == SourceKind.infection)
				covered.add(((VirusStrain) source.getSource()).getPathogen());
			for (ProtectionParams p : source.getProtections())
				covered.add(p.getStrain().getPathogen());

			for (VirusStrain target : circulating) {
				if (!covered.contains(target.getPathogen())) {
					uncovered.add(source.getSource() + " -> " + target);
					continue;
				}
				for (DiseaseStatus against : SUPPORTED_TARGETS) {
					if (source.getProtection(against, target) == null)
						problems.add("Source of immunity '" + source.getSource() + "' covers pathogen '" + target.getPathogen()
							+ "' but has no protection against " + against + " for its strain '" + target
							+ "'. Every target has to be listed; write curve '0>0.0' for no protection.");
				}
			}
		}

		if (!uncovered.isEmpty() && otherPathogensProtection == null)
			problems.add("Sources give no curves for strains of other pathogens " + uncovered + ", and '"
				+ OTHER_PATHOGENS_PROTECTION + "' is not set. Set it explicitly, e.g. to 0.0 for no cross-protection.");
	}

	/**
	 * Reads an enum value and names the allowed values if it is unknown, instead of the bare "No enum constant".
	 */
	private static <E extends Enum<E>> E parseEnum(Class<E> type, String param, String value) {
		try {
			return Enum.valueOf(type, value.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("'" + param + "' has no value '" + value.trim() + "'; allowed are "
				+ java.util.Arrays.toString(type.getEnumConstants()), e);
		}
	}

	/**
	 * Whether a source of immunity is an infection or a product.
	 */
	public enum SourceKind {
		infection, product
	}

	/**
	 * Protection given by one source of immunity.
	 */
	public static final class SourceParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "immunitySource";

		private static final String SOURCE = "source";
		private static final String SOURCE_KIND = "sourceKind";

		private String sourceName;
		private SourceKind sourceKind;

		/**
		 * One set per target disease status and target strain.
		 */
		private final Map<DiseaseStatus, Map<VirusStrain, ProtectionParams>> protections = new LinkedHashMap<>();

		SourceParams() {
			super(SET_TYPE);
		}

		void setSource(String name, SourceKind kind) {
			this.sourceName = name;
			this.sourceKind = kind;
		}

		/**
		 * The immunity event this source describes.
		 *
		 * @throws IllegalStateException if {@code source} or {@code sourceKind} is not set
		 */
		public ImmunityEvent getSource() {
			if (sourceName == null || sourceKind == null)
				throw new IllegalStateException("A '" + SET_TYPE + "' in config group '" + GROUPNAME + "' needs both '"
					+ SOURCE + "' and '" + SOURCE_KIND + "', got source=" + sourceName + ", sourceKind=" + sourceKind);

			return sourceKind == SourceKind.infection ? VirusStrain.of(sourceName) : VaccinationType.of(sourceName);
		}

		public SourceKind getSourceKind() {
			return sourceKind;
		}

		@StringGetter(SOURCE)
		String getSourceName() {
			return sourceName;
		}

		@StringSetter(SOURCE)
		void setSourceName(String name) {
			this.sourceName = name == null || name.isBlank() ? null : name.trim();
		}

		@StringGetter(SOURCE_KIND)
		String getSourceKindString() {
			return sourceKind == null ? null : sourceKind.name();
		}

		@StringSetter(SOURCE_KIND)
		void setSourceKindString(String kind) {
			this.sourceKind = kind == null || kind.isBlank() ? null : parseEnum(SourceKind.class, SOURCE_KIND, kind);
		}

		/**
		 * Protection against {@code against} for {@code strain}, added with the given curve or replacing its curve.
		 */
		public ProtectionParams setProtection(DiseaseStatus against, VirusStrain strain, ProtectionCurve curve) {
			ProtectionParams existing = find(against, strain);
			if (existing != null) {
				existing.setCurve(curve);
				return existing;
			}

			ProtectionParams p = new ProtectionParams();
			p.setAgainst(against);
			p.setStrain(strain);
			p.setCurve(curve);
			addParameterSet(p);
			return p;
		}

		/**
		 * Curve for the given target, or {@code null} if this source does not list it.
		 */
		public ProtectionCurve getProtection(DiseaseStatus against, VirusStrain strain) {
			ProtectionParams p = find(against, strain);
			return p == null ? null : p.getCurve();
		}

		/**
		 * All protections of this source, in the order they were added.
		 */
		public List<ProtectionParams> getProtections() {
			List<ProtectionParams> all = new ArrayList<>();
			protections.values().forEach(byStrain -> all.addAll(byStrain.values()));
			return Collections.unmodifiableList(all);
		}

		private ProtectionParams find(DiseaseStatus against, VirusStrain strain) {
			Map<VirusStrain, ProtectionParams> byStrain = protections.get(against);
			return byStrain == null ? null : byStrain.get(strain);
		}

		@Override
		public ConfigGroup createParameterSet(String type) {
			if (ProtectionParams.SET_TYPE.equals(type))
				return new ProtectionParams();

			throw new IllegalArgumentException("Unknown parameter set type: " + type);
		}

		@Override
		public void addParameterSet(final ConfigGroup set) {
			if (!ProtectionParams.SET_TYPE.equals(set.getName()))
				throw new IllegalStateException("Unknown parameter set type: " + set.getName());

			ProtectionParams p = (ProtectionParams) set;
			p.requireTarget();
			if (find(p.getAgainst(), p.getStrain()) != null)
				throw new IllegalArgumentException("Source of immunity '" + sourceName + "' lists protection against "
					+ p.getAgainst() + " for strain '" + p.getStrain() + "' twice");

			protections.computeIfAbsent(p.getAgainst(), k -> new LinkedHashMap<>()).put(p.getStrain(), p);
			super.addParameterSet(set);
		}

		private void collectProblems(List<String> problems) {
			if (sourceName == null || sourceKind == null) {
				problems.add("A '" + SET_TYPE + "' needs both '" + SOURCE + "' and '" + SOURCE_KIND + "', got source="
					+ sourceName + ", sourceKind=" + sourceKind);
				return;
			}

			for (ProtectionParams p : getProtections()) {
				if (p.getCurve() == null)
					problems.add("Source of immunity '" + sourceName + "' has no '" + ProtectionParams.CURVE
						+ "' for protection against " + p.getAgainst() + " for strain '" + p.getStrain() + "'");
			}
		}
	}

	/**
	 * Protection a source gives against one target disease status for one target strain.
	 */
	public static final class ProtectionParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "protection";

		private static final String AGAINST = "against";
		private static final String STRAIN = "strain";
		private static final String CURVE = "curve";

		private DiseaseStatus against;
		private String strainName;
		private ProtectionCurve curve;

		ProtectionParams() {
			super(SET_TYPE);
		}

		public DiseaseStatus getAgainst() {
			return against;
		}

		/**
		 * Target disease status of this protection.
		 *
		 * @throws IllegalArgumentException if the status is not one a person can be protected against
		 */
		public void setAgainst(DiseaseStatus against) {
			if (!SUPPORTED_TARGETS.contains(against))
				throw new IllegalArgumentException("Protection against '" + against + "' is not supported; supported are "
					+ SUPPORTED_TARGETS);
			this.against = against;
		}

		@StringGetter(AGAINST)
		String getAgainstString() {
			return against == null ? null : against.name();
		}

		@StringSetter(AGAINST)
		void setAgainstString(String value) {
			setAgainst(parseEnum(DiseaseStatus.class, AGAINST, value));
		}

		/**
		 * Strain the protection is against. Resolved by name on access, so that a strain whose pathogen is declared
		 * by a config group read later still ends up with the right pathogen.
		 */
		public VirusStrain getStrain() {
			return strainName == null ? null : VirusStrain.of(strainName);
		}

		public void setStrain(VirusStrain strain) {
			this.strainName = strain.getVirusStrainName();
		}

		@StringGetter(STRAIN)
		String getStrainName() {
			return strainName;
		}

		@StringSetter(STRAIN)
		void setStrainName(String name) {
			this.strainName = name == null || name.isBlank() ? null : name.trim();
		}

		public ProtectionCurve getCurve() {
			return curve;
		}

		public void setCurve(ProtectionCurve curve) {
			this.curve = Objects.requireNonNull(curve, "Protection curve must not be null");
		}

		@StringGetter(CURVE)
		String getCurveString() {
			return curve == null ? null : curve.toString();
		}

		@StringSetter(CURVE)
		void setCurveString(String value) {
			try {
				setCurve(ProtectionCurve.parse(value));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Protection against " + against + " for strain '" + strainName
					+ "': " + e.getMessage(), e);
			}
		}

		private void requireTarget() {
			if (against == null || strainName == null)
				throw new IllegalStateException("A '" + SET_TYPE + "' needs both '" + AGAINST + "' and '" + STRAIN
					+ "', got against=" + against + ", strain=" + strainName);
		}
	}
}
