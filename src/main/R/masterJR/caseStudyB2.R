# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 11/17/2021


if (TRUE) {
  library(readxl)
  library(tidyverse)
  library(janitor)
  library(lubridate)
  library(ggthemes)
  library(scales)
  library(cowplot)
  library(vistime)
  library(data.table)
  library(grid)
  library(gridExtra)
  library(gt)
  library(glue)


  rm(list = ls())
  setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

  gbl_image_output <- "C:/Users/jakob/projects/60eeeb14daadd7ca9fc56fea/images/"
  gbl_directory <- "master/b2/"

  # load all functions
  source("C:/Users/jakob/projects/matsim-episim/src/main/R/masterJR-utils.R", encoding = 'utf-8')

}


#########################################################################################
########################## LOAD, PREPARE, AND SAVE DATA #################################
#########################################################################################

# This only has to be done once; later the data can be simply loaded

# Infection Data
if (FALSE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  run_params <- get_run_parameters(gbl_directory)

  # infections in each berlin district
  episim_infections_district <- read_combine_episim_output(gbl_directory, "infections_subdistrict.txt", TRUE)

  # incidence in each berlin district
  episim_incidence_district <- convert_infections_into_incidence(gbl_directory, episim_infections_district, FALSE) %>%
    rename("Trigger" = trigger, "Rf" = restrictedFraction)

  # incidence in each berlin district, only seed 4711
  episim_incidence_district_4711 <- episim_incidence_district %>% filter(seed == 4711)

  # incidence in each berlin district, aggregated over all seeds
  episim_incidence_district_agg <- episim_incidence_district %>%
    group_by(across(-c(nShowingSymptomsCumulative, infections, infections_week, incidence, seed))) %>%
    summarise(infections = mean(infections),
              nShowingSymptomsCumulative = mean(nShowingSymptomsCumulative),
              infections_week = mean(infections_week),
              incidence = mean(incidence))


  # infections for all of berlin
  episim_infections_berlin <- episim_infections_district %>%
    filter(district != "unknown") %>%
    group_by(across(run_params), date) %>%
    summarise(across(nSusceptible:nTested, ~sum(.x, na.rm = TRUE))) %>%
    mutate(district = "Berlin")

  # incidence for all of berlin, aggregated over all seeds
  episim_incidence_berlin <- convert_infections_into_incidence(gbl_directory, episim_infections_berlin, TRUE) %>%
    rename("Trigger" = trigger, "Rf" = restrictedFraction)

  save(episim_infections_district, file = paste0(gbl_directory, "episim_infections_district"))
  save(episim_incidence_district, file = paste0(gbl_directory, "episim_incidence_district"))
  save(episim_incidence_district_4711, file = paste0(gbl_directory, "episim_incidence_district_4711"))
  save(episim_incidence_district_agg, file = paste0(gbl_directory, "episim_incidence_district_agg"))
  save(episim_infections_berlin, file = paste0(gbl_directory, "episim_infections_berlin"))
  save(episim_incidence_berlin, file = paste0(gbl_directory, "episim_incidence_berlin"))
}

