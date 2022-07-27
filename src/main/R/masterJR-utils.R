# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 7/22/2021

library(data.table)


# Functions:
# read_and_process_episim_infections <- function(directory, facilities_to_district_map) {
#   fac_to_district_map <- read_delim(facilities_to_district_map,
#                                     ";", escape_double = FALSE, col_names = FALSE,
#                                     trim_ws = TRUE) %>%
#     rename("facility" = "X1") %>%
#     rename("district" = "X2")
#
#   fac_to_district_map[is.na(fac_to_district_map)] <- "not_berlin"
#
#   info_df <- read_delim(paste0(directory, "_info.txt"), delim = ";")
#
#   # gathers column names that should be included in final dataframe
#   col_names <- colnames(info_df)
#   relevant_cols <- col_names[!col_names %in% c("RunScript", "RunId", "Config", "Output")]
#
#   episim_df_all_runs <- data.frame()
#
#   for (row in seq_len(nrow(info_df))) {
#
#     runId <- info_df$RunId[row]
#     seed <- info_df$seed[row]
#
#     file_name <- paste0(directory, runId, ".infectionEvents.txt")
#
#     if (!file.exists(file_name)) {
#       warning(paste0(file_name, " does not exist"))
#       next
#     }
#
#     df_for_run <- read_delim(file = file_name,
#                              "\t", escape_double = FALSE, trim_ws = TRUE) %>%
#       select(date, facility)
#
#     # adds important variables concerning run to df, so that individual runs can be filtered in later steps
#     for (var in relevant_cols) {
#       df_for_run[var] <- info_df[row, var]
#     }
#
#     episim_df_all_runs <- rbind(episim_df_all_runs, df_for_run)
#
#   }
#
#   episim_df2 <- episim_df_all_runs %>% filter(!grepl("^tr_", facility))
#
#   merged <- episim_df2 %>%
#     left_join(fac_to_district_map, by = c("facility"), keep = TRUE)
#
#   na_facs <- merged %>%
#     filter(is.na(district)) %>%
#     pull(facility.x)
#   length(unique(na_facs))
#
#   episim_final <- merged %>%
#     filter(!is.na(district)) %>%
#     filter(district != "not_berlin") %>%
#     select(!starts_with("facility")) %>%
#     group_by_all() %>%
#     count() %>%
#     group_by(across(c(-n, -seed))) %>%
#     summarise(infections = mean(n))
#
#   return(episim_final)
# }

convert_infections_into_incidence <- function(directory, infections_raw, aggregate_seeds) {
  run_params <- get_run_parameters(directory)
  infections <- infections_raw %>%
    select(date, nShowingSymptomsCumulative, nSusceptible, district, run_params) %>%
    filter(district != "unknown") %>%
    arrange(date) %>%
    group_by(district, across(run_params)) %>%
    mutate(infections_1dayAgo = lag(nShowingSymptomsCumulative, default = 0, order_by = date)) %>%
    mutate(infections_7daysAgo = lag(nShowingSymptomsCumulative, default = 0, n = 7, order_by = date)) %>%
    mutate(infections = nShowingSymptomsCumulative - infections_1dayAgo) %>%
    mutate(infections_week = nShowingSymptomsCumulative - infections_7daysAgo) %>%
    mutate(population = first(nSusceptible)) %>%
    mutate(incidence = infections_week / population * 100000) %>%
    ungroup() %>%
    select(-c(population, infections_1dayAgo, infections_7daysAgo, nSusceptible)) %>%
    arrange(date)

  if (aggregate_seeds == FALSE) {
    return(infections)
  }

  infections_aggregated <- infections %>%
    group_by(across(-c(nShowingSymptomsCumulative, infections, infections_week, incidence, seed))) %>%
    summarise(infections = mean(infections),
              nShowingSymptomsCumulative = mean(nShowingSymptomsCumulative),
              infections_week = mean(infections_week),
              incidence = mean(incidence))
  return(infections_aggregated)

}


geolocate_infections <- function(infections_raw, facilities_to_district_map) {

  # read facilities
  fac_to_district_map <- read_delim(facilities_to_district_map,
                                    ";", escape_double = FALSE, col_names = FALSE,
                                    trim_ws = TRUE) %>%
    rename("facility" = "X1") %>%
    rename("district" = "X2")

  fac_to_district_map[is.na(fac_to_district_map)] <- "not_berlin"

  # filter, merge, & find count per district
  merged <- infections_raw %>%
    select(-c(time, infector, infected, infectionType, groupSize, virusStrain, probability)) %>%
    filter(!grepl("^tr_", facility)) %>%
    left_join(fac_to_district_map, by = c("facility"), keep = TRUE) %>%
    filter(!is.na(district)) %>%
    filter(district != "not_berlin") %>%
    select(!starts_with("facility")) %>%
    group_by_all() %>%
    count() %>%
    rename("infections" = "n")

  return(merged)

}


