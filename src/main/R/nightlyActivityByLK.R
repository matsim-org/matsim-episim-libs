library(readr)
library(tidyverse)
library(tmap)
library(sf)
data <- read_csv("LK_Timeline_WeeklyNumbers_perPerson.csv")
View(data)

unwanted_hours <- as.character(seq(6,21))

data %>% glimpse()
data2 <- data %>%
  mutate(date = as.Date(date, format = "%d.%m.%y")) %>% 
  filter(type == "endNonHomeActs") %>%
  select(!contains(unwanted_hours)) %>% 
  #glimpse()
  mutate("night_total" = `<0h` +`0-1h`+`1-2h`+`2-3h`+`3-4h`+`4-5h`+`22-23h`+`23-24h`+`24-25h`+`>30h`) %>% 
  select(!contains(c("-","0h"))) %>% filter(area=="Berlin" | area=="Hamburg") 

ggplot(data2)+
  geom_line(mapping = aes(x=date,y=night_total, color=area))

tmap_mode("view")
lk <- st_read("D:/Dropbox/Documents/VSP/landkreise-DE/landkreise-in-germany.shp")  
tm_shape(lk) +
  tm_polygons()  
