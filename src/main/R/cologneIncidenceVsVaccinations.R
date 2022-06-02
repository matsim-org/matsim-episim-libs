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
output <- "/Users/sebastianmuller/git/matsim-episim-libs/output/2021-10-28/"

#base
scenario <- paste0(output, "seed_6137546356583794141-thetaFactor_1.0-impFac_3.0-effDeltaMRNA_0.7-boosterSpeed_0.0-nonVaccinableHh_0.0-hhSusc_1.0-AltstadtNord_no-Vingst_no-Bickendorf_no-Weiden_no/calibration1424.incidencePerDistrict.txt")
scenario <- paste0(output, "seed_6137546356583794141-thetaFactor_1.0-impFac_3.0-effDeltaMRNA_0.7-boosterSpeed_0.0-nonVaccinableHh_1.0-hhSusc_5.0-AltstadtNord_yes-Vingst_yes-Bickendorf_no-Weiden_no/calibration1460.incidencePerDistrict.txt")


path_txt <- "/Users/sebastianmuller/git/matsim-episim-libs/output/te/seed_6137546356583794141-thetaFactor_1.0-impFac_3.0-effDeltaMRNA_0.7-boosterSpeed_0.0-nonVaccinableHh_1.0-hhSusc_5.0-AltstadtNord_no-Vingst_yes-Bickendorf_yes-Weiden_yes/calibration1465.incidencePerDistrict.txt"
path_shp <- "/Users/sebastianmuller/git/shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/CologneDistricts/CologneDistricts.shp"

date_of_concern <- ymd("2021-11-05")

# read episim output and filter date
episim <- read_delim(scenario, delim = "\t")
episim_one_day <- episim %>% filter(date == date_of_concern)

# read cologne shp file
shp <- st_read(path_shp)

# merge episim output into cologne shp file
cologne_merged <- shp %>% left_join(episim_one_day, by = c("STT_NAME" = "cityDistrict"))

# Incidence Map
tmap_mode("plot")
incidence_breaks <- c(0, 10, 20, 40, 80, 160, 320, Inf)
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

ggplot(data = episim, mapping=aes(x = date)) +
  geom_point(mapping=aes(y = incidence, colour = cityDistrict)) +
  theme(legend.position = "bottom") +
  scale_y_log10()

write_csv(episim, "episim.csv")
