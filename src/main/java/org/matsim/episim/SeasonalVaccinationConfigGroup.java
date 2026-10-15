package org.matsim.episim;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.VaccinationType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Yearly vaccination campaigns, such as the seasonal influenza vaccination, read by
 * {@link org.matsim.episim.model.vaccination.SeasonalVaccinationModel}.
 *
 * <p>One parameter set {@code season} per campaign, with the product that is given, the share of the agents of each age who are
 * vaccinated in the season, and the timing of the campaign. Without a season the model does nothing, which is the state of the
 * scenarios without vaccination.</p>
 */
public final class SeasonalVaccinationConfigGroup extends ReflectiveConfigGroup {

	public static final String GROUPNAME = "seasonalVaccination";

	private final Map<String, Season> seasons = new LinkedHashMap<>();

	/**
	 * A group without a campaign.
	 */
	public SeasonalVaccinationConfigGroup() {
		super(GROUPNAME);
	}

	@Override
	public ConfigGroup createParameterSet(String type) {
		if (Season.SET_TYPE.equals(type))
			return new Season();

		throw new IllegalArgumentException("Unknown parameter set type: " + type);
	}

	/** The campaigns, in the order of the config. */
	public Collection<Season> getSeasons() {
		return Collections.unmodifiableCollection(seasons.values());
	}

	/** A campaign, added to the group; the name of the season has to be new. */
	public Season addSeason(String name, VaccinationType product, Map<Integer, Double> shareByAge, NavigableMap<LocalDate, Double> timing) {
		Season season = new Season();
		season.setSeason(name);
		season.setProduct(product);
		season.setShareByAge(shareByAge);
		season.setTiming(timing);
		addParameterSet(season);
		return season;
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		if (!Season.SET_TYPE.equals(set.getName()))
			throw new IllegalStateException("Unknown parameter set type: " + set.getName());

		Season season = (Season) set;
		if (seasons.containsKey(season.getSeason()))
			throw new IllegalArgumentException("Season '" + season.getSeason() + "' is configured twice in config group '" + GROUPNAME + "'");

		seasons.put(season.getSeason(), season);
		super.addParameterSet(set);
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);

		List<String> problems = new ArrayList<>();
		List<Season> sorted = new ArrayList<>(seasons.values());
		for (Season season : sorted)
			season.collectProblems(problems);

		sorted.sort((a, b) -> a.firstDay().compareTo(b.firstDay()));
		for (int i = 1; i < sorted.size(); i++)
			if (!sorted.get(i).firstDay().isAfter(sorted.get(i - 1).lastDay()))
				problems.add("The campaigns of " + sorted.get(i - 1).getSeason() + " and " + sorted.get(i).getSeason() + " overlap");

		// every product needs the protection it gives and a neutral entry for the infection model
		ConfigGroup immunity = config.getModules().get(ImmunityConfigGroup.GROUPNAME);
		ConfigGroup vaccination = config.getModules().get(VaccinationConfigGroup.GROUPNAME);
		for (Season season : sorted) {
			if (season.getProduct() == null)
				continue;
			if (immunity instanceof ImmunityConfigGroup && ((ImmunityConfigGroup) immunity).getSource(season.getProduct()) == null)
				problems.add("The product '" + season.getProduct() + "' of season " + season.getSeason() + " has no source in config group '"
						+ ImmunityConfigGroup.GROUPNAME + "'");
			if (vaccination instanceof VaccinationConfigGroup && !((VaccinationConfigGroup) vaccination).hasParams(season.getProduct()))
				problems.add("The product '" + season.getProduct() + "' of season " + season.getSeason() + " has no entry in config group '"
						+ VaccinationConfigGroup.GROUPNAME + "', the infection model needs one");
		}

