# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 12/8/2021



if (TRUE) {
  library(viridis)
  library(spData)
  library(tmap)
  library(sf)
  library(readxl)
  library(tidyverse)
  library(janitor)
  library(lubridate)
  library(ggthemes)
  library(scales)
  library(cowplot)
  library(vistime)
  library(data.table)

  rm(list = ls())
  setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

  gbl_directory <- "master/a2/"
  gbl_image_output <- "C:/Users/jakob/projects/60eeeb14daadd7ca9fc56fea/images/"

  # load all functions
  source("C:/Users/jakob/projects/matsim-episim/src/main/R/masterJR-utils.R", encoding = 'utf-8')


  ####### SETUP: GET REAL INCIDENCE FOR BERLIN AND DISTRICTS


  # Source: https://www.statistik-berlin-brandenburg.de/bevoelkerung/demografie/einbuergerungen-auslaender
  district <- c("Mitte", "Friedrichshain_Kreuzberg", "Pankow", "Charlottenburg_Wilmersdorf", "Spandau", "Steglitz_Zehlendorf", "Tempelhof_Schoeneberg", "Neukoelln", "Treptow_Koepenick", "Marzahn_Hellersdorf", "Lichtenberg", "Reinickendorf")
  population <- c(374581, 279210, 404187, 316223, 239374, 291915, 341296, 318509, 272992, 274076, 292005, 259720)
  berlin_population <- data.frame(district, population) %>%
    rbind(c("Berlin", sum(population))) %>%
    mutate(population = as.numeric(population))


  #Source: https://www.berlin.de/lageso/gesundheit/infektionskrankheiten/corona/tabelle-bezirke-gesamtuebersicht/index.php/index/all.xls?q=
  #        https://www.berlin.de/lageso/gesundheit/infektionskrankheiten/corona/tabelle-bezirke-gesamtuebersicht/
  berlin_cases <- read_delim("RKI_NEW_DATA_BEZIRK.csv", delim = ";")
  berlin_incidence <- berlin_cases %>%
    select(-id) %>%
    rename(date = datum) %>%
    replace(is.na(.), 0) %>%
    mutate(Berlin = rowSums(across(-date))) %>%
    pivot_longer(-date, names_to = "district", values_to = "infections") %>%
    group_by(district) %>%
    mutate(infections_week = infections +
      lag(infections, n = 1, default = 0, order_by = date) +
      lag(infections, n = 2, default = 0, order_by = date) +
      lag(infections, n = 3, default = 0, order_by = date) +
      lag(infections, n = 4, default = 0, order_by = date) +
      lag(infections, n = 5, default = 0, order_by = date) +
      lag(infections, n = 6, default = 0, order_by = date)) %>%
    ungroup() %>%
    left_join(berlin_population) %>%
    mutate(incidence = infections_week * 100000 / population) %>%
    select(date, district, incidence) %>%
    mutate(Scenario = "rki")
  # TODO: should I move date three days earlier since its 7 day infections; also should i just remove the first 7 days to not skew

  gbl_berlin_incidence_all <- berlin_incidence %>% filter(district == "Berlin")
  gbl_berlin_incidence_district <- berlin_incidence %>% filter(district != "Berlin")
}

#########################################################################################
########################## LOAD, PREPARE, AND SAVE DATA #################################
#########################################################################################

if(FALSE){
  rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))

  run_params <- get_run_parameters(gbl_directory)
  episim_infections_district <- read_combine_episim_output(directory, "infections_subdistrict.txt", TRUE)
  episim_incidence_district <- convert_infections_into_incidence(directory, episim_infections_district, TRUE)

  save(episim_infections_district, file = paste0(gbl_directory, "episim_infections_district"))
  save(episim_incidence_district, file = paste0(gbl_directory, "episim_incidence_district"))
}


#########################################################################################
########################## PLOT #########################################################
#########################################################################################

# Plot: base case vs. localCI
rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
load(paste0(gbl_directory, "episim_incidence_district"))

start_date <- ymd("2020-02-15")
end_date <- ymd("2021-02-19")

scenario_base <- "noth1mod0"
scenario_policy <- "noth0.96mod0.3"
to_plot <- episim_incidence_district %>%
  mutate(Scenario = paste0(locationBasedRestrictions, "th", thetaFactor, "mod", ciModifier)) %>%
  rbind(gbl_berlin_incidence_district) %>%
  filter(date >= start_date & date <= end_date) %>%
  filter(Scenario == "rki" |
           Scenario == scenario_base |
           Scenario == scenario_policy) %>%
  mutate(Scenario = str_replace(Scenario, regex(scenario_policy), "policy")) %>%
  mutate(Scenario = str_replace(Scenario, regex(scenario_base), "base"),) %>%
  mutate(Scenario = factor(Scenario, levels = c("rki", "base", "policy")))


