library(gridExtra)
library(ggiraphExtra)
library(xlsx)
library(tidyverse)

source("/Users/sydney/git/matsim-episim-libs/src/main/R/gpsAnalyses.R")

options(scipen = 999) #Use this, to NOT display axis labeling of plots in scientific notation

#Renaming of column and
colnames(netcheck)[6] <- "Cologne Resident"
netcheck$`Cologne Resident`[netcheck$`Cologne Resident` == ""] <- "Unknown"
netcheck$holiday[netcheck$holiday == "no"] <- "False"
netcheck$holiday[netcheck$holiday == "yes"] <- "True"

# First exploratory plots
# Counting no of timestamps over time, differentiatd by whether data point is provided by a resident of the city of Cologne
netcheck %>%
  group_by(date, `Cologne Resident`) %>%
  summarise(count = sum(nTimeStamps)) %>%
  ungroup() %>%
  ggplot(aes(x = date, y = count, color = `Cologne Resident`)) +
  geom_point(size = 2) +
  theme_minimal() +
  theme(legend.position = "bottom") +
  theme(text = element_text(size = 13)) +
  theme(axis.ticks.x = element_line(),
                   axis.ticks.y = element_line(),
                   axis.ticks.length = unit(5, "pt")) +
    scale_x_date(date_breaks = "3 month", date_labels = "%d/%b/%y") +
    xlab("Date") +
    ylab("Counts") +
    scale_color_brewer(palette = "Set2")

#Similar plot as above, here differentiation by weekday and holiday (TRUE/FALSE)
netcheck %>%
  group_by(date, weekday, holiday) %>%
  summarise(count = sum(nTimeStamps)) %>%
  ungroup() %>%
  ggplot(aes(x = date, y = count, color = weekday)) +
  geom_point(size = 1.5) +
  theme_minimal() +
  theme(legend.position = "bottom", legend.title = element_blank()) +
  theme(text = element_text(size = 17)) +
  theme(axis.ticks.x = element_line(),
                   axis.ticks.y = element_line(),
                   axis.ticks.length = unit(5, "pt")) +
  theme(axis.text.x = element_text(angle = 45, vjust = 1)) +
    scale_x_date(date_breaks = "2 month", date_labels = "%d/%b/%y",  limit=c(as.Date("2019-10-01"),as.Date("2021-03-01"))) +
    scale_y_continuous(labels = scales::comma) +
    xlab("Date") +
    ylab("Counts") +
    scale_color_brewer(palette = "Dark2")

ggsave("timepoints.pdf", dpi = 500, w = 9, h = 4.5)

#Plots for results section
filteredForPlot <- merged_multipolygons %>%
  #restaurant
  # filter(amenity.x == "restaurant" | amenity.x == "bar" | amenity.x == "biergarten" | amenity.y == "restaurant" | amenity.y == "bar" | amenity.y == "biergarten")
  
  # other leisure
  # filter(amenity == "theatre" | amenity == "cinema")
  
  # private leisure
  # filter(distance_to_home > 0 & landuse == "residential")
  # filter(distance_to_home > 0 & (building == "apartments" | building == "house" | building == "semidetached_house"))
  
  #school
 filter(amenity.x == "school" | amenity.y == "school")
# filter(landuse == "education")

#university
# filter(amenity.x == "university" | amenity.y == "university")

#kindergarten
# filter(amenity == "kindergarten")

# work
# filter(distance_to_work < 1)
# filter(landuse == "commercial" | landuse == "industrial")
# filter(landuse == "commercial")
# filter(building == "office" | building == "commercial" | building == "industrial")

# home
# filter(distance_to_home != 0)
# filter(landuse == "residential")

# not home
# filter(distance_to_home > 0)

# shop
#filter(!is.na(shop.x) | !is.na(shop.y)) %>%
# filter(shop.x != "supermarket" | shop.y != "supermarket")
# filter(shop.x == "mall" | shop.y == "mall")
# filter(landuse == "retail")
# filter(shop.x == "hairdresser" | shop.y == "hairdresser")

# park
#filter(leisure.x == "park")

#errands

# S: I believe the following two lines are unnecessary and can be removed
#shareTimeStamps = 100 * sum(filteredForPlot$weightedTimeStamps) / sum(merged_multipolygons$weightedTimeStamps)
#shareDurations = 100 * sum(filteredForPlot$weightedDurations) / sum(merged_multipolygons$weightedDurations)

