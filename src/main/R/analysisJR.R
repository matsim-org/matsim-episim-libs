# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 6/9/2021

library(readxl)
library(tidyverse)
library(janitor)

rm(list = ls())


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
    date_improved <- as.character(excel_numeric_to_date(as.numeric(dateX)))#,format = "%Y-%m-%d")
    print(date_improved)
    rki_berlin$date[i] <- date_improved
  } else {

    #date_improved <- as.Date(dateX,format = "%d.%m.%Y")
    date_improved <- dateX
    rki_berlin$date[i] <- date_improved
  }
}

rki_berlin$LK <- rki_berlin$LK %>%
  str_replace("SK Berlin ", "")

rki_ch <- rki_berlin

# 2) Display simulation data also per Bezirk


# 3) Compare data, see if my ... made improvement
