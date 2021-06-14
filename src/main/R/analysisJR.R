# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 6/9/2021

library(readxl)
library(tidyverse)
library(janitor)
library(lubridate)
library(sf)
library(tmap)


rm(list = ls())


setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

# Functions:
outersect <- function(x, y) {
  sort(c(setdiff(x, y),
         setdiff(y, x)))
}

read_and_process_episim_events <- function(filename){
  fac_to_district_map <- read_delim("FacilityToDistrictMapCOMPLETE.txt",
                                    ";", escape_double = FALSE, col_names = FALSE,
                                    trim_ws = TRUE) %>%
    rename("facility" = "X1") %>%
    rename("district" = "X2")

  fac_to_district_map[is.na(fac_to_district_map)] <- "outta berlin"

  fac_to_district_map$facility <- fac_to_district_map$facility %>%
    str_replace("[A-Z]$", "")

  # episim
  episim_df <- read_delim(file = filename,
                          "\t", escape_double = FALSE, trim_ws = TRUE) %>% select(date, facility)

  episim_df2 <- episim_df %>% filter(!grepl("^tr_", facility))

  episim_df2$facility <- episim_df2$facility %>%
    str_replace("home_", "") %>%
    str_replace("_split\\d", "") %>%
    str_replace("[A-Z]$", "")

  merged <- episim_df2 %>%
    left_join(fac_to_district_map, by = c("facility"), keep = TRUE)

  na_facs <- merged %>%
    filter(is.na(district)) %>%
    pull(facility.x)
  length(unique(na_facs))

  # All unique facilities in episim output: 39.734

  # Unusable Portion of Data
  # 1st merge with no cleaning : 28.372 unique unmergable facs
  # remove home_ : 16.062
  # remove _split\\d : 9.282
  # remove tr_ : 7500
  # remove A/B at end : 6571


  episim_final <- merged %>%
    filter(!is.na(district)) %>%
    filter(district != "outta berlin") %>%
    select(!starts_with("facility")) %>%
    group_by(date, district) %>%
    count() %>%
    rename(infections = n)

  return(episim_final)
}