# Time Use Data
if (FALSE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))

  run_params <- get_run_parameters(gbl_directory)

  # raw timeUse
  time_raw <- read_combine_episim_output(gbl_directory, "timeUse.txt", FALSE)


  # pivots activity columns to single column
  time <- time_raw %>%
    pivot_longer(!c("day", "date", paste0(run_params)), names_to = "activity", values_to = "time") %>%
    rename("Rf" = restrictedFraction, "Trigger" = trigger)


  # aggregated over x seeds
  time_agg <- time %>%
    group_by(across(-c(seed, time))) %>%
    summarise(time = mean(time))

  # timeUse for single seed: 4711
  time_4711 <- time %>% filter(seed == 4711) %>% select(-seed)

  # time spent outside per day
  time_outside_daily <- time_agg %>%
    filter(activity != "home" & activity != "quarantine_home") %>%
    ungroup() %>%
    select(-day) %>%
    group_by(across(-c(time, activity))) %>% # get total time per day (sum of all activities)
    summarise(time = sum(time))

  # time spent outside per day (weekly average)
  time_outside_weekly_avg <- time_outside_daily %>%
    ungroup() %>%
    mutate(week = isoweek(date)) %>% # iso 8601 standard: week always begins with Monday; week 1 must contains January 4th
    mutate(year = isoyear(date)) %>%
    group_by(across(-c(time, date))) %>%
    summarise(time = mean(time, na.rm = TRUE), date = mean(date, na.rm = TRUE)) %>%
    ungroup() %>%
    select(!c(week, year))


  save(time_raw, file = paste0(gbl_directory, "time_raw"))
  save(time, file = paste0(gbl_directory, "time"))
  save(time_agg, file = paste0(gbl_directory, "time_agg"))
  save(time_4711, file = paste0(gbl_directory, "time_4711"))
  save(time_outside_daily, file = paste0(gbl_directory, "time_outside_daily"))
  save(time_outside_weekly_avg, file = paste0(gbl_directory, "time_outside_weekly_avg"))
}


# Adaptive Restriction Data
if (FALSE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))

  ar_raw <- read_combine_episim_output(gbl_directory, "adaptiveRestrictions.tsv", FALSE)

  # adaptive restrictions for leisure activities; global restrictions renamed as ----Global----
  ar_leisure <- ar_raw %>%
    filter(activity == "leisure") %>%
    filter((adaptivePolicy == "yesLocal" & location != "total") | adaptivePolicy == "yesGlobal") %>%
    mutate(location = str_replace(location, "total", "----Global----")) %>%
    select(date, seed, location, policy, restrictedFraction, trigger) %>%
    rename(Rf = "restrictedFraction", Trigger = "trigger") %>%
    arrange(location)


  # adaptive restrictions for seed 4711
  ar_leisure_4711 <- ar_leisure %>%
    filter(seed == 4711) %>%
    select(-seed)

  save(ar_raw, file = paste0(gbl_directory, "ar_raw"))
  save(ar_leisure, file = paste0(gbl_directory, "ar_leisure"))
  save(ar_leisure_4711, file = paste0(gbl_directory, "ar_leisure_4711"))
}

##########################################################################
######################## PLOTS & TABLES ##################################
##########################################################################