# read_and_process_episim_timeUse <- function(directory) {
#
#   info_df <- read_delim(paste0(directory, "_info.txt"), delim = ";")
#
#   # gathers column names that should be included in final dataframe
#   col_names <- colnames(info_df)
#   relevant_cols <- col_names[!col_names %in% c("RunScript", "RunId", "Config", "Output")]
#
#   episim_df_all_runs <- data.frame()
#   for (row in seq_len(nrow(info_df))) {
#
#     runId <- info_df$RunId[row]
#
#     file_name <- paste0(directory, runId, ".timeUse.txt")
#
#     if (!file.exists(file_name)) {
#       warning(paste0(file_name, " does not exist"))
#       next
#     }
#
#     df_for_run <- read_delim(file = file_name,
#                              "\t", escape_double = FALSE, trim_ws = TRUE) %>%
#       pivot_longer(!c("day", "date"), names_to = "activity", values_to = "time")
#
#     # adds important variables concerning run to df, so that individual runs can be filtered in later steps
#     for (var in relevant_cols) {
#       df_for_run[var] <- info_df[row, var]
#     }
#
#     episim_df_all_runs <- rbind(episim_df_all_runs, df_for_run)
#
#   }
# }

get_run_parameters <- function(directory) {
  info_df <- read_delim(paste0(directory, "_info.txt"), delim = ";")
  col_names <- colnames(info_df)
  run_params <- col_names[!col_names %in% c("RunScript", "RunId", "Config", "Output")]
  return(run_params)
}

read_combine_episim_output <- function(directory, file_root, allow_missing_files) {

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
    # episim_df_all_runs <- rbind(episim_df_all_runs, df_for_run) # inefficient

  }

  return(data.frame(episim_df_all_runs))
}

