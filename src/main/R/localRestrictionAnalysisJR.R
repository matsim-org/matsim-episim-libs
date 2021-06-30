# Title     : LocalRestrictionAnalysis
# Objective : Splits InfectionEvents into districts, and compares to rki data
# Created by: jakobrehmann
# Created on: 6/9/2021

library(readxl)
library(tidyverse)
library(janitor)
library(lubridate)

rm(list = ls())

setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

# Functions:
read_and_process_episim_events_BATCH <- function(infection_events_directory, facilities_to_district_map){
  fac_to_district_map <- read_delim(facilities_to_district_map,
                                    ";", escape_double = FALSE, col_names = FALSE,
                                    trim_ws = TRUE) %>%
    rename("facility" = "X1") %>%
    rename("district" = "X2")

  fac_to_district_map[is.na(fac_to_district_map)] <- "not_berlin"

  info_df <- read_delim(paste0(infection_events_directory, "_info.txt") , delim = ";" )


  episim_df_all_runs <- data.frame()

  for(row in seq_len(nrow(info_df))){
    runId <- info_df$RunId[row]
    seed <- info_df$seed[row]
    districtLevelRestrictions <- info_df$districtLevelRestrictions[row]
    df_for_run <- read_delim(file = paste0(infection_events_directory,runId,".infectionEvents.txt"),
                            "\t", escape_double = FALSE, trim_ws = TRUE) %>%
      select(date, facility) %>%
      mutate(seed = seed, districtLevelRestrictions = districtLevelRestrictions)

    episim_df_all_runs <- rbind(episim_df_all_runs,df_for_run)
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
    group_by(date, district,seed,districtLevelRestrictions) %>%
    count() %>%
    group_by(date,district,districtLevelRestrictions) %>%
    summarise(infections = mean(n))

  return(episim_final)
}

read_and_process_new_rki_data <- function(filename){
  rki <- read_excel(filename,
                    sheet = "LK_7-Tage-Fallzahlen (fixiert)", skip = 4)

  rki_berlin <- rki %>%
    filter(grepl("berlin", LK, ignore.case = TRUE)) %>%
    select(-c("...1", "LKNR")) %>%
    pivot_longer(!contains("LK"), names_to = "date", values_to = "cases")

  for (i in 1:nrow(rki_berlin)) {
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
    mutate(district = str_replace(district, "ö", "oe"))
  return(rki_berlin_old)
}

######################################################################################################

# 1) RKI Data

rki_new <- read_and_process_new_rki_data("Fallzahlen_Kum_Tab_9Juni.xlsx")
ggplot(rki_new, mapping = aes(x = date, y = cases)) +
  geom_line(aes(color = district)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
  labs(title = paste0("Daily  Cases in Berlin"), x = "Date", y = "Cases")

rki_old <- read_and_process_old_rki_data("RKI_COVID19_02112020.csv")

ggplot(rki_old %>% filter(district == "Mitte"), aes(x = date, y = rki_cases_old)) +
  geom_line(aes(color = district))


# 2) Display simulation data also per Bezirk
episim_all_runs <- read_and_process_episim_events_BATCH("2021-06-21/","FacilityToDistrictMapCOMPLETE.txt")

ggplot(episim_all_runs %>%  filter(district == "Neukoelln"),
       mapping = aes(x = date, y = infections)) +
  geom_line(mapping = aes(color = districtLevelRestrictions))

# 3) Compare data with & without localRestrictions
rki_and_episim <- episim_all_runs %>%
  pivot_wider(names_from = districtLevelRestrictions,values_from = infections, names_prefix = "districtLevelRestriction_") %>%
  full_join(rki_new, by = c("date", "district")) %>%
  rename(rki_new = cases) %>%
  full_join(rki_old, by = c("date", "district")) %>%
  mutate(month = months(date)) %>%
  mutate(week = week(date)) %>%
  mutate(year = year(date)) %>%
  group_by(district, year, week) %>%
  summarise(episim_base = mean(districtLevelRestriction_no, na.rm = TRUE),
            episim_policy = mean(districtLevelRestriction_yes, na.rm = TRUE),
            rki_new = mean(rki_new, na.rm = TRUE),
            rki_old = mean(rki_cases_old, na.rm = TRUE)) %>%
  mutate(week_year = as.Date(paste(year, week, 1, sep = "-"), "%Y-%U-%u")) %>%
  pivot_longer(c(rki_new, rki_old, episim_base,episim_policy), names_to = "data_source", values_to = "cases")

## Facet Plot - all districts

ggplot(rki_and_episim , aes(x = week_year, y = cases)) + #%>% filter(district == district_to_profile)
  geom_line(aes(color = data_source)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
  labs(title = paste0("Infections per Day for Berlin Districts (Weekly Average)"),
       subtitle = "Comparison of Local vs. Global Activity Reductions",
       x = "Date", y = "New Infections") +
  theme(axis.text.x = element_text(angle = 90))+                                        # Adjusting colors of line plot in ggplot2
  scale_color_manual(values = c("blue", "magenta", "dark grey","dark grey")) +
  facet_wrap(~district,  ncol=4)

# Single Plot for single district
all_districts <- unique(rki_and_episim$district)
district_to_profile <- "Treptow_Koepenick"
ggplot(rki_and_episim %>% filter(district == district_to_profile), aes(x = week_year, y = cases)) +
  geom_line(aes(color = data_source)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
  labs(title = paste0("Infections per Day for ",district_to_profile," (Weekly Average)"),
       subtitle = "Comparison of Local vs. Global Activity Reductions",
       x = "Date", y = "New Infections") +
  theme(axis.text.x = element_text(angle = 90))+                                        # Adjusting colors of line plot in ggplot2
  scale_color_manual(values = c("blue", "magenta", "dark grey","dark grey"))


agent_count_per_district <- read_delim("C:/Users/jakob/projects/matsim-episim/AgentCntPerDistrict.txt", delim = ";", col_names = FALSE) %>%
  rename(district = X1, population = X2) %>% mutate(population = population * 4)

## Facet Plot - all districts - Incidenz
rki_and_episim_incidenz <- rki_and_episim %>%
  left_join(agent_count_per_district) %>%
  mutate(incidence = cases/population * 100000 * 7)

ggplot(rki_and_episim_incidenz , aes(x = week_year, y = incidence)) + #%>% filter(district == district_to_profile)
geom_line(aes(color = data_source)) +
scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
labs(title = paste0("7-Day Infections / 100k Pop for Berlin Districts"),
       subtitle = "Comparison of Local vs. Global Activity Reductions",
       x = "Date", y = "7-Day Infections / 100k Pop.") +
theme(axis.text.x = element_text(angle = 90))+                                        # Adjusting colors of line plot in ggplot2
scale_color_manual(values = c("blue", "magenta", "dark grey","dark grey")) +
facet_wrap(~district,  ncol=4)

#
# read_and_process_episim_events <- function(events_filename, facilities_to_district_map){
#   fac_to_district_map <- read_delim(facilities_to_district_map,
#                                     ";", escape_double = FALSE, col_names = FALSE,
#                                     trim_ws = TRUE) %>%
#     rename("facility" = "X1") %>%
#     rename("district" = "X2")
#
#   fac_to_district_map[is.na(fac_to_district_map)] <- "outta berlin"
#
#   fac_to_district_map$facility <- fac_to_district_map$facility %>%
#     str_replace("[A-Z]$", "")
#
#   # episim
#   episim_df <- read_delim(file = events_filename,
#                           "\t", escape_double = FALSE, trim_ws = TRUE) %>% select(date, facility)
#
#   episim_df2 <- episim_df %>% filter(!grepl("^tr_", facility))
#
#   episim_df2$facility <- episim_df2$facility %>%
#     str_replace("home_", "") %>%
#     str_replace("_split\\d", "") %>%
#     str_replace("[A-Z]$", "")
#
#   merged <- episim_df2 %>%
#     left_join(fac_to_district_map, by = c("facility"), keep = TRUE)
#
#   na_facs <- merged %>%
#     filter(is.na(district)) %>%
#     pull(facility.x)
#   length(unique(na_facs))
#
#   # All unique facilities in episim output: 39.734
#
#   # Unusable Portion of Data
#   # 1st merge with no cleaning : 28.372 unique unmergable facs
#   # remove home_ : 16.062
#   # remove _split\\d : 9.282
#   # remove tr_ : 7500
#   # remove A/B at end : 6571
#
#
#   episim_final <- merged %>%
#     filter(!is.na(district)) %>%
#     filter(district != "outta berlin") %>%
#     select(!starts_with("facility")) %>%
#     group_by(date, district) %>%
#     count() %>%
#     rename(infections = n)
#
#   return(episim_final)
# }