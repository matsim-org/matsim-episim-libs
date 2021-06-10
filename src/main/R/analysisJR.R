# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 6/9/2021

library(readxl)
library(tidyverse)
library(janitor)
library(lubridate)


rm(list = ls())

outersect <- function(x, y) {
  sort(c(setdiff(x, y),
         setdiff(y, x)))
}


# 1) Analyse RKI Data: Make infection graph Bezirk
#NOTE : Changed date format in excel, since RKI switched date format in April of 2021
rki <- read_excel("D:/Dropbox/Documents/VSP/Fallzahlen_Kum_Tab_9JuniB.xlsx",
                  sheet = "LK_7-Tage-Fallzahlen (fixiert)", skip = 4)

rki_berlin <- rki %>%
  filter(grepl("berlin", LK, ignore.case = TRUE)) %>%
  select(-c("...1","LKNR")) %>%
  pivot_longer(!contains("LK"),names_to = "date",values_to = "cases")

for(i in 1:nrow(rki_berlin)){
  dateX <- as.character(rki_berlin$date[i])
  if(grepl("44",dateX)){
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
  str_replace("-","_") %>%
  str_replace("ö","oe")


rki_berlin <- rki_berlin %>% rename(district = LK) %>% mutate(cases = cases/7)

ggplot(rki_berlin, mapping = aes(x = date, y = cases)) +
  geom_line(aes(color = district)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y")+
  labs(title = paste0("Daily  Cases in Berlin"), x = "Date", y = "Cases")



### 1b) RKI Previous Data
rki_old <- read_csv("D:/Dropbox/Documents/VSP/RKI_COVID19_02112020.csv")

rki_berlin_old <- rki_old %>%
  filter(grepl("berlin", Landkreis, ignore.case = TRUE)) %>%
  mutate(date = as.Date(Refdatum, format = "%m/%d/%Y"), district = Landkreis) %>% ## TODO: RefDatum or Meldedaturm
  select(district,AnzahlFall,date) %>%
  group_by(district,date) %>%
  summarise(rki_cases_old = sum(AnzahlFall)) %>%
  mutate(district = str_replace(district,"SK Berlin ","")) %>%
  mutate(district = str_replace(district,"-","_")) %>%
  mutate(district = str_replace(district,"ö","oe"))

ggplot(rki_berlin_old %>% filter(district=="Mitte"), aes(x= date, y= rki_cases_old))+
  geom_line(aes(color = district))


# 2) Display simulation data also per Bezirk

# rm(list = ls())

# facilities
fac_to_district_map <- read_delim("C:/Users/jakob/projects/matsim-episim/FacilityToDistrictMapCOMPLETE.txt",
                                     ";", escape_double = FALSE, col_names = FALSE,
                                     trim_ws = TRUE) %>% rename("facility" = "X1") %>% rename("district" = "X2")

fac_to_district_map[is.na(fac_to_district_map)] <- "outta berlin"

fac_to_district_map$facility <- fac_to_district_map$facility %>%
  str_replace("[A-Z]$","")


# episim
episim_df <- read_delim("C:/Users/jakob/Desktop/locationBasedRestrictions3.infectionEvents.txt",
                        "\t", escape_double = FALSE, trim_ws = TRUE) %>% select(date,facility)

episim_df2 <- episim_df %>% filter(!grepl("^tr_",facility))

episim_df2$facility <- episim_df2$facility %>%
  str_replace("home_","") %>%
  str_replace("_split\\d","") %>%
  str_replace("[A-Z]$","")

merged <- episim_df2 %>%
  left_join(fac_to_district_map, by = c("facility"),keep = TRUE)

na_facs <- merged %>% filter(is.na(district)) %>% pull(facility.x)
length(unique(na_facs))

# All unique facilities in episim output: 39.734

# Unusable Portion of Data
# 1st merge with no cleaning : 28.372 unique unmergable facs
# remove home_ : 16.062
# remove _split\\d : 9.282
# remove tr_ : 7500
# remove A/B at end : 6571


episim_final  <- merged %>%
  filter(!is.na(district)) %>%
  filter(district!="outta berlin") %>%
  select(!starts_with("facility")) %>%
  group_by(date, district) %>%
  count() %>% rename(infections = n)

ggplot(episim_final, mapping = aes(x=date,y = infections))+
  geom_line(mapping = aes(color = district))



# 3) Compare data, see if my ... made improvement
rki_and_episim <- episim_final %>%
  full_join(rki_berlin, by = c("date","district")) %>%
  rename(rki = cases, episim = infections) %>%
  full_join(rki_berlin_old, by = c("date","district")) %>%
  mutate(month = months(date)) %>%
  mutate(week = week(date)) %>%
  mutate(year = year(date)) %>%
  group_by(district,year,week) %>%
  summarise(episim_week = mean(episim,na.rm = TRUE),
            rki_week = mean(rki,na.rm = TRUE),
            rki_old_week = mean(rki_cases_old,na.rm = TRUE)) %>%
  mutate(week_year = as.Date(paste(year, week, 1, sep="-"), "%Y-%U-%u"))

# ggplot(rki_and_episim %>% filter(district=="Mitte"), aes(x = date)) +
#   geom_line(aes(y= rki), color = 'red') +
#   geom_line(aes(y= episim), color = 'blue')+
#   geom_line(aes(y = rki_cases_old), color = 'orange')

ggplot(rki_and_episim %>% filter(district=="Mitte"), aes(x = week_year)) +
  geom_line(aes(y= rki_week), color = 'red') +
  geom_line(aes(y = rki_old_week), color = 'dark red') +
  geom_line(aes(y= episim_week), color = 'blue')

