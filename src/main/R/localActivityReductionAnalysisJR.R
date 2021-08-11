# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 8/7/2021


library(jsonlite)
library(data.table)
library(tidyverse)
library(lubridate)

rm(list = ls())

file_pattern <- "C:/Users/jakob/projects/matsim-episim/output/locationBasedRestrictions_yesForHomeLocation/locationBasedRestrictions2.policy.conf"

df <- data.frame(number = unlist(fromJSON(file_pattern, flatten = TRUE)))


df$rownames <- rownames(df)
rownames(df) <- NULL
df_clean <- df %>%
  separate(rownames, sep = "([.])", into = c("activity", "date", "col_type", "district")) %>%
  filter(col_type == "fraction" | col_type == "locationBasedRf")

df_clean2 <- setDT(df_clean)[col_type == "fraction", district := "Berlin"]
df_clean3 <- df_clean2 %>%
  select(-col_type) %>%
  mutate(date = as.Date(date, format = "%Y-%m-%d"))

df_avg <- df_clean3 %>%
  filter(district != "Berlin") %>%
  group_by(activity, date) %>%
  summarise(number = mean(number)) %>%
  mutate(district = "average")

df_clean_avg <- rbind(df_clean3 %>% filter(district == "Berlin"), df_avg)
ggplot(df_clean_avg %>% filter(!grepl('edu', activity)), aes(date, number)) +
  geom_line(aes(color = district)) +
  facet_wrap(~activity, ncol = 4)


# ggplot(df4, aes(x = as.Date(date,format = "%Y-%m-%d"), y = number)) +
ggplot(df_clean3 %>% filter(activity == "leisure"), aes(x = date, y = number)) +
  geom_line(aes(color = district))


df_clean3 %>%
  filter(date == as.Date("2020-11-08", format = "%Y-%m-%d") & district == "Steglitz_Zehlendorf") %>%
  glimpse()

unique((df_clean3 %>% filter(activity == "pt"))$date) # TODO: why do only three dates have


# scale_x_date(date_breaks = "1 month", date_labels = "%b-%y")
# labs(title = paste0("Infections per Day for Berlin Districts (Weekly Average)"),
#      subtitle = "Comparison of Local vs. Global Activity Reductions",
#      x = "Date", y = "New Infections") +
# theme(axis.text.x = element_text(angle = 90)) +                                        # Adjusting colors of line plot in ggplot2
# scale_color_manual(values = color_scheme) +
# facet_wrap(~district, ncol = 4)












#########################################################################
## The following codes is meant to test whether an average of the kiez activityReduction values equals the value for berlin...


rm(list = ls())

# folder <- "C:/Users/jakob/projects/shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/"
folder <-"C:/Users/jakob/Desktop/snz_output/"
until_date <- "20210518"
file_pattern <- paste0("SnzData_daily_until", until_date, ".csv")
kiez_files <- list.files(paste0(folder, "perNeighborhood/"), pattern = file_pattern)

df_kiez_raw <- data.frame()
for (kiez_file in kiez_files) {
  df_single_kiez <- read_delim(file = paste0(folder, "perNeighborhood/", kiez_file),
                               "\t", escape_double = FALSE, trim_ws = TRUE)
  df_single_kiez$district <- gsub("SnzData.*?$", "", kiez_file)

  df_kiez_raw <- rbind(df_kiez_raw, df_single_kiez)

}

df_kiez_clean <- df_kiez_raw %>%
  mutate(date = ymd(date)) %>%
  pivot_longer(cols = !c(district, date), names_to = "activity", values_to = "reduction")


df_kiez_avg <- df_kiez_clean %>%
  group_by(date, activity) %>%
  summarise(reduction = mean(reduction)) %>%
  mutate(scenario = "average")


# for Berlin:
# df_berlin <- read_delim(file = "C:/Users/jakob/Desktop/snz_output/BerlinSnzData_daily_until20210518.csv",
df_berlin <- read_delim(file = paste0(folder, "Berlin", file_pattern),
                        "\t", escape_double = FALSE, trim_ws = TRUE) %>%
  mutate(date = ymd(date)) %>%
  pivot_longer(cols = !date, names_to = "activity", values_to = "reduction") %>%
  mutate(scenario = "berlin")

df_join <- rbind(df_kiez_avg, df_berlin) %>%
  pivot_wider(names_from = "scenario", values_from = "reduction") %>%
  mutate(difference = average - berlin) %>%
  pivot_longer(cols = !c(activity, date), names_to = "scenario", values_to = "reduction")


ggplot(df_join %>% filter(activity == "notAtHome" & scenario != "difference"), aes(date, reduction)) +
  geom_point(aes(color = scenario)) #+
# ylim(NA, 100)


# This plot shows that averaging the kiezes results in LESS ACTIVITY REDUCTION (more activity, more infection) than the berlin value...
# This points to a systematic error...
ggplot(df_join %>% filter(scenario == "difference" & activity == "notAtHome"), aes(date, reduction)) +
  geom_point()

mean_diff <- mean((df_join %>% filter(scenario == "difference"))$reduction)
print(paste0("mean difference: ", mean_diff))

total_size <- length((df_join %>% filter(scenario == "difference" & activity == "notAtHome"))$reduction)
num_above_zero <- length((df_join %>% filter(scenario == "difference" & activity == "notAtHome" & reduction > 0))$reduction)

print(paste0(num_above_zero, " of ", total_size, " are above 0: ", num_above_zero/total_size*100, " pct"))

# ok, now that we know that the district average is systematically above berlin value (less activity reduction), we should find out why;
# maybe check the individual districts.

df_kiez_clean %>%
  filter(activity == "notAtHome") %>%
  group_by(district) %>%
  summarise(red = mean(reduction)) %>%
  summarise(red = mean(red))

df_berlin %>% filter(activity == "notAtHome") %>% summarise(red = mean(reduction))

# Xberg_Fhein have the highest reduction: -20%
# Treptow_Koepenick have the lowest reduction: -10%
# Kiez avg = -14.2 / -14.4
# Berlin avg = -14.5 / -14.7

# Candidates for messing with our data: Treptow_Koepi, Marzahn_Hellersdorf,Lichtenberg, Spandau, Reinickendorf, NK


#why?
# 1) Zipcode missing in district -- NOPE
# 2) Wrong zipcodes in Berlin -- NOPE
# 3) Rounding area (always ceiling or floor?)



