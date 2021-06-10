library(readr)
library(tidyverse)
library(tmap)
library(sf)
library(stats)

#setwd("C:/Users/jakob/projects")
setwd("/Users/Ricardo/git")
#setwd("/Users/kainagel/")

rm(list = ls())

## Define global variables
gbl_location_data <- "public-svn/matsim/scenarios/countries/de/episim/mobilityData/landkreise/"

location_shape <- "public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/"
gbl_bl <- st_read(paste0(location_shape, "Bundesländer_2016_ew.shp"))
# import and modify shape file for districts (Landkreise, Stadtkreise...)

lk <- st_read(paste0(location_shape, "landkreise-in-germany.shp"))
gbl_lk <- lk %>% mutate(area = name_2)

for(i in 1:nrow(gbl_lk)){
  gbl_lk$isLandkreis[i] = if (gbl_lk$type_2[i] == "Landkreis") TRUE else FALSE
  gbl_lk$isKreis[i] = if (gbl_lk$type_2[i] == "Kreis") TRUE else FALSE
}  

## Define global functions
gbl_outersect <- function(x, y) {
  sort(c(setdiff(x, y),
         setdiff(y, x)))
}

gbl_prep_mobility_data <- function(df){
  
  for(i in 1:nrow(df)){
    if (grepl("Kreis", df$area[i],fixed = TRUE)
        | grepl("kreis", df$area[i],fixed = TRUE) ){
      if (grepl("Landkreis", df$area[i],fixed = TRUE)
          |grepl("landkreis", df$area[i],fixed = TRUE) ){
        df$isLandkreis[i] <- TRUE
        df$isKreis[i] <- FALSE
      } else {
        df$isLandkreis[i] <- FALSE
        df$isKreis[i] <- TRUE
      }
    } else {
      df$isLandkreis[i] <- FALSE
      df$isKreis[i] <- FALSE
    }
  }  
  
  # Manual change of names to work with merge later
  df$area <- df$area %>% 
    str_replace("Landkreis ","") %>%
    str_replace("Kreis ","") %>%
    str_replace("Nienburg/Weser","Nienburg (Weser)") %>% 
    str_replace("Lindau","Lindau (Bodensee)") %>% 
    str_replace("Rhein-Neuss", "Rhein-Kreis Neuss") %>% 
    str_replace("Altenkirchen","Altenkirchen (Westerwald)") %>% 
    str_replace("St, Wendel", "St. Wendel")
  
  
  df[df$area == "München" & df$isLandkreis,"area"] <- "München(Landkreis)"
  df[grepl("Cottbus",df$area),"area"] <- "Cottbus"
  
  return(df)
}

############################################
## Part 1) Pct Reduction in nightly trips ##
############################################

rm(list = gbl_outersect(ls(),ls(pattern = "^gbl")))

#data <- read.csv2(paste0(gbl_location_data,"LK_Timeline_weekly.csv"), fileEncoding = "UTF-8")
data <- read.csv2(paste0(gbl_location_data,"LK_Timeline_weekdays.csv"), fileEncoding = "UTF-8")
#data <- read.csv2(paste0(gbl_location_data,"LK_Timeline_weekends.csv"), fileEncoding = "UTF-8")

data2 <- data %>%
  mutate_all(type.convert) %>%
  mutate(date = as.Date(date, format = "%d.%m.%y")) %>% 
  mutate_if(is.factor, as.character) %>%
  filter(type == "endNonHomeActs") %>%
  mutate("night_total" = X.0h + X0.1h + X1.2h + X2.3h + X3.4h + X4.5h + X22.23h + X23.24h + X24.25h + X25.26h + X26.27h)


data3 <- gbl_prep_mobility_data(data2)

# weekly or weekend dates
# 2020-03-08                  
# 2021-03-28                  
# 2021-04-04
# 2021-04-11
# 2021-04-18
# 2021-04-25
# 2021-05-02

#weekdays dates
#2021-03-26
#2021-04-30
#2021-05-07

dateBefore <- "2021-03-26"
dateAfter <- "2021-05-07"


#select dates that should be compared and find the change between two daes. 
data_to_merge <- data3 %>%
  filter(date == dateAfter | date == dateBefore ) %>%
  select(c(date,area,isKreis,isLandkreis,night_total)) %>% 
  pivot_wider(names_from = date,values_from = night_total, values_fn = mean ) %>%
  mutate(change = !!sym(dateAfter)/ !!sym(dateBefore) ) %>%
  select(c(area,isKreis,isLandkreis,change)) %>%
  {.}

# yyyyyy Bei mir existieren einige Einträge (z.B. München, Würzburg) für jedes Datum doppelt.  Damit scheitert dann die Division in "mutate".
# Es gibt einen "Landkreis München", und es gibt "München" (= die Stadt).  Vielleicht ist es doch nicht so gut, das oben rauszulöschen?
# kai, jun'21

# Für München wurde das jetzt manuell gerichtet. -jakob

# First merge where all three variables are checked --> change.x
lk3 <- gbl_lk %>% left_join(data_to_merge, by=c("area","isLandkreis", "isKreis")) 
#Then, just using location names --> change.y
lk3 <- lk3 %>% left_join(data_to_merge, by= "area") 

# if there is no value in change.x, then use value from change.y
for(i in 1:nrow(lk3)){
  if(!is.na(lk3$change.x[i])){
    lk3$change_final[i] = lk3$change.x[i]
  } else {
    lk3$change_final[i] = lk3$change.y[i] 
  }
}  


