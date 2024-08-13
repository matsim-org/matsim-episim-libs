library(lubridate)
library(tidyverse)
library(readr)



rm(list=ls())
source("/Users/jakob/git/matsim-episim/src/main/R/masterJR-utils.R", encoding = 'utf-8')

# directory_snap_old <- "/Users/jakob/git/public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-11-05/2-imm-snap/"
directory_base <- "/Users/jakob/git/public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-11-24/1-makeImmHist/"
directory_imm <- "/Users/jakob/git/public-svn/matsim/scenarios/countries/de/episim/battery/jakob/2022-11-24/3-imm-20seeds/"
# file_root<- "antibodies.tsv"

#infections
file_root_inf<- "infections.txt.csv"
snap_inf_raw <- read_combine_episim_output_zipped(directory_base, file_root_inf )
# snap_inf_raw_old <- read_combine_episim_output_zipped(directory_snap, file_root_inf )
imm_inf_raw <- read_combine_episim_output_zipped(directory_imm, file_root_inf)

unique(snap_inf_raw$seed)


start_date <- ymd("2022-04-01")
end_date <- ymd("2022-09-01")
snap_inf <- snap_inf_raw %>%
  filter(date >= start_date) %>%
  filter(date <= end_date) %>%
  filter(seed %in% unique(imm_inf_raw$seed))
  # filter(pHh == 0.0, immuneSigma == 0.0)
  # mutate(vax = generic + mRNA + vector + ba1Update + ba5Update + natural)


imm_inf <- imm_inf_raw %>%
  filter(date >= start_date) %>%
  filter(date <= end_date) %>%
  filter(StrainA == 2.0 & startFromImm =="sepSeeds")


  # mutate(vax = generic + mRNA + vector + ba1Update + ba5Update + natural)
ggplot() + #nShowingSymptoms # SARS_CoV_2
  geom_line(snap_inf, mapping = aes(date, nShowingSymptoms , group = seed, col = "base")) +
  geom_line(imm_inf, mapping = aes(date, nShowingSymptoms , group = seed, col = "imm-hist")) +
  scale_color_manual(name='Regression Model',
                       breaks=c('base', 'imm-hist'),
                       values=c('base'='red', 'imm-hist'='blue'))+
  ggtitle("Cologne Cases") +
  labs(x="Date", y="New Cases")



# antibodies
file_root_ab<- "antibodies.tsv"
snap_ab_raw <- read_combine_episim_output_zipped(directory_base, file_root_ab )
imm_ab_raw <- read_combine_episim_output_zipped(directory_imm, file_root_ab)


start_date <- ymd("2021-11-15")
end_date <- ymd("2029-11-30")
snap_ab <- snap_ab_raw %>%
  filter(date >= start_date) %>%
  filter(date <= end_date) %>%
  filter(pHh == 0.0, immuneSigma == 0.0)
# mutate(vax = generic + mRNA + vector + ba1Update + ba5Update + natural)
imm_ab <- imm_ab_raw %>%
  filter(date >= start_date) %>%
  filter(date <= end_date) %>%
  filter(pHh == 0.0, immuneSigma == 0.0)
# mutate(vax = generic + mRNA + vector + ba1Update + ba5Update + natural)
ggplot() + #nShowingSymptoms # SARS_CoV_2
  geom_line(imm_ab, mapping = aes(date, SARS_CoV_2 , group = seed, col = "imm-hist")) +
  geom_line(snap_ab, mapping = aes(date, SARS_CoV_2 , group = seed, col = "snapshot")) +
  scale_color_manual(name='Regression Model',
                     breaks=c('snapshot', 'imm-hist'),
                     values=c('snapshot'='red', 'imm-hist'='blue'))+
  ggtitle("Antibodies")




  # scale_colour_manual(name = "scenario", values = c("red"="red", "blue"="blue"), labels = c("snapshot", "immune history")) +
  # facet_wrap(pHh ~ immuneSigma)

# antibodies are a bit lower for immune history people
# jump in snapshot on first day is a bit sus... (maybe there is something wrong there..., not with immune history))
# what happens to Antibodies from June 30 to July 1?? Why do they jump?

# infections: now imm-Hist (blue) run has lower case numbers... too many antibodies or whats going on?
# but blue also has slightly lower antibodies. How can this be?