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


######## A - Restrict Mitte ########

if (FALSE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  directoryA <- "2021-09-08-masterA/"
  episim_all_runs_raw <- read_and_process_episim_infections(directoryA, "FacilityToDistrictMapCOMPLETE.txt")
  episim_all_runs <- episim_all_runs_raw %>%
    ungroup() %>%
    pivot_wider(names_from = "locationBasedRestrictions", values_from = "infections")

  # merge datasets and make tidy
  merged_weekly <- merge_tidy_average(episim_all_runs, gbl_rki_new, gbl_rki_old)


  ######## III - PLOT ##########
  # Facet Plot - all districts - Incidenz
  rki_and_episim_incidenz <- merged_weekly %>%
    left_join(gbl_agent_count_per_district) %>%
    mutate(incidence = infections / population * 100000 * 7)

  to_plot <- rki_and_episim_incidenz %>%
    filter(!grepl("rki", scenario))

  ggplot(to_plot, aes(x = date, y = incidence)) +
    geom_line(aes(color = scenario)) +
    annotate("rect", xmin = ymd("2020-10-1"), xmax = ymd("2020-10-31"), ymin = 0, ymax = Inf, alpha = 0.4, fill = " light blue") +
    scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
    labs(title = "7-Day Infections / 100k Pop for Berlin Districts",
         subtitle = "Comparison of Local vs. Global Activity Reductions",
         x = "Date", y = "7-Day Infections / 100k Pop.") +
    facet_wrap(~district, ncol = 3) +
    scale_color_calc() +
    theme_minimal() +
    theme(axis.text.x = element_text(angle = 90)) +
    xlim(ymd("2020-09-01"), ymd("2021-02-01"))

}

######## B - Infection Dynamics of Neighborhoods ######## (contact intensity)

if (FALSE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  directoryB <- "2021-08-22-ci/"
  episim_all_runs_raw <- read_and_process_episim_infections(directoryB, "FacilityToDistrictMapCOMPLETE.txt")
  # For contact intensity runs
  episim_all_runs <- episim_all_runs_raw %>%
    filter(locationBasedRestrictions == "yesForHomeLocation") %>%
    ungroup() %>%
    pivot_wider(names_from = c(thetaFactor, ciMultiplier), names_glue = "th{thetaFactor}_mult{ciMultiplier}", values_from = infections) %>%
    select(-c("locationBasedRestrictions"))

  # merge datasets and make tidy
  merged_weekly <- merge_tidy_average(episim_all_runs, gbl_rki_new, gbl_rki_old)

  # Facet Plot - all districts - Incidenz
  rki_and_episim_incidenz <- merged_weekly %>%
    left_join(gbl_agent_count_per_district) %>%
    mutate(incidence = infections / population * 100000 * 7)

  to_plot <- rki_and_episim_incidenz %>%
    filter(grepl("rki", scenario) |
             scenario == "th1_multoff" |
             scenario == "th0.9_mult1.5")


  ggplot(to_plot, aes(x = date, y = incidence)) +
    geom_line(aes(color = scenario)) +
    annotate("rect", xmin = ymd("2020-10-1"), xmax = ymd("2020-10-31"), ymin = 0, ymax = Inf, alpha = 0.4, fill = " light blue") +
    scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
    labs(title = "7-Day Infections / 100k Pop for Berlin Districts",
         subtitle = "Comparison of Local vs. Global Activity Reductions",
         x = "Date", y = "7-Day Infections / 100k Pop.") +
    facet_wrap(~district, ncol = 3) +
    # scale_color_calc() +
    scale_color_manual(values = c("rki_old" = "dark grey", "rki_new" = "dark grey", "red", "blue")) +
    theme_minimal() +
    theme(axis.text.x = element_text(angle = 90)) +
    xlim(ymd("2020-09-01"), ymd("2021-02-01"))
}

