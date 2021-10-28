# Title     : LocalRestrictionAnalysis
# Objective : Splits InfectionEvents into districts, and compares to rki data
# Created by: jakobrehmann
# Created on: 6/9/2021

library(readxl)
library(tidyverse)
library(janitor)
library(lubridate)
library(ggthemes)
library(scales)
library(cowplot)
library(vistime)
library(data.table)

rm(list = ls())
setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

gbl_image_output <- "C:/Users/jakob/projects/60eeeb14daadd7ca9fc56fea/images/"

# load all functions
source("C:/Users/jakob/projects/matsim-episim/src/main/R/utilsJR.R", encoding = 'utf-8')

######## GLOBAL VARIABLES ########
# RKI Data
gbl_rki_new <- read_and_process_new_rki_data("Fallzahlen_Kum_Tab_9Juni.xlsx")
gbl_rki_old <- read_and_process_old_rki_data("RKI_COVID19_02112020.csv")
gbl_agent_count_per_district <- read_delim("C:/Users/jakob/projects/matsim-episim/AgentCntPerDistrict_OLD.txt", delim = ";", col_names = FALSE) %>%
  rename(district = X1, population = X2) %>%
  mutate(population = population * 4)

if (FALSE) {
  directoryT <- "2021-10-27-bmbf-test2/"

  directory <- directoryT
  allow_missing_files <- TRUE
  file_root <- "infections_subdistrict.txt"

  info_df <- read_delim(paste0(directory, "_info.txt"), delim = ";")

  # gathers column names that should be included in final dataframe
  col_names <- colnames(info_df)
  relevant_cols <- col_names[!col_names %in% c("RunScript", "RunId", "Config", "Output")]

  episim_df_all_runs <- data.frame()
  for (row in seq_len(nrow(info_df))) {

    runId <- info_df$RunId[row]

    file_name <- paste0(directory, runId, ".", file_root)

    if (!file.exists(file_name) & allow_missing_files) {
      warning(paste0(file_name, " does not exist"))
      next
    }


    df_for_run <- read_delim(file = file_name, "\t", escape_double = FALSE, trim_ws = TRUE)

    if (dim(df_for_run)[1] == 0) {
      warning(paste0(file_name, " is empty"))
      next
    }

    # adds important variables concerning run to df, so that individual runs can be filtered in later steps
    for (var in relevant_cols) {
      df_for_run[var] <- info_df[row, var]
    }

    episim_df_all_runs <- rbindlist(list(episim_df_all_runs, df_for_run))

  }
  filtered <- episim_df_all_runs %>% select(date,nInfectedCumulative,district, relevant_cols) %>% filter(district!="unknown")


}


######## A - Restrict Mitte ########