#For each day the no. of time stamps are counted
filteredForPlot <- filteredForPlot %>%
  group_by(date) %>%
  summarise(sumTimeStamps = sum(weightedTimeStamps), sumDurations = sum(weightedDurations)) %>%
  ungroup()

#If for a certain date no data exists, then the corresponding entry of the data frame is set equal to 0
filteredForPlot <- filteredForPlot %>% full_join(all, by = c("date" = "date")) %>%
  mutate(sumTimeStamps.x = ifelse(is.na(sumTimeStamps.x), 0, sumTimeStamps.x)) %>%
  mutate(sumDurations.x = ifelse(is.na(sumDurations.x), 0, sumDurations.x))

#For a given category, its share (of time stamps and durations) of all activity types is computed
filteredForPlot$normTimeStamps <- filteredForPlot$sumTimeStamps.x / filteredForPlot$sumTimeStamps.y
filteredForPlot$normDurations <- filteredForPlot$sumDurations.x / filteredForPlot$sumDurations.y

baseline <- filteredForPlot %>%
  filter(date >= as.Date("2020-09-01") & date <= as.Date("2020-09-30")) %>%
  # filter(date >= as.Date("2020-03-01")) %>%
  filter(holiday == "no") %>%
  group_by(weekday) %>%
  summarise(baseTimeStamps = median(normTimeStamps), baseDurations = median(normDurations)) %>%
  #summarise(baseTimeStamps = quantile(normTimeStamps, 0.9), baseDurations = quantile(normDurations, 0.9)) %>%
  ungroup()

scaleMonFriTimeStamps <- baseline$baseTimeStamps[1]
scaleSatTimeStamps <- baseline$baseTimeStamps[2]
scaleSunTimeStamps <- baseline$baseTimeStamps[3]
scaleMonFriDurations <- baseline$baseDurations[1]
scaleSatDurations <- baseline$baseDurations[2]
scaleSunDurations <- baseline$baseDurations[3]

filteredForPlot <- filteredForPlot %>%
  mutate(normTimeStamps2 = -1) %>%
  mutate(normTimeStamps2 = case_when(weekday == "Mon-Fri" ~ (normTimeStamps - scaleMonFriTimeStamps) / scaleMonFriTimeStamps,
                                     weekday == "Sat" ~ (normTimeStamps - scaleSatTimeStamps) / scaleSatTimeStamps, 
                                     weekday == "Sun" ~ (normTimeStamps - scaleSunTimeStamps) / scaleSunTimeStamps,
                                     .default = normTimeStamps2)) %>%
  mutate(normDurations2 = case_when(weekday == "Mon-Fri" ~ (normDurations - scaleMonFriDurations) / scaleMonFriDurations,
                                     weekday == "Sat" ~ (normDurations - scaleSatDurations) / scaleSatDurations, 
                                     weekday == "Sun" ~ (normDurations - scaleSunDurations) / scaleSunDurations,
                                     .default = normTimeStamps2))