		if (!problems.isEmpty())
			throw new IllegalStateException("Config group '" + GROUPNAME + "':\n" + String.join("\n", problems));
	}

	/**
	 * One campaign: the product, who gets it, and when.
	 */
	public static final class Season extends ReflectiveConfigGroup {

		static final String SET_TYPE = "season";

		private static final String SEASON = "season";
		private static final String PRODUCT = "product";
		private static final String SHARE_BY_AGE = "shareByAge";
		private static final String TIMING = "timing";

		private String season = "";
		private String product = "";

		/** Share of the agents of an age who are vaccinated in the season, between the keys linear, the first and the last held. */
		private final NavigableMap<Integer, Double> shareByAge = new TreeMap<>();

		/** Cumulative share of the vaccinations of the season at the end of a day, between the dates linear. */
		private final NavigableMap<LocalDate, Double> timing = new TreeMap<>();

		Season() {
			super(SET_TYPE);
		}

		/** The season of the campaign, for example {@code 2022/23}. */
		@StringGetter(SEASON)
		public String getSeason() {
			return season;
		}

		@StringSetter(SEASON)
		public void setSeason(String season) {
			this.season = season == null ? "" : season.strip();
		}

		@StringGetter(PRODUCT)
		String getProductName() {
			return product;
		}

		@StringSetter(PRODUCT)
		void setProductName(String product) {
			this.product = product == null ? "" : product.strip();
		}

		/** The product the vaccinated agents get, {@code null} if it is not set. */
		public VaccinationType getProduct() {
			return product.isEmpty() ? null : VaccinationType.of(product);
		}

		public void setProduct(VaccinationType product) {
			this.product = product == null ? "" : product.toString();
		}

		@StringGetter(SHARE_BY_AGE)
		String getShareByAgeString() {
			StringBuilder text = new StringBuilder();
			shareByAge.forEach((age, share) -> text.append(text.length() > 0 ? ";" : "").append(age).append("=").append(share));
			return text.toString();
		}

		@StringSetter(SHARE_BY_AGE)
		void setShareByAgeString(String text) {
			Map<Integer, Double> parsed = new TreeMap<>();
			if (text != null && !text.isBlank()) {
				for (String entry : text.split(";")) {
					String[] parts = entry.split("=");
					if (parts.length != 2)
						throw new IllegalArgumentException("'" + entry + "' in " + SHARE_BY_AGE + " is not of the form age=share");
					parsed.put(Integer.parseInt(parts[0].strip()), Double.parseDouble(parts[1].strip()));
				}
			}
			setShareByAge(parsed);
		}

		public Map<Integer, Double> getShareByAge() {
			return Collections.unmodifiableNavigableMap(shareByAge);
		}

		/**
		 * Sets the share of the agents of each age who are vaccinated in the season.
		 *
		 * @throws IllegalArgumentException if a share is not between 0 and 1
		 */
		public void setShareByAge(Map<Integer, Double> shares) {
			for (Map.Entry<Integer, Double> e : shares.entrySet())
				if (!(e.getValue() >= 0.0 && e.getValue() <= 1.0))
					throw new IllegalArgumentException("The share " + e.getValue() + " of age " + e.getKey() + " is not between 0 and 1");
			shareByAge.clear();
			shareByAge.putAll(shares);
		}

		/** The share of the agents of this age who are vaccinated in the season. */
		public double shareAt(int age) {
			return shareByAge.isEmpty() ? 0.0 : EpisimUtils.interpolateEntry(shareByAge, age);
		}

		@StringGetter(TIMING)
		String getTimingString() {
			StringBuilder text = new StringBuilder();
			timing.forEach((date, share) -> text.append(text.length() > 0 ? ";" : "").append(date).append("=").append(share));
			return text.toString();
		}

		@StringSetter(TIMING)
		void setTimingString(String text) {
			NavigableMap<LocalDate, Double> parsed = new TreeMap<>();
			if (text != null && !text.isBlank()) {
				for (String entry : text.split(";")) {
					String[] parts = entry.split("=");
					if (parts.length != 2)
						throw new IllegalArgumentException("'" + entry + "' in " + TIMING + " is not of the form date=share");
					parsed.put(LocalDate.parse(parts[0].strip()), Double.parseDouble(parts[1].strip()));
				}
			}
			setTiming(parsed);
		}

		public NavigableMap<LocalDate, Double> getTiming() {
			return Collections.unmodifiableNavigableMap(timing);
		}

		/** The cumulative share of the vaccinations of the season at the end of the day of each date; see {@link #cumulativeShare}. */
		public void setTiming(NavigableMap<LocalDate, Double> cumulative) {
			timing.clear();
			timing.putAll(cumulative);
		}

		/** The day before the first entry has the share 0: the first campaign day is the day after the first date. */
		public LocalDate firstDay() {
			return timing.firstKey().plusDays(1);
		}

		/** The day on which the share reaches its last value. */
		public LocalDate lastDay() {
			return timing.lastKey();
		}

		/**
		 * The share of the vaccinations of the season that are done at the end of {@code date}: 0 up to the first date, linear between
		 * the dates, the last value after the last date.
		 */
		public double cumulativeShare(LocalDate date) {
			if (timing.isEmpty() || !date.isAfter(timing.firstKey()))
				return 0.0;
			if (!date.isBefore(timing.lastKey()))
				return timing.lastEntry().getValue();

			Map.Entry<LocalDate, Double> before = timing.floorEntry(date);
			Map.Entry<LocalDate, Double> after = timing.higherEntry(date);
			double span = java.time.temporal.ChronoUnit.DAYS.between(before.getKey(), after.getKey());
			double done = java.time.temporal.ChronoUnit.DAYS.between(before.getKey(), date);
			return before.getValue() + (after.getValue() - before.getValue()) * done / span;
		}

		private void collectProblems(List<String> problems) {
			String label = "Season '" + season + "'";
			if (season.isEmpty())
				problems.add("A '" + SET_TYPE + "' has no '" + SEASON + "'");
			if (product.isEmpty())
				problems.add(label + " has no '" + PRODUCT + "'");
			if (shareByAge.isEmpty())
				problems.add(label + " has no '" + SHARE_BY_AGE + "', so nobody would be vaccinated");
			if (timing.size() < 2) {
				problems.add(label + " needs at least two dates in '" + TIMING + "'");
				return;
			}

			double previous = 0.0;
			for (Map.Entry<LocalDate, Double> e : timing.entrySet()) {
				if (!(e.getValue() >= previous && e.getValue() <= 1.0))
					problems.add(label + ": the cumulative share " + e.getValue() + " at " + e.getKey()
							+ " falls or is above 1 (it must not fall and must stay between 0 and 1)");
				previous = e.getValue();
			}
			if (timing.firstEntry().getValue() != 0.0)
				problems.add(label + ": the first date of '" + TIMING + "' has to have the share 0, it is the day before the campaign");
			if (timing.lastEntry().getValue() != 1.0)
				problems.add(label + ": the last date of '" + TIMING + "' has to have the share 1, otherwise the coverage is not reached");
		}
	}
}