lk3 <-lk3 %>% mutate(percent_reduction = (change_final -1)*100)

Reduce(gbl_outersect,list(lk3$name_2,unique(data3$area)))

tmap_mode("view")
tm_shape(lk3) +
  tm_polygons(col = "percent_reduction",
              id = "area", 
              title.col = "% Reduction of Nightly Activites", title= paste0(dateAfter," vs ",dateBefore) ) +
  tm_layout(legend.position = c("right", "top"), title= 'Veränderung der Anzahl beendeter Aktivitäten außer Haus zwischen 22-5 Uhr in %',  title.position = c('right', 'top')) +
  tm_shape(gbl_bl) +
  tm_borders(lwd = 2, col = "blue")

####################################
## Part 2) duration of activities ##
####################################

rm(list = gbl_outersect(ls(),ls(pattern = "^gbl")))

#data_hours <- read.csv2(paste0(gbl_location_data,"LK_mobilityData_weekly.csv"),fileEncoding = "UTF-8")
#data_hours <- read.csv2(paste0(gbl_location_data,"LK_mobilityData_weekends.csv"),fileEncoding = "UTF-8")
data_hours <- read.csv2(paste0(gbl_location_data,"LK_mobilityData_weekdays.csv"),fileEncoding = "UTF-8")


data_hours2 <- data_hours %>%
  mutate(date = as.Date(strptime(date, "%Y%m%d"))) %>% rename(area = Landkreis)

data_hours3 <- gbl_prep_mobility_data(data_hours2) %>% filter(area != "Landau in der Pfalz")

# weekly or weekend dates
# 2020-03-08                  
# 2021-03-28                  
# 2021-04-04
# 2021-04-11
# 2021-04-18
# 2021-04-25
# 2021-05-02

#weekdays dates
#2021-03-26
#2021-04-30
#2021-05-07

dateBefore <- "2021-03-26"
dateAfter <- "2021-05-07" # also used for single day analysis

data_hours_date <- data_hours3 %>%
  filter(date == dateAfter ) %>%
  mutate (outOfHomeDuration = as.numeric(outOfHomeDuration)) %>%
  select(c(area,isKreis,isLandkreis, outOfHomeDuration))

data_hours_change <- data_hours3 %>%
  filter(date == dateBefore | date == dateAfter) %>%
  select(c(date,area,isKreis,isLandkreis,outOfHomeDuration)) %>% 
  mutate (outOfHomeDuration = as.numeric(outOfHomeDuration)) %>%
  pivot_wider(names_from = date,values_from = outOfHomeDuration) %>%
  mutate(change = !!sym(dateAfter)/!!sym(dateBefore) ) %>%
  select(c(area,isKreis,isLandkreis,change)) %>%
  {.}


# First merge where all three variables are checked --> change.x
lk3_change <- gbl_lk %>% left_join(data_hours_change, by=c("area","isLandkreis", "isKreis")) 
lk3_date <- gbl_lk %>% left_join(data_hours_date, by=c("area","isLandkreis", "isKreis")) 
#Then, just using location names --> change.y
lk3_change <- lk3_change %>% left_join(data_hours_change, by= "area") 
lk3_date <- lk3_date %>% left_join(data_hours_date, by= "area")

# if there is no value in change.x, then use value from change.y
for(i in 1:nrow(lk3_change)){
  if(!is.na(lk3_change$change.x[i])){
    lk3_change$change_final[i] = lk3_change$change.x[i]
  } else {
    lk3_change$change_final[i] = lk3_change$change.y[i] 
  }
}
for(i in 1:nrow(lk3_date)){
  if(!is.na(lk3_date$outOfHomeDuration.x[i])){
    lk3_date$outOfHomeDuration_final[i] = lk3_date$outOfHomeDuration.x[i]
  } else {
    lk3_date$outOfHomeDuration_final[i] = lk3_date$outOfHomeDuration.y[i] 
  }
}  


lk3_date <- lk3_date %>% left_join(data_hours_date, by="area") %>% mutate(hoursOutHome = outOfHomeDuration_final)
lk3_changes <- lk3_change %>% left_join(data_hours_change, by="area") %>% mutate( changeOutOfHome = (change_final -1.)*100.  )
# yyyyyy seems like a duplicated code? -jakob, june '21


Reduce(gbl_outersect,list(lk3_date$area,unique(data_hours3$area)))

tmap_mode("view" )
tm_shape(lk3_date) +
  tm_polygons(col = "hoursOutHome",
              id = "area", 
              title.col = "% Average hours out of home", title=dateAfter) +
  tm_layout(legend.position = c("right", "top"), title = 'Durchschnittliche Dauer aushäusiger Aktivitäten pro Person in Stunden',  title.position = c('right', 'top')) +
  tm_shape(gbl_bl) +
  tm_borders(lwd = 2, col = "blue") 

tm_shape(lk3_changes) +
  tm_polygons(col = "changeOutOfHome",
              id = "area", 
              title.col = "% Average hours out of home", title=paste0( dateAfter, " vs ", dateBefore )) +
  tm_layout(legend.position = c("right", "top"), title= 'Veränderung Dauer aushäusiger Aktivitäten pro Person in %',  title.position = c('right', 'top')) +
  tm_shape(gbl_bl) +
  tm_borders(lwd = 2, col = "blue") 
