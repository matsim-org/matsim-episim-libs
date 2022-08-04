library(tidyverse)
library(lubridate)
library (RCurl)
library(gridExtra)


# Weather Data
weatherData <- read_delim("https://bulk.meteostat.net/daily/10382.csv.gz", delim = ",", col_names = FALSE, col_types = cols(
  X1 = col_date(format = ""),
  X2 = col_double(),
  X3 = col_double(),
  X4 = col_double(),
  X5 = col_double(),
  X6 = col_double(),
  X7 = col_double(),
  X8 = col_double(),
  X9 = col_double(),
  X10 = col_double(),
  X11 = col_double()
)) 
colnames(weatherData) <- c("date", "tavg", "tmin", "tmax", "prcp", "snow", "wdir", "wspd", "wpgt", "pres", "tsun")

weatherDataByWeek <- weatherData %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by( week ) %>%
  summarize( date=mean(date), tavg=mean(tavg), tmin=mean(tmin), tmax=mean(tmax), prcp=mean(prcp), snow=mean(snow), wdir=mean(wdir), wspd=mean(wspd), wpgt=mean(wpgt), pres=mean(pres), tsun=mean(tsun))


# Google Mobility Report
googleMobilityReportRaw <- read_csv(getURL("https://www.gstatic.com/covid19/mobility/Global_Mobility_Report.csv"))

googleMobilityReport <- googleMobilityReportRaw %>%
  filter(sub_region_1 == "Berlin") %>%
  select(-c(country_region_code, country_region, sub_region_1, sub_region_2, metro_area, iso_3166_2_code, census_fips_code, place_id)) %>%
  mutate(notAtHome = -residential_percent_change_from_baseline) %>%
  pivot_longer(!date, names_to = "type", values_to = "restriction") %>%
  filter(type == "notAtHome")  %>%
  mutate(weekday = wday(date, week_start = 1)) %>%
  filter(weekday < "6") %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by( week, type ) %>%
  summarize( restriction=mean(restriction), date=mean(date))

# Senozon Restrictions
snzRestrictionsFile <- "BerlinSnzData_daily_until20220204.csv"
svnLocation <- "/Users/sebastianmuller/git/shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/"

snzRestrictions <- read_delim(paste0(svnLocation, snzRestrictionsFile), delim = "\t") %>%
  pivot_longer(!date, names_to = "type", values_to = "restriction") %>%
  mutate(newDate = as.Date(strptime(date, "%Y%m%d"))) %>%
  mutate(weekday = wday(newDate, week_start = 1)) %>%
  filter(weekday < "6") %>%
  filter(type == "notAtHome") %>%
  mutate( week = paste0(isoweek(newDate), "-", isoyear(newDate))) %>%
  group_by( week, type ) %>%
  summarize( restriction=mean(restriction), newDate=mean(newDate))

# Google Mobility and weather
ggplot() +
  geom_point(data = googleMobilityReport, mapping=aes(x = date, y = restriction), colour = "red") +
  geom_point(data = snzRestrictions, mapping=aes(x = newDate, y = restriction), colour = "blue") +
  geom_point(data = weatherDataByWeek, mapping=aes(x = date, y = tmax), colour = "black") +
  theme(legend.position = "bottom") +
  xlim(c(as.Date("2020-02-24"), as.Date("2022-07-30"))) +
  labs(
    title="Time spent not at home (google and senozon) and max temperature (Berlin)",
    caption="Source: Google, Senozon",
    x="date", y="Reduction in % / tmax")
