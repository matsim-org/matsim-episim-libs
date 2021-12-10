# Title     : TODO
# Objective : TODO
# Created by: jakob
# Created on: 11/17/2021


if (TRUE) {
  library(readxl)
  library(tidyverse)
  library(janitor)
  library(lubridate)
  library(ggthemes)
  library(scales)
  library(cowplot)
  library(vistime)
  library(data.table)
  library(grid)
  library(gridExtra)
  library(gt)
  library(glue)


  rm(list = ls())
  setwd("D:/Dropbox/Documents/VSP/episim/location_based_restrictions/")

  gbl_image_output <- "C:/Users/jakob/projects/60eeeb14daadd7ca9fc56fea/images/"
  gbl_directory <- "master/b1/"

  # load all functions
  source("C:/Users/jakob/projects/matsim-episim/src/main/R/masterJR-utils.R", encoding = 'utf-8')

}


#########################################################################################
########################## LOAD AND PREPARE DATA ########################################
#########################################################################################

rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))

run_params <- get_run_parameters(gbl_directory)
episim_infections_district <- read_combine_episim_output(gbl_directory, "infections_subdistrict.txt", TRUE)
episim_incidence_district <- convert_infections_into_incidence(gbl_directory, episim_infections_district, TRUE)



#########################################################################################
########################## PLOT #########################################################
#########################################################################################

start_date <- ymd("2020-09-01")
end_date <- ymd("2021-02-01")

load()

# Plot: restrict mitte - compare no restriction vs. home restriction vs. activity restriction
to_plot <- episim_incidence_district %>%
  rename("Scenario" = "locationBasedRestrictions") %>%
  filter(date >= start_date & date <= end_date) %>%
  mutate(Scenario = str_replace(Scenario, "no", "base")) %>%
  mutate(Scenario = str_replace(Scenario, "yesForHomeLocation", "policy-home")) %>%
  mutate(Scenario = str_replace(Scenario, "yesForActivityLocation", "policy-activity")) %>%
  mutate(Scenario = factor(Scenario, levels = c("base", "policy-home", "policy-activity")))

plot_mod <- build_plot(to_plot, c("blue", "red", "green")) +
  scale_x_date(date_breaks = "1 month", date_labels = "%b-%y") +
  annotate("rect", xmin = ymd("2020-10-1"), xmax = ymd("2020-10-31"), ymin = 0, ymax = Inf, alpha = 0.5, fill = " light blue")
plot_mod
save_png_pdf(plot_mod, "b1_mitte")