#Aggregation of daily data on a weekly level
filteredForPlotWeekly <- filteredForPlot %>%
  filter(weekday == "Mon-Fri") %>%
  filter(holiday == "no") %>%
  mutate(week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by(week) %>%
  summarize(sumTimeStamps.x=mean(sumTimeStamps.x), sumDurations.x=mean(sumDurations.x), date=mean(date), normTimeStamps=mean(normTimeStamps), normDurations=mean(normDurations), normTimeStamps2=mean(normTimeStamps2), normDurations2=mean(normDurations2)) %>%
  ungroup()


# NETCHECK PLOTS -----------------------------------------------------------

ggplot() +
  geom_point(data=filteredForPlot, aes(x=date, y=sumTimeStamps.x, color = weekday, shape = holiday)) +
  theme_minimal() +
  theme(legend.position = "bottom") +
  theme(text = element_text(size = 13)) +
  theme(axis.ticks.x = element_line(),
                   axis.ticks.y = element_line(),
                   axis.ticks.length = unit(5, "pt")) +
    scale_x_date(date_breaks = "1 month", date_labels = "%d/%m/%y", limit=c(as.Date("2020-03-01"),as.Date("2021-03-24"))) +
    xlab("Date") +
    ylab("Counts") +
    scale_color_brewer(palette = "Set2")

ggplot() +
  geom_point(data=filteredForPlotWeekly, aes(x=date, y=normTimeStamps2), color = "#1b9e77") +
  geom_point(data=notAtHomeWeekly, aes(x=date, y=normTimeStamps2), color = "#d95f02") +
  #geom_point(data=snz, aes(x=newDate, y=actReduction), color="darkgrey") +
  labs(
    # title="Time Stamps",
    # caption="red: act (nc), green: out of home (nc), blue: out of home (snz)",
    caption = "Time stamps: activity (green), out of home (orange)",
    # x="date", y="Red. vs. last week in Feb. 2020") +
    x = "Date", y = "Red. vs. 90th percentile") +
  scale_y_continuous(labels = scales::percent, limit=c(-1.0, 0.3)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%d/%m/%y", limit=c(as.Date("2020-03-01"),as.Date("2021-03-24"))) +
  theme_minimal() +
  theme(legend.position = "bottom") +
  theme(text = element_text(size = 13)) +
  theme(axis.ticks.x = element_line(),
                   axis.ticks.y = element_line(),
                   axis.ticks.length = unit(5, "pt"))


ggsave("ts22.png", width = 4, height = 3.2)

# NETCHECK AND SENOZON PLOTS -----------------------------------------------------------
colnames(snz)[2] <- "Activity"

#Plot of
#1) Activity reduction of chosen activity type (netcheck and senozon)
#2) Reduction of out of home duration (senozon)
colors <- c("Not At Home (cell-based)" = "dimgrey", "School (cell-based)" = "darkgrey", "School (GPS-based)" = "#1b9e77")

#School Vacation
date_breaks <- data.frame(start = c(as.Date("2020-04-06"), as.Date("2020-06-29"), as.Date("2020-10-12"), as.Date("2020-12-21")),
                          end = c(as.Date("2020-04-18"), as.Date("2020-08-11"), as.Date("2020-10-24"), as.Date("2021-01-06")),
                          colors = c("Vacation", "Vacation", "Vacation", "Vacation"))


school <- ggplot() +
  geom_rect(data = date_breaks,
            aes(xmin = start,
                xmax = end,
                ymin = - Inf,
                ymax = Inf,
                fill = colors),
            alpha = 0.3) +
    scale_fill_manual(values = c("#B9BBB6", "#B9BBB6", "#B9BBB6", "#B9BBB6")) +
  geom_point(data=filteredForPlotWeekly, aes(x=date, y=normTimeStamps2, color ="School (GPS-based)"), size = 2) +
  geom_line(data=snz %>% filter(Activity == "notAtHome"), aes(x=newDate, y=actReduction, color = "Not At Home (cell-based)"), size = 2) +
  geom_point(data=snz %>% filter(Activity == "education"), aes(x=newDate, y=actReduction, color = "School (cell-based)"), size = 2) +
  labs(
    caption = "",
    x = "Date",
#   y = "") +
    y = "Activity Reduction") +
  geom_vline(xintercept = as.Date("2020-03-13"), linetype = "dotted") + #School closure
  geom_vline(xintercept = as.Date("2020-12-14"), linetype = "dotted") + #School closure
  scale_y_continuous(labels = scales::percent, limit = c(-1.0, 0.3)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%d/%m/%y", limit=c(as.Date("2020-03-01"),as.Date("2021-03-01"))) +
  theme_minimal() +
  theme(legend.position = "bottom", legend.title = element_blank()) +
  theme(text = element_text(size = 20)) +
  theme(axis.ticks.x = element_line(),
                   axis.ticks.y = element_line(),
                   axis.ticks.length = unit(5, "pt")) +
  theme(axis.text.x = element_text(angle = 45, vjust = 1)) +
    scale_color_manual(values = colors) +
      guides(color=guide_legend(nrow=2, byrow = TRUE)) 

p <- arrangeGrob(school,work, nrow=1)

ggsave("SchoolWorkActivityRed.pdf", p, dpi = 500, width = 15.75, height = 5)

#Plot of
#1) "Not at home" reduction via reduction of # of time stamps (netcheck)
#2) "Not at home" reduction via reduction of out of home duration (netcheck)
#3) "Not at home" reduction via reduction of out of home duration (senozon)
ggplot() +
  geom_point(data = snz, aes(x = newDate, y = actReduction), color = "#666666") +
  geom_point(data=notAtHomeWeekly, aes(x=date, y=normDurations2), color="#66C2A5") +
  geom_point(data=notAtHomeWeekly, aes(x=date, y=normTimeStamps2), color="#FC8D62") +
  labs(
    caption="Out of home gps time stamps (orange) and durations (green), out of home mobile phone (grey)",
    x = "Date",
    y = "Red. vs. last week in Feb. 2020") +
  scale_y_continuous(labels = scales::percent, limit=c(-0.7, 0.3)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%d/%m/%y", limit=c(as.Date("2020-03-01"),as.Date("2021-03-24"))) +
  theme_minimal() +
  theme(legend.position = "bottom") +
  theme(text = element_text(size = 13)) +
  theme(axis.ticks.x = element_line(),
                   axis.ticks.y = element_line(),
                   axis.ticks.length = unit(5, "pt"))

ggsave("outOfHome.png", width = 8, height = 4)

# SENOZON PLOTS -----------------------------------------------------------

# Data prep
# Include column that contains information whether or not a given date is a school holiday
snz <- snz %>%
  mutate(schoolVac = "no") %>%
  mutate(schoolVac = ifelse(newDate > as.Date("2020-04-05") & newDate < as.Date("2020-04-19"), "yes", schoolVac)) %>%
  mutate(schoolVac = ifelse(newDate > as.Date("2020-06-28") & newDate < as.Date("2020-08-12"), "yes", schoolVac)) %>%
  mutate(schoolVac = ifelse(newDate > as.Date("2020-10-11") & newDate < as.Date("2020-10-25"), "yes", schoolVac)) %>%
  mutate(schoolVac = ifelse(newDate > as.Date("2020-12-22") & newDate < as.Date("2021-01-07"), "yes", schoolVac))

# Plot depicting reduction of out of home duration (line) and reduction of school activity (dots)
colnames(snz)[5] <- "School Vacation"
snz <- snz %>% mutate(`School Vacation` = case_when(`School Vacation` == "yes" ~ "education, during school vacation",
                                                    `School Vacation` == "no" ~ "education, no school vacation"))
ggplot() +
  geom_line(data = snz %>% filter(Activity == "notAtHome") , aes(x=newDate, y=actReduction, linetype = Activity), color="#666666", size = 1.5) +
  geom_point(data = snz %>% filter(Activity == "education") , aes(x=newDate, y=actReduction, color=`School Vacation`), size = 1.5) +
  geom_vline(xintercept = as.Date("2020-03-13"), linetype = "dotted") +
  geom_vline(xintercept = as.Date("2020-12-14"), linetype = "dotted") +
  labs(x="Date", y="Red. vs. before pandemic") +
  scale_y_continuous(labels = scales::percent, limit = c(-0.6, 0.1)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%d/%m/%y", limit=c(as.Date("2020-03-01"),as.Date("2021-03-01"))) +
  theme_minimal() +
  theme(legend.position = "bottom", legend.title = element_blank()) +
  theme(text = element_text(size = 17)) +
  theme(axis.ticks.x = element_line(),
                   axis.ticks.y = element_line(),
                   axis.ticks.length = unit(5, "pt")) +
  theme(legend.position = "bottom") +
  scale_color_brewer(palette = "Dark2") +
  theme(axis.text.x = element_text(angle = 45, vjust = 1)) +
  guides(color = guide_legend(nrow = 2))

ggsave("snzSchool.pdf", width = 8, height = 5, dpi = 500)

# Plot depicting reduction of out of home duration (line) and reduction of all other activities (dots)
snz$Activity <- factor(snz$Activity, levels = c("business", "education", "errands", "leisure", "shop_daily", "shop_other", "work", "notAtHome"))
ggplot(snz, aes(x = newDate, y = actReduction, colour = Activity,
                    linetype = Activity, shape = Activity), size = 1.5) +
geom_point(size = 1.5) +
geom_line(size = 1.5) +
   scale_linetype_manual(values=c(NA, NA, NA, NA, NA, NA, NA, "solid")) +
    scale_shape_manual(values=c(16, 16, 16, 16, 16, 16, 16, NA)) +
  #geom_point(data = snz %>% filter(Activity != "notAtHome") , aes(x = newDate, y = actReduction, color = Activity)) +
 # geom_line(data = snz %>% filter(Activity == "notAtHome"), aes(x = newDate, y = actReduction), color = "#666666", size = 1.2) +
  labs(x = "Date", y = "Reduction vs. before pandemic") +
  scale_y_continuous(labels = scales::percent, limit=c(-0.6, 0.1)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%d/%m/%y", limit=c(as.Date("2020-03-01"),as.Date("2021-03-01"))) +
  theme_minimal() +
  theme(legend.position = "bottom", legend.title = element_blank()) +
  theme(text = element_text(size = 17)) +
  theme(axis.ticks.x = element_line(),
                   axis.ticks.y = element_line(),
                   axis.ticks.length = unit(5, "pt")) +
  scale_color_brewer(palette = "Dark2") +
  theme(axis.text.x = element_text(angle = 45, vjust = 1))

ggsave("snzAll.pdf", width = 8, height = 5.5, dpi = 500)


# GOOGLE MOBILITY REPORTS PLOTS -----------------------------------------------------------

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
  mutate(week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by(week, type) %>%
  summarize(restriction = 0.01 * mean(restriction), date = mean(date))

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
    caption = "durations: activity (red), work google (green)",
    x = "date", y = "Red. vs. last week in Feb. 2020") +
  scale_x_date(date_labels = "%m-%Y", limit=c(as.Date("2020-01-01"),as.Date("2021-03-24")), date_breaks = "3 months") +
  scale_y_continuous(labels = scales::percent, limit = c(-0.8, 0.4))

ggsave("dur.png", width = 4, height = 3.2)

# WEATHER PLOTS -----------------------------------------------------------
weatherData <- read_delim("https://bulk.meteostat.net/daily/10513.csv.gz", delim = ",", col_names = FALSE, col_types = cols(
  X1 = col_date(format = ""),
  X2 = col_double(),
  X3 = col_double(),
  X4 = col_double(),
  X5 = col_double(),
  X6 = col_double(),
  X7 = col_double(),
  X8 = col_double(),
  X9 = col_double(),
  X10 = col_double(),
  X11 = col_double()
))

colnames(weatherData) <- c("date", "tavg", "tmin", "tmax", "prcp", "snow", "wdir", "wspd", "wpgt", "pres", "tsun")

weatherDataByWeek <- weatherData %>%
  mutate(week = paste0(isoweek(date), "-", isoyear(date))) %>%
  group_by(week) %>%
  summarize( date=mean(date), tavg=mean(tavg), tmin=mean(tmin), tmax=mean(tmax), prcp=mean(prcp), snow=mean(snow), wdir=mean(wdir), wspd=mean(wspd), wpgt=mean(wpgt), pres=mean(pres), tsun=mean(tsun))


#Plot of Daily maximum temperature over time and reduction of chosen activity type (GPS based data)
ggplot() +
  geom_point(data=filteredForPlotWeekly, aes(x=date, y=normTimeStamps2), color="#FC8D62") +
  # geom_point(data=notAtHomeWeekly, aes(x=date, y=normDurations2), color="blue") +
  geom_point(data=weatherDataByWeek, aes(x=date, y= -0.7 + tmax * 0.03), color="darkgreen") +
  # geom_point(data=snz, aes(x=newDate, y=actReduction), color="darkgrey") +
  labs(
    caption = "time stamps: activity (red), tmax (green, scaled)",
    x = "Date",
    y = "Red. vs. 90th percentile") +
  scale_y_continuous(labels = scales::percent, limit = c(-1.0, 0.3)) +
  scale_x_date(date_breaks = "3 month", date_labels = "%d/%b/%y", limit=c(as.Date("2020-03-01"),as.Date("2021-03-24"))) +
  theme_minimal() +
  theme(legend.position = "bottom") +
  theme(text = element_text(size = 13)) +
  theme(axis.ticks.x = element_line(),
                   axis.ticks.y = element_line(),
                   axis.ticks.length = unit(5, "pt")) +
  theme(legend.position = "bottom") +
  scale_color_brewer(palette = "Set2")

ggsave("weather.png", width = 4, height = 3.2)



#Figure 5 --> Time stamps at OSM facilities
amenity <- read.xlsx("/Users/sydney/Downloads/timestampsOSM.xlsx", sheetIndex = 1)
amenity <- amenity[1:11,]
amenity <- amenity[,-1]
amenity <- amenity[,-6]
colnames(amenity) <- c("amenity", "timepointsDatasetA", "timepointsDatasetB", "timepointsAll", "share")
amenity$amenity <- factor(amenity$amenity, levels = c("other", "social_facility", "pharmacy", "doctors", "cafe", "school", "fast_food", "atm", "restaurant", "hospital", "parking"))
amenityplot <- ggplot(amenity) +
  geom_col(aes(timepointsAll/1000, amenity), fill = "#1b9e77", width = 0.6) +
  theme_minimal() +
  theme(text = element_text(size = 19)) + 
  scale_x_continuous() +
  ylab("Amenity") +
  scale_x_continuous(labels = scales::comma, breaks = c(0,1000,2000,3000)) +
  xlab("Thousand Timestamps")
ggsave("amenity.pdf", amenityplot, dpi = 500, w = 6, h = 3)

shop <- read.xlsx("/Users/sydney/Downloads/timestampsOSM.xlsx", sheetIndex = 2)
shop <- shop[1:11,]
shop <- shop[,-6]
colnames(shop) <- c("shop", "timepointsDatasetA", "timepointsDatasetB", "timepointsAll", "share")
shop$shop <- factor(shop$shop, levels = c("other", "electronics", "department__store", "hairdresser", "do_it_yourself", "kiosk", "bakery", "furniture", "clothes", "mall", "supermarket"))
shopplot <- ggplot(shop) +
  geom_col(aes(timepointsAll/1000, shop), fill = "#1b9e77", width = 0.6) +
  theme_minimal() +
  theme(text = element_text(size = 19)) + 
  scale_x_continuous() +
  ylab("Shop") +
  scale_x_continuous(labels = scales::comma, breaks = c(0,500, 1000,1500,2000)) +
  xlab("Thousand Timestamps")
ggsave("shop.pdf", shopplot, dpi = 500, w = 6, h = 3)

leisure <- read.xlsx("/Users/sydney/Downloads/timestampsOSM.xlsx", sheetIndex = 3)
leisure <- leisure[1:11,]
leisure <- leisure[,-6]
colnames(leisure) <- c("leisure facility", "timepointsDatasetA", "timepointsDatasetB", "timepointsAll", "share")
leisure$`leisure facility` <- factor(leisure$`leisure facility`, levels = c("other", "common", "pitch", "dance", "golf_course", "garden", "stadium", "playground", "fitness_centre", "sports_centre", "park"))
leisureplot <- ggplot(leisure) +
  geom_col(aes(timepointsAll/1000, `leisure facility`), fill = "#1b9e77", width = 0.6) +
  theme_minimal() +
  theme(text = element_text(size = 19)) + 
  scale_x_continuous() +
  ylab("Leisure") +
  scale_x_continuous(labels = scales::comma, breaks = c(0,250, 500, 750, 1000,1250, 1500, 1750,2000)) +
  xlab("Thousand Timestamps")
ggsave("leisure.pdf", leisureplot, dpi = 500, w = 6, h = 3)


landuse <- read.xlsx("/Users/sydney/Downloads/timestampsOSM.xlsx", sheetIndex = 4)
landuse <- landuse[1:11,]
landuse <- landuse[,-6]
colnames(landuse) <- c("landuse", "timepointsDatasetA", "timepointsDatasetB", "timepointsAll", "share")
landuse$landuse <- factor(landuse$landuse, levels = c("other", "farmyard", "farmland", "allotments", "forest", "grass", "railway", "retail", "industrial", "commercial", "residential"))
ggplot(landuse) +
  geom_col(aes(timepointsAll/1000, landuse), fill = "#1b9e77", width = 0.6) +
  theme_minimal() +
  theme(text = element_text(size = 19)) + 
  scale_x_continuous() +
  ylab("Landuse Activity") +
  scale_x_continuous(labels = scales::comma, breaks = c(0,25000, 50000, 75000, 100000)) +
  xlab("Thousand Timestamps") +
ggsave("landuse.pdf", dpi = 500, w = 6, h = 3)

building <- read.xlsx("/Users/sydney/Downloads/timestampsOSM.xlsx", sheetIndex = 5)
building <- building[1:11,]
building <- building[,-6]
colnames(building) <- c("building", "timepointsDatasetA", "timepointsDatasetB", "timepointsAll", "share")
building$building <- factor(building$building, levels = c("other", "commercial", "industrial", "hospital", "semidetachedhouse", "office", "house", "retail", "terrace", "apartments", "yes"))
buildingplot <- ggplot(building) +
  geom_col(aes(timepointsAll/1000, building), fill = "#1b9e77", width = 0.6) +
  theme_minimal() +
  theme(text = element_text(size = 19)) + 
  scale_x_continuous() +
  ylab("Building") +
  scale_x_continuous(labels = scales::comma, breaks = c(0,3000,6000,9000)) +
  xlab("Thousand Timestamps")
ggsave("building.pdf", buildingplot, dpi = 500, w = 6, h = 3)


#Figure 6 --> Demonstration of the data standardization process
TimeStamps <- read.xlsx("/Users/sydney/Downloads/Standardizationtimestmaps.xlsx", sheetIndex = 1)
TimeStamps <- TimeStamps[1:13, 1:5]
TimeStamps <- pivot_longer(TimeStamps, cols = c("Act.A", "Act.B", "Other", "All"))
TimeStamps <- TimeStamps %>% mutate(name = case_when(name == "Act.A" ~ "Activity A",
                                                     name == "Act.B" ~ "Activity B",
                                                     name == "Other" ~ "Other",
                                                     name == "All" ~ "All"))
#TimeStamps$Date <- as.Date(TimeStamps$Date)
#TimeStamps$Date <- TimeStamps$Date + 25
ggplot(TimeStamps %>% filter(name != "All"), aes(fill = name, y = value, x = Date)) +
  geom_bar(position="stack", stat="identity") + 
  theme_minimal() +
  theme(text = element_text(size = 21)) +
  theme(legend.position = "bottom", legend.title = element_blank()) +
  scale_x_date(date_breaks = "1 month", date_labels = "%d/%m/%y") +
  theme(axis.text.x = element_text(angle = 45, vjust = 1, hjust = 1)) +
  ylab("Timestamps") +
  xlab("Date") +
  scale_fill_brewer(palette = "Dark2")

ggsave("TimestampsLeft.pdf", dpi = 500, w = 5.25, h = 5.25)

TimeStampsRelative <- read.xlsx("/Users/sydney/Downloads/Standardizationtimestmaps.xlsx", sheetIndex = 2)
TimeStampsRelative <- pivot_longer(TimeStampsRelative, cols = c("Activity.A", "Activity.B", "Other"))
TimeStampsRelative <- TimeStampsRelative %>% mutate(name = case_when(name == "Activity.A" ~ "Activity A",
                                                     name == "Activity.B" ~ "Activity B",
                                                     name == "Other" ~ "Other"))

ggplot(TimeStampsRelative, aes(fill = name, y = value, x = Date)) +
  geom_bar(position="fill", stat="identity") + 
  theme_minimal() +
  theme(text = element_text(size = 21)) +
  theme(legend.position = "bottom", legend.title = element_blank()) +
  scale_x_date(date_breaks = "1 month", date_labels = "%d/%m/%y") +
  theme(axis.text.x = element_text(angle = 45, vjust = 1, hjust = 1)) +
  scale_y_continuous(labels=scales::percent) +
  ylab("") +
  xlab("Date") +
  scale_fill_brewer(palette = "Dark2")

ggsave("TimestampsMiddle.pdf", dpi = 500, w = 5.25, h = 5.25)

TimeStampsFinal <- read.xlsx("/Users/sydney/Downloads/Standardizationtimestmaps.xlsx", sheetIndex = 3)
TimeStampsFinal <- pivot_longer(TimeStampsFinal, cols = c("Activity.A", "Activity.B", "Other"))
TimeStampsFinal <- TimeStampsFinal %>% mutate(name = case_when(name == "Activity.A" ~ "Activity A",
                                                                     name == "Activity.B" ~ "Activity B",
                                                                     name == "Other" ~ "Other"))
ggplot(TimeStampsFinal, aes(color = name, y = value, x = Date)) +
  geom_hline(yintercept=0) +
  geom_point(size = 4) + 
  theme_minimal() +
  theme(text = element_text(size = 21)) +
  theme(legend.position = "bottom", legend.title = element_blank()) +
  scale_x_date(date_breaks = "1 month", date_labels = "%d/%m/%y") + 
  theme(axis.text.x = element_text(angle = 45, vjust = 1, hjust = 1)) +
  scale_y_continuous(labels=scales::percent) +
  ylab("") +
  xlab("Date") +
  scale_color_brewer(palette = "Dark2")

ggsave("TimestampsRight.pdf", dpi = 500, w = 5.25, h = 5.25)

