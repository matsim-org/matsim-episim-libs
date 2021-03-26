
in = $(WD)/snz/Heinsberg/original-data
out = $(WD)/snz/Heinsberg/episim-input
tmp = $(WD)/snz/Heinsberg/processed-data

Heinsberg: $(JAR) $(out)/hb_2020-week_snz_episim_events_wt_25pt_split.xml.gz $(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz \
$(out)/hb_2020-week_snz_episim_events_wt_100pt_split.xml.gz $(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_split.xml.gz
	echo "Building Berlin Week scenario"

$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz:
	$(sc) convertPersonAttributes $(in)/populationAttributes.xml.gz\
   --ids $(in)/personIds.diluted.txt.gz\
   --requireAttribute "microm:modeled:age"\
   --output $@

$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz: $(out)/hb_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz
	$(sc) districtLookup $<\
 	 --output $@\
	 --shp ../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp

########
# 25pct
########

$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/hb_2020-week_snz_episim_events_wt_25pt.xml.gz &: \
$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.25\
   --population $<\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population0.25.xml.gz $(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.25.xml.gz $(out)/hb_2020-week_snz_episim_events_wt_25pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.25.xml.gz $(out)/hb_2020-week_snz_episim_events_sa_25pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.25.xml.gz $(out)/hb_2020-week_snz_episim_events_so_25pt.xml.gz

$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz $(out)/hb_2020-week_snz_episim_events_wt_25pt_split.xml.gz &: \
$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/hb_2020-week_snz_episim_events_wt_25pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/hb_2020-week_snz_episim_events_wt_25pt.xml.gz\
	 --events $(out)/hb_2020-week_snz_episim_events_sa_25pt.xml.gz\
	 --events $(out)/hb_2020-week_snz_episim_events_so_25pt.xml.gz\
	 --shape-file $(in)/dilutionArea.shp\
	 --output $(out)

$(out)/wLeisure/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz $(out)/wLeisure/hb_2020-week_snz_episim_events_wt_25pt_split.xml.gz &: \
$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/hb_2020-week_snz_episim_events_wt_25pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/hb_2020-week_snz_episim_events_wt_25pt.xml.gz\
	 --events $(out)/hb_2020-week_snz_episim_events_sa_25pt.xml.gz\
	 --events $(out)/hb_2020-week_snz_episim_events_so_25pt.xml.gz\
	 --shape-file $(in)/dilutionArea.shp\
	 --remap visit\
	 --output $(out)/wLeisure

###########
# 100 pct
###########

$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz $(out)/hb_2020-week_snz_episim_events_wt_100pt.xml.gz &: \
$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 1.0\
   --population $<\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population1.0.xml.gz $(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-1.0.xml.gz $(out)/hb_2020-week_snz_episim_events_wt_100pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-1.0.xml.gz $(out)/hb_2020-week_snz_episim_events_sa_100pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-1.0.xml.gz $(out)/hb_2020-week_snz_episim_events_so_100pt.xml.gz

$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_split.xml.gz $(out)/hb_2020-week_snz_episim_events_wt_100pt_split.xml.gz &: \
$(out)/hb_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz $(out)/hb_2020-week_snz_episim_events_wt_100pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/hb_2020-week_snz_episim_events_wt_100pt.xml.gz\
	 --events $(out)/hb_2020-week_snz_episim_events_sa_100pt.xml.gz\
	 --events $(out)/hb_2020-week_snz_episim_events_so_100pt.xml.gz\
	 --shape-file $(in)/dilutionArea.shp\
	 --output $(out)