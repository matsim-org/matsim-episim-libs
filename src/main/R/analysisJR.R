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
rki_berlin$date2 <- ymd


rki_berlin$LK <- rki_berlin$LK %>%
  str_replace("SK Berlin ", "")

rki_ch <- rki_berlin %>%
  filter(LK=="Charlottenburg-Wilmersdorf")

ggplot(rki_berlin, mapping = aes(x = date2, y = cases)) +
  geom_line(aes(color = LK)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y")+
  labs(title = paste0("Daily  Cases in Berlin"), x = "Date", y = "Cases")

# 2) Display simulation data also per Bezirk

rm(list = ls())

# facilities
fac_to_district_map <- read_delim("C:/Users/jakob/projects/matsim-episim/FacilityToDistrictMapCOMPLETE.txt",
                                     ";", escape_double = FALSE, col_names = FALSE,
                                     trim_ws = TRUE) %>% rename("facility" = "X1") %>% rename("district" = "X2")

fac_to_district_map[is.na(fac_to_district_map)] <- "outta berlin"


# episim
episim_df <- read_delim("C:/Users/jakob/Desktop/locationBasedRestrictions3.infectionEvents.txt",
                        "\t", escape_double = FALSE, trim_ws = TRUE) %>% select(date,facility)

episim_df2 <- episim_df

episim_df2$facility <- episim_df2$facility %>%
  str_replace("home_","") %>%
  str_replace("_split\\d","")

merged <- episim_df2 %>%
  left_join(fac_to_district_map, by = c("facility"),keep = TRUE) %>%
  filter(!grepl("tr_",facility.x)) %>%
  filter(is.na(district))

antiiii <-anti_join(episim_df2,fac_to_district_map, by = "facility")

fac_to_district_map %>% filter(grepl("110000000563100031",facility)) %>% view()
# fac_file_unique <- unique(fac_to_district_map$facility)
# events_file_unique <- unique(episim_df$facility)
#
# inter <- Reduce(intersect,list(fac_file_unique,events_file_unique))
# outer <- Reduce(outersect,list(fac_file_unique,events_file_unique))
# intersect(fac_to_district_map$facility)

  rename("kiez" = X2 ) %>%
  group_by(kiez,date) %>% summarise(count = n())

episim_df2

episim_mitte <- episim_df2 %>%
  filter(kiez == "Mitte")

ggplot(data = episim_mitte, mapping = aes(date,count)) + geom_line()


# 3) Compare data, see if my ... made improvement