read_combine_episim_output_zipped <- function(directory, file_root) {

  info_df <- read_delim(paste0(directory, "_info.txt"), delim = ";")

  # gathers column names that should be included in final dataframe
  col_names <- colnames(info_df)
  relevant_cols <- col_names[!col_names %in% c("RunScript", "RunId", "Config", "Output")]

  episim_df_all_runs <- data.frame()
  for (row in seq_len(nrow(info_df))) {

    runId <- info_df$RunId[row]

    zipDir <- paste0(directory,"summaries/",runId,".zip")

    file_name <- paste0(runId, ".", file_root)

    df_for_run <- read_delim(unz(zipDir, file_name))

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

  return(data.frame(episim_df_all_runs))
}



read_and_process_new_rki_data_incidenz <- function(filename) {
  rki <- read_excel(filename,
                    sheet = "LK_7-Tage-Inzidenz (fixiert)", skip = 4)

  rki_berlin <- rki %>%
    filter(grepl("berlin", LK, ignore.case = TRUE)) %>%
    select(-c("...1", "LKNR")) %>%
    pivot_longer(!contains("LK"), names_to = "date", values_to = "cases")

  for (i in seq_len(nrow(rki_berlin))) {
    dateX <- as.character(rki_berlin$date[i])
    if (grepl("44", dateX)) {
      date_improved <- as.character(excel_numeric_to_date(as.numeric(dateX)))
      rki_berlin$date[i] <- date_improved
    } else {
      date_improved <- dateX
      rki_berlin$date[i] <- date_improved
    }
  }

  ymd <- ymd(rki_berlin$date)
  dmy <- dmy(rki_berlin$date)
  ymd[is.na(ymd)] <- dmy[is.na(ymd)] # some dates are ambiguous, here we give
  rki_berlin$date <- ymd


  rki_berlin$LK <- rki_berlin$LK %>%
    str_replace("SK Berlin ", "") %>%
    str_replace("-", "_") %>%
    str_replace("ö", "oe")


  rki_berlin <- rki_berlin %>%
    rename(district = LK, incidence = cases)

  return(rki_berlin)
}

read_and_process_new_rki_data <- function(filename) {
  rki <- read_excel(filename,
                    sheet = "LK_7-Tage-Fallzahlen (fixiert)", skip = 4)

  rki_berlin <- rki %>%
    filter(grepl("berlin", LK, ignore.case = TRUE)) %>%
    select(-c("...1", "LKNR")) %>%
    pivot_longer(!contains("LK"), names_to = "date", values_to = "cases")

  for (i in seq_len(nrow(rki_berlin))) {
    dateX <- as.character(rki_berlin$date[i])
    if (grepl("44", dateX)) {
      date_improved <- as.character(excel_numeric_to_date(as.numeric(dateX)))
      rki_berlin$date[i] <- date_improved
    } else {
      date_improved <- dateX
      rki_berlin$date[i] <- date_improved
    }
  }

  ymd <- ymd(rki_berlin$date)
  dmy <- dmy(rki_berlin$date)
  ymd[is.na(ymd)] <- dmy[is.na(ymd)] # some dates are ambiguous, here we give
  rki_berlin$date <- ymd


  rki_berlin$LK <- rki_berlin$LK %>%
    str_replace("SK Berlin ", "") %>%
    str_replace("-", "_") %>%
    str_replace("ö", "oe")

  glimpse(rki_berlin)

  rki_berlin <- rki_berlin %>%
    mutate(cases = cases / 7) %>%
    rename(district = LK, rki_new = cases)

  return(rki_berlin)
}

read_and_process_old_rki_data <- function(filename) {
  rki_old <- read_csv(filename)
  rki_berlin_old <- rki_old %>%
    filter(grepl("berlin", Landkreis, ignore.case = TRUE)) %>%
    mutate(date = as.Date(Refdatum, format = "%m/%d/%Y"), district = Landkreis) %>% ## TODO: RefDatum or Meldedaturm
    select(district, AnzahlFall, date) %>%
    group_by(district, date) %>%
    summarise(rki_cases_old = sum(AnzahlFall)) %>%
    mutate(district = str_replace(district, "SK Berlin ", "")) %>%
    mutate(district = str_replace(district, "-", "_")) %>%
    mutate(district = str_replace(district, "ö", "oe")) %>%
    rename(rki_old = rki_cases_old)
  return(rki_berlin_old)
}

merge_tidy_average <- function(episim_all_runs, rki_new, rki_old) {
  # BEFORE MERGING:
  # each dataset should have "date", "district" + a seperate column per situation (with good name)
  # delete all other columns!
  merged_tidy <- episim_all_runs %>%
    full_join(rki_new, by = c("date", "district")) %>%
    full_join(rki_old, by = c("date", "district")) %>%
    pivot_longer(!c("date", "district"), names_to = "scenario", values_to = "infections")

  merged_weekly <- merged_tidy %>%
    mutate(week = isoweek(date)) %>% # iso 8601 standard: week always begins with Monday; week 1 must contains January 4th
    mutate(year = isoyear(date)) %>%
    group_by(across(-c(infections, date))) %>%
    summarise(infections = mean(infections, na.rm = TRUE), date = mean(date, na.rm = TRUE)) %>%
    ungroup() %>%
    select(!c(week, year))

  return(merged_weekly)

}

### Plotting functions
build_plot <- function(df, scale_colors) {
  plot <- ggplot(df) +
    geom_line(aes(date, incidence, col = Scenario)) +
    theme_minimal(base_size = 11) +
    theme(legend.position = "bottom", axis.text.x = element_text(angle = 45, hjust=1)) +
    labs(x = "Date", y = "7-Day Infections / 100k Pop.") +
    scale_x_date(date_breaks = "2 month", date_labels = "%b-%y") +
    facet_wrap(~district, ncol = 3) +
    scale_color_manual(values = scale_colors)

  print(plot)

  return(plot)
}

save_png_pdf <- function(.data, name) {
  # print(.data)
  ggsave(.data, filename = paste0(name, ".png"), path = gbl_image_output, width = 16, height = 12, units = "cm")
  ggsave(.data, filename = paste0(name, ".pdf"), path = gbl_image_output, width = 16, height = 12, units = "cm")
}


### TIMELINE FUNCTIONS FOR ADAPTIVE RESTRICTIONS
make_timeline <- function(ar_filtered, title) {
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

### SETUP TIMELINE DATA
make_timeline_data <- function(ar_filtered) {
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

  return(timeline_data_final)
}

make_legend <- function() {
  data_legend <- data.frame(policy = c("Restriction Policy:", "Initial", "Restricted", "Open"), color = c(rgb(0, 0, 0, 0), "gray", "indianred1", "royalblue1"))
  data_legend$start <- c(as.Date("2020-01-01"), as.Date("2020-01-07"), as.Date("2020-01-13"), as.Date("2020-01-19"))
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
    group_by(location,policy) %>%
    count() %>%
    pivot_wider(names_from = policy, values_from = n, values_fill = 0) %>%
    mutate(weeks_restricted = paste0(round(restricted / 7, 1), " wks")) %>%
    arrange(location)


  # data = timeline_data_final, col.group = "location",
  lockdown_weeks$color <- rgb(0, 0, 0, 0)
  lockdown_weeks$start <- c(as.Date("2020-01-1"))
  lockdown_weeks$end <- c(as.Date("2020-01-2"))
  lockdown_weeks
  plot_text <- gg_vistime(data = lockdown_weeks,
                          col.group = "location",
                          col.event = "weeks_restricted",
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


  make_gt_table <- function(.data) {
    gt(.data, rowname_col = "Rf") %>%
      tab_stubhead(label = "Rf") %>%
      tab_spanner(
        label = "Trigger",
        columns = trigs
      ) %>%
      fmt_number(
        columns = trigs,
        decimals = 1,
        use_seps = FALSE
      )
  }

  localMaxima <- function(x) {
    # Use -Inf instead if x is numeric (non-integer)
    y <- diff(c(-.Machine$integer.max, x)) > 0L
    rle(y)$lengths
    y <- cumsum(rle(y)$lengths)
    y <- y[seq.int(1L, length(y), 2L)]
    if (x[[1]] == x[[2]]) {
      y <- y[-1]
    }
    y
  }


  localMinima <- function(x) {
    # Use -Inf instead if x is numeric (non-integer)
    y <- diff(c(.Machine$integer.max, x)) < 0L
    rle(y)$lengths
    y <- cumsum(rle(y)$lengths)
    y <- y[seq.int(1L, length(y), 2L)]
    if (x[[1]] == x[[2]]) {
      y <- y[-1]
    }
    y
  }
}

