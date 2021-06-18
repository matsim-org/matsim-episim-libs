
in = $(WD)/snz/Dresden/original-data
out = $(WD)/snz/Dresden/episim-input
tmp = $(WD)/snz/Dresden/processed-data

Dresden: $(out)/dresden_snz_episim_events_wt_100pt_split.xml.gz $(out)/dresden_snz_entirePopulation_emptyPlans_withDistricts_100pt_split.xml.gz
	echo "Building Dresden scenario"

$(tmp)/personIds.diluted.txt.gz:
	$(sc) filterPersons $(in)/de2020gsmwt_events_reduced.xml.gz\
	 --facilities $(in)/facilities_assigned_simplified.xml.gz\
	 --shape-file $(out)/../shape-file/case-study_Dresden_PLZ.shp\
	 --output $@

$(out)/dresden_snz_entirePopulation_emptyPlans_100pt.xml.gz: $(tmp)/personIds.diluted.txt.gz
	$(sc) convertPersonAttributes $(in)/populationAttributes.xml.gz\
   --ids $<\
   --requireAttribute "microm:modeled:age"\
   --output $@

$(out)/dresden_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz: $(out)/dresden_snz_entirePopulation_emptyPlans_100pt.xml.gz
	$(sc) districtLookup $<\
 	 --output $@\
	 --shp ../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp

###########
# 100 pct
###########
# https://www.destatis.de/DE/Themen/Gesellschaft-Umwelt/Bevoelkerung/Haushalte-Familien/Tabellen/1-2-privathaushalte-bundeslaender.html

$(out)/dresden_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz $(out)/dresden_snz_episim_events_wt_100pt.xml.gz &: \
$(out)/dresden_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 1.0\
   --population $<\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population1.0.xml.gz $(out)/dresden_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-1.0.xml.gz $(out)/dresden_snz_episim_events_wt_100pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-1.0.xml.gz $(out)/dresden_snz_episim_events_sa_100pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-1.0.xml.gz $(out)/dresden_snz_episim_events_so_100pt.xml.gz

$(out)/dresden_snz_entirePopulation_emptyPlans_withDistricts_100pt_split.xml.gz $(out)/dresden_snz_episim_events_wt_100pt_split.xml.gz &: \
$(out)/dresden_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz $(out)/dresden_snz_episim_events_wt_100pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/dresden_snz_episim_events_wt_100pt.xml.gz\
	 --events $(out)/dresden_snz_episim_events_sa_100pt.xml.gz\
	 --events $(out)/dresden_snz_episim_events_so_100pt.xml.gz\
	 --target="44.9 35.2 10.4 7.4 2.1"\
	 --shape-file $(out)/../shape-file-utm32/case-study_Dresden_PLZ.shp\
	 --output $(out)

