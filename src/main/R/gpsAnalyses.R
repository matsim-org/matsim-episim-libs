library("osmextract")
library("plyr")
library("tidyverse")
library("lubridate")
library("stringr")
library("sf")


setwd("/Users/sebastianmuller/git/shared-svn/projects/episim/data/netcheck/data2")

# read osm points
osm_points <- oe_read("/Users/sebastianmuller/git/koeln-regbez-latest.osm.pbf", layer = "points", extra_tags = c("amenity", "shop", "leisure")) %>%
  select(osm_id, shop, leisure, amenity) %>%
  filter(!is.na(osm_id))

# read osm polygons
osm_multipolygons <- oe_read("/Users/sebastianmuller/git/koeln-regbez-latest.osm.pbf", layer = "multipolygons") %>%
  select(osm_way_id, shop, leisure, amenity, building, landuse) %>%
  filter(!is.na(osm_way_id)) %>%
  mutate(osm_way_id = as.numeric(osm_way_id))
# osm_multipolygons <- oe_read("/Users/sebastianmuller/git/nordrhein-westfalen-latest.osm.pbf", layer = "multipolygons")

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
bankHolidays = read.csv("/Users/sebastianmuller/git/matsim-episim-libs/src/main/resources/bankHolidays_sm.csv", header = TRUE)

#read senozon activity reductions
snz <- read.delim("/Users/sebastianmuller/git/shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/CologneSnzData_daily_until20221205.csv", header = TRUE, sep = "\t") %>%
  mutate(newDate = as.Date(strptime(date, "%Y%m%d"))) %>%
  mutate(weekday = wday(newDate, week_start = 1)) %>%
  filter(weekday < "6") %>%
  mutate( week = paste0(isoweek(newDate), "-", isoyear(newDate))) %>%
  group_by( week ) %>%
  summarize( actReduction=mean(notAtHome) * 0.01, newDate=mean(newDate))
  
#read all netcheck csv files
netcheck <- ldply( .data = list.files(path ="/Users/sebastianmuller/git/shared-svn/projects/episim/data/netcheck/data2", pattern="*.csv"),
                    .fun = read.csv,
                    header = TRUE)

# add an ID
netcheck <- tibble::rowid_to_column(netcheck, "ID")


# merge neteck with bankholidays 
netcheck <- netcheck %>% left_join(bankHolidays, by = c("day" = "bankHoliday"))

netcheck <- netcheck %>%
  mutate(date = as.Date(day)) %>%
  mutate(weekday = "Mon-Fri") %>%
  mutate(weekday = ifelse(wday(date) == 1, "Sun", weekday )) %>%
  mutate(weekday = ifelse(wday(date) == 7, "Sat", weekday )) %>%
  mutate(holiday = "no") %>%
  mutate(holiday = ifelse(str_count(Bundesland, "Germany") > 0, "yes", holiday )) %>%
  mutate(holiday = ifelse(str_count(Bundesland, "Nordrhein") > 0, "yes", holiday )) %>%
  mutate(holiday = ifelse(is.na(holiday), "no", holiday )) %>%
  mutate(nTimeStamps = 1 + str_count(timestamps, ","))

netcheck <- netcheck %>%
  mutate(maxTime =  strptime(substr(timestamps, nchar(timestamps) - 7, nchar(timestamps)), format="%H:%M:%S")) %>%
  mutate(minTime =  strptime(substr(timestamps, 1, 8), format="%H:%M:%S")) %>%
  mutate(duration = maxTime - minTime)

sumDurations <- as.numeric(sum(netcheck$duration))
sumTimePeriods <- sum(netcheck$nTimeStamps) - nrow(netcheck)
timePerTimePeriod <- sumDurations / sumTimePeriods

netcheck <- netcheck %>%
  mutate(duration = timePerTimePeriod + as.numeric(maxTime - minTime))

# some plots
netcheck %>%
  group_by(date, lives_in_cologne) %>%
  summarise(count = sum(nTimeStamps)) %>%
  ggplot (aes(x=date, y=count, color = lives_in_cologne)) +
  geom_point()

netcheck %>%
  group_by(date, weekday, holiday) %>%
  summarise(count = sum(nTimeStamps)) %>%
  ggplot (aes(x=date, y=count, color = weekday, shape = holiday)) +
  geom_point()


