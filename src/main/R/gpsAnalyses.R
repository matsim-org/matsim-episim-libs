library("osmextract")
library("plyr")
library("tidyverse")
library("lubridate")
library("stringr")
library("sf")


setwd("/Users/sydney/root/svn/shared-svn/projects/episim/data/netcheck/data2")

# read osm points
osm_points <- oe_read("/Users/sydney/Downloads/koeln-regbez-latest.osm.pbf", layer = "points", extra_tags = c("amenity", "shop", "leisure")) %>%
  select(osm_id, shop, leisure, amenity) %>%
  filter(!is.na(osm_id))

# read osm polygons
osm_multipolygons <- oe_read("/Users/sydney/Downloads/koeln-regbez-latest.osm.pbf", layer = "multipolygons") %>%
  select(osm_way_id, shop, leisure, amenity, building, landuse) %>%
  filter(!is.na(osm_way_id)) %>%
  mutate(osm_way_id = as.numeric(osm_way_id))

# filter shop, amenity, leisure points
osm_points_shop <- osm_points %>%
  filter(!is.na(shop)) %>%
  select(c(osm_id, shop))

osm_points_amenity <- osm_points %>%
  filter(!is.na(amenity)) %>%
  select(c(osm_id, amenity))

osm_points_leisure <- osm_points %>%
  filter(!is.na(leisure)) %>%
  select(c(osm_id, leisure))

# filter building polygons
osm_multipolygons_buildings <- osm_multipolygons %>%
  filter(!is.na(building)) %>%
  select(osm_way_id)

# join shop, amenity, leisure ponts with building polygons 
sf_use_s2(FALSE)

osm_multipolygons_with_shop_points <- osm_multipolygons_buildings %>%
  st_join(osm_points_shop) %>%
  filter(!is.na(osm_way_id)) %>%
  filter(!is.na(shop)) %>%
  select(c(osm_way_id, shop)) %>%
  st_drop_geometry()

osm_multipolygons_with_amenity_points <- osm_multipolygons_buildings %>%
  st_join(osm_points_amenity) %>%
  filter(!is.na(osm_way_id)) %>%
  filter(!is.na(amenity)) %>%
  select(c(osm_way_id, amenity)) %>%
  st_drop_geometry()

osm_multipolygons_with_leisure_points <- osm_multipolygons_buildings %>%
  st_join(osm_points_leisure) %>%
  filter(!is.na(osm_way_id)) %>%
  filter(!is.na(leisure)) %>%
  select(c(osm_way_id, leisure)) %>%
  st_drop_geometry()

osm_multipolygons <- osm_multipolygons %>%
  st_drop_geometry()

# merge with all polygons
osm_multipolygons_with_points <- osm_multipolygons %>% left_join(osm_multipolygons_with_shop_points, by = c("osm_way_id" = "osm_way_id"))
osm_multipolygons_with_points <- osm_multipolygons_with_points %>% left_join(osm_multipolygons_with_amenity_points, by = c("osm_way_id" = "osm_way_id"))
osm_multipolygons_with_points <- osm_multipolygons_with_points %>% left_join(osm_multipolygons_with_leisure_points, by = c("osm_way_id" = "osm_way_id"))


# remove some stuff from memory
rm(osm_multipolygons_buildings)
rm(osm_multipolygons)
rm(osm_points)
rm(osm_points_shop)
rm(osm_points_amenity)
rm(osm_points_leisure)
rm(osm_multipolygons_with_shop_points)
rm(osm_multipolygons_with_amenity_points)
rm(osm_multipolygons_with_leisure_points)


# osm_lines <- oe_read("/Users/sebastianmuller/git/koeln-regbez-latest.osm.pbf", layer = "lines")

# osm_multilinestrings <- oe_read("/Users/sebastianmuller/git/koeln-regbez-latest.osm.pbf", layer = "multilinestrings")

# osm_other_relations <- oe_read("/Users/sebastianmuller/git/koeln-regbez-latest.osm.pbf", layer = "other_relations")