######## C - Adaptive Local Restrictions ########
if (FALSE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  directoryC <- "2021-09-14-masterC-10seeds/"

  # episim_all_runs_raw <- read_and_process_episim_infections(directoryC, "FacilityToDistrictMapCOMPLETE.txt")
  infections_raw <- read_combine_episim_output(directoryC, "infectionEvents.txt", FALSE)
  infections_by_district <- geolocate_infections(infections_raw, "FacilityToDistrictMapCOMPLETE.txt") %>%
    mutate(infections = infections * 4)


  infections_corrected <- infections_by_district %>% filter(adaptivePolicy != "no")
  to_duplicate <- infections_by_district %>% filter(adaptivePolicy == "no")

  lalala <- to_duplicate %>% group_by(across(-c(infections,district))) %>% summarise(infections = sum(infections), date = mean(date))
  ggplot(lalala) + geom_line(aes(date,infections)) + scale_y_continuous(trans = 'log10')



  for (seeeeeeed in unique(infections_by_district$seed)) {
    for (trig in unique(infections_by_district$trigger)) {
      for (rf in unique(infections_by_district$restrictedFraction)) {
        dup <- to_duplicate %>%
          mutate(seed = seed) %>%
          mutate(restrictedFraction = rf) %>%
          mutate(trigger = trig)

        infections_corrected <- rbindlist(list(infections_corrected, dup))
      }
    }
  }

  # since sample is 25%, we multiply by 4

  # infection progression for single seed used to demonstrate local adaptive policy
  infections_4711 <- infections_by_district %>% filter(seed == 4711)

  # aggregated infection over multiple seeds used to show trend in infections for different parameters
  infections_aggregated <- infections_by_district %>%
    group_by(across(-c(infections, seed))) %>%
    summarise(infections = mean(infections))


  # Prepare Infection Graph where extreme values of trigger & Rf are compared in facet
{
  infections_aggregated_allBerlin <- infections_aggregated %>%
    group_by(across(-c(infections, district))) %>%
    summarise(infections = sum(infections)) %>%
    mutate(week = week(date), year = year(date)) %>%
    group_by(across(-c(date, infections))) %>%
    summarise(infections = mean(infections), date = mean(date)) %>%
    select(-c(week, year)) %>%
    filter(restrictedFraction == 0.0 | restrictedFraction == 0.6) %>%
    filter(trigger == 10 | trigger == 100) %>%
    rename("Trigger" = trigger, "Rf" = restrictedFraction)


  plot_infection_facet <- ggplot(infections_aggregated_allBerlin %>% filter(date<ymd("2021-02-25"))) +
    geom_line(aes(x = date, y = infections, col = adaptivePolicy)) +
    facet_grid(Rf ~ Trigger, labeller = labeller(Rf = label_both, Trigger = label_both)) +
    labs(x = "Date", y = "Daily Infections") +
    theme_light() +
    scale_color_manual(values = c("grey40", "blue", "red")) +
    scale_y_continuous(trans = 'log10')

  plot_infection_facet
  ggsave(plot_infection_facet, filename = "resultsC_plot_infection_facet.png", path = gbl_image_output, width = 10, height = 10, units = "in")
  ggsave(plot_infection_facet, filename = "resultsC_plot_infection_facet.pdf", path = gbl_image_output, width = 10, height = 10, units = "in")

}

  # Get sum of total infections over year
{

  infections_total <- infections_aggregated %>%
    filter(adaptivePolicy != "no") %>%
    filter(date > ymd("2020-08-01")) %>%
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
    pivot_wider(names_from = adaptivePolicy, values_from = infections) %>%
    mutate(local_over_global = (yesLocal - yesGlobal) / yesGlobal * 100)


  triggers <- as.character(unique(versus$trigger))
  plot_local_versus_global <- ggplot(versus %>% mutate(trigger = as.character(trigger))) +
    geom_line(aes(x = restrictedFraction, y = local_over_global, color = trigger)) +
    scale_color_discrete(limits = triggers) +
    theme_minimal() +
    labs(x = "Rf for Restricted Policy", y = "Local vs. Global, Percent Change")

  plot_local_versus_global

  # A global adaptive restriction does well when trigger is high and rf is low
  # A local adpative restriction does very well when trigger is low and rf is low
  # A local adaptive restriction does well at high Rfs, regardless of Trigger (low trigger still better)
  #
  ggsave(plot_local_versus_global, filename = "resultsC_local_versus_global.png", path = gbl_image_output)
  ggsave(plot_local_versus_global, filename = "resultsC_local_versus_global.pdf", path = gbl_image_output)


  print("runs where global adaptive restrictions perform better than local")
  versus %>% filter(local_over_global > 0)
  print("simulation with greatest improvement local / global: ")
  versus[which.min(versus$local_over_global),]
}


  # infections vs. trigger
  # ggplot(infections_total) +
  #   geom_line(aes(x = trigger, y = infections, col = adaptivePolicy)) +
  #   facet_wrap(~restrictedFraction)
  #
  # # infections vs. remaining fraction of restricted policy
  # ggplot(infections_total) +
  #   geom_line(aes(x = restrictedFraction, y = infections, col = adaptivePolicy)) +
  #   facet_wrap(~trigger)


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

  ggplot(to_plot, aes(x = date, y = incidence)) +
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

  ggsave(filename = "resultsC_trigger5.png", path = gbl_image_output)
  ggsave(filename = "resultsC_trigger5.png", path = gbl_image_output)


  # get initial, restricted, open policies
  if (FALSE) {

    ar_raw <- read_combine_episim_output(directoryC, "adaptiveRestrictions.tsv", FALSE)

  {
    ar <- ar_raw %>%
      filter(activity == "leisure") %>%
      filter(seed == 4711) %>%
      filter((adaptivePolicy == "yesLocal" & location != "total") | adaptivePolicy == "yesGlobal") %>%
      mutate(location = str_replace(location, "total", "----Global----")) %>%
      select(date, location, policy, restrictedFraction, trigger) %>%
      arrange(location)

    plot_list <- c()
    rfs <- c(min(ar$restrictedFraction), max(ar$restrictedFraction))
    triggers <- c(min(ar$trigger), max(ar$trigger))

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
    ggsave(timelines, filename = "resultsC_timelines.png", path = gbl_image_output, width = 20, height = 10, units = "in")
    ggsave(timelines, filename = "resultsC_timelines.pdf", path = gbl_image_output, width = 20, height = 10, units = "in")


  }
  }

  ## Time Use
  if (FALSE) {

    time_raw <- read_combine_episim_output(directoryC, "timeUse.txt", FALSE)

    # pivots activity columns to single column
    time <- time_raw %>%
      pivot_longer(!c("day", "date", "seed", "restrictedFraction", "trigger", "adaptivePolicy"), names_to = "activity", values_to = "time")

    # if only a single run with "no" was run, this corrects for it:
    time_corrected <- time %>% filter(adaptivePolicy != "no")
    to_duplicate <- time %>% filter(adaptivePolicy == "no")
    for (seeeeeeed in unique(time$seed)) {
      for (rf in unique(time$restrictedFraction)) {
        for (trig in unique(time$trigger)) {
          dup <- to_duplicate %>%
            mutate(seed = seeeeeeed) %>%
            mutate(restrictedFraction = rf) %>%
            mutate(trigger = trig)

          time_corrected <- rbindlist(list(time_corrected, dup))
        }
      }
    }

    # again, we have one df that aggregates the 5 seeds, and one that takes a single seed
    time_agg <- time_corrected %>%
      group_by(across(-c(seed, time))) %>%
      summarise(time = mean(time))
    time_4711 <- time_corrected %>% filter(seed == 4711)

    # plot average "out of home" time per day  versus rf & trigger
    # find min and max outOfHome times for different adaptive strategies
  {
    time_avg <- time_agg %>%
      # filter(activity=="leisure") %>%
      filter(activity != "home" & activity != "quarantine_home") %>%
      # filter(grepl("^educ_",activity)) %>%
      # filter(activity=="work" | activity =="business") %>%
      ungroup() %>%
      select(-day) %>%
      filter(date > ymd("2020-08-01")) %>%
      group_by(across(-c(time, activity))) %>%
      summarise(time = sum(time)) %>%
      group_by(across(-c(time, date))) %>%
      summarise(time = mean(time)) %>%
      rename("Trigger" = trigger)

    plot_average_outHome <- ggplot(time_avg) +
      geom_line(aes(restrictedFraction, time, col = adaptivePolicy)) +
      facet_grid(~Trigger, labeller = labeller(Trigger = label_both)) +
      scale_color_manual(values = c("grey40", "blue", "red")) +
      labs(x = "Restricted Rf", y = "Average Out of Home Time Per Day (min) ") +
      theme_light()

    plot_average_outHome

    ggsave(plot_average_outHome, filename = "resultsC_average_outHome.png", path = gbl_image_output, width = 10, height = 10, units = "in")
    ggsave(plot_average_outHome, filename = "resultsC_average_outHome.pdf", path = gbl_image_output, width = 10, height = 10, units = "in")

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

    time_progression <- time_4711 %>%
      filter(restrictedFraction %in% rfs) %>%
      filter(trigger %in% triggers) %>%
      filter(activity != "home" & activity != "quarantine_home") %>%
      group_by(across(-c(activity, time))) %>%
      summarise(time = sum(time)) %>%
      ungroup() %>%
      select(-c(seed, day)) %>%
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

    ggsave(plot_time_use_4711, filename = "resultsC_time_use_4711s.png", path = gbl_image_output, width = 10, height = 10, units = "in")
    ggsave(plot_time_use_4711, filename = "resultsC_time_use_4711.pdf", path = gbl_image_output, width = 10, height = 10, units = "in")

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

  ggplot(berlin_clean) +
    geom_line(aes(datum, inzidenz))

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


  berlin_clean[which.min(berlin_clean)]
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
    single <- data.frame(location = loc, policy = policy, start = start, end = policy_filtered$date[row])
    timeline_data_district <- rbind(timeline_data_district, single)

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
    # theme_void() +
    ggplot2::theme(
      plot.title = element_text(size = 14),
      # panel.border  = element_rect(color =  rgb(0, 0, 0, 0)),

      # panel.grid = element_line(color = rgb(0, 0, 0, 0)),
      axis.ticks.length = unit(0, "in"),

      axis.text.x = element_text(size = 12, color = rgb(0, 0, 0, 0), angle = 30, vjust = 1, hjust = 1),
      axis.text.y = element_blank(),
      axis.ticks = element_blank(),
      axis.title.y = element_blank(),
      axis.line.y = element_blank(),
      # axis.text.y = element_text(size = 12, color = "black"),
      # axis.text.x = element_blank(),
      # axis.ticks.y = element_blank(),
    ) +
    scale_x_datetime(breaks = breaks_width("1 month"), labels = date_format("%b %y"))
  # guides(colour = "none")
  # ggplot2::theme(
  #   plot.title = element_text(size = 11),
  #   # plot.title = element_blank(),
  #   # plot.title.position = "plot",
  #   axis.title.x = element_blank(),
  #   axis.ticks.x = element_blank(),

  return(plot_text)
}

# local improves when 1) trigger decreases and 2) restrictedFraction decreases

# find out time use
#


# episim <- episim_raw %>% pivot_longer(!c())