merged_multipolygons <- netcheck %>% inner_join(osm_multipolygons_with_points, by = c("osm_id" = "osm_way_id")) 

merged_multipolygons <- merged_multipolygons %>%
  group_by(ID) %>%
  add_count(name = "id_occurrence") %>%
  mutate(weightedTimeStamps = nTimeStamps / id_occurrence) %>%
  mutate(weightedDurations = duration / id_occurrence)

# remove some stuff from memory
rm(netcheck)
rm(osm_multipolygons_with_points)

# number of pings per day, needed for scaling
all <- merged_multipolygons %>%
  group_by(date, weekday, holiday) %>%
  summarise(sumTimeStamps = sum(weightedTimeStamps), sumDurations = sum(weightedDurations))



# not at home
notAtHome <- merged_multipolygons %>%
  # filter(distance_to_home > 0)
  filter(landuse != "residential" | is.na(landuse))


notAtHome <- notAtHome %>%
  group_by(date) %>%
  summarise(sumTimeStamps = sum(weightedTimeStamps), sumDurations = sum(weightedDurations))

notAtHome <- notAtHome %>% full_join(all, by = c("date" = "date")) %>%
  mutate(sumTimeStamps.x = ifelse(is.na(sumTimeStamps.x), 0, sumTimeStamps.x)) %>%
  mutate(sumDurations.x = ifelse(is.na(sumDurations.x), 0, sumDurations.x))

notAtHome$normTimeStamps <- notAtHome$sumTimeStamps.x / notAtHome$sumTimeStamps.y
notAtHome$normDurations <- notAtHome$sumDurations.x / notAtHome$sumDurations.y

baseline <- notAtHome %>%
  filter(date >= as.Date("2020-02-22") & date <= as.Date("2020-02-28")) %>%
  filter(holiday == "no") %>%
  group_by(weekday) %>%
  summarise(baseTimeStamps = median(normTimeStamps), baseDurations = median(normDurations))
  # summarise(base = quantile(norm, 0.9))

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


# plots

filteredForPlot <- merged_multipolygons %>%
  #restaurant
  # filter(amenity.x == "restaurant" | amenity.x == "bar" | amenity.x == "biergarten" | amenity.y == "restaurant" | amenity.y == "bar" | amenity.y == "biergarten")

  # other leisure
  # filter(amenity == "theatre" | amenity == "cinema")
  
  # private leisure
  # filter(distance_to_home > 0 & landuse == "residential")
  # filter(distance_to_home > 0 & (building == "apartments" | building == "house" | building == "semidetached_house"))
  
  #school
  # filter(amenity.x == "school" | amenity.y == "school")
  # filter(landuse == "education")
  
  #university
  # filter(amenity.x == "university" | amenity.y == "university")
  
  #kindergarten
  # filter(amenity == "kindergarten")
  
  # work
  # filter(distance_to_work < 1)
  filter(landuse == "commercial" | landuse == "industrial")
  # filter(landuse == "commercial")
  # filter(building == "office" | building == "commercial" | building == "industrial")
  
  # home
  # filter(distance_to_home != 0)
  # filter(landuse == "residential")

  # not home
  # filter(distance_to_home > 0)
  
  # shop
  # filter(!is.na(shop.x) | !is.na(shop.y))
  # filter(shop.x == "supermarket" | shop.y == "supermarket")
  # filter(shop.x == "mall" | shop.y == "mall")
  # filter(landuse == "retail")
  # filter(shop.x == "hairdresser" | shop.y == "hairdresser")
  
  # park
  # filter(leisure.x == "park")
  
  #errands

shareTimeStamps = 100 * sum(filteredForPlot$weightedTimeStamps) / sum(merged_multipolygons$weightedTimeStamps)
shareDurations = 100 * sum(filteredForPlot$weightedDurations) / sum(merged_multipolygons$weightedDurations)


filteredForPlot <- filteredForPlot %>%
  group_by(date) %>%
  summarise(sumTimeStamps = sum(weightedTimeStamps), sumDurations = sum(weightedDurations))

filteredForPlot <- filteredForPlot %>% full_join(all, by = c("date" = "date")) %>%
  mutate(sumTimeStamps.x = ifelse(is.na(sumTimeStamps.x), 0, sumTimeStamps.x)) %>%
  mutate(sumDurations.x = ifelse(is.na(sumDurations.x), 0, sumDurations.x))