#read bank holidays 
bankHolidays = read.csv("/Users/sydney/git/matsim-episim-libs/src/main/resources/bankHolidays.csv", header = TRUE) %>%
  mutate(date = as.Date(bankHoliday))

#read senozon activity reductions
snz <- read.delim("/Users/sydney/root/svn/shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/CologneSnzData_daily_until20221205.csv", header = TRUE, sep = "\t") %>%
  select(-c(notAtHomeExceptLeisureAndEdu, notAtHomeExceptEdu, notAtHome_22, accomp, traveling, undefined, visit, home)) %>%
  pivot_longer(!date, names_to = "act", values_to = "reduction") %>%
  mutate(newDate = as.Date(strptime(date, "%Y%m%d"))) %>%
  left_join(bankHolidays, by = c("newDate" = "date")) %>%
  mutate(weekday = wday(newDate, week_start = 1)) %>%
  filter(weekday < "6") %>%
  mutate(holiday = "no") %>%
  mutate(holiday = ifelse(str_count(Bundesland, "Germany") > 0, "yes", holiday )) %>%
  mutate(holiday = ifelse(str_count(Bundesland, "Nordrhein") > 0, "yes", holiday )) %>%
  mutate(holiday = ifelse(is.na(holiday), "no", holiday )) %>%
  filter(holiday == "no") %>%
  mutate( week = paste0(isoweek(newDate), "-", isoyear(newDate))) %>%
  group_by( week, act ) %>%
  summarize( actReduction=mean(reduction) * 0.01, newDate=mean(newDate)) %>%
  ungroup()



#read all netcheck csv files
netcheck <- ldply( .data = list.files(path ="/Users/sydney/root/svn/shared-svn/projects/episim/data/netcheck/data2", pattern="*_v2.csv"),
                   .fun = read.csv,
                   header = TRUE)

# add an ID
netcheck <- tibble::rowid_to_column(netcheck, "ID")

# merge netcheck with bankholidays 
netcheck <- netcheck %>% left_join(bankHolidays, by = c("day" = "bankHoliday"))

netcheck <- netcheck %>%
  mutate(date = as.Date(day)) %>%
  mutate(weekday = "Mon-Fri") %>%
  mutate(weekday = ifelse(wday(date) == 1, "Sun", weekday )) %>%
  mutate(weekday = ifelse(wday(date) == 7, "Sat", weekday )) %>%
  mutate(holiday = "no") %>%
  mutate(holiday = ifelse(str_count(Bundesland, "Germany") > 0, "yes", holiday )) %>%
  mutate(holiday = ifelse(str_count(Bundesland, "Nordrhein") > 0, "yes", holiday )) %>%
  mutate(holiday = ifelse(is.na(holiday), "no", holiday)) %>%
  mutate(nTimeStamps = 1 + str_count(timestamps, ","))

# calcualte duration
netcheck <- netcheck %>%
  mutate(maxTime =  strptime(substr(timestamps, nchar(timestamps) - 7, nchar(timestamps)), format="%H:%M:%S")) %>%
  mutate(minTime =  strptime(substr(timestamps, 1, 8), format="%H:%M:%S")) %>%
  mutate(duration = maxTime - minTime)

sumDurations <- as.numeric(sum(netcheck$duration))
sumTimePeriods <- sum(netcheck$nTimeStamps) - nrow(netcheck)
timePerTimePeriod <- sumDurations / sumTimePeriods

netcheck <- netcheck %>%
  mutate(duration = timePerTimePeriod + as.numeric(maxTime - minTime))


merged_multipolygons <- netcheck %>% inner_join(osm_multipolygons_with_points, by = c("osm_id" = "osm_way_id")) 

merged_multipolygons <- merged_multipolygons %>%
  group_by(ID) %>%
  add_count(name = "id_occurrence") %>%
  mutate(weightedTimeStamps = nTimeStamps / id_occurrence) %>%
  mutate(weightedDurations = duration / id_occurrence) %>%
  ungroup()

# remove some stuff from memory
#rm(netcheck)
#rm(osm_multipolygons_with_points)

