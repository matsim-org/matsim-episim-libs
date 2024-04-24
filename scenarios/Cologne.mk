
in = $(WD)/snz/CologneV2/original-data
out = $(WD)/snz/CologneV2/episim-input
tmp = $(WD)/snz/CologneV2/processed-data

Cologne: $(out)/cologne_snz_episim_events_wt_25pt_split.xml.gz $(out)/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz
	echo "Building Cologne scenario"

$(tmp)/personIds.diluted.txt.gz:
	$(sc) filterPersons $(in)/de2020gsmwt_events_reduced.xml.gz\
	 --facilities $(in)/facilities_assigned_simplified.xml.gz\
	 --attributes $(in)/populationAttributes.xml.gz\
	 --shape-file $(out)/../shape-File/cologne-shp.shp\
	 --shape-crs EPSG:25832\
	 --output $@

$(out)/cologne_snz_entirePopulation_emptyPlans_100pt.xml.gz: $(tmp)/personIds.diluted.txt.gz
	$(sc) convertPersonAttributes $(in)/populationAttributes.xml.gz\
   --ids $<\
   --requireAttribute "microm:modeled:age"\
   --output $@

$(out)/cologne_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz: $(out)/cologne_snz_entirePopulation_emptyPlans_100pt.xml.gz
	$(sc) districtLookup $<\
 	 --output $@\
	 --shp ../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp

###########
# 25 pct
###########
# https://www.destatis.de/DE/Themen/Gesellschaft-Umwelt/Bevoelkerung/Haushalte-Familien/Tabellen/1-2-privathaushalte-bundeslaender.html
# https://de.statista.com/statistik/daten/studie/1195923/umfrage/haushaltsgroessen-koeln/

$(out)/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/cologne_snz_episim_events_wt_25pt.xml.gz &: \
$(out)/cologne_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.25\
   --population $<\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population0.25.xml.gz $(out)/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.25.xml.gz $(out)/cologne_snz_episim_events_wt_25pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.25.xml.gz $(out)/cologne_snz_episim_events_sa_25pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.25.xml.gz $(out)/cologne_snz_episim_events_so_25pt.xml.gz

$(out)/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz $(out)/cologne_snz_episim_events_wt_25pt_split.xml.gz &: \
$(out)/cologne_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/cologne_snz_episim_events_wt_25pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/cologne_snz_episim_events_wt_25pt.xml.gz\
	 --events $(out)/cologne_snz_episim_events_sa_25pt.xml.gz\
	 --events $(out)/cologne_snz_episim_events_so_25pt.xml.gz\
	 --target="44.9 35.2 10.4 7.4 2.1"\
	 --shape-file $(out)/../shape-File/cologne-shp.shp\
	 --output $(out)

