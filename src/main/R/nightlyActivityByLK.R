library(readr)
library(tidyverse)
library(tmap)
library(sf)


data <- read_csv("D:/Dropbox/Documents/VSP/LK_Timeline_WeeklyNumbers_perPerson.csv")

unwanted_hours <- as.character(seq(6,21))

data %>% glimpse()
data2 <- data %>%
  mutate(date = as.Date(date, format = "%d.%m.%y")) %>% 
  filter(type == "endNonHomeActs") %>%
  select(!contains(unwanted_hours)) %>% 
  #glimpse()
  mutate("night_total" = `<0h` +`0-1h`+`1-2h`+`2-3h`+`3-4h`+`4-5h`+`22-23h`+`23-24h`+`24-25h`+`>30h`)

data2$area <- data2$area %>% str_replace("Landkreis ","") %>% 
  str_replace("Kreis ","")

data_to_merge <- data2 %>% 
  filter(date == "2020-03-08" | date == "2021-05-30") %>% 
  select(c(date,area,night_total)) %>% 
  pivot_wider(names_from = date,values_from = night_total,values_fn = mean) %>% 
  mutate(change = `2021-05-30`/`2020-03-08`) %>% select(c(area,change))


  
         
  
  #select(!contains(c("-","0h"))) %>% filter(area=="Berlin" | area=="Hamburg") 

ggplot(data2)+
  geom_line(mapping = aes(x=date,y=night_total, color=area))

tmap_mode("view")
lk <- st_read("D:/Dropbox/Documents/VSP/landkreise-DE/landkreise-in-germany.shp") %>% mutate(area = name_2)

lk2 <- lk %>% left_join(data_to_merge, by="area")

lk_names <- lk$name_2
data_names <- unique(data2$area)

Reduce(intersect,list(lk_names,data_names))
Reduce(outersect,list(lk_names,data_names))

tm_shape(lk2) +
  tm_polygons(col = "change")


outersect <- function(x, y) {
  sort(c(setdiff(x, y),
         setdiff(y, x)))
}
