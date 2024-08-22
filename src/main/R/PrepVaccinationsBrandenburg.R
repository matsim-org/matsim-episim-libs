
# This script aggregates the vaccination data for the different age groups (5-11, 12-17, 18-59, 60+) for the federal state of Brandenburg

vaccinations <- read_csv("https://raw.githubusercontent.com/robert-koch-institut/COVID-19-Impfungen_in_Deutschland/main/Deutschland_Landkreise_COVID-19-Impfungen.csv")

vaccinations <- vaccinations %>% filter(LandkreisId_Impfort %in% c("12051" ,"12052", "12053", "12054", "12060", "12061", "12062", "12063", "12064", "12065", "12066", "12067", "12068",
 "12069", "12070", "12071", "12072", "12073"))

vaccinations <- vaccinations %>% group_by(Impfdatum, Impfschutz, Altersgruppe) %>%
  summarise(Impfdatum = Impfdatum, LandkreisId_Impfort = "12", Altersgruppe = Altersgruppe, Impfschutz = Impfschutz, Anzahl = sum (Anzahl)) %>%
  distinct()

write_csv(vaccinations, "vaccinationsBrandeburg.csv")