## Plot: incidences (single seed, single rf & trigger) with overlaid timelines
if (TRUE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  start_date <- ymd("2020-07-18")
  end_date <- ymd("2021-02-17")

  rf <- 0.4
  trig <- 25.

  # prepare incidences
  load(paste0(gbl_directory, "episim_incidence_district_4711"))
  incidences <- episim_incidence_district_4711 %>%
    rename("Scenario" = "adaptivePolicy") %>%
    filter(Scenario == "yesLocal") %>%
    filter(Trigger == trig) %>%
    filter(Rf == rf) %>%
    filter(date >= start_date & date < end_date) %>%
    mutate(district = str_replace(district, "_", "-"))


  # prepare restriction timeline
  load(paste0(gbl_directory, "ar_leisure_4711"))

  ar_filtered <- ar_leisure_4711 %>%
    filter(date >= start_date & date <= end_date) %>%
    filter(Rf == rf & Trigger == trig)

  ar_timeline <- make_timeline_data(ar_filtered) %>% filter(location != "----Global----")


  # combine:
  plot_list <- list()
  districts <- unique(incidences$district)
  for (i in seq(length(districts))) {
    district_to_plot <- districts[i]
    plot1 <- ggplot(incidences %>% filter(district == district_to_plot)) +
      geom_line(aes(date, incidence)) +
      theme_minimal(base_size = 11) +
      theme(legend.position = "none", axis.text.x = element_text(angle = 45, hjust = 1)) +
      labs(x = NULL, y = NULL) +
      lims(y = c(0, 75)) +
      scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
      facet_wrap(~district, ncol = 3)

    timeline_data_district <- ar_timeline %>% filter(location == district_to_plot)
    plot2 <- plot1
    for (row in seq_len(nrow(timeline_data_district))) {
      plot2 <- plot2 + annotate("rect", xmin = timeline_data_district[row, "start"], xmax = timeline_data_district[row, "end"], ymin = trig, ymax = Inf, alpha = 0.5, fill = timeline_data_district[row, "color"])
    }

    if (!i %in% c(1, 4, 7, 10)) {
      plot2 <- plot2 + theme(axis.text.y = element_blank())
    }

    if (!i %in% c(10, 11, 12)) {
      plot2 <- plot2 + theme(axis.text.x = element_blank())
    }

    plot_list[[i]] <- plot2

  }

  legend <- make_legend()
  plot_combined <- plot_grid(legend, NULL, NULL,
                             plot_list[[1]], plot_list[[2]], plot_list[[3]],
                             plot_list[[4]], plot_list[[5]], plot_list[[6]],
                             plot_list[[7]], plot_list[[8]], plot_list[[9]],
                             plot_list[[10]], plot_list[[11]], plot_list[[12]],
                             rel_heights = c(0.05, 1.0, 1.0, 1.0, 1.1), ncol = 3, nrow = 5)


  x_lab <- textGrob("Date",
                    gp = gpar(col = "black", fontsize = 15))

  y_lab <- textGrob("7-Day Infections / 100k Pop",
                    gp = gpar(col = "black", fontsize = 15), rot = 90)
  final_plot <- grid.arrange(arrangeGrob(plot_combined, left = y_lab, bottom = x_lab))

  scale_factor <- 2.5
  ggsave(final_plot, filename = "b2_4711_incidence_timeline.png", path = gbl_image_output, width = 16 * scale_factor, height = 12 * scale_factor, units = "cm")
  ggsave(final_plot, filename = "b2_4711_incidence_timeline.pdf", path = gbl_image_output, width = 16 * scale_factor, height = 12 * scale_factor, units = "cm")
}

## Plot: timelines at extreme values
if (TRUE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  start_date <- ymd("2020-07-18")
  end_date <- ymd("2021-02-17")

  load(paste0(gbl_directory, "ar_leisure_4711"))
  ar_filtered <- ar_leisure_4711 %>%
    filter(date >= start_date & date <= end_date) %>%
    filter(location != "----Global----")


  ar1 <- ar_filtered %>% filter(Rf == 0.0 & Trigger == 10)
  ar2 <- ar_filtered %>% filter(Rf == 0.0 & Trigger == 150)
  ar3 <- ar_filtered %>% filter(Rf == 0.6 & Trigger == 10)
  ar4 <- ar_filtered %>% filter(Rf == 0.6 & Trigger == 150)

  plot_data1 <- make_timeline(ar1, "Rf = 0.0, Trigger = 10")
  plot_data2 <- make_timeline(ar2, "Rf = 0.0, Trigger = 150")
  plot_data3 <- make_timeline(ar3, "Rf = 0.6, Trigger = 10")
  plot_data4 <- make_timeline(ar4, "Rf = 0.6, Trigger = 150")
  lockdown_weeks1 <- make_text(ar1)
  lockdown_weeks2 <- make_text(ar2)
  lockdown_weeks3 <- make_text(ar3)
  lockdown_weeks4 <- make_text(ar4)


  legend <- make_legend()
  timelines <- plot_grid(plot_data1, lockdown_weeks1, plot_data2, lockdown_weeks2, plot_data3, lockdown_weeks3, plot_data4, lockdown_weeks4, legend, NULL, nrow = 3, rel_heights = c(1, 1, 0.05), rel_widths = c(1, 0.125, 1, 0.125))
  timelines
  scale_factor <- 2.5
  ggsave(timelines, filename = "b2_timelines.png", path = gbl_image_output, width = 16 * scale_factor, height = 12 * scale_factor, units = "cm")
  ggsave(timelines, filename = "b2_timelines.pdf", path = gbl_image_output, width = 16 * scale_factor, height = 12 * scale_factor, units = "cm")

}


