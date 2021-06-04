library(readr)
library(tidyverse)
library(tmap)
library(sf)

setwd("C:/Users/jakob/projects")
#setwd("/Users/Ricardo/git")

svnLocationShape <-"public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp"
svnLocationData <- "public-svn/matsim/scenarios/countries/de/episim/mobilityData/landkreise/"
svnLocationShapeBld <- "public-svn/matsim/scenarios/countries/de/episim/mobilityData/bundeslaender/vg2500_bld.shp"

outersect <- function(x, y) {
  sort(c(setdiff(x, y),
         setdiff(y, x)))
}

#Part 1) LK_Timeline 22-5 compare two dates
data <- read.csv2(paste0(svnLocationData,"LK_Timeline_WeeklyNumbers_perPerson.csv"))

data %>% glimpse()

data2 <- data %>%
  mutate(date = as.Date(date, format = "%d,%m,%y")) %>% 
  filter(type == "endNonHomeActs") %>%
  mutate("night_total" = X.0h + X0.1h + X1.2h + X2.3h + X3.4h + X4.5h + X22.23h + X23.24h + X24.25h + X25.26h + X26.27h)


for(i in 1:nrow(data2)){
  if (grepl("Kreis", data2$area[i],fixed = TRUE)
      | grepl("kreis", data2$area[i],fixed = TRUE) ){
    if (grepl("Landkreis", data2$area[i],fixed = TRUE)
        |grepl("landkreis", data2$area[i],fixed = TRUE) ){
      data2$isLandkreis[i] = TRUE
      data2$isKreis[i] = FALSE
    } else {
      data2$isLandkreis[i] = FALSE
      data2$isKreis[i] = TRUE
      }
  } else {
    data2$isLandkreis[i] = FALSE
    data2$isKreis[i] = FALSE
  }
}  

#data2$isLandkreis[i] = if (grepl("Landkreis", data2$area[i],ignore.case = TRUE, fixed = TRUE)) TRUE else FALSE
#data2$isKreis[i] = if (grepl("Kreis", data2$area[i], ignore.case = TRUE,fixed = TRUE)) TRUE else FALSE

data2$area <- data2$area %>% 
  str_replace("Landkreis ","") %>% 
  str_replace("Kreis ","") %>% 
  str_replace("Nienburg/Weser","Nienburg (Weser)") %>% 
  str_replace("Cottbus - Chóśebuz","Cottbus") %>% 
  str_replace("Lindau","Lindau (Bodensee)") %>% 
  str_replace("Rhein-Neuss", "Rhein-Kreis Neuss") %>% 
  str_replace("Altenkirchen","Altenkirchen (Westerwald)")


data_to_merge <- data2 %>% 
  filter(date == "2021-05-02" | date == "2021-03-28") %>% 
  select(c(date,area,isKreis,isLandkreis,night_total)) %>% 
  pivot_wider(names_from = date,values_from = night_total) %>%
  mutate(change = `2021-05-02`/`2021-03-28`) %>% select(c(area,isKreis,isLandkreis,change))

# Landkreise Shapefile 

lk <- st_read(svnLocationShape)

lk$name_2 <- lk$name_2 %>% 
  str_replace("(Landkreis)","")
  

lk2 <- lk %>% mutate(area = name_2)

for(i in 1:nrow(lk2)){
  lk2$isLandkreis[i] = if (lk2$type_2[i] == "Landkreis") TRUE else FALSE
  lk2$isKreis[i] = if (lk2$type_2[i] == "Kreis") TRUE else FALSE
}  

lk3 <- lk2 %>% left_join(data_to_merge, by=c("area","isLandkreis", "isKreis")) 
lk3 <- lk3 %>% left_join(data_to_merge, by= "area") 

for(i in 1:nrow(lk3)){
  if(!is.na(lk3$change.x[i])){
    lk3$change_final[i] = lk3$change.x[i]
  } else {
    lk3$change_final[i] = lk3$change.y[i] 
  }
}  

lk3 <-lk3 %>% mutate(percent_reduction = change_final -1)

lk_names <- lk3$name_2
data_names <- unique(data2$area)

Reduce(intersect,list(lk_names,data_names))
Reduce(outersect,list(lk_names,data_names))
bundeslaender <- st_read(svnLocationShapeBld)

tmap_mode("view")
tm_shape(lk3) +
  tm_polygons(col = "percent_reduction",
              id = "area", 
             title.col = "% Reduction of Nightly Activites", title='28.March/02.May') #+ 
  tm_shape(bundeslaender) +
  tm_polygons()

  
  
  
  
  
  
#Part 2 duration for one day

data_hours <- read.csv2(paste0(svnLocationData,"mobilityData_OverviewLK.csv"))
data_hours %>% glimpse()

data_hours2 <- data_hours %>%
  mutate(date = as.Date(strptime(date, "%Y%m%d")))

data_hours2$Landkreis <- data_hours2$Landkreis %>%
  str_replace("Landkreis ","") %>% 
  str_replace("Kreis ","") %>% 
  str_replace("Nienburg/Weser","Nienburg (Weser)") %>% 
  str_replace("Cottbus - Chóśebuz","Cottbus") %>% 
  str_replace("Lindau","Lindau (Bodensee)") %>% 
  str_replace("Rhein-Neuss", "Rhein-Kreis Neuss") %>% 
  str_replace("Altenkirchen","Altenkirchen (Westerwald)")

data_hours_date <- data_hours2 %>%
  filter(date == "2021-05-02") %>%
  filter(Landkreis != "Landau in der Pfalz")


lk <- st_read(svnLocationShape) %>% 
  mutate(Landkreis = name_2)

lk2 <- lk %>% left_join(data_hours_date, by="Landkreis") %>% mutate(hoursOutHome = outOfHomeDuration)
lk_names <- lk$name_2
data_names <- unique(data_hours$Landkreis)

Reduce(intersect,list(lk_names,data_names))
Reduce(outersect,list(lk_names,data_names))



tm_shape(lk2) +
  tm_polygons(col = "hoursOutHome",
              id = "Landkreis", 
              title.col = "% Average hours out of home", title='02. May 2021')
dddd = lk$name_2
#View(dddd)

