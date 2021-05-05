library(tidyverse)
library(lubridate)

hospital <- read_csv('../public-svn/matsim/scenarios/countries/de/episim/original-data/Fallzahlen/Berlin/berlin-hospital.csv')

hospital_daily <- hospital %>%
  mutate(date = dmy(Datum), cases = `Stationäre Behandlung`)

# Second Wave Component - Linear Regression
start_date_2nd_wave <- as.Date("2021/1/15")
end_date_2nd_wave <- as.Date("2021/3/1")

sample_2nd_wave <- hospital_daily %>% filter(date >= start_date_2nd_wave & date <= end_date_2nd_wave)
lin.mod_2nd_wave <- lm(cases ~ date, data = sample_2nd_wave)
pr.lm_2nd_wave <- predict(lin.mod_2nd_wave, newdata = hospital_daily)
hospital_daily <- hospital_daily %>% mutate(predict_2nd_wave = ifelse(pr.lm_2nd_wave > 0 & date > start_date_2nd_wave, pr.lm_2nd_wave, NA))


# Combination of 2nd and 3rd Wave - Linear Regression (currently unused)
start_date_combo <- as.Date("2021/4/1")
end_date_combo <- max(hospital_daily$date)

sample_combo <- hospital_daily %>% filter(date >= start_date_combo & date <= end_date_combo)
lin.mod_combo <- lm(cases ~ date, data = sample_combo)
pr.lm_combo <- predict(lin.mod_combo, newdata = hospital_daily)
hospital_daily <- hospital_daily %>% mutate(predict_combo = ifelse(date >= start_date_2nd_wave, pr.lm_combo,NA))

# Third Wave Component = Total Cases - Second Wave Component
hospital_daily <- hospital_daily %>%
  mutate(predict_3rd_wave = ifelse(is.na(predict_2nd_wave), cases, cases - predict_2nd_wave)) %>% 
  mutate(predict_3rd_wave = ifelse(date <= end_date_2nd_wave, NA, predict_3rd_wave))



colors <- c("Total Hospitalizations" = "black",
            "2nd Wave Component" = "red",
            "3rd Wave Component" = "green",
            "Combo" = "blue",
            "Linear Regression Limits" = "yellow")

ggplot(data = hospital_daily, aes(x = date)) + 
  geom_point(aes(y = cases, color = "Total Hospitalizations"), size = 1) + 
  geom_line(aes(y = predict_2nd_wave, color = "2nd Wave Component"), size = 1) +
  #geom_point(data = hospital_daily %>% filter(date == start_date_2nd_wave | date == end_date_2nd_wave), mapping = aes(x = date,y = cases), color = "cyan") +
  #geom_line(aes(y = predict_combo, color = "Combo"), size = 1 ) +
  #geom_point(data = hospital_daily %>% filter(date == start_date_combo | date == end_date_combo), mapping = aes(x = date,y = cases), color = "cyan")+
  geom_point(aes(y = predict_3rd_wave, color = "3rd Wave Component"), size = 1) +
  labs(title = "Berlin Hospitalizations", subtitle = "2nd and 3rd Wave Seperated Using Linear Regression", x = "Date", y="Hospitalized Patients", color = "Legend") +
  scale_color_manual(values = colors) + theme(legend.position = "bottom")


#df_plot <- gather(hospital_daily, key = measure,value = hospitalizations, c('cases','predict_2nd_wave','second_wave'))
#ggplot(df_plot, aes(x=date, y = hospitalizations, group = measure, colour = measure)) + 
#  geom_line()