## Plot: incidences at extreme values
if (TRUE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  load(paste0(gbl_directory, "episim_incidence_berlin"))

  start_date <- ymd("2020-07-18")
  end_date <- ymd("2021-02-17")
  rfs <- c(min(episim_incidence_berlin$Rf), max(episim_incidence_berlin$Rf))
  triggers <- c(min(episim_incidence_berlin$Trigger), max(episim_incidence_berlin$Trigger))


  to_plot <- episim_incidence_berlin %>%
    filter(Rf %in% rfs) %>%
    filter(Trigger %in% triggers) %>%
    filter(date >= start_date & date <= end_date)


  to_plot_text <- to_plot %>%
    group_by(adaptivePolicy, Rf, Trigger) %>%
    summarise(infections = sum(infections)) %>%
    mutate(lab = paste0(adaptivePolicy, " : ", round(infections, 0))) %>%
    group_by(Rf, Trigger) %>%
    summarise(lab = paste(lab, collapse = "\n")) %>%
    mutate(lab = paste0("Total Infections: \n", lab))


  plot <- ggplot(to_plot) +
    geom_line(aes(x = date, y = incidence, col = adaptivePolicy)) +
    facet_grid(Rf ~ Trigger, labeller = labeller(Rf = label_both, Trigger = label_both)) +
    labs(x = "Date", y = "7-Day Infections / 100k Pop.") +
    theme_minimal(base_size = 11) +
    theme(legend.position = "bottom", axis.text.x = element_text(angle = 45, hjust = 1)) +
    scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
    scale_color_manual(values = c("grey40", "blue", "red")) +
    scale_y_continuous(trans = 'log10') +
    geom_text(data = to_plot_text, mapping = aes(ymd("2020-07-20"), 150, label = lab), size = 2, hjust = "left")


  plot

  save_png_pdf(plot, "b2_incidence")

}

# Plot: time use at extremes values
if (TRUE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  load(paste0(gbl_directory, "time_outside_weekly_avg"))
  rfs <- c(min(time_outside_weekly_avg$Rf), max(time_outside_weekly_avg$Rf))
  triggers <- c(min(time_outside_weekly_avg$Trigger), max(time_outside_weekly_avg$Trigger))

  start_date <- ymd("2020-07-18")
  end_date <- ymd("2021-02-17")

  to_plot <- time_outside_weekly_avg %>%
    filter(Rf %in% rfs) %>%
    filter(Trigger %in% triggers) %>%
    filter(date >= start_date & date <= end_date)

  to_plot_text <- to_plot %>%
    group_by(adaptivePolicy, Rf, Trigger) %>%
    summarise(time = mean(time)) %>%
    mutate(lab = paste0(adaptivePolicy, " : ", round(time, 0))) %>%
    group_by(Rf, Trigger) %>%
    summarise(lab = paste(lab, collapse = "\n")) %>%
    mutate(lab = paste0("Avg. Time-Use: \n", lab))


  plot <- ggplot(to_plot) +
    geom_line(aes(date, time, col = adaptivePolicy)) +
    facet_grid(Rf ~ Trigger, labeller = labeller(Rf = label_both, Trigger = label_both)) +
    labs(x = "Date", y = "Daily Time Outside of Home (min), Weekly Average") +
    theme_minimal(base_size = 11) +
    theme(legend.position = "bottom", axis.text.x = element_text(angle = 45, hjust = 1)) +
    scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
    scale_color_manual(values = c("grey40", "blue", "red")) +
    geom_text(data = to_plot_text, mapping = aes(ymd("2020-07-20"), 80, label = lab), size = 2., hjust = "left")

  plot

  save_png_pdf(plot, "b2_timeUse")
}