# number of pings per day, needed for scaling
all <- merged_multipolygons %>%
  group_by(date, weekday, holiday) %>%
  summarise(sumTimeStamps = sum(weightedTimeStamps), sumDurations = sum(weightedDurations)) %>%
  ungroup()

# not at home
notAtHome <- merged_multipolygons %>%
  # filter(distance_to_home > 0)
  filter(landuse != "residential" | is.na(landuse))

colnames(notAtHome)[10] <- "date"
colnames(all)[1] <- "date"

notAtHome <- notAtHome %>%
  group_by(date) %>%
  summarise(sumTimeStamps = sum(weightedTimeStamps), sumDurations = sum(weightedDurations)) %>%
  ungroup()

notAtHome <- notAtHome %>% full_join(all, by = c("date" = "date")) %>%
  mutate(sumTimeStamps.x = ifelse(is.na(sumTimeStamps.x), 0, sumTimeStamps.x)) %>%
  mutate(sumDurations.x = ifelse(is.na(sumDurations.x), 0, sumDurations.x))

notAtHome$normTimeStamps <- notAtHome$sumTimeStamps.x / notAtHome$sumTimeStamps.y
notAtHome$normDurations <- notAtHome$sumDurations.x / notAtHome$sumDurations.y

baseline <- notAtHome %>%
  filter(date >= as.Date("2020-09-01") & date <= as.Date("2020-09-30")) %>%
  # filter(date >= as.Date("2020-03-01")) %>%
  filter(holiday == "no") %>%
  group_by(weekday) %>%
  summarise(baseTimeStamps = median(normTimeStamps), baseDurations = median(normDurations)) %>%
  # summarise(baseTimeStamps = quantile(normTimeStamps, 0.9), baseDurations = quantile(normDurations, 0.9)) %>%
  ungroup()

scaleMonFriTimeStamps <- baseline$baseTimeStamps[1]
scaleSatTimeStamps <- baseline$baseTimeStamps[2]
scaleSunTimeStamps <- baseline$baseTimeStamps[3]
scaleMonFriDurations <- baseline$baseDurations[1]
scaleSatDurations <- baseline$baseDurations[2]
scaleSunDurations <- baseline$baseDurations[3]

notAtHome <- notAtHome %>%
  mutate(normTimeStamps2 = -1) %>%
  mutate(normTimeStamps2 = ifelse(weekday == "Mon-Fri", (normTimeStamps - scaleMonFriTimeStamps) / scaleMonFriTimeStamps, normTimeStamps2)) %>%
  mutate(normTimeStamps2 = ifelse(weekday == "Sat", (normTimeStamps - scaleSatTimeStamps) / scaleSatTimeStamps, normTimeStamps2)) %>%
  mutate(normTimeStamps2 = ifelse(weekday == "Sun", (normTimeStamps - scaleSunTimeStamps) / scaleSunTimeStamps, normTimeStamps2)) %>%
  mutate(normDurations2 = -1) %>%
  mutate(normDurations2 = ifelse(weekday == "Mon-Fri", (normDurations - scaleMonFriDurations) / scaleMonFriDurations, normDurations2)) %>%
  mutate(normDurations2 = ifelse(weekday == "Sat", (normDurations - scaleSatDurations) / scaleSatDurations, normDurations2)) %>%
  mutate(normDurations2 = ifelse(weekday == "Sun", (normDurations - scaleSunDurations) / scaleSunDurations, normDurations2))

notAtHomeWeekly <- notAtHome %>%
  filter(weekday == "Mon-Fri") %>%
  filter(holiday == "no") %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by( week ) %>%
  summarize( sumTimeStamps.x=mean(sumTimeStamps.x), sumDurations.x=mean(sumDurations.x), date=mean(date), normTimeStamps=mean(normTimeStamps), normDurations=mean(normDurations), normTimeStamps2=mean(normTimeStamps2), normDurations2=mean(normDurations2))




sort(table(merged_multipolygons$name, useNA="always"), decreasing = TRUE)

table(merged_multipolygons$ID, useNA="always")