build_plot(to_plot, c("black", "blue", "red")) %>%
  save_png_pdf("a2_borough_localCI")



#########################################################################################
########################## LIVING SPACE MAPS ############################################
#########################################################################################
rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))

## Prepare Data
bzk <- st_read("D:/Dropbox/Documents/VSP/bezirke/bezirksgrenzen.shp")
lor <- st_read("D:/Dropbox/Documents/VSP/LOR_SHP_2015/RBS_OD_LOR_2015_12.shp") %>%
  rename(PLR_ID = PLR)


mss2019 <- read_excel("D:/Dropbox/Documents/VSP/4.1.KontextInd_Anteile_PLR_MSS2019.xlsx", skip = 14) %>%
  rename(PLR_ID = Planungsraum,
         PLR_NAME = ...2,
         EW = "EW          31.12.2018") %>%
  select(-"...4") %>%
  filter(grepl("^[0-9][0-9]{1,}[0-9]$", PLR_ID))

#K15 : Wohnfläche: Wohnfläche in m² je Einwohnerinnen und Einwohner am 31.12.2018
#K14 : Wohnräume: Anzahl der Wohnräume (einschl. Küche) je Einwohnerinnen und Einwohner am 31.12.2018

k15 <- mss2019 %>%
  select(contains("PLR"), EW, "K 15") %>%
  rename("m2pp" = "K 15")

joined <- lor %>% left_join(k15, by = "PLR_ID")

joined$m2pp[joined$m2pp == 0] <- NA

# st_write(joined, "D:/Dropbox/Documents/VSP/episim/local_contact_intensity/LORs_with_living_space/lors.shp")


bzk_mod <- bzk %>%
  mutate(Gemeinde_n = str_replace(Gemeinde_n, "Tempelhof-Schöneberg", "Tempelhof-\nSchöneberg\n\n\n\n")) %>%
  mutate(Gemeinde_n = str_replace(Gemeinde_n, "Marzahn-Hellersdorf", "Marzahn-\nHellersdorf")) %>%
  mutate(Gemeinde_n = str_replace(Gemeinde_n, "Charlottenburg-Wilmersdorf", "Charlottenburg-\nWilmersdorf")) %>%
  mutate(Gemeinde_n = str_replace(Gemeinde_n, "Friedrichshain-Kreuzberg", "Friedrichshain-\nKreuzberg"))

# Plot Living space per LOR

tmap_mode("plot")
plot_m2_lor <- tm_basemap(leaflet::providers$OpenStreetMap) +
  tm_shape(joined %>% rename("m2 per Person" = m2pp)) +
  tm_polygons(col = "m2 per Person", id = "PLRNAME", palette = viridis(9), alpha = 0.9) +
  tm_shape(bzk_mod) +
  tm_borders(col = "red", lwd = 3) +
  tm_text("Gemeinde_n", size = 0.65, fontface = "bold")+
  tm_layout(frame = FALSE)

plot_m2_lor
tmap_save(plot_m2_lor, filename = paste0(gbl_image_output, "a2_map_lor.png"), width = 16, height = 12, units = "cm")
tmap_save(plot_m2_lor, filename = paste0(gbl_image_output, "a2_map_lor.pdf"), width = 16, height = 12, units = "cm")


# Plot living space per district
regions2 <- joined %>%
  group_by(BEZNAME) %>%
  summarize(pop = sum(EW, na.rm = TRUE), totM2 = sum(EW * m2pp, na.rm = TRUE)) %>%
  mutate(avg = totM2 / pop)

plot_m2_bzk <- tm_basemap(leaflet::providers$OpenStreetMap) +
  tm_shape(regions2 %>% rename("m2 per Person" = avg)) +
  tm_polygons(col = "m2 per Person", id = "BEZNAME", palette = viridis(9), alpha = 0.9) +
  tm_shape(bzk_mod) +
  tm_borders(col = "red", lwd = 3) +
  tm_text("Gemeinde_n", size = 0.65, fontface = "bold") +
  tm_layout(frame = FALSE)
plot_m2_bzk

tmap_save(plot_m2_bzk, filename = paste0(gbl_image_output, "a2_map_borough.png"), width = 16, height = 12, units = "cm")
tmap_save(plot_m2_bzk, filename = paste0(gbl_image_output, "a2_map_borough.pdf"), width = 16, height = 12, units = "cm")

