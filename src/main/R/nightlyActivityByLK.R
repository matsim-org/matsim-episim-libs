library(readr)
library(tidyverse)
library(tmap)
library(sf)

svnLocationShape <- "/Users/Ricardo/git/public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp"
svnLocationData <- "/Users/Ricardo/git/public-svn/matsim/scenarios/countries/de/episim/mobilityData/landkreise/"

outersect <- function(x, y) {
  sort(c(setdiff(x, y),
         setdiff(y, x)))
}

#Part 1) LK_Timeline 22-5 compare two dates
data <- read.csv2(paste0(svnLocationData,"LK_Timeline_WeeklyNumbers_perPerson.csv"))

unwanted_hours <- as.character(seq(6,21))

data %>% glimpse()

data2 <- data %>%
  mutate(date = as.Date(date, format = "%d,%m,%y")) %>% 
  filter(type == "endNonHomeActs") %>%
#  select(!contains(unwanted_hours))# %>% 
  mutate("night_total" = `X.0h` +`X0.1h`+`X1.2h`+`X2.3h`+`X3.4h`+`X4.5h`+`X22.23h`+`X23.24h`+`X24.25h`+`X25.26h`+`X26.27h`)

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
  select(c(date,area,night_total)) %>% 
  pivot_wider(names_from = date,values_from = night_total,values_fn = mean) %>%
  mutate(change = `2021-05-02`/`2021-03-28`) %>% select(c(area,change))

# Landkreise Shapefile 

lk <- st_read(svnLocationShape) %>% 
  mutate(area = name_2)

lk2 <- lk %>% left_join(data_to_merge, by="area") %>% mutate(percent_reduction = change -1)

lk_names <- lk$name_2
data_names <- unique(data2$area)

Reduce(intersect,list(lk_names,data_names))
Reduce(outersect,list(lk_names,data_names))

tmap_mode("view")
tm_shape(lk2) +
  tm_polygons(col = "percent_reduction",
              id = "area", 
             title.col = "% Reduction of Nightly Activites", title='28.March/02.May')

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