if (FALSE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  directoryA <- "2021-09-08-masterA/"
  infections_raw <- read_combine_episim_output(directoryA, "infectionEvents.txt", FALSE)

  xxx <- infections_raw %>%
    filter(date == ymd("2020-08-31")) %>%
    filter(locationBasedRestrictions == "no") %>%
    filter(seed == 4711)

  infections_by_district <- geolocate_infections(infections_raw, "FacilityToDistrictMapCOMPLETE.txt") %>%
    mutate(infections = infections * 4)

  sum((infections_by_district %>%
    filter(date == lala) %>%
    filter(locationBasedRestrictions == "no") %>%
    filter(seed == 4711))$infections)

  # aggregated infection over multiple seeds used to show trend in infections for different parameters
  infections_aggregated <- infections_by_district %>%
    group_by(across(-c(infections, seed))) %>%
    summarise(infections = mean(infections))

  episim_all_runs <- infections_aggregated %>%
    ungroup() %>%
    pivot_wider(names_from = "locationBasedRestrictions", values_from = "infections")

  # merge datasets and make tidy
  merged_weekly <- merge_tidy_average(episim_all_runs, gbl_rki_new, gbl_rki_old)


  bleepbloop <- merged_weekly %>%
    group_by(across(-c(infections, district))) %>%
    summarise(infections = sum(infections)) %>%
    filter(scenario == "no")

  ggplot(bleepbloop) +
    geom_line(aes(date, infections, color = scenario))

  ######## III - PLOT ##########
  # Facet Plot - all districts - Incidenz
  rki_and_episim_incidenz <- merged_weekly %>%
    left_join(gbl_agent_count_per_district) %>%
    mutate(incidence = infections / population * 100000 * 7)

  to_plot <- rki_and_episim_incidenz %>%
    filter(!grepl("rki", scenario))

  plot_resultsA <- ggplot(to_plot, aes(x = date, y = incidence)) +
    geom_line(aes(color = scenario)) +
    annotate("rect", xmin = ymd("2020-10-1"), xmax = ymd("2020-10-31"), ymin = 0, ymax = Inf, alpha = 0.7, fill = " light blue") +
    scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
    labs(
      # title = "7-Day Infections / 100k Pop for Berlin Districts",
      #    subtitle = "Comparison of Local vs. Global Activity Reductions",
      x = "Date", y = "7-Day Infections / 100k Pop.") +
    facet_wrap(~district, ncol = 3) +
    # scale_color_calc() +
    theme_minimal() +
    theme(axis.text.x = element_text(angle = 90)) +
    xlim(ymd("2020-09-01"), ymd("2021-02-01"))

  plot_resultsA

  ggsave(plot_resultsA, filename = "resultsA.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
  ggsave(plot_resultsA, filename = "resultsA.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")


}

######## B - Infection Dynamics of Neighborhoods ######## (contact intensity)

if (FALSE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  # directoryB <- "2021-08-22-ci/"
  directoryB <- "2021-10-26-masterB/"
  # episim_all_runs_raw <- read_combine_episim_output(directoryB, "infectionEvents.txt",FALSE)
  #
  # temp_filename_raw <- paste0(directoryB,"episim_all_runs_raw.Rda")
  # save(episim_all_runs_raw, file = temp_filename_raw)
  #
  # infections_by_district <- geolocate_infections(episim_all_runs_raw, "FacilityToDistrictMapCOMPLETE.txt") %>%
  #   mutate(infections = infections * 4)
  #
  temp_filename <- paste0(directoryB, "infections_by_district.Rda")
  # save(infections_by_district, file = temp_filename)
  load(file = temp_filename)

  # aggregated infection over multiple seeds used to show trend in infections for different parameters
  infections_aggregated <- infections_by_district %>%
    group_by(across(-c(infections, seed))) %>%
    summarise(infections = mean(infections))

  # For contact intensity runs
  episim_all_runs <- infections_aggregated %>%
    filter(locationBasedRestrictions == "yesForHomeLocation") %>%
    ungroup() %>%
    pivot_wider(names_from = c(thetaFactor, ciModifier), names_glue = "th{thetaFactor}_mult{ciModifier}", values_from = infections) %>%
    select(-c("locationBasedRestrictions"))

  # merge datasets and make tidy
  merged_weekly <- merge_tidy_average(episim_all_runs, gbl_rki_new, gbl_rki_old)

  # Facet Plot - all districts - Incidenz
  rki_and_episim_incidenz <- merged_weekly %>%
    left_join(gbl_agent_count_per_district) %>%
    mutate(incidence = infections / population * 100000 * 7)

  to_plot <- rki_and_episim_incidenz %>%
    filter(grepl("rki", scenario) |
             scenario == "th1_mult0.3" |
             scenario == "th1_mult0")


  plot_resultsB <- ggplot(to_plot, aes(x = date, y = incidence)) +
    geom_line(aes(color = scenario)) +
    # annotate("rect", xmin = ymd("2020-10-1"), xmax = ymd("2020-10-31"), ymin = 0, ymax = Inf, alpha = 0.4, fill = " light blue") +
    scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
    labs(title = "7-Day Infections / 100k Pop for Berlin Districts",
         subtitle = "Comparison of Local vs. Global Activity Reductions",
         x = "Date", y = "7-Day Infections / 100k Pop.") +
    facet_wrap(~district, ncol = 4) +
    scale_color_manual(values = c("rki_old" = "dark grey", "rki_new" = "dark grey", "red", "blue")) +
    theme_minimal() +
    theme(axis.text.x = element_text(angle = 90)) +
    xlim(ymd("2020-09-01"), ymd("2021-02-01"))

  plot_resultsB

  ggsave(plot_resultsB, filename = "resultsB.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
  ggsave(plot_resultsB, filename = "resultsB.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")


  # Results from last run: theta values need to be more fine tuned btwn 0.95 and 1.05
  # There is signal: the further apart you make the cis:
}

######## C - Adaptive Local Restrictions ########
if (FALSE) {

  # some general setup
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  directoryC <- "2021-09-16-masterC-10seeds/"

  analysis_begin_date <- ymd("2020-07-18")
  analysis_end_date <- ymd("2021-02-17")

  # Analyse Infections Output

  # infections_raw <- read_combine_episim_output(directoryC, "infectionEvents.txt", FALSE)
  # infections_by_district <- geolocate_infections(infections_raw, "FacilityToDistrictMapCOMPLETE.txt") %>%
  #   mutate(infections = infections * 4) %>% # since sample is 25%, we multiply by 4
  #   filter(date >= analysis_begin_date & date <= analysis_end_date)

  temp_filename <- paste0(directoryC, "infections_by_district.Rda")
  # save(infections_by_district, file = temp_filename)
  load(file = temp_filename)

  # infection progression for single seed used to demonstrate local adaptive policy
  infections_4711 <- infections_by_district %>% filter(seed == 4711)

  # aggregated infection over multiple seeds used to show trend in infections for different parameters
  infections_aggregated <- infections_by_district %>%
    group_by(across(-c(infections, seed))) %>%
    summarise(infections = mean(infections))


  rfs <- c(min(infections_aggregated$restrictedFraction), max(infections_aggregated$restrictedFraction))
  triggers <- c(min(infections_aggregated$trigger), max(infections_aggregated$trigger))

  # Prepare Infection Graph where extreme values of trigger & Rf are compared in facet
{
  infections_aggregated_allBerlin <- infections_aggregated %>%
    group_by(across(-c(infections, district))) %>%
    summarise(infections = sum(infections)) %>%
    mutate(week = week(date), year = year(date)) %>%
    group_by(across(-c(date, infections))) %>%
    summarise(infections = mean(infections), date = mean(date)) %>%
    select(-c(week, year)) %>%
    filter(restrictedFraction %in% rfs) %>%
    filter(trigger %in% triggers) %>%
    rename("Trigger" = trigger, "Rf" = restrictedFraction)


  infection_sums <- infections_aggregated_allBerlin %>%
    group_by(adaptivePolicy, Rf, Trigger) %>%
    summarise(infections = sum(infections))


  infection_labels <- infection_sums %>%
    mutate(lab = paste0(adaptivePolicy, " : ", round(infections, 0))) %>%
    group_by(Rf, Trigger) %>%
    summarise(lab = paste(lab, collapse = "\n"))

  # yesLocal vs. no: percent reduction of infections
  infection_sums %>%
    filter(adaptivePolicy != "yesGlobal") %>%
    pivot_wider(names_from = adaptivePolicy, values_from = infections) %>%
    mutate(pct_red = (no - yesLocal) * 100 / no)

  plot_infection_facet <- ggplot(infections_aggregated_allBerlin) +
    geom_line(aes(x = date, y = infections, col = adaptivePolicy)) +
    facet_grid(Rf ~ Trigger, labeller = labeller(Rf = label_both, Trigger = label_both)) +
    labs(x = "Date", y = "Daily Infections") +
    theme_light() +
    scale_color_manual(values = c("grey40", "blue", "red")) +
    scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
    scale_y_continuous(trans = 'log10') +
    geom_text(data = infection_labels, mapping = aes(ymd("2021-02-15"), 3000, label = lab), size = 2.5, hjust = "right") +
    theme(axis.text.x = element_text(size = 6, color = "black", angle = 30, vjust = 1, hjust = 1))

  plot_infection_facet
  ggsave(plot_infection_facet, filename = "resultsC_infection_facet.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
  ggsave(plot_infection_facet, filename = "resultsC_infection_facet.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")

}

  # Get sum of total infections over timeframe
{
  infections_total <- infections_aggregated %>%
    # filter(date >= analysis_begin_date & date <= analysis_end_date) %>%
    group_by(across(-c(infections, district, date))) %>%
    summarise(infections = sum(infections))

  print("min & max case numbers for local adaptive policies")
  local <- infections_total %>% filter(adaptivePolicy == "yesLocal")
  local[which.min(local$infections),]
  local[which.max(local$infections),]

  print("min & max case numbers for global adaptive policies")
  global <- infections_total %>% filter(adaptivePolicy == "yesGlobal")
  global[which.min(global$infections),]
  global[which.max(global$infections),]

  versus <- infections_total %>%
    filter(adaptivePolicy != "no") %>%
    pivot_wider(names_from = adaptivePolicy, values_from = infections) %>%
    mutate(local_over_global = (yesLocal - yesGlobal) / yesGlobal * 100)

  trigger_scale <- as.character(unique(versus$trigger))
  plot_local_versus_global <- ggplot(versus %>% mutate(trigger = as.character(trigger))) +
    geom_line(aes(x = restrictedFraction, y = local_over_global, color = trigger)) +
    scale_color_discrete(limits = trigger_scale) +
    theme_minimal() +
    labs(x = "Rf for Restricted Policy", y = "Local vs. Global, Percent Change")

  plot_local_versus_global

  # A global adaptive restriction does well when trigger is high and rf is low
  # A local adpative restriction does very well when trigger is low and rf is low
  # A local adaptive restriction does well at high Rfs, regardless of Trigger (low trigger still better)
  ggsave(plot_local_versus_global, filename = "resultsC_infection_versus.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
  ggsave(plot_local_versus_global, filename = "resultsC_infection_versus.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")


  print("runs where global adaptive restrictions perform better than local")
  versus %>% filter(local_over_global > 0)
  print("simulation with greatest improvement local / global: ")
  versus[which.min(versus$local_over_global),]


  plot_local_versus_global2 <- ggplot(infections_total %>% filter(adaptivePolicy != "no")) +
    geom_line(aes(x = restrictedFraction, y = infections, color = adaptivePolicy)) +
    # scale_color_discrete(limits = trigger_scale) +
    theme_light() +
    facet_grid(~Trigger, labeller = labeller(Trigger = label_both)) +
    labs(x = "Rf for Restricted Policy", y = "Local vs. Global, Percent Change") +
    facet_wrap(~trigger, nrow = 1)
  plot_local_versus_global2
  ggsave(plot_local_versus_global2, filename = "resultsC_infection_versus2.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
  ggsave(plot_local_versus_global2, filename = "resultsC_infection_versus2.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")

}


  # Plot progressions of single run (with single seed, so that individual lockdowns can are visible)
  trigger_plot <- 10
  restricted_fraction_plot <- 0.0

  infections_4711_filtered <- infections_4711 %>%
    filter(restrictedFraction == restricted_fraction_plot) %>%
    ungroup() %>%
    select(-c("restrictedFraction")) %>%
    pivot_wider(names_from = c(adaptivePolicy, trigger), values_from = infections, values_fill = 0)

  # merge datasets and make tidy
  infections_4711_merged <- merge_tidy_average(infections_4711_filtered, gbl_rki_new, gbl_rki_old)

  # Facet Plot - all districts - Incidenz
  infections_4711_incidence <- infections_4711_merged %>%
    left_join(gbl_agent_count_per_district) %>%
    mutate(incidence = infections / population * 100000 * 7)

  to_plot <- infections_4711_incidence %>%
    # filter(!grepl("rki", scenario)) %>%
    filter(grepl(paste0("_", trigger_plot, "$"), scenario)) %>%
    filter(!grepl("no", scenario)) %>%
    filter(date > ymd("2020-07-01") & date < ymd("2021-02-01"))

  plot_infections_4711_incidence <- ggplot(to_plot, aes(x = date, y = incidence)) +
    geom_line(aes(color = scenario)) +
    # annotate("rect", xmin = ymd("2020-10-1"), xmax = ymd("2020-10-31"), ymin = 0, ymax = Inf, alpha = 0.4, fill = " light blue") +
    scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
    scale_y_continuous() +
    labs(title = "7-Day Infections / 100k Pop for Berlin Districts",
         subtitle = "Comparison of Local vs. Global Activity Reductions",
         x = "Date", y = "7-Day Infections / 100k Pop.") +
    facet_wrap(~district, ncol = 3) +
    theme_minimal() +
    theme(axis.text.x = element_text(angle = 90))

  plot_infections_4711_incidence

  ggsave(plot_infections_4711_incidence, filename = "resultsC_infection_4711.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
  ggsave(plot_infections_4711_incidence, filename = "resultsC_infection_4711.png", path = gbl_image_output, width = 16, height = 12, units = "cm")


  # get initial, restricted, open policies
  if (FALSE) {

    ar_raw <- read_combine_episim_output(directoryC, "adaptiveRestrictions.tsv", FALSE)

  {
    ar <- ar_raw %>%
      filter(activity == "leisure") %>%
      filter(seed == 4711) %>%
      filter((adaptivePolicy == "yesLocal" & location != "total") | adaptivePolicy == "yesGlobal") %>%
      filter(date >= analysis_begin_date & date <= analysis_end_date) %>%
      mutate(location = str_replace(location, "total", "----Global----")) %>%
      select(date, location, policy, restrictedFraction, trigger) %>%
      arrange(location)


    ar1 <- ar %>% filter(restrictedFraction == 0.0 & trigger == 10)
    ar2 <- ar %>% filter(restrictedFraction == 0.0 & trigger == 100)
    ar3 <- ar %>% filter(restrictedFraction == 0.6 & trigger == 10)
    ar4 <- ar %>% filter(restrictedFraction == 0.6 & trigger == 100)
    plot_data1 <- make_timeline(ar1, "left", "Rf = 0.0, Trigger = 10")
    plot_data2 <- make_timeline(ar2, "right", "Rf = 0.0, Trigger = 100")
    plot_data3 <- make_timeline(ar3, "left", "Rf = 0.6, Trigger = 10")
    plot_data4 <- make_timeline(ar4, "right", "Rf = 0.6, Trigger = 100")
    lockdown_weeks1 <- make_text(ar1)
    lockdown_weeks2 <- make_text(ar2)
    lockdown_weeks3 <- make_text(ar3)
    lockdown_weeks4 <- make_text(ar4)


    legend <- make_legend()
    timelines <- plot_grid(plot_data1, lockdown_weeks1, plot_data2, lockdown_weeks2, plot_data3, lockdown_weeks3, plot_data4, lockdown_weeks4, legend, NULL, nrow = 3, rel_heights = c(1, 1, 0.05), rel_widths = c(1, 0.125, 1, 0.125))
    timelines
    scale_factor <- 2.5
    ggsave(timelines, filename = "resultsC_timelines.png", path = gbl_image_output, width = 16 * scale_factor, height = 12 * scale_factor, units = "cm")
    ggsave(timelines, filename = "resultsC_timelines.pdf", path = gbl_image_output, width = 16 * scale_factor, height = 12 * scale_factor, units = "cm")


  }
  }

  ## Time Use
  if (FALSE) {

    time_raw <- read_combine_episim_output(directoryC, "timeUse.txt", FALSE)

    # pivots activity columns to single column
    time <- time_raw %>%
      pivot_longer(!c("day", "date", "seed", "restrictedFraction", "trigger", "adaptivePolicy"), names_to = "activity", values_to = "time") %>%
      filter(date >= analysis_begin_date & date <= analysis_end_date)


    # again, we have one df that aggregates the 5 seeds, and one that takes a single seed
    time_agg <- time %>%
      group_by(across(-c(seed, time))) %>%
      summarise(time = mean(time))

    time_4711 <- time %>% filter(seed == 4711) %>% select(-seed)

    # plot average "out of home" time per day  versus rf & trigger
    # find min and max outOfHome times for different adaptive strategies
  {
    time_outside_perDay <- time_agg %>%
      filter(activity != "home" & activity != "quarantine_home") %>%
      # filter(activity=="leisure") %>%
      # filter(grepl("^educ_",activity)) %>%
      # filter(activity=="work" | activity =="business") %>%
      ungroup() %>%
      select(-day) %>%
      group_by(across(-c(time, activity))) %>% # get total time per day (sum of all activities)
      summarise(time = sum(time))


    time_outside_perDay_weekly_average <- time_outside_perDay %>%
      filter(restrictedFraction %in% rfs) %>%
      filter(trigger %in% triggers) %>%
      ungroup() %>%
      mutate(week = isoweek(date)) %>% # iso 8601 standard: week always begins with Monday; week 1 must contains January 4th
      mutate(year = isoyear(date)) %>%
      group_by(across(-c(time, date))) %>%
      summarise(time = mean(time, na.rm = TRUE), date = mean(date, na.rm = TRUE)) %>%
      ungroup() %>%
      select(!c(week, year)) %>%
      rename("Rf" = restrictedFraction, "Trigger" = trigger)


    time_outside_perDay_weekly_average_labels <- time_outside_perDay_weekly_average %>%
      group_by(adaptivePolicy, Rf, Trigger) %>%
      summarise(time = mean(time)) %>%
      mutate(lab = paste0(adaptivePolicy, " : ", round(time, 0))) %>%
      group_by(Rf, Trigger) %>%
      summarise(lab = paste(lab, collapse = "\n"))


    plot_time_outside_perDay_weekly_average <- ggplot(time_outside_perDay_weekly_average %>% filter(date > ymd("2020-08-01"))) +
      geom_line(aes(date, time, col = adaptivePolicy)) +
      facet_grid(Rf ~ Trigger, labeller = labeller(Rf = label_both, Trigger = label_both)) +
      labs(x = "Date", y = "Out of Home Time (min), Daily") +
      theme_light() +
      scale_color_manual(values = c("grey40", "blue", "red")) +
      scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
      geom_text(data = time_outside_perDay_weekly_average_labels, mapping = aes(ymd("2021-02-15"), 25, label = lab), size = 2.5, hjust = "right") +
      theme(axis.text.x = element_text(size = 6, color = "black", angle = 30, vjust = 1, hjust = 1))


    plot_time_outside_perDay_weekly_average

    ggsave(plot_time_outside_perDay_weekly_average, filename = "resultsC_time_outside_perDay_weekly_average.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
    ggsave(plot_time_outside_perDay_weekly_average, filename = "resultsC_time_outside_perDay_weekly_average.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")


    time_avg %>%
      filter(adaptivePolicy != "yesGlobal") %>%
      filter(restrictedFraction %in% rfs) %>%
      filter(Trigger %in% triggers) %>%
      pivot_wider(names_from = adaptivePolicy, values_from = time) %>%
      mutate(pct_red = (no - yesLocal) * 100 / no)


    plot_average_outHome <- ggplot(time_avg) +
      geom_line(aes(restrictedFraction, time, col = adaptivePolicy)) +
      facet_grid(~Trigger, labeller = labeller(Trigger = label_both)) +
      scale_color_manual(values = c("grey40", "blue", "red")) +
      labs(x = "Restricted Rf", y = "Average Out of Home Time Per Day (min) ") +
      theme_light()

    plot_average_outHome

    ggsave(plot_average_outHome, filename = "resultsC_time_avg.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
    ggsave(plot_average_outHome, filename = "resultsC_time_avg.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")


    versus_time <- time_avg %>%
      pivot_wider(names_from = adaptivePolicy, values_from = time) %>%
      mutate(local_over_global = (yesLocal - yesGlobal) / yesGlobal * 100)

    # trigger_scale <- as.character(unique(versus$trigger))
    plot_local_versus_global_time <- ggplot(versus_time %>% mutate(Trigger = as.character(Trigger))) +
      geom_line(aes(x = restrictedFraction, y = local_over_global, color = Trigger)) +
      scale_color_discrete(limits = trigger_scale) +
      theme_minimal() +
      labs(x = "Rf for Restricted Policy", y = "Local vs. Global, Percent Change")

    plot_local_versus_global_time

    ggsave(plot_local_versus_global_time, filename = "resultsC_time_versus.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
    ggsave(plot_local_versus_global_time, filename = "resultsC_time_versus.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")

    print("minimum avg time out of home for local adaptive restriction ")
    time_avg %>%
      filter(adaptivePolicy == "yesLocal") %>%
      arrange(time) %>%
      head(1)
    print("minimum avg time out of home for global adaptive restriction ")
    time_avg %>%
      filter(adaptivePolicy == "yesGlobal") %>%
      arrange(time) %>%
      head(1)
    print("maximum avg time out of home for local adaptive restriction ")
    time_avg %>%
      filter(adaptivePolicy == "yesLocal") %>%
      arrange(desc(time)) %>%
      head(1)
    print("maximum avg time out of home for global adaptive restriction ")
    time_avg %>%
      filter(adaptivePolicy == "yesGlobal") %>%
      arrange(desc(time)) %>%
      head(1)
  }

    # for seed 4711
    time_progression <- time_4711 %>%
      filter(restrictedFraction %in% rfs) %>%
      filter(trigger %in% triggers) %>%
      filter(activity != "home" & activity != "quarantine_home") %>%
      group_by(across(-c(activity, time))) %>%
      summarise(time = sum(time)) %>%
      ungroup() %>%
      select(-day) %>%
      mutate(week = isoweek(date)) %>% # iso 8601 standard: week always begins with Monday; week 1 must contains January 4th
      mutate(year = isoyear(date)) %>%
      group_by(across(-c(time, date))) %>%
      summarise(time = mean(time, na.rm = TRUE), date = mean(date, na.rm = TRUE)) %>%
      ungroup() %>%
      select(!c(week, year)) %>%
      rename("Rf" = restrictedFraction, "Trigger" = trigger)


    plot_time_use_4711 <- ggplot(time_progression %>% filter(date > ymd("2020-08-01"))) +
      geom_line(aes(date, time, col = adaptivePolicy)) +
      facet_grid(Rf ~ Trigger, labeller = labeller(Rf = label_both, Trigger = label_both)) +
      labs(x = "Date", y = "Out of Home Time (min), Daily") +
      theme_light() +
      scale_color_manual(values = c("grey40", "blue", "red"))

    plot_time_use_4711


    ggsave(plot_time_use_4711, filename = "resultsC_time_facet_4711.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
    ggsave(plot_time_use_4711, filename = "resultsC_time_facet_4711.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")

    # aggregated
    time_progression_agg <- time_agg %>%
      filter(restrictedFraction %in% rfs) %>%
      filter(trigger %in% triggers) %>%
      filter(activity != "home" & activity != "quarantine_home") %>%
      group_by(across(-c(activity, time))) %>%
      summarise(time = sum(time)) %>%
      ungroup() %>%
      select(-day) %>%
      mutate(week = isoweek(date)) %>% # iso 8601 standard: week always begins with Monday; week 1 must contains January 4th
      mutate(year = isoyear(date)) %>%
      group_by(across(-c(time, date))) %>%
      summarise(time = mean(time, na.rm = TRUE), date = mean(date, na.rm = TRUE)) %>%
      ungroup() %>%
      select(!c(week, year)) %>%
      rename("Rf" = restrictedFraction, "Trigger" = trigger)


    plot_time_outside_perDay_weekly_average <- ggplot(time_progression_agg %>% filter(date > ymd("2020-08-01"))) +
      geom_line(aes(date, time, col = adaptivePolicy)) +
      facet_grid(Rf ~ Trigger, labeller = labeller(Rf = label_both, Trigger = label_both)) +
      labs(x = "Date", y = "Out of Home Time (min), Daily") +
      theme_light() +
      scale_color_manual(values = c("grey40", "blue", "red")) +
      scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
      # geom_text(data = infection_labels, mapping = aes(ymd("2021-02-15"), 3000, label = lab), size = 2.5, hjust = "right") +
      theme(axis.text.x = element_text(size = 6, color = "black", angle = 30, vjust = 1, hjust = 1))


    plot_time_outside_perDay_weekly_average
  }

}

# read berlin corona data to find dates with lowest incidence -
# this will be our study time frame
{
  berlin_raw <- read_delim("Cases_Berlin.csv", delim = ";")


  berlin_clean <- berlin_raw %>%
    rename(inzidenz = `7_tage_inzidenz`) %>%
    select(datum, inzidenz) %>%
    mutate(month = month(datum), year = year(datum))

  berlin_avg <- berlin_clean %>%

    group_by(month, year) %>%
    summarise(avg_incidence = mean(inzidenz), date = mean(datum)) %>%
    arrange(avg_incidence)

  print("lowest incidenz in 2020 (after first wave): ")
  berlin_clean %>%
    filter(year == 2020) %>%
    arrange(inzidenz) %>%
    head(1)
  # 2020-07-18 - 3.2

  print("lowest incidenz in 2021: ")
  berlin_clean %>%
    filter(year == 2021) %>%
    arrange(inzidenz) %>%
    head(1)
  # 2021-07-04 - 5.3

  print("lowest incidenz between second and third wave")
  berlin_clean %>%
    filter(year == 2021 & month < 5) %>%
    arrange(inzidenz) %>%
    head(1)
  # 2021-02-17 - 54.7

  plot_berlin_cases <- ggplot(berlin_clean) +
    geom_line(aes(datum, inzidenz)) +
    annotate("rect", xmin = ymd("2020-07-18"),
             xmax = ymd("2021-02-17"), ymin = 0, ymax = Inf,
             alpha = 0.4, fill = "light blue") +
    labs(x = "Date", y = "Incidence")

  plot_berlin_cases

  ggsave(plot_berlin_cases, filename = "resultsC_setup_timeframe.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
  ggsave(plot_berlin_cases, filename = "resultsC_setup_timeframe.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")


}

make_timeline <- function(ar_filtered, axis_position, title) {
  timeline_data <- data.frame()

  locations <- unique(ar_filtered$location)

  for (loc in locations) {

    policy_filtered <- ar_filtered %>% filter(location == loc)

    timeline_data_district <- data.frame()
    policy <- policy_filtered$policy[1]
    start <- policy_filtered$date[1]

    for (row in seq(2, nrow(policy_filtered))) {

      if (policy_filtered$policy[row] == policy) {
        next
      }

      end <- policy_filtered$date[row]
      single <- data.frame(location = loc, policy = policy, start = start, end = end)
      timeline_data_district <- rbind(timeline_data_district, single)

      start <- end
      policy <- policy_filtered$policy[row]

    }

    end <- policy_filtered$date[row]

    if (start != end) {
      single <- data.frame(location = loc, policy = policy, start = start, end = end)
      timeline_data_district <- rbind(timeline_data_district, single)
    }

    timeline_data <- rbind(timeline_data, timeline_data_district)
  }


  timeline_data_final <- timeline_data %>%
    mutate(location = str_replace(location, "_", "-")) %>%
    mutate(color = policy) %>%
    mutate(color = str_replace(color, "initial", "gray")) %>%
    mutate(color = str_replace(color, "restricted", "indianred1")) %>%
    mutate(color = str_replace(color, "open", "royalblue1"))


  # following was adapted from https://github.com/wlhamilton/Patient-ward-movement-timelines/blob/main/R%20script%20for%20formatting%20ward%20movement%20data.R

  plot_data <- gg_vistime(data = timeline_data_final, col.group = "location", col.event = "policy", col.start = "start", col.end = "end", col.color = "color", show_labels = FALSE) +  #theme_bw() +
    ggplot2::theme(
      plot.title = element_text(size = 14),
      axis.text.x = element_text(size = 12, color = "black", angle = 30, vjust = 1, hjust = 1),
      axis.text.y = element_text(size = 12, color = "black")) +
    scale_x_datetime(breaks = breaks_width("1 month"), labels = date_format("%b %y")) +
    labs(title = title)

  return(plot_data)

}

make_legend <- function() {
  data_legend <- data.frame(policy = c("Restriction Policy:", "Initial", "Restricted", "Open"), color = c(rgb(0, 0, 0, 0), "gray", "indianred1", "royalblue1"))
  data_legend$start <- c(as.Date("2020-01-03"), as.Date("2020-01-07"), as.Date("2020-01-13"), as.Date("2020-01-19"))
  data_legend$end <- c(as.Date("2020-01-06"), as.Date("2020-01-12"), as.Date("2020-01-18"), as.Date("2020-01-24"))
  data_legend
  plot_legend <- gg_vistime(data = data_legend,
                            col.event = "policy",
                            col.color = "color",
                            show_labels = TRUE,
                            linewidth = 20,
                            title = "Legend")
  plot_legend

  # Tweak the legend plot
  plot_legend <- plot_legend +
    theme_void() +
    ggplot2::theme(
      # plot.title = element_text(size = 11),
      plot.title = element_blank(),
      # plot.title.position = "plot",
      axis.title.x = element_blank(),
      axis.text.x = element_blank(),
      axis.ticks.x = element_blank(),
      axis.title.y = element_blank(),
      axis.text.y = element_blank(),
      axis.ticks.y = element_blank())

  return(plot_legend)
}

make_text <- function(ar) {

  lockdown_weeks <- ar %>%
    filter(policy == "restricted") %>%
    group_by(location) %>%
    count() %>%
    mutate(n = paste0(round(n / 7, 1), " wks")) %>%
    arrange(location)


  # data = timeline_data_final, col.group = "location",
  lockdown_weeks$color <- rgb(0, 0, 0, 0)
  lockdown_weeks$start <- c(as.Date("2020-01-1"))
  lockdown_weeks$end <- c(as.Date("2020-01-2"))
  lockdown_weeks
  plot_text <- gg_vistime(data = lockdown_weeks,
                          col.group = "location",
                          col.event = "n",
                          col.color = "color",
                          show_labels = TRUE,
                          linewidth = 20,
                          title = "Lockdown")

  # Tweak the legend plot
  plot_text <- plot_text +
    ggplot2::theme(
      plot.title = element_text(size = 14),
      axis.ticks.length = unit(0, "in"),
      axis.text.x = element_text(size = 12, color = rgb(0, 0, 0, 0), angle = 30, vjust = 1, hjust = 1),
      axis.text.y = element_blank(),
      axis.ticks = element_blank(),
      axis.title.y = element_blank(),
      axis.line.y = element_blank(),
    ) +
    scale_x_datetime(breaks = breaks_width("1 month"), labels = date_format("%b %y"))

  return(plot_text)
}


infection_to_incidence <- function(infection) {
  return(infection / 3600000 * 100000 * 7)
}

incidence_to_infection <- function(incidence) {
  return(incidence * 3600000 / 100000 / 7)
}
