# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 7/5/2021

library(readxl)
library(tidyverse)
setwd("C:/Users/jakob/projects/shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/")
file_names <- list.files(path = ".",
                pattern = "0407.csv")

snz_data <- data.frame()
for (file_name in file_names) {
  bezirk <- str_replace(file_name,"SnzData_daily_until20210407.csv","")
  snz_data_bzk <- read_delim(file_name,delim = "\t") %>%
    mutate("bezirk" = bezirk) %>%
    pivot_longer(cols = -c("date","bezirk"),names_to = "act", values_to = "reduction")

  snz_data <- rbind(snz_data,snz_data_bzk)

}

snz_data$date <- as.Date(as.character(snz_data$date),"%Y%m%d")

ggplot(snz_data %>% filter(act == "work"), aes(date,reduction)) +
  geom_line(aes(color = bezirk)) +
  ylim(-50,50)

avg_ber <- snz_data %>%
  filter(bezirk!="Berlin") %>%
  group_by(date,act) %>%
  summarise(avg = mean(reduction)) %>%
  left_join(snz_data %>% filter(bezirk=="Berlin"),by=c("date","act")) %>%
  rename("berlin" = "reduction") %>%
  mutate(pct_diff = (avg-berlin)/berlin * 100)

ggplot(avg_ber) +
  # geom_line(aes(x=date,y=avg)) +
  # geom_line(aes(x=date,y=berlin),color="red") +
  geom_line(aes(x=date,y=pct_diff))
  # ylim(-50,50)


max((snz_data %>% filter(act=="work"))$reduction)
snz_data %>% filter(reduction == "845") %>% glimpse()

