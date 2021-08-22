# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 8/22/2021


library(readxl)
library(tidyverse)
library(lubridate)
library(janitor)

#https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Daten/Ausbruchsdaten.html


rm(list = ls())

setwd("D:/Dropbox/Documents/VSP/episim/local_contact_intensity/")

filename <- "RKI-Ausbruchsdaten.xlsx"
rki <- read_excel(filename) %>% rename(year = Meldejahr, week = Meldewoche)

rki_dated <- rki %>%
  tbl_df() %>%
  mutate(newyears = ymd(paste0(year, "-01-01"))) %>%
  mutate(date = newyears + weeks(week))

rki_share <- rki_dated %>% group_by(date) %>%
  mutate(share= round(n/sum(n)*100, 2)) %>%
  ungroup

ggplot(rki_share %>% filter(sett_engl == "Residential home" | sett_engl == "Leisure"),aes(x=date,y=share, col=sett_engl))+
  geom_line()

rki_mutated <- rki_dated %>%
  # filter(sett_engl != "Not documented in an outbreak") %>%
  # filter(sett_engl != "Unknown" & sett_engl != "Other") %>%
  select(-c("week","year","sett_f","newyears")) %>%
  pivot_wider(names_from = "sett_engl", values_from = n) %>%
  clean_names() %>%
  replace(is.na(.), 0) %>%
  mutate(unknown = not_documented_in_an_outbreak + unknown + other,.keep = "unused") %>%
  mutate(leisure = leisure + dining_venue + overnight_stay,.keep = "unused") %>%
  mutate(education = educational_institution + kindergarten_after_school_child_care,.keep = "unused") %>%
  mutate(work = work_place,.keep = "unused") %>%
  mutate(medical_elderly = health_care_centre + hospital + medical_practice + medical_rehabilitation + day_care_centre_for_the_elderly + care_facility + retirement_nursing_home,.keep = "unused") %>%
  mutate(home = residences + residential_home + private_household,.keep = "unused") %>%
  pivot_longer(-"date",names_to = "setting",values_to = "infections") %>%
  # filter(setting!="unknown") %>%
  group_by(date) %>%
  mutate(share= round(infections/sum(infections)*100, 2)) %>%
  ungroup %>%
  filter(setting!= "unknown" & setting!= "medical_elderly" & setting!= "refugee_accomodation" & setting!="public_transport")



ggplot(rki_mutated, aes(x = date, y = share, col = setting)) +
  geom_line()