# Tables: local vs. base, infections saved vs. time lost (fixed time-frame)
if (TRUE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  run_params <- get_run_parameters(gbl_directory)


  # load infections & time
  load(paste0(gbl_directory, "episim_incidence_berlin"))
  load(paste0(gbl_directory, "time_outside_daily"))
  load(paste0(gbl_directory, "time_outside_weekly_avg"))
  trigs <- as.character(unique(time_outside_daily$Trigger))

  start_date <- ymd("2020-07-18")
  end_date <- ymd("2021-02-17")


  # calc average infections per day
  infections <- episim_incidence_berlin %>%
    ungroup() %>%
    select(date, adaptivePolicy, Rf, Trigger, infections) %>%
    filter(date >= start_date & date <= end_date) %>%
    group_by(adaptivePolicy, Rf, Trigger) %>%
    summarise(infections = mean(infections)) %>%
    pivot_wider(names_from = adaptivePolicy, names_prefix = "inf_", values_from = infections)


  # time spent outside per day (average over entire study period)
  time <- time_outside_daily %>%
    ungroup() %>%
    filter(date >= start_date & date <= end_date) %>%
    group_by(across(-c(time, date))) %>%
    summarise(time = mean(time, na.rm = TRUE)) %>%
    pivot_wider(names_from = adaptivePolicy, names_prefix = "time_", values_from = time)

  # combine
  combined <- infections %>%
    left_join(time, by = c("Rf", "Trigger"))

  # local vs base
  calculations <- combined %>%
    # infections
    mutate(delta_inf_loc = inf_no - inf_yesLocal) %>%
    mutate(delta_inf_glo = inf_no - inf_yesGlobal) %>%
    mutate(pct_inf_loc = delta_inf_loc / inf_no * 100) %>%
    mutate(pct_inf_glo = delta_inf_glo / inf_no * 100) %>%
    # time
    mutate(delta_time_loc = time_no - time_yesLocal) %>%
    mutate(delta_time_glo = time_no - time_yesGlobal) %>%
    mutate(pct_time_loc = delta_time_loc / time_no * 100) %>%
    mutate(pct_time_glo = delta_time_glo / time_no * 100) %>%
    # combined metrics
    mutate(eff_loc = delta_inf_loc / delta_time_loc) %>%
    mutate(eff_glo = delta_inf_glo / delta_time_glo) %>%
    mutate(eff_diff = eff_loc - eff_glo) %>%
    mutate(eff_pct_change = eff_diff / eff_glo * 100)


  # Table 1: pct changes for local vs. base, infections
  tbl <- calculations %>%
    select(Trigger, Rf, pct_inf_loc) %>%
    pivot_wider(names_from = Trigger, values_from = pct_inf_loc) %>%
    ungroup() %>%
    make_gt_table()

  tbl
  gtsave(tbl, "b2_tbl_pct_inf_loc.png", path = gbl_image_output)
  gtsave(tbl, "b2_tbl_pct_inf_loc.pdf", path = gbl_image_output)

  # Table 2: pct changes for local vs. base, time
  tbl <- calculations %>%
    select(Trigger, Rf, pct_time_loc) %>%
    pivot_wider(names_from = Trigger, values_from = pct_time_loc) %>%
    ungroup() %>%
    make_gt_table()

  tbl
  gtsave(tbl, "b2_tbl_pct_time_loc.png", path = gbl_image_output)
  gtsave(tbl, "b2_tbl_pct_time_loc.pdf", path = gbl_image_output)

  # Table 3:  change infections /  change time (local)
  tbl <- calculations %>%
    select(Trigger, Rf, eff_loc) %>%
    pivot_wider(names_from = Trigger, values_from = eff_loc) %>%
    ungroup() %>%
    make_gt_table()

  tbl
  gtsave(tbl, "b2_tbl_eff_loc.png", path = gbl_image_output)
  gtsave(tbl, "b2_tbl_eff_loc.pdf", path = gbl_image_output)

  # Table 4:  local vs. global, difference in efficiences (eff_loc-eff_glo)
  # tbl <- calculations %>%
  #   select(Trigger, Rf, eff_diff) %>%
  #   pivot_wider(names_from = Trigger, values_from = eff_diff) %>%
  #   ungroup() %>%
  #   make_gt_table()
  #
  # tbl
  # gtsave(tbl, "b2_eff_diff.png", path = gbl_image_output)
  # gtsave(tbl, "b2_eff_diff.pdf", path = gbl_image_output)
  #
  # # Table 5:  local vs. global, percent diff in efficiences
  # tbl <- calculations %>%
  #   select(Trigger, Rf, eff_pct_change) %>%
  #   pivot_wider(names_from = Trigger, values_from = eff_pct_change) %>%
  #   ungroup() %>%
  #   make_gt_table()
  #
  # tbl
  # gtsave(tbl, "b2_eff_pct_change.png", path = gbl_image_output)
  # gtsave(tbl, "b2_eff_pct_change.pdf", path = gbl_image_output)

  # APPENDIX TABLES (global runs)
  # Table 1: pct changes for global vs. base, infections
  tbl <- calculations %>%
    select(Trigger, Rf, pct_inf_glo) %>%
    pivot_wider(names_from = Trigger, values_from = pct_inf_glo) %>%
    ungroup() %>%
    make_gt_table()

  tbl
  gtsave(tbl, "app_tbl_pct_inf_glo.png", path = gbl_image_output)
  gtsave(tbl, "app_tbl_pct_inf_glo.pdf", path = gbl_image_output)

  # Table 2: pct changes for global vs. base, time
  tbl <- calculations %>%
    select(Trigger, Rf, pct_time_glo) %>%
    pivot_wider(names_from = Trigger, values_from = pct_time_glo) %>%
    ungroup() %>%
    make_gt_table()

  tbl
  gtsave(tbl, "app_tbl_pct_time_glo.png", path = gbl_image_output)
  gtsave(tbl, "app_tbl_pct_time_glo.pdf", path = gbl_image_output)

  # Table 3:  change infections /  change time (global)
  tbl <- calculations %>%
    select(Trigger, Rf, eff_glo) %>%
    pivot_wider(names_from = Trigger, values_from = eff_glo) %>%
    ungroup() %>%
    make_gt_table()

  tbl
  gtsave(tbl, "app_tbl_eff_glo.png", path = gbl_image_output)
  gtsave(tbl, "app_tbl_eff_glo.pdf", path = gbl_image_output)


}

