# Title     : LivingSpacePerDistrict
# Objective : Reads living space per LOR from MS2019, merges this with shape file of LORs, and saves expanded shape file
# Created by: jakob
# Created on: 6/15/2021

library(tidyverse)
library(sf)
library(tmap)
library(tabulizer)
library(viridis)
library(spData)
library(readxl)
library(rgdal)


#### #### #### #### #### #### #### #### #### #### #### #### #### #### #### #### ####
#### Living Area per Person
rm(list = ls())
# LOR_shape <-  #LOR_SHP_2021/lor_plr.shp"
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


# antiiiiii <- lor %>% anti_join(k15, by = "PLR_ID")

bzk_mod <- bzk %>%
  mutate(Gemeinde_n = str_replace(Gemeinde_n, "Tempelhof-Schöneberg", "Tempelhof-\nSchöneberg\n\n\n\n")) %>%
  mutate(Gemeinde_n = str_replace(Gemeinde_n, "Marzahn-Hellersdorf", "Marzahn-\nHellersdorf")) %>%
  mutate(Gemeinde_n = str_replace(Gemeinde_n, "Charlottenburg-Wilmersdorf", "Charlottenburg-\nWilmersdorf")) %>%
  mutate(Gemeinde_n = str_replace(Gemeinde_n, "Friedrichshain-Kreuzberg", "Friedrichshain-\nKreuzberg"))


tmap_mode("plot")
plot_m2_lor <- tm_basemap(leaflet::providers$OpenStreetMap) +
  tm_shape(joined %>% rename("m2 per Person" = m2pp)) +
  tm_polygons(col = "m2 per Person", id = "PLRNAME", palette = viridis(9), alpha = 0.9) +
  tm_shape(bzk_mod) +
  tm_borders(col = "red", lwd = 3) +
  tm_text("Gemeinde_n", size = 0.65, fontface = "bold")+
  tm_layout(frame = FALSE)
# tm_layout(title = "Wohnraum pro Planungsraum, Stand 2018")

plot_m2_lor
tmap_save(plot_m2_lor, filename = paste0(gbl_image_output, "m2_lor.png"), width = 16, height = 12, units = "cm")
tmap_save(plot_m2_lor, filename = paste0(gbl_image_output, "m2_lor.pdf"), width = 16, height = 12, units = "cm")


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

tmap_save(plot_m2_bzk, filename = paste0(gbl_image_output, "m2_bzk.png"), width = 16, height = 12, units = "cm")
tmap_save(plot_m2_bzk, filename = paste0(gbl_image_output, "m2_bzk.pdf"), width = 16, height = 12, units = "cm")


#####

# update.packages()

# install_version(
#   "htmltools",
#   version = "0.6.0")

# library(htmltools)


#extents_02_03 <- locate_areas("https://cdn0.scrvt.com/ee046e2ad31b65165b1780ff8b3b5fb6/9e8efb180d4a9e16/5376322099fc/AfS_JB_2020_BE.pdf",pages = c(78))
#extents_08_13 <- locate_areas("https://cdn0.scrvt.com/ee046e2ad31b65165b1780ff8b3b5fb6/9e8efb180d4a9e16/5376322099fc/AfS_JB_2020_BE.pdf",pages = c(306))

#
# all_tables <- extract_tables("https://cdn0.scrvt.com/ee046e2ad31b65165b1780ff8b3b5fb6/9e8efb180d4a9e16/5376322099fc/AfS_JB_2020_BE.pdf",
#                             output = "data.frame",
#                             pages = c(78,306),
#                             area = list(
#                               c(124.24353,  47.88801, 236.04589, 364.81730), #02_03
#                               c(209.14608,  50.46593, 321.78906, 357.19663) #08_13
#                             ),
#                             guess = FALSE,
#                             method = "stream", header = FALSE
# )
#
# table_02_03 <- all_tables[[1]] # x1000 # People in each district, in different Income Brackets
# colnames(table_02_03) <- c("Bezirk","Insgesamt","i<700","i700_900","i900_1100","i1100_1300","i1300_1500","i1500_2000","i2000_2600","i>2600","Ohne Einkommen")
# table_02_03 <- data.frame(lapply(table_02_03, function(x) {gsub(",", ".", x)}))
#
# table_02_03_mod <- table_02_03 %>%
#   mutate(Bezirk = str_replace_all(Bezirk, "\\.{2,}", ""))
#
# table_02_03_mod[,seq(2,11)] <- table_02_03_mod[,seq(2,11)] %>% mutate_all(as.numeric)
#
#
#
# table_08_13 <- all_tables[[2]]
# colnames(table_08_13) <- c("Bezirk","Wohnungen_Insgesamt","Wohnungen_je1000EW","Wohnflaeche_Insgesamt","Wohnflaeche_jeWohnung","Wohnflaeche_jeEW","Raueme_Insgesamt","Raueme_jeWohnung","Raueme_jeEW")
# table_08_13 <- data.frame(lapply(table_08_13, function(x) {gsub(",", ".", x)}))
# table_08_13_mod <- table_08_13 %>%
#   mutate(Bezirk = str_replace_all(Bezirk, "\\.{2,}", "")) %>%
#   mutate(Wohnflaeche_Insgesamt = str_replace(Wohnflaeche_Insgesamt," ", "")) %>%
#   mutate(Wohnflaeche_Insgesamt = as.double(Wohnflaeche_Insgesamt)*1000) %>%
#   mutate(Wohnflaeche_jeEW = as.numeric(Wohnflaeche_jeEW))
#
# ggplot(table_08_13_mod) +
#   geom_col(aes(x = Bezirk, y = Wohnflaeche_jeEW))+
#   theme(axis.text.x = element_text(angle = 90))
#
#


