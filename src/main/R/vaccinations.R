
library(tidyverse)
library(lubridate)

impf <- read_tsv("https://impfdashboard.de/static/data/germany_vaccinations_timeseries_v2.tsv")

df <- impf %>%
    mutate(mrna=dosen_biontech_erst_kumulativ+dosen_moderna_erst_kumulativ-lag(dosen_moderna_erst_kumulativ) - lag(dosen_biontech_erst_kumulativ)) %>%
    mutate(vector=dosen_johnson_kumulativ - lag(dosen_johnson_kumulativ) + dosen_astra_erst_kumulativ - lag(dosen_astra_erst_kumulativ)) %>%
    mutate(booster=dosen_biontech_dritt_kumulativ+dosen_moderna_dritt_kumulativ+dosen_astra_dritt_kumulativ-lag(dosen_biontech_dritt_kumulativ+dosen_moderna_dritt_kumulativ+dosen_astra_dritt_kumulativ)) %>%
    group_by(week = cut(date, "week")) %>%
    summarise(mrna = sum(mrna), vector=sum(vector), booster=sum(booster)) %>%
    mutate(mrnaShare=mrna/(mrna + vector))

#write_csv(df, "vaccination.csv")

printf <- function(...) cat(sprintf(...))

for (row in 1:nrow(df)) {
  
  s <- as.numeric(df[row, "mrnaShare"])
  
  printf("share.put(LocalDate.parse(\"%s\"), Map.of(VaccinationType.mRNA, %.2fd, VaccinationType.vector, %.2fd));\n", 
          paste(df[row, "week"]$week), s, 1 - s)
  
}


for (row in 1:nrow(df)) {
  
  s <- as.numeric(df[row, "booster"])
  
  pop <- 82000000
  
  printf("booster.put(LocalDate.parse(\"%s\"), (%.6f * population) / 7);\n", 
         paste(df[row, "week"]$week), s/pop)
  
}

