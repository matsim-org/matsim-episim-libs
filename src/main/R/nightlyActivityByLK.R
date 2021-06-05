library(readr)
library(tidyverse)
library(tmap)
library(sf)

setwd("C:/Users/jakob/projects")
#setwd("/Users/Ricardo/git")
#setwd("/Users/kainagel/")

svnLocationShape <- "public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/"
svnLocationData <- "public-svn/matsim/scenarios/countries/de/episim/mobilityData/landkreise/"

outersect <- function(x, y) {
  sort(c(setdiff(x, y),
         setdiff(y, x)))
}

#Part 1) LK_Timeline 22-5 compare two dates
data <- read.csv2(paste0(svnLocationData,"LK_Timeline_WeeklyNumbers_perPerson.csv"))

prepare_activity_data <- function(data)
data2 <- data %>%
  mutate(date = as.Date(date, format = "%d,%m,%y")) %>% 
  filter(type == "endNonHomeActs") %>%
  mutate("night_total" = X.0h + X0.1h + X1.2h + X2.3h + X3.4h + X4.5h + X22.23h + X23.24h + X24.25h + X25.26h + X26.27h)

# Specify whether location is Landkreis or Kreis, needed for merge later, since some kreise and landkreise share a name
for(i in 1:nrow(data2)){
  if (grepl("Kreis", data2$area[i],fixed = TRUE)
      | grepl("kreis", data2$area[i],fixed = TRUE) ){
    if (grepl("Landkreis", data2$area[i],fixed = TRUE)
        |grepl("landkreis", data2$area[i],fixed = TRUE) ){
      data2$isLandkreis[i] <- TRUE
      data2$isKreis[i] <- FALSE
    } else {
      data2$isLandkreis[i] <- FALSE
      data2$isKreis[i] <- TRUE
    }
  } else {
    data2$isLandkreis[i] <- FALSE
    data2$isKreis[i] <- FALSE
  }
}  

# Manual change of names to work with merge later
data2$area <- data2$area %>% 
  str_replace("Landkreis ","") %>%
  str_replace("München()","München") %>%
  str_replace("Kreis ","") %>%
  str_replace("Nienburg/Weser","Nienburg (Weser)") %>% 
  str_replace("Cottbus - Ch�?ebuz","Cottbus") %>% 
  str_replace("Lindau","Lindau (Bodensee)") %>% 
  str_replace("Rhein-Neuss", "Rhein-Kreis Neuss") %>% 
  str_replace("Altenkirchen","Altenkirchen (Westerwald)") %>% 
  str_replace("St, Wendel", "St. Wendel")


data2[data2$area == "M�nchen" & data2$isLandkreis,"area"] <- "M�nchen(Landkreis)"
data2[grepl("Cottbus",data2$area),"area"] <- "Cottbus"

#select dates that should be compared and find the change between two daes. 
data_to_merge <- data2 %>%
  filter(date == "2021-05-02" | date == "2021-03-28") %>%
  select(c(date,area,isKreis,isLandkreis,night_total)) %>% 
  pivot_wider(names_from = date,values_from = night_total) %>%
  mutate(change = `2021-05-02`/`2021-03-28`) %>%
  select(c(area,isKreis,isLandkreis,change)) %>%
  {.}

# yyyyyy Bei mir existieren einige Einträge (z.B. München, Würzburg) für jedes Datum doppelt.  Damit scheitert dann die Division in "mutate".
# Es gibt einen "Landkreis München", und es gibt "München" (= die Stadt).  Vielleicht ist es doch nicht so gut, das oben rauszulöschen?
# kai, jun'21


# Landkreise Shapefile 
lk <- st_read(paste0(svnLocationShape, "landkreise-in-germany.shp"))
bl <- st_read(paste0(svnLocationShape, "Bundesl�nder_2016_ew.shp"))




lk2 <- lk %>% mutate(area = name_2)


for(i in 1:nrow(lk2)){
  lk2$isLandkreis[i] = if (lk2$type_2[i] == "Landkreis") TRUE else FALSE
  lk2$isKreis[i] = if (lk2$type_2[i] == "Kreis") TRUE else FALSE
}  

# First merge where all three variables are checked --> change.x
lk3 <- lk2 %>% left_join(data_to_merge, by=c("area","isLandkreis", "isKreis")) 
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

lk_names <- lk3$name_2
data_names <- unique(data2$area)

Reduce(intersect,list(lk_names,data_names))
Reduce(outersect,list(lk_names,data_names))


tmap_mode("view")
tm_shape(lk3) +
  tm_polygons(col = "percent_reduction",
              id = "area", 
              title.col = "% Reduction of Nightly Activites", title='28.March/02.May') +
  tm_layout(legend.position = c("right", "top"), title= 'Veränderung der Anzahl beendeter Aktivitäten außer Haus zwischen 22- 5 Uhr in %',  title.position = c('right', 'top')) #+
#tm_shape(bl) +
#  tm_borders(lwd = 2, col = "blue")


#Part 2 duration for one day

data_hours <- read.csv2(paste0(svnLocationData,"mobilityData_OverviewLK.csv"))
data_hours %>% glimpse()

