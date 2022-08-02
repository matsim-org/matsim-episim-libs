
library(lubridate)
library(tidyverse)
library(readr)



rm(list=ls())
source("/Users/jakob/git/matsim-episim/src/main/R/masterJR-utils.R", encoding = 'utf-8')

# Global variables
directory <- "/Users/jakob/git/public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-07-27/4-eu-noAgg/"

origin_date <- ymd("2022-07-24") # first day (sunday) of epiweek 30, 2022
end_date <- ymd("2023-07-29") # last day (saturday) of epiweek 30, 2023 (latest possible value)



## read & prep infections
infections_raw <- read_combine_episim_output_zipped(directory,"infections.txt.csv")

infections_incidence <- convert_infections_into_incidence(directory,infections_raw,FALSE) %>%
  select(-c(infections_week,nShowingSymptomsCumulative, district, incidence))


population_cologne <- infections_raw[1, "nSusceptible"]

population_germany <- 83695430 #https://www.destatis.de/EN/Themes/Society-Environment/Population/Current-Population/Tables/liste-current-population.html
scale_factor <- population_germany / population_cologne # pop germany / pop koelln

infections_ready <- infections_incidence %>%
  filter(date >= origin_date & date <= end_date) %>%
  # mutate(weekday = lubridate::wday(date, label = TRUE)) %>%
  mutate(year = epiyear(date)) %>%
  mutate(epiweek = epiweek(date)) %>%
  group_by(seed,vacCamp,vacType,year,epiweek) %>%
  summarise(value = sum(infections) * scale_factor, target_end_date = last(date) ) %>%
  mutate(target_variable = "inc infection") %>%
  select(year, epiweek, target_end_date, target_variable, value, seed, vacCamp, vacType)


## read & prep hospitalizations
hosp_raw <- read_combine_episim_output_zipped(directory,"post.hospital.tsv")

hosp_ready <- hosp_raw %>%
  filter(date >= origin_date & date <= end_date) %>%
  mutate(year = year(date)) %>%
  mutate(wkday = lubridate::wday(date, label = TRUE)) %>%
  filter(wkday == "Sat") %>%
  filter(measurement == "intakesHosp") %>%
  filter(severity == "Omicron") %>%
  mutate(epiweek = epiweek(date)) %>%
  rename("target_end_date" = date, value = n) %>%
  mutate(value = value * population_cologne / 100000 * scale_factor) %>%
  mutate(target_variable = "inc hosp") %>%
  select(year, epiweek, target_end_date, target_variable, value, seed, vacCamp, vacType)

# combine two dataframes and modify columns to match specs
combined <- rbind(infections_ready, hosp_ready)
# combined <- infections_ready #todo revert




seed <- unique(combined$seed)
sample <- seq(length(seed))
map <- data.frame(seed,sample)

final <- combined %>% filter(vacCamp!="off") %>%
  mutate(scenario_id = paste0(vacCamp,"_",vacType)) %>%
  mutate(scenario_id = case_when(scenario_id == "60plus_omicronUpdate"~"A-2022-07-24",
                              scenario_id == "18plus_omicronUpdate"~"B-2022-07-24",
                              scenario_id == "60plus_mRNA"~"C-2022-07-24",
                              scenario_id == "18plus_mRNA"~"D-2022-07-24")) %>%
  merge(map,by ="seed") %>%
  mutate(horizon =  case_when(year == 2022 ~ epiweek - 29, year == 2023~ (52-29) + epiweek)) %>%
  mutate("origin_date" = "2022-07-24") %>%
  mutate("location" = "DE") %>%
  mutate(value = round(value)) %>%
  select(origin_date,scenario_id, horizon, target_end_date, location, sample,target_variable, value) %>%
  arrange(scenario_id,sample,horizon) %>%
  mutate(horizon = paste0(horizon," wk"))

write.csv(final,"/Users/jakob/git/covid19-scenario-hub-europe/data-processed/MODUS_Covid-Episim/2022-07-24-MODUS_Covid-Episim.csv", row.names = FALSE)



# xxx <- read.delim("/Users/jakob/antibodies_2022-07-23.tsv",sep = "\t")
#
# yyy <- xxx %>% filter(nVaccinations == 0 & nInfections == 0)
#
# nrow(yyy)/nrow(xxx)






