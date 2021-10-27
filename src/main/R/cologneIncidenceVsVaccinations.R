# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 10/27/2021

library(readr)
library(tidyverse)
library(tmap)
library(sf)
library(lubridate)

rm(list = ls())

path_txt <- "D:/Dropbox/Documents/VSP/cologne/calibration1465.incidencePerDistrict.txt"
path_shp <- "C:/Users/jakob/projects/shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/CologneDistricts/CologneDistricts.shp"

date_of_concern <- ymd("2021-09-03")

# read episim output and filter date
episim <- read_delim(path_txt, delim = "\t")
episim_one_day <- episim %>% filter(date == date_of_concern)

# read cologne shp file
shp <- st_read(path_shp)

# merge episim output into cologne shp file
cologne_merged <- shp %>% left_join(episim_one_day, by = c("STT_NAME" = "cityDistrict"))

# Incidence Map
tmap_mode("view")
incidence_breaks <- c(0, 25, 50, 75, 100, 125, 150, Inf)
tm_shape(cologne_merged) +
tm_polygons("incidence",
            id = "STT_NAME",
            breaks = incidence_breaks , palette = "Oranges")

# Vaccination Map
vaccination_breaks <- c(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)
tm_shape(cologne_merged) +
tm_polygons(col = "vaccinatedShare",
            id = "STT_NAME",
            breaks = vaccination_breaks, palette = "Purples")

# Faceted Plot with Incidence and Vaccination
tm_shape(cologne_merged) +
  tm_facets(sync = TRUE, ncol = 2) +
  tm_polygons(c("incidence", "vaccinatedShare"),
              palette=list("Oranges", "Purples"),
              breaks = list(incidence_breaks, vaccination_breaks))
