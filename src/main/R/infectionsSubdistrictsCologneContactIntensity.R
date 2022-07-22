
#rsync -rv  --include='*/' --include="*infections_subdistrict.txt" --exclude="*"  hlrn:/scratch/projects/bzz0020/runs/jakob/2022-07-13/1/output/ output/
#scp hlrn:/scratch/projects/bzz0020/runs/jakob/2022-07-15/1/_info.txt .
#scp hlrn:/scratch/projects/bzz0020/runs/jakob/2022-07-15/1/metadata.yaml .


if (TRUE) {
  library(viridis)
  library(spData)
  library(tmap)
  library(sf)
  library(readxl)
  library(tidyverse)
  library(lubridate)
  library(data.table)
  library(patchwork)

  rm(list = ls())
  setwd("/Users/jakob/git/public-svn/matsim/scenarios/countries/de/episim/battery/jakob/")

  gbl_directory <- "2022-07-15/1/output/"

  # load all functions
  source("/Users/jakob/git/matsim-episim/src/main/R/masterJR-utils.R", encoding = 'utf-8')
}

#########################################################################################
########################## LOAD, PREPARE, AND SAVE DATA #################################
#########################################################################################

rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))

#directory <- "2022-07-15/1/output/"
#run_params <- get_run_parameters(gbl_directory)
episim_infections_district <- read_combine_episim_output(gbl_directory, "infections_subdistrict.txt", TRUE)
episim_incidence_district <- convert_infections_into_incidence(gbl_directory, episim_infections_district, TRUE)

#save(episim_infections_district, file = paste0(gbl_directory, "episim_infections_district"))
#save(episim_incidence_district, file = paste0(gbl_directory, "episim_incidence_district"))


#########################################################################################
########################## PLOT #########################################################
#########################################################################################

# Plot: base case vs. localCI
#rm(list = setdiff(ls(), union(ls(pattern = "^gbl_"), lsf.str())))
#load(paste0(gbl_directory, "episim_incidence_district"))

start_date <- ymd("2020-02-15")
# end_date <- ymd("2021-02-19")
end_date <- ymd("2020-07-15")

# filter:
episim_incidence_district2 <- episim_incidence_district %>%
  mutate(status = ifelse(str_detect(district,'^[A-L].*'),"poor","rich")) %>%
  group_by(date, ciModifier,vaxPoor,vaxRich, status) %>%
  summarise(infections = sum(infections))

scenario_base <- "ciMod0-vaxRichFALSE-vaxPoorFALSE"
scenario_policy <- "ciMod0.3-vaxRichFALSE-vaxPoorFALSE"
scenario_policyRich <- "ciMod0.3-vaxRichTRUE-vaxPoorFALSE"
scenario_policyPoor <- "ciMod0.3-vaxRichFALSE-vaxPoorTRUE"
to_plot <- episim_incidence_district2 %>%
  mutate(Scenario = paste0("ciMod", ciModifier,"-vaxRich",vaxRich,"-vaxPoor",vaxPoor)) %>%
  # rbind(gbl_berlin_incidence_district) %>%
  filter(date >= start_date & date <= end_date) %>%
  filter( Scenario == "rki" |
           Scenario == scenario_base |
           Scenario == scenario_policy |
            Scenario == scenario_policyRich |
            Scenario == scenario_policyPoor) %>%
  mutate(Scenario = str_replace(Scenario, regex(scenario_policy), "policy")) %>%
    mutate(Scenario = str_replace(Scenario, regex(scenario_policyPoor), "policyRich")) %>%
    mutate(Scenario = str_replace(Scenario, regex(scenario_policyRich), "policyPoor")) %>%
    mutate(Scenario = str_replace(Scenario, regex(scenario_base), "base"),)
  #mutate(Scenario = factor(Scenario, levels = c("rki", "base", "policy")))

ggplot(to_plot %>% filter(Scenario == "base" | Scenario == "policy")) +
  geom_line(aes(date, infections, col = Scenario)) +
  theme_minimal(base_size = 11) +
  theme(legend.position = "bottom", axis.text.x = element_text(angle = 45, hjust=1)) +
  labs(x = "Date", y = "Infections") +
  scale_x_date(date_breaks = "2 month", date_labels = "%b-%y") +
  facet_wrap(~status, ncol = 2)

