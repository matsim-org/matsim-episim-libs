# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 12/9/2021

# Study Time Frame

rm(list = ls())
setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

gbl_image_output <- "C:/Users/jakob/projects/60eeeb14daadd7ca9fc56fea/images/"


berlin_raw <- read_delim("Cases_Berlin.csv", delim = ";")


berlin_clean <- berlin_raw %>%
  rename(inzidenz = `7_tage_inzidenz`) %>%
  select(datum, inzidenz) %>%
  mutate(month = month(datum), year = year(datum))

berlin_avg <- berlin_clean %>%
  group_by(month, year) %>%
  summarise(avg_incidence = mean(inzidenz), date = mean(datum)) %>%
  arrange(avg_incidence)

print("lowest incidenz in 2020 (after first wave): ")
berlin_clean %>%
  filter(year == 2020) %>%
  arrange(inzidenz) %>%
  head(1)
# 2020-07-18 - 3.2


print("lowest incidenz between second and third wave")
berlin_clean %>%
  filter(year == 2021 & month < 5) %>%
  arrange(inzidenz) %>%
  head(1)
# 2021-02-17 - 54.7

plot_berlin_cases <- ggplot(berlin_clean) +
  geom_line(aes(datum, inzidenz)) +
  annotate("rect", xmin = ymd("2020-07-18"),
           xmax = ymd("2021-02-17"), ymin = 0, ymax = Inf,
           alpha = 0.4, fill = "light blue") +
  labs(x = "Date", y = "7-Day Infections / 100k Pop.")+
  theme_minimal(base_size = 11) +
  theme(legend.position = "bottom", axis.text.x = element_text(angle = 45, hjust=1)) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y")

plot_berlin_cases

ggsave(plot_berlin_cases, filename = "methodology_timeframe.png", path = gbl_image_output, width = 16, height = 12, units = "cm")
ggsave(plot_berlin_cases, filename = "methodology_timeframe.pdf", path = gbl_image_output, width = 16, height = 12, units = "cm")