filteredForPlot$normTimeStamps <- filteredForPlot$sumTimeStamps.x / filteredForPlot$sumTimeStamps.y
filteredForPlot$normDurations <- filteredForPlot$sumDurations.x / filteredForPlot$sumDurations.y

baseline <- filteredForPlot %>%
  filter(date >= as.Date("2020-02-22") & date <= as.Date("2020-02-28")) %>%
  filter(holiday == "no") %>%
  group_by(weekday) %>%
  summarise(baseTimeStamps = median(normTimeStamps), baseDurations = median(normDurations))
# summarise(base = quantile(norm, 0.9))

scaleMonFriTimeStamps <- baseline$baseTimeStamps[1]
scaleSatTimeStamps <- baseline$baseTimeStamps[2]
scaleSunTimeStamps <- baseline$baseTimeStamps[3]
scaleMonFriDurations <- baseline$baseDurations[1]
scaleSatDurations <- baseline$baseDurations[2]
scaleSunDurations <- baseline$baseDurations[3]

filteredForPlot <- filteredForPlot %>%
  mutate(normTimeStamps2 = -1) %>%
  mutate(normTimeStamps2 = ifelse(weekday == "Mon-Fri", (normTimeStamps - scaleMonFriTimeStamps) / scaleMonFriTimeStamps, normTimeStamps2)) %>%
  mutate(normTimeStamps2 = ifelse(weekday == "Sat", (normTimeStamps - scaleSatTimeStamps) / scaleSatTimeStamps, normTimeStamps2)) %>%
  mutate(normTimeStamps2 = ifelse(weekday == "Sun", (normTimeStamps - scaleSunTimeStamps) / scaleSunTimeStamps, normTimeStamps2)) %>%
  mutate(normDurations2 = -1) %>%
  mutate(normDurations2 = ifelse(weekday == "Mon-Fri", (normDurations - scaleMonFriDurations) / scaleMonFriDurations, normDurations2)) %>%
  mutate(normDurations2 = ifelse(weekday == "Sat", (normDurations - scaleSatDurations) / scaleSatDurations, normDurations2)) %>%
  mutate(normDurations2 = ifelse(weekday == "Sun", (normDurations - scaleSunDurations) / scaleSunDurations, normDurations2))

filteredForPlotWeekly <- filteredForPlot %>%
  filter(weekday == "Mon-Fri") %>%
  filter(holiday == "no") %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by( week ) %>%
  summarize( sumTimeStamps.x=mean(sumTimeStamps.x), sumDurations.x=mean(sumDurations.x), date=mean(date), normTimeStamps=mean(normTimeStamps), normDurations=mean(normDurations), normTimeStamps2=mean(normTimeStamps2), normDurations2=mean(normDurations2))



ggplot () +
  geom_point(data=filteredForPlot, aes(x=date, y=sumTimeStamps.x, color = weekday, shape = holiday))

  
ggplot () +
  geom_point(data=filteredForPlotWeekly, aes(x=date, y=normTimeStamps2), color="red") +
  geom_point(data=notAtHomeWeekly, aes(x=date, y=normTimeStamps2), color="blue") +
  # geom_point(data=snz, aes(x=newDate, y=actReduction), color="darkgrey") +
  labs(
    # title="Time Stamps",
    # caption="red: act (nc), green: out of home (nc), blue: out of home (snz)",
    caption="time stamps: activity (red), out of home (blue)",
    x="date", y="Red. vs. last week in Feb. 2020") +
  scale_x_date(date_labels = "%m-%Y", limit=c(as.Date("2020-01-01"),as.Date("2021-03-24")), date_breaks = "3 months") +
  scale_y_continuous(labels = scales::percent, limit=c(-1.0, 1.0))
  scale_y_continuous(labels = scales::percent)
  
ggsave("ts.png", width = 4, height = 3.2)


ggplot () +
  geom_point(data=filteredForPlotWeekly, aes(x=date, y=normDurations2), color="red") +
  geom_point(data=notAtHomeWeekly, aes(x=date, y=normDurations2), color="blue") +
  # geom_point(data=snz, aes(x=newDate, y=actReduction), color="darkgrey") +
  labs(
    # title="Duration",
    # caption="red: act (nc), green: out of home (nc), blue: out of home (snz)",
    caption="durations: activity (red), out of home (blue)",
    x="date", y="Red. vs. last week in Feb. 2020") +
  scale_x_date(date_labels = "%m-%Y", limit=c(as.Date("2020-01-01"),as.Date("2021-03-24")), date_breaks = "3 months") +
  scale_y_continuous(labels = scales::percent, limit=c(-1.0, 1.0))
  scale_y_continuous(labels = scales::percent)

