# To run this, you need a newer version of make. 4.4.1 works (in macOS under the command of gmake)
# To run, execute "gmake Brandenburg" in terminal
in = $(WD)/snz/Brandenburg/original-data
out = $(WD)/snz/Brandenburg/episim-input
tmp = $(WD)/snz/Brandenburg/processed-data

Brandenburg: $(JAR) $(out)/br_2020-week_snz_episim_events_wt_25pt_split.xml.gz $(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz \
$(out)/br_2020-week_snz_episim_events_wt_100pt_split.xml.gz $(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_split.xml.gz
	echo "Building Brandenburg Week scenario"

BrandenburgSamples: $(JAR) $(out)/samples/br_2020-week_snz_episim_events_wt_10pt_split.xml.gz $(out)/samples/br_2020-week_snz_episim_events_wt_5pt_split.xml.gz \
$(out)/samples/br_2020-week_snz_episim_events_wt_1pt_split.xml.gz
	echo "Building Brandenburg Week samples"

$(tmp)/personIds.diluted.txt.gz:
	$(sc) filterPersons $(in)/de2020gsmwt_events_reduced.xml.gz\
	 --facilities $(in)/facilities_assigned_simplified.xml.gz\
	 --attributes $(in)/populationAttributes.xml.gz\
	 --shape-file $(out)/shape-File/brandenburg.shp\
	 --shape-crs EPSG:25833\
	 --output $@



$(out)/br_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz: $(tmp)/personIds.diluted.txt.gz
	$(sc) convertPersonAttributes $(in)/populationAttributes.xml.gz\
   --ids $<\
   --requireAttribute "microm:modeled:age"\
   --output $@

$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz: $(out)/br_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz
	$(sc) districtLookup $<\
 	 --output $@\
	 --shp ../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp

########
# 25pct
########

$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/br_2020-week_snz_episim_events_wt_25pt.xml.gz &: \
$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.25\
   --population $<\
   --facilities $(in)/facilities_assigned_simplified.xml.gz\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population0.25.xml.gz $(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz
	mv $(tmp)/facilities0.25.xml.gz $(out)/br_2020-week_snz_episim_facilities_25pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.25.xml.gz $(out)/br_2020-week_snz_episim_events_wt_25pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.25.xml.gz $(out)/br_2020-week_snz_episim_events_sa_25pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.25.xml.gz $(out)/br_2020-week_snz_episim_events_so_25pt.xml.gz

$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz $(out)/br_2020-week_snz_episim_events_wt_25pt_split.xml.gz &: \
$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/br_2020-week_snz_episim_events_wt_25pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/br_2020-week_snz_episim_events_wt_25pt.xml.gz\
	 --events $(out)/br_2020-week_snz_episim_events_sa_25pt.xml.gz\
	 --events $(out)/br_2020-week_snz_episim_events_so_25pt.xml.gz\
	 --shape-file $(out)/shape-File/brandenburg.shp\
	 --output $(out)

$(out)/wLeisure/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz $(out)/wLeisure/br_2020-week_snz_episim_events_wt_25pt_split.xml.gz &: \
$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/br_2020-week_snz_episim_events_wt_25pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/br_2020-week_snz_episim_events_wt_25pt.xml.gz\
	 --events $(out)/br_2020-week_snz_episim_events_sa_25pt.xml.gz\
	 --events $(out)/br_2020-week_snz_episim_events_so_25pt.xml.gz\
	 --shape-file $(out)/shape-File/brandenburg.shp\
	 --remap visit --remap leisure\
	 --output $(out)/wLeisure

###########
# 100 pct
###########

$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz $(out)/br_2020-week_snz_episim_events_wt_100pt.xml.gz &: \
$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 1.0\
   --population $<\
   --facilities $(in)/facilities_assigned_simplified.xml.gz\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population1.0.xml.gz $(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz
	mv $(tmp)/facilities1.0.xml.gz $(out)/br_2020-week_snz_episim_facilities_100pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-1.0.xml.gz $(out)/br_2020-week_snz_episim_events_wt_100pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-1.0.xml.gz $(out)/br_2020-week_snz_episim_events_sa_100pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-1.0.xml.gz $(out)/br_2020-week_snz_episim_events_so_100pt.xml.gz

$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_split.xml.gz $(out)/br_2020-week_snz_episim_events_wt_100pt_split.xml.gz &: \
$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz $(out)/br_2020-week_snz_episim_events_wt_100pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/br_2020-week_snz_episim_events_wt_100pt.xml.gz\
	 --events $(out)/br_2020-week_snz_episim_events_sa_100pt.xml.gz\
	 --events $(out)/br_2020-week_snz_episim_events_so_100pt.xml.gz\
	 --shape-file $(out)/shape-File/brandenburg.shp\
	 --output $(out)

#################
# 10pct 5pct 1pct
#################


$(out)/samples/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_10pt.xml.gz $(out)/samples/br_2020-week_snz_episim_events_wt_10pt.xml.gz &: \
$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.1\
	 --population $<\
	 --facilities $(in)/facilities_assigned_simplified.xml.gz\
	 --events $(in)/de2020gsmwt_events_reduced.xml.gz\
	 --events $(in)/de2020gsmsa_events_reduced.xml.gz\
	 --events $(in)/de2020gsmso_events_reduced.xml.gz\
	 --output $(tmp)

	mkdir -p $(out)/samples # added -p flag to avoid error if directory already exists

	mv $(tmp)/population0.1.xml.gz $(out)/samples/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_10pt.xml.gz
	mv $(tmp)/facilities0.1.xml.gz $(out)/samples/br_2020-week_snz_episim_facilities_10pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.1.xml.gz $(out)/samples/br_2020-week_snz_episim_events_wt_10pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.1.xml.gz $(out)/samples/br_2020-week_snz_episim_events_sa_10pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.1.xml.gz $(out)/samples/br_2020-week_snz_episim_events_so_10pt.xml.gz


$(out)/samples/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_5pt_split.xml.gz $(out)/samples/br_2020-week_snz_episim_events_wt_5pt_split.xml.gz &: \
$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.05\
	 --population $<\
	 --facilities $(in)/facilities_assigned_simplified.xml.gz\
	 --events $(in)/de2020gsmwt_events_reduced.xml.gz\
	 --events $(in)/de2020gsmsa_events_reduced.xml.gz\
	 --events $(in)/de2020gsmso_events_reduced.xml.gz\
	 --output $(tmp)

	mkdir -p $(out)/samples # added -p flag to avoid error if directory already exists
	mv $(tmp)/population0.05.xml.gz $(out)/samples/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_5pt_split.xml.gz
	mv $(tmp)/facilities0.05.xml.gz $(out)/samples/br_2020-week_snz_episim_facilities_5pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.05.xml.gz $(out)/samples/br_2020-week_snz_episim_events_wt_5pt_split.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.05.xml.gz $(out)/samples/br_2020-week_snz_episim_events_sa_5pt_split.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.05.xml.gz $(out)/samples/br_2020-week_snz_episim_events_so_5pt_split.xml.gz

$(out)/samples/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_1pt_split.xml.gz $(out)/samples/br_2020-week_snz_episim_events_wt_1pt_split.xml.gz &: \
$(out)/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.01\
	 --population $<\
	 --facilities $(in)/facilities_assigned_simplified.xml.gz\
	 --events $(in)/de2020gsmwt_events_reduced.xml.gz\
	 --events $(in)/de2020gsmsa_events_reduced.xml.gz\
	 --events $(in)/de2020gsmso_events_reduced.xml.gz\
	 --output $(tmp)

	mkdir -p $(out)/samples # added -p flag to avoid error if directory already exists
	mv $(tmp)/population0.01.xml.gz $(out)/samples/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_1pt_split.xml.gz
	mv $(tmp)/facilities0.01.xml.gz $(out)/samples/br_2020-week_snz_episim_facilities_1pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.01.xml.gz $(out)/samples/br_2020-week_snz_episim_events_wt_1pt_split.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.01.xml.gz $(out)/samples/br_2020-week_snz_episim_events_sa_1pt_split.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.01.xml.gz $(out)/samples/br_2020-week_snz_episim_events_so_1pt_split.xml.gz


$(out)/samples/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_10pt_split.xml.gz $(out)/samples/br_2020-week_snz_episim_events_wt_10pt_split.xml.gz &: \
$(out)/samples/br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_10pt.xml.gz $(out)/samples/br_2020-week_snz_episim_events_wt_10pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/samples/br_2020-week_snz_episim_events_wt_10pt.xml.gz\
	 --events $(out)/samples/br_2020-week_snz_episim_events_sa_10pt.xml.gz\
	 --events $(out)/samples/br_2020-week_snz_episim_events_so_10pt.xml.gz\
	 --shape-file $(out)/shape-File/brandenburg.shp\
	 --output $(out)/samples