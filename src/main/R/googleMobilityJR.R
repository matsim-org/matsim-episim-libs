# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 6/7/2021

library(tidyverse)
library(lubridate)
library(RCurl)
library(hash)

rm(list = ls())


setwd("D:/Dropbox/Documents/VSP/")
# Google Mobility Report
# googleMobilityReport <- read_csv(getURL("https://www.gstatic.com/covid19/mobility/Global_Mobility_Report.csv"))
googleMobilityReport <- read_csv("D:/Dropbox/Documents/VSP/Global_Mobility_Report.csv")

germany <- googleMobilityReport %>%
  filter(country_region == "Germany") %>%
  filter(sub_region_1 != "NA")



# Regions in Germany
regions <- unique(germany$sub_region_1)


# Day Indicies
h <- hash()
h[["Monday-Sunday"]] <- seq(1, 7)
h[["Monday-Friday"]] <- seq(1, 5)
h[["Saturday"]] <- c(6)
h[["Sunday"]] <- c(7)


for (region in regions) {
  regional_df <- germany %>%
    filter(sub_region_1 == region) %>%
    select(-c(country_region_code, country_region, sub_region_1, sub_region_2, metro_area, iso_3166_2_code, census_fips_code, place_id)) %>%
    pivot_longer(!date, names_to = "type", values_to = "restriction") %>%
    filter(type == "retail_and_recreation_percent_change_from_baseline" |
             type == "parks_percent_change_from_baseline" |
             type == "workplaces_percent_change_from_baseline") %>%
    mutate(weekday = wday(date, week_start = 1))
  for (days_of_week in keys(h)) {
    start_day <- min(h[[days_of_week]])
    end_day <- max(h[[days_of_week]])
    regional_temporal_df <- regional_df %>%
      filter(weekday >= start_day & weekday <= end_day) %>%
      mutate(week = paste0(isoweek(date), "-", isoyear(date))) %>%
      group_by(week, type) %>%
      summarize(restriction = mean(restriction), date = mean(date))

    ggplot(data = regional_temporal_df, mapping = aes(x = date)) +
      geom_point(mapping = aes(y = restriction, colour = type)) +
      theme(legend.position = "bottom") +
      geom_vline(xintercept = as.Date("2021-04-24")) +
      labs(title = paste0("Mobility Reduction - ", region),
           subtitle = paste0(days_of_week),
           caption = "Data source: Google Mobility Data")

    ggsave(paste0(region, "-", days_of_week,".png"))

  }
}