read_and_process_episim_events_BATCH <- function(filename){
  fac_to_district_map <- read_delim("FacilityToDistrictMapCOMPLETE.txt",
                                    ";", escape_double = FALSE, col_names = FALSE,
                                    trim_ws = TRUE) %>%
    rename("facility" = "X1") %>%
    rename("district" = "X2")

  fac_to_district_map[is.na(fac_to_district_map)] <- "outta berlin"

  fac_to_district_map$facility <- fac_to_district_map$facility %>%
    str_replace("[A-Z]$", "")

  info222 <- read_delim(filename, delim = ";" )


  episim_df_all_runs <- data.frame()

  for(row in 1 : nrow(info222)){
    runId <- info222$RunId[row]
    seed <- info222$seed[row]
    districtLevelRestrictions <- info222$districtLevelRestrictions[row]
    df_for_run <- read_delim(file = paste0("5seeds/",runId,".infectionEvents.txt"),
                            "\t", escape_double = FALSE, trim_ws = TRUE) %>%
      select(date, facility) %>%
      mutate(seed = seed, districtLevelRestrictions = districtLevelRestrictions)

    episim_df_all_runs <- rbind(episim_df_all_runs,df_for_run)
  }

  # episim
  # episim_df <- read_delim(file = filename,
  #                         "\t", escape_double = FALSE, trim_ws = TRUE) %>% select(date, facility)

  episim_df2 <- episim_df_all_runs %>% filter(!grepl("^tr_", facility))

  episim_df2$facility <- episim_df2$facility %>%
    str_replace("home_", "") %>%
    str_replace("_split\\d", "") %>%
    str_replace("[A-Z]$", "")

  merged <- episim_df2 %>%
    left_join(fac_to_district_map, by = c("facility"), keep = TRUE)

  na_facs <- merged %>%
    filter(is.na(district)) %>%
    pull(facility.x)
  length(unique(na_facs))

  # All unique facilities in episim output: 39.734

  # Unusable Portion of Data
  # 1st merge with no cleaning : 28.372 unique unmergable facs
  # remove home_ : 16.062
  # remove _split\\d : 9.282
  # remove tr_ : 7500
  # remove A/B at end : 6571


  episim_final <- merged %>%
    filter(!is.na(district)) %>%
    filter(district != "outta berlin") %>%
    select(!starts_with("facility")) %>%
    group_by(date, district,seed,districtLevelRestrictions) %>%
    count() %>%
    group_by(date,district,districtLevelRestrictions) %>%
    summarise(infections = mean(n))
    # rename(infections = n)

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
episim_all_runs <- read_and_process_episim_events_BATCH("5seeds/_info.txt")

ggplot(episim_all_runs %>%  filter(district == "Neukoelln"),
       mapping = aes(x = date, y = infections)) +
  geom_line(mapping = aes(color = districtLevelRestrictions))

# 3) Compare data, see if my ... made improvement
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

##deprecated, before average of seeds,

# 2) Display simulation data also per Bezirk
# episim_base <- read_and_process_episim_events("locationBasedRestrictions4.infectionEvents_base.txt")
# episim_localRestrictions <- read_and_process_episim_events("locationBasedRestrictions3.infectionEvents_withLocationBasedRestrictions.txt")
#
# episim_final <- episim_base %>%
#   full_join(episim_localRestrictions, by = c("date","district")) %>%
#   rename(episim_base = "infections.x") %>%
#   rename(episim_policy = "infections.y")
#
#
# ggplot(episim_final %>%
#          filter(district == "Neukoelln") %>%
#          pivot_longer(contains("episim"),names_to = "Restriction Type", values_to = "infections"),
#        mapping = aes(x = date, y = infections)) +
#   geom_line(mapping = aes(color = `Restriction Type`))


# 3) Compare data, see if my ... made improvement
# rki_and_episim <- episim_final %>%
#   full_join(rki_new, by = c("date", "district")) %>%
#   rename(rki_new = cases) %>%
#   full_join(rki_old, by = c("date", "district")) %>%
#   mutate(month = months(date)) %>%
#   mutate(week = week(date)) %>%
#   mutate(year = year(date)) %>%
#   group_by(district, year, week) %>%
#   summarise(episim_base = mean(episim_base, na.rm = TRUE),
#             episim_policy = mean(episim_policy, na.rm = TRUE),
#             rki_new = mean(rki_new, na.rm = TRUE),
#             rki_old = mean(rki_cases_old, na.rm = TRUE)) %>%
#   mutate(week_year = as.Date(paste(year, week, 1, sep = "-"), "%Y-%U-%u")) %>%
#   pivot_longer(c(rki_new, rki_old, episim_base,episim_policy), names_to = "data_source", values_to = "cases")

# ggplot(rki_and_episim %>% filter(district=="Mitte"), aes(x = date)) +
#   geom_line(aes(y= rki), color = 'red') +
#   geom_line(aes(y= episim), color = 'blue')+
#   geom_line(aes(y = rki_cases_old), color = 'orange')

# ggplot(rki_and_episim %>% filter(district=="Mitte"), aes(x = week_year)) +
#   geom_line(aes(y= rki_week), color = 'red') +
#   geom_line(aes(y = rki_old_week), color = 'orange') +
#   geom_line(aes(y= episim_week), color = 'blue') +
#   scale_x_date(date_breaks = "1 month", date_labels = "%b-%y")+
#   labs(title = "Infections per Day for Mitte (Weekly Average)", x = "Date", y="New Infections")

ggplot(rki_and_episim %>% filter(district == "Treptow_Koepenick"), aes(x = week_year, y = cases)) +
  geom_line(aes(color = data_source)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
  labs(title = "Infections per Day for Mitte (Weekly Average)",
       subtitle = "With Location Based Restrictions",
       x = "Date", y = "New Infections") +
  theme(axis.text.x = element_text(angle = 90))








#### #### #### #### #### #### #### #### #### #### #### #### #### #### #### #### ####
#### Living Area per Person
rm(list = ls())
LOR_shape <-  #LOR_SHP_2021/lor_plr.shp"
bzk <- st_read("D:/Dropbox/Documents/VSP/bezirke/bezirksgrenzen.shp")
lor <- st_read("D:/Dropbox/Documents/VSP/LOR_SHP_2015/RBS_OD_LOR_2015_12.shp") %>%
  rename(PLR_ID = PLR)


mss2019 <- read_excel("D:/Dropbox/Documents/VSP/4.1.KontextInd_Anteile_PLR_MSS2019.xlsx", skip = 14) %>%
  rename(PLR_ID = Planungsraum,
         PLR_NAME = ...2,
         EW = "EW          31.12.2018") %>%
  select(- "...4") %>%
  filter(grepl("^[0-9][0-9]{1,}[0-9]$", PLR_ID))

#K15 : Wohnfläche: Wohnfläche in m² je Einwohnerinnen und Einwohner am 31.12.2018
#K14 : Wohnräume: Anzahl der Wohnräume (einschl. Küche) je Einwohnerinnen und Einwohner am 31.12.2018

k15 <- mss2019 %>%
  select(contains("PLR"), EW, "K 15") %>%
  rename("m2 pro Person" = "K 15")



joined <- lor %>% left_join(k15,by= "PLR_ID")

joined$`m2 pro Person`[joined$`m2 pro Person` == 0] <- NA


antiiiiii <- lor %>% anti_join(k15, by = "PLR_ID")

library(viridis)
library(spData)

tmap_mode("view")
tm_basemap(leaflet::providers$OpenStreetMap) +
  tm_shape(joined)+
  tm_polygons(col = "m2 pro Person", id = "PLRNAME", palette = viridis(9), alpha = 0.85) +
  tm_shape(bzk) +
  tm_borders(col= "blue")+
  tm_layout(title = "Wohnraum pro Planungsraum, Stand 2018")