# Tables: local vs. base, infections saved vs. time lost (variable time-frame)
if (TRUE) {
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
  run_params <- get_run_parameters(gbl_directory)


  # load infections & time
  load(paste0(gbl_directory, "episim_incidence_berlin"))
  load(paste0(gbl_directory, "time_outside_daily"))
  load(paste0(gbl_directory, "time_outside_weekly_avg"))

  start_date <- ymd("2020-07-18")

  # Here we find the custom end_dates --> these are manually found and saved in a csv
  if (FALSE) {
    time_filtered <- time_outside_weekly_avg %>%
      filter(adaptivePolicy == "yesGlobal") %>%
      filter(Rf == 0.6) %>%
      filter(Trigger == 150) %>%
      filter(date > start_date)

    ggplot(time_filtered) +
      geom_line(aes(date, time)) +
      scale_x_date(date_breaks = "1 month", date_labels = "%b-%y")

    # local min
    time_filtered[localMinima(time_filtered$time), "date"]
  }

  # read in custom end dates (generated from above process)
  custom_end_dates <- read_excel("C:/Users/jakob/Desktop/end_dates.xlsx", col_types = c("text", "numeric", "numeric", "text", "text")) %>%
    mutate(start_date = as.Date(start_date, "%Y-%m-%d")) %>%
    mutate(end_date = str_replace(end_date, "[0-9] ", "")) %>%
    mutate(end_date = as.Date(end_date, "%Y-%m-%d"))


  time_all <- data.frame()
  infections_all <- data.frame()

  time_base <- time_outside_daily %>%
    ungroup()

  infections_base <- episim_incidence_berlin %>%
    ungroup() %>%
    select(date, adaptivePolicy, Rf, Trigger, infections)

  for (row_num in seq(nrow(custom_end_dates))) {
    # get all variables set up
    adaptiveP <- custom_end_dates[[row_num, "adaptivePolicy"]]
    trig <- custom_end_dates[[row_num, "Trigger"]]
    rf <- custom_end_dates[[row_num, "Rf"]]
    start_date <- custom_end_dates[[row_num, "start_date"]]
    end_date <- custom_end_dates[[row_num, "end_date"]]

    # do infection calculations
    infections_temp <- infections_base %>%
      filter(adaptivePolicy == adaptiveP | adaptivePolicy == "no") %>%
      mutate(adaptivePolicy = str_replace(adaptivePolicy, "no", paste0(adaptiveP, "_base"))) %>%
      filter(Trigger == trig) %>%
      filter(Rf == rf) %>%
      filter(date >= start_date & date <= end_date)

    # time calculations
    time_temp <- time_base %>%
      filter(adaptivePolicy == adaptiveP | adaptivePolicy == "no") %>%
      mutate(adaptivePolicy = str_replace(adaptivePolicy, "no", paste0(adaptiveP, "_base"))) %>%
      filter(Trigger == trig) %>%
      filter(Rf == rf) %>%
      filter(date >= start_date & date <= end_date)

    time_all <- rbind(time_all, time_temp)
    infections_all <- rbind(infections_all, infections_temp)

  }


  # calc average infections per day
  infections <- infections_all %>%
    group_by(adaptivePolicy, Rf, Trigger) %>%
    summarise(infections = mean(infections)) %>%
    pivot_wider(names_from = adaptivePolicy, names_prefix = "inf_", values_from = infections)


  # time spent outside per day (average over entire study period)
  time <- time_all %>%
    group_by(across(-c(time, date))) %>%
    summarise(time = mean(time, na.rm = TRUE)) %>%
    pivot_wider(names_from = adaptivePolicy, names_prefix = "time_", values_from = time)

  # combine
  combined <- infections %>%
    left_join(time, by = c("Rf", "Trigger"))

  calculations <- combined %>%
    # infections
    mutate(delta_inf_loc = inf_yesLocal_base - inf_yesLocal) %>%
    mutate(delta_inf_glo = inf_yesGlobal_base - inf_yesGlobal) %>%
    mutate(pct_inf_loc = delta_inf_loc / inf_yesLocal_base * 100) %>%
    mutate(pct_inf_glo = delta_inf_glo / inf_yesGlobal_base * 100) %>%
    # time
    mutate(delta_time_loc = time_yesLocal_base - time_yesLocal) %>%
    mutate(delta_time_glo = time_yesGlobal_base - time_yesGlobal) %>%
    mutate(pct_time_loc = delta_time_loc / time_yesLocal_base * 100) %>%
    mutate(pct_time_glo = delta_time_glo / time_yesGlobal_base * 100) %>%
    # combined metrics
    mutate(eff_loc = delta_inf_loc / delta_time_loc) %>%
    mutate(eff_glo = delta_inf_glo / delta_time_glo) %>%
    mutate(eff_diff = eff_loc - eff_glo) %>%
    mutate(eff_pct_change = eff_diff / eff_glo * 100)

  trigs <- as.character(unique(calculations$Trigger))


  # Table 1: pct changes for local vs. base, impacts, variable time frame
  tbl <- calculations %>%
    select(Trigger, Rf, eff_pct_change) %>%
    pivot_wider(names_from = Trigger, values_from = eff_pct_change) %>%
    ungroup() %>%
    make_gt_table()

  tbl
  gtsave(tbl, "b2_eff_pct_change_variableDates.png", path = gbl_image_output)
  gtsave(tbl, "b2_eff_pct_change_variableDates.pdf", path = gbl_image_output)
}