ggsave("dur.png", width = 4, height = 3.2)


ggplot () +
  geom_point(data=notAtHomeWeekly, aes(x=date, y=normDurations2), color="purple") +
  geom_point(data=notAtHomeWeekly, aes(x=date, y=normTimeStamps2), color="orange") +
  geom_point(data=snz, aes(x=newDate, y=actReduction), color="darkgrey") +
  labs(
    # title="Duration",
    # caption="red: act (nc), green: out of home (nc), blue: out of home (snz)",
    caption="out of home gps time stamps (orange) and durations (purple), out of home mobile phone (grey)",
    x="date", y="Red. vs. last week in Feb. 2020") +
  scale_x_date(date_labels = "%m-%Y", limit=c(as.Date("2020-01-01"),as.Date("2021-03-24")), date_breaks = "3 months") +
  # scale_y_continuous(labels = scales::percent, limit=c(-1.0, 1.0))
scale_y_continuous(labels = scales::percent)

ggsave("outOfHome.png", width = 8, height = 4)




# Google Mobility Report
googleMobilityReportRaw <- read_csv("/Users/sebastianmuller/Downloads/Global_Mobility_Report.csv")

googleMobilityReport <- googleMobilityReportRaw %>%
  filter(sub_region_1 == "North Rhine-Westphalia") %>%
  select(-c(country_region_code, country_region, sub_region_1, sub_region_2, metro_area, iso_3166_2_code, census_fips_code, place_id)) %>%
  mutate(notAtHome =  -2 * residential_percent_change_from_baseline) %>%
  pivot_longer(!date, names_to = "type", values_to = "restriction") %>%
  # filter(type == "retail_and_recreation_percent_change_from_baseline" | type == "workplaces_percent_change_from_baseline" | type == "grocery_and_pharmacy_percent_change_from_baseline" | type == "parks_percent_change_from_baseline" )  %>%
  filter(type == "workplaces_percent_change_from_baseline")  %>%
  #filter(type == "retail_and_recreation_percent_change_from_baseline" | type == "workplaces_percent_change_from_baseline" | type == "notAtHome")   %>%
  #filter(type == "retail_and_recreation_percent_change_from_baseline" | type == "parks_percent_change_from_baseline")  %>%
  mutate(weekday = wday(date, week_start = 1)) %>%
  filter(weekday < "6") %>%
  mutate( week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by( week, type ) %>%
  summarize( restriction=0.01 * mean(restriction), date=mean(date))

# ggplot(data = googleMobilityReport, mapping=aes(x = date)) +
#   labs(
#     title="Google mobility report (NRW)",
#     caption="Source: Google",
#     x="date", y="Reduction in %") +
#   geom_point(mapping=aes(y = restriction, colour = type)) +
#   ylim(-75, 50) +
#   xlim(c(as.Date("2020-03-01"), as.Date("2021-02-28"))) +
#   theme(legend.position = "bottom")

ggplot () +
  geom_point(data=filteredForPlotWeekly, aes(x=date, y=normDurations2), color="red") +
  # geom_point(data=notAtHomeWeekly, aes(x=date, y=normDurations2), color="blue") +
  geom_point(data=googleMobilityReport, aes(x=date, y=restriction), color="darkgreen") +
  # geom_point(data=snz, aes(x=newDate, y=actReduction), color="darkgrey") +
  labs(
    # title="Duration",
    # caption="red: act (nc), green: out of home (nc), blue: out of home (snz)",
    caption="durations: activity (red), out of home (blue)",
    x="date", y="Red. vs. last week in Feb. 2020") +
  scale_x_date(date_labels = "%m-%Y", limit=c(as.Date("2020-01-01"),as.Date("2021-03-24")), date_breaks = "3 months") +
  scale_y_continuous(labels = scales::percent, limit=c(-0.8, 0.4))
scale_y_continuous(labels = scales::percent)

ggsave("dur.png", width = 4, height = 3.2)








sort(table(filteredForPlot$name, useNA="always"), decreasing = TRUE)

table(merged_multipolygons$ID, useNA="always")


  