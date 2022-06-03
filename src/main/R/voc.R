
library(tidyverse)
library(lubridate)
library(readxl)
library(openxlsx)
library(rio)

# setwd("C:/Users/chris/Development/matsim-org/public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen")
 
df <- read_csv("Cologne/VOC_Cologne.csv", skip_empty_rows = T, locale = locale(decimal_mark = ","))


xls <- read.xlsx("https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Daten/VOC_VOI_Tabelle.xlsx?__blob=publicationFile", sheet = 3, startRow = 1)

# Alpha, Beta, Gamma, Delta, Omikron
kw49 <- c(
  5.7616962433740497E-5,
  5.7616962433740497E-5,
  0,
  0.97695321502650401,
  2.1318276100484E-2
)

kw50 <- c(
  6.2531265632816404E-5,
  0,
  0,
  0.91145572786393203,
  8.5230115057528796E-2
)

kw51 <- c(
  1.5110305228165601E-4,
  7.5551526140828004E-5,
  7.5551526140828004E-5,
  0.72249924448473901,
  0.27258990631610802
)

kw52 <- c(
  .0076997112608303E-4,
  1.2030798845043299E-4,
  0,
  0.41241578440808502,
  0.58355389797882595
)

kw1 <- c(
  3.2187114425191802E-4,
  5.3645190708652997E-5,
  5.3645190708652997E-5,
  0.22826028646531801,
  0.75838206104822703
)

rf <- df %>% add_column(`Omikron Fälle pro Tag (%)`= 0.0)

kw <- function(w, year, d=0) {
  lubridate::parse_date_time(paste(year, w, 1, sep="/"),'Y/W/w') + days(d)
}


add_date <- function(w, year, r) {

  res <- rf
    
  for (i in 0:6) {
    res <- res %>% add_row(
      `Date` = kw(w, year, i),
      `Wildtyp Fälle pro Tag (%)`=0,
      `Delta (B.1.617) Fälle pro Tag (%)`=r[4],
      `Gama (B.1.1.28 P1) Fälle pro Tag (%)`=r[3],
      `Beta (B.1.351) Fälle pro Tag (%)`=r[2],
      `Alpha (B.1.1.7) Fälle pro Tag (%)`=r[1],
      `Omikron Fälle pro Tag (%)`=r[5]
    )
  }
  
  return(res)
}


rf <- add_date(49, 2021, kw49)
rf <- add_date(50, 2021, kw50)
rf <- add_date(51, 2021, kw51)
rf <- add_date(52, 2021, kw52)
rf <- add_date(1, 2022, kw1)

f <- function(X) {
  format(X, digits = 4, scientific = F)
}

tmp <- rf %>% mutate(Date=format(Date, "%Y-%m-%d")) %>%
          mutate_if(is.numeric, f)

write_csv(tmp, "Cologne/VOC_Cologne_RKI.csv")