data_hours2 <- data_hours %>%
  mutate(date = as.Date(strptime(date, "%Y%m%d")))

# Specify whether location is Landkreis or Kreis, needed for merge later, since some kreise and landkreise share a name
for(i in 1:nrow(data_hours2)){
  if (grepl("Kreis", data_hours2$Landkreis[i],fixed = TRUE)
      | grepl("kreis", data_hours2$Landkreis[i],fixed = TRUE) ){
    if (grepl("Landkreis", data_hours2$Landkreis[i],fixed = TRUE)
        |grepl("landkreis", data_hours2$Landkreis[i],fixed = TRUE) ){
      data_hours2$isLandkreis[i] = TRUE
      data_hours2$isKreis[i] = FALSE
    } else {
      data_hours2$isLandkreis[i] = FALSE
      data_hours2$isKreis[i] = TRUE
    }
  } else {
    data_hours2$isLandkreis[i] = FALSE
    data_hours2$isKreis[i] = FALSE
  }
}  

data_hours2$Landkreis <- data_hours2$Landkreis %>%
  str_replace("Landkreis ","") %>%
  str_replace("München()","München") %>% 
  str_replace("Kreis ","") %>% 
  str_replace("Nienburg/Weser","Nienburg (Weser)") %>% 
  str_replace("Cottbus - Chóśebuz","Cottbus") %>% 
  str_replace("Lindau","Lindau (Bodensee)") %>% 
  str_replace("Rhein-Neuss", "Rhein-Kreis Neuss") %>% 
  str_replace("Altenkirchen","Altenkirchen (Westerwald)") %>% 
  str_replace("St, Wendel", "St. Wendel")

data_hours2[data_hours2$Landkreis == "M�nchen" & data_hours2$isLandkreis,"Landkreis"] <- "M�nchen(Landkreis)"
data_hours2[grepl("Cottbus",data_hours2$Landkreis),"Landkreis"] <- "Cottbus"

data_hours_date <- data_hours2 %>%
  filter(date == "2021-03-28") %>%
  select(c(Landkreis,isKreis,isLandkreis, outOfHomeDuration))

data_hours_change <- data_hours2%>%
  filter(date == "2021-05-02" | date == "2021-03-28") %>%
  select(c(date,Landkreis,isKreis,isLandkreis,outOfHomeDuration)) %>% 
  pivot_wider(names_from = date,values_from = outOfHomeDuration) %>%
  mutate(change = `2021-05-02`/`2021-03-28`) %>% 
  select(c(Landkreis,isKreis,isLandkreis,change))

lk <- st_read(paste0(svnLocationShape, "landkreise-in-germany.shp")) 
lk2 <- lk %>% mutate(Landkreis = name_2)

bl <- st_read(paste0(svnLocationShape, "Bundesländer_2016_ew.shp"))
for(i in 1:nrow(lk2)){
  lk2$isLandkreis[i] = if (lk2$type_2[i] == "Landkreis") TRUE else FALSE
  lk2$isKreis[i] = if (lk2$type_2[i] == "Kreis") TRUE else FALSE
}  

# First merge where all three variables are checked --> change.x
lk3_change <- lk2 %>% left_join(data_hours_change, by=c("Landkreis","isLandkreis", "isKreis")) 
lk3_date <- lk2 %>% left_join(data_hours_date, by=c("Landkreis","isLandkreis", "isKreis")) 
#Then, just using location names --> change.y
lk3_change <- lk3_change %>% left_join(data_hours_change, by= "Landkreis") 
lk3_date <- lk3_date %>% left_join(data_hours_date, by= "Landkreis")

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


lk3_date <- lk3_date %>% left_join(data_hours_date, by="Landkreis") %>% mutate(hoursOutHome = outOfHomeDuration_final)
lk3_changes <- lk3_change %>% left_join(data_hours_change, by="Landkreis") %>% mutate(changeOutOfHome = (change_final -1)*100)

lk_names <- lk2$name_2
data_names <- unique(data_hours2$Landkreis)

#Reduce(intersect,list(lk_names,data_names))
Reduce(outersect,list(lk_names,data_names))

tmap_mode("view")
tm_shape(lk3_date) +
  tm_polygons(col = "hoursOutHome",
              id = "Landkreis", 
              title.col = "% Average hours out of home", title='02. May 2021') +
  tm_layout(legend.position = c("right", "top"), title= 'Durschnittliche Dauer außhäusiger Aktivitäten pro Person in Stunden',  title.position = c('right', 'top'))# +
  #tm_shape(bl) +
  #tm_borders(lwd = 2, col = "blue") 

tm_shape(lk3_changes) +
  tm_polygons(col = "changeOutOfHome",
              id = "Landkreis", 
              title.col = "% Average hours out of home", title='28.March/02.May') +
  #tm_layout(legend.position = c("right", "top"), title= 'Veränderung Dauer außhäusiger Aktivitäten pro Person in %',  title.position = c('right', 'top')) #+
  #tm_shape(bl) +
  tm_borders(lwd = 2, col = "blue") 

