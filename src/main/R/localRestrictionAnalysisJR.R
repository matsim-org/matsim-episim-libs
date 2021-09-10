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

rm(list = ls())
setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

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
    filter(grepl("rki", scenario) | scenario == "th1_multoff" | scenario == "th0.9_mult1.5")


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
  directoryC <- "2021-09-09-masterC/"

  trigger_plot <- 5
  restricted_fraction_plot <- 0.2

  episim_all_runs_raw <- read_and_process_episim_infections(directoryC, "FacilityToDistrictMapCOMPLETE.txt")

  episim_all_runs <- episim_all_runs_raw %>%
    filter(restrictedFraction == restricted_fraction_plot) %>%
    ungroup() %>%
    select(-c("restrictedFraction")) %>%
    pivot_wider(names_from = c(adaptivePolicy, trigger), values_from = infections, values_fill = 0)

  # merge datasets and make tidy
  merged_weekly <- merge_tidy_average(episim_all_runs, gbl_rki_new, gbl_rki_old)

  # Facet Plot - all districts - Incidenz
  rki_and_episim_incidenz <- merged_weekly %>%
    left_join(gbl_agent_count_per_district) %>%
    mutate(incidence = infections / population * 100000 * 7)

  to_plot <- rki_and_episim_incidenz %>%
    # filter(!grepl("rki", scenario)) %>%
    filter(grepl(paste0("_",trigger_plot,"$"), scenario)) %>%
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

  ggsave(filename = "resultsC_trigger5.png", path = "images/")

  # get initial, restricted, open policies
  if (FALSE) {

    ar_raw <- read_combine_episim_output(directoryC, "adaptiveRestrictions.tsv")

    ar_filtered <- ar_raw %>%
      filter(activity == "leisure") %>%
      filter(trigger == 5) %>%
      filter(restrictedFraction == 0.2) %>%
      filter(adaptivePolicy == "yesLocal") %>%
      select(date, location, policy)

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
      scale_x_datetime(breaks = breaks_width("1 month"), labels = date_format("%b %y"))

    ### Create a legend
    data_legend <- data.frame(policy = c("initial", "restricted", "open"), color = c("gray", "indianred1", "royalblue1"))
    data_legend$start <- as.Date("2020-01-01")
    data_legend$end <- as.Date("2020-01-02")
    data_legend
    plot_legend <- gg_vistime(data = data_legend,
                              col.event = "policy",
                              col.color = "color",
                              show_labels = TRUE,
                              linewidth = 20,
                              title = "Legend")
    # plot_legend

    # Tweak the legend plot
    plot_legend <- plot_legend +
      theme_void() +
      ggplot2::theme(
        plot.title = element_text(size = 11),
        axis.title.x = element_blank(),
        axis.text.x = element_blank(),
        axis.ticks.x = element_blank(),
        axis.title.y = element_blank(),
        axis.text.y = element_blank(),
        axis.ticks.y = element_blank())
    # plot_legend


    ### Combine the main plot and legend into a single figure
    plot_combined <- plot_grid(plot_data, plot_legend,
                               rel_widths = c(1, 0.1))
    plot_combined
  }

  ## Time Use
  if (FALSE) {

    time_raw <- read_combine_episim_output(directoryC, "timeUse.txt")

    time <- time_raw %>%
      pivot_longer(!c("day", "date","seed","restrictedFraction","trigger","adaptivePolicy"), names_to = "activity", values_to = "time") %>%
      filter(activity == "leisure") %>%
      # filter(adaptivePolicy == "yesGlobal" | adaptivePolicy== "yesLocal") %>%
      filter(restrictedFraction == restricted_fraction_plot) %>%
      filter(trigger == trigger_plot)

    time_avg <- time %>%
      select(-c(activity, restrictedFraction, trigger, seed, day)) %>%
      mutate(week = isoweek(date)) %>% # iso 8601 standard: week always begins with Monday; week 1 must contains January 4th
      mutate(year = isoyear(date)) %>%
      group_by(across(-c(time, date))) %>%
      summarise(time = mean(time, na.rm = TRUE), date = mean(date, na.rm = TRUE)) %>%
      ungroup() %>%
      select(!c(week, year))

    ggplot(time_avg %>% filter(date > ymd("2020-08-01"))) +
      geom_line(aes(date, time, col = adaptivePolicy))

    yesGlobal <- time %>% filter(adaptivePolicy == "yesGlobal")
    yesLocal <- time %>% filter(adaptivePolicy == "yesLocal")
    no <- time %>% filter(adaptivePolicy == "no")

    require(Bolstad2)
    int_no <- sintegral(no$day, no$time)$int
    int_glo <- sintegral(yesGlobal$date, yesGlobal$time)$int
    int_loc <- sintegral(yesLocal$date, yesLocal$time)$int

    int_no
    int_glo
    int_loc
    (int_loc - int_glo) / int_glo * 100 # 6 % improvement
  }

}


# local improves when 1) trigger decreases and 2) restrictedFraction decreases

# find out time use
#


# episim <- episim_raw %>% pivot_longer(!c())