ggplot(to_plot %>% filter(Scenario != "base")) +
  geom_line(aes(date, infections, col = Scenario)) +
  theme_minimal(base_size = 11) +
  theme(legend.position = "bottom", axis.text.x = element_text(angle = 45, hjust=1)) +
  labs(x = "Date", y = "Infections") +
  scale_x_date(date_breaks = "2 month", date_labels = "%b-%y") +
  facet_wrap(~status, ncol = 2)

plot


# Plot 2
lor <- st_read("/Users/jakob/git/shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/CologneDistricts/CologneDistricts.shp")

infections <- episim_incidence_district %>%
  select(date,district, ciModifier,vaxPoor, vaxRich,infections)%>%
  filter(date >= start_date & date <= end_date) %>%
  pivot_wider(names_from = c("ciModifier","vaxRich","vaxPoor"), names_glue = "ciMod{ciModifier}-vaxRich{vaxRich}-vaxPoor{vaxPoor}", values_from = "infections") %>%
  group_by(district) %>%
  summarise(across(starts_with("ci"), ~ sum(.x, na.rm = TRUE))) %>%
  # NOTE/TODO: rich and poor are switchen in the following line because of bug in code!
  rename(c("base" = scenario_base, "policy" = scenario_policy, "policyPoor" = scenario_policyRich, "policyRich" = scenario_policyPoor)) %>%
  select(!starts_with("ci")) %>%
  mutate(policyRich = policyRich / policy, policyPoor = policyPoor / policy) %>%
  mutate(policy = policy / base)

  joined <- lor %>% left_join(infections, by = c("STT_NAME" = "district")) %>%
  pivot_longer(cols = starts_with("policy"), names_to = "scenario", values_to = "infections")


joined.fix <- st_make_valid(joined)
st_is_valid(joined.fix, reason = TRUE)

shp_high <- lor %>% filter(str_detect(STT_NAME, '^[A-L]'))
shp_low <- lor %>% filter(str_detect(STT_NAME, '^[M-Z]'))
tmap_mode("plot")
# plot_m2_lor <- #tm_basemap(leaflet::providers$OpenStreetMap) +
plot_policy <- tm_shape(joined.fix %>% filter(scenario == "policy")%>% rename(c("Change in Infections" = "infections"))) + #' )
  tm_facets(by = "scenario") +
  tm_polygons(col = "Change in Infections", id = "STT_NAME", palette = viridis(9), alpha = 0.9,breaks = c(0.1,0.3,0.5,0.7,0.9,1.1,1.3,1.5,1.7,1.9), legend.show = FALSE)+
  tm_shape(shp_high) +
  tm_borders(col = "red", lwd = 2, lty = "dashed") +
  tm_shape(shp_low) +
  tm_borders(col = "blue", lwd = 2, lty = "dashed")

plot_policyRichPoor <- tm_shape(joined.fix %>% filter(scenario != "policy") %>% mutate(scenario = str_replace(scenario, "policyPoor", "Vaccinate Poor Neighborhoods"), scenario = str_replace(scenario, "policyRich", "Vaccinate Rich Neighborhoods"))) + #' %>% rename(c("policy vs. base" = "change"))
  tm_facets(by = "scenario", nrow = 2) +
  tm_polygons(col = "infections", id = "STT_NAME", palette = viridis(9), alpha = 0.9,breaks = c(0.1,0.3,0.5,0.7,0.9,1.1,1.3,1.5,1.7,1.9))+
  tm_shape(shp_high) +
  tm_borders(col = "red", lwd = 2, lty = "dashed") +
  tm_shape(shp_low) +
  tm_borders(col = "blue", lwd = 2, lty = "dashed") +
  tm_add_legend(title = "Neighborhood Status", type = "line", lwd = 2, lty = "dashed", col = c("blue", "red"), labels = c("rich","poor"))



tmap_arrange(plot_policy,plot_policyRichPoor)


# blue border = [R-Z] = large home size (75m2 per person) =  low contact intensity (0.1) = less infections
#  red border = [A-F] = small home size (15m2 per person) = high contact intensity (1.9) = more infections
