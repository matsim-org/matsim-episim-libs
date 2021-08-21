# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 7/22/2021


# Functions:
read_and_process_episim_events_BATCH <- function(infection_events_directory, facilities_to_district_map) {
  fac_to_district_map <- read_delim(facilities_to_district_map,
                                    ";", escape_double = FALSE, col_names = FALSE,
                                    trim_ws = TRUE) %>%
    rename("facility" = "X1") %>%
    rename("district" = "X2")

  fac_to_district_map[is.na(fac_to_district_map)] <- "not_berlin"

  info_df <- read_delim(paste0(infection_events_directory, "_info.txt"), delim = ";")

  # gathers column names that should be included in final dataframe
  col_names <- colnames(info_df)
  relevant_cols <- col_names[!col_names %in% c("RunScript", "RunId", "Config", "Output")]

  episim_df_all_runs <- data.frame()

  for (row in seq_len(nrow(info_df))) {

    runId <- info_df$RunId[row]
    seed <- info_df$seed[row]

    file_name <- paste0(infection_events_directory, runId, ".infectionEvents.txt")

    if(!file.exists(file_name)) {
      warning(paste0(file_name, " does not exist"))
      next
    }

    df_for_run <- read_delim(file = file_name,
                             "\t", escape_double = FALSE, trim_ws = TRUE) %>%
      select(date, facility)

    # adds important variables concerning run to df, so that individual runs can be filtered in later steps
    for (var in relevant_cols) {
      df_for_run[var] <- info_df[row, var]
    }

    episim_df_all_runs <- rbind(episim_df_all_runs, df_for_run)

  }

  episim_df2 <- episim_df_all_runs %>% filter(!grepl("^tr_", facility))

  merged <- episim_df2 %>%
    left_join(fac_to_district_map, by = c("facility"), keep = TRUE)

  na_facs <- merged %>%
    filter(is.na(district)) %>%
    pull(facility.x)
  length(unique(na_facs))

  episim_final <- merged %>%
    filter(!is.na(district)) %>%
    filter(district != "not_berlin") %>%
    select(!starts_with("facility")) %>%
    group_by_all() %>%
    count() %>%
    group_by(across(c(-n, -seed))) %>%
    summarise(infections = mean(n))

  return(episim_final)
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
    rename(district = LK) %>%
    mutate(cases = cases / 7)

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

plot_allDistrict_cases <- function(merged_weekly, color_scheme) {
  ggplot(merged_weekly, aes(x = week_year, y = infections)) +
    geom_line(aes(color = scenario)) +
    scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
    labs(
      # title = paste0("Infections per Day for Berlin Districts (Weekly Average)"),
      #    subtitle = "Comparison of Local vs. Global Activity Reductions",
         x = "Date", y = "New Infections") +
    theme(axis.text.x = element_text(angle = 90)) +                                        # Adjusting colors of line plot in ggplot2
    scale_color_manual(values = color_scheme) +
    facet_wrap(~district, ncol = 4)
}