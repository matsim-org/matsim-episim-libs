
in = $(WD)/snz/BerlinV2/original-data
out = $(WD)/snz/BerlinV2/episim-input
tmp = $(WD)/snz/BerlinV2/processed-data

BerlinWeek: $(JAR) $(out)/be_2020-week_snz_episim_events_wt_25pt_split.xml.gz $(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz \
$(out)/be_2020-week_snz_episim_events_wt_100pt_split.xml.gz $(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_split.xml.gz
	echo "Building Berlin Week scenario"

BerlinWeekSamples: $(JAR) $(out)/samples/be_2020-week_snz_episim_events_wt_10pt_split.xml.gz $(out)/samples/be_2020-week_snz_episim_events_wt_5pt_split.xml.gz \
$(out)/samples/be_2020-week_snz_episim_events_wt_1pt_split.xml.gz
	echo "Building Berlin Week samples"

$(out)/be_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz:
	$(sc) convertPersonAttributes $(in)/populationAttributes.xml.gz\
   --ids $(in)/berlin_umland_wt/personIds.diluted.txt.gz\
   --requireAttribute "microm:modeled:age"\
   --output $@

$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz: $(out)/be_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz
	$(sc) districtLookup $<\
 	 --output $@\
	 --shp ../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp

########
# 25pct
########

$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/be_2020-week_snz_episim_events_wt_25pt.xml.gz &: \
$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.25\
   --population $<\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population0.25.xml.gz $(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.25.xml.gz $(out)/be_2020-week_snz_episim_events_wt_25pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.25.xml.gz $(out)/be_2020-week_snz_episim_events_sa_25pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.25.xml.gz $(out)/be_2020-week_snz_episim_events_so_25pt.xml.gz

$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz $(out)/be_2020-week_snz_episim_events_wt_25pt_split.xml.gz &: \
$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/be_2020-week_snz_episim_events_wt_25pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/be_2020-week_snz_episim_events_wt_25pt.xml.gz\
	 --events $(out)/be_2020-week_snz_episim_events_sa_25pt.xml.gz\
	 --events $(out)/be_2020-week_snz_episim_events_so_25pt.xml.gz\
	 --shape-file $(out)/../shape-File/dilutionArea.shp\
	 --output $(out)

$(out)/wLeisure/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz $(out)/wLeisure/be_2020-week_snz_episim_events_wt_25pt_split.xml.gz &: \
$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/be_2020-week_snz_episim_events_wt_25pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/be_2020-week_snz_episim_events_wt_25pt.xml.gz\
	 --events $(out)/be_2020-week_snz_episim_events_sa_25pt.xml.gz\
	 --events $(out)/be_2020-week_snz_episim_events_so_25pt.xml.gz\
	 --shape-file $(out)/../shape-File/dilutionArea.shp\
	 --remap visit --remap leisure\
	 --output $(out)/wLeisure

###########
# 100 pct
###########

$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz $(out)/be_2020-week_snz_episim_events_wt_100pt.xml.gz &: \
$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 1.0\
   --population $<\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population1.0.xml.gz $(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-1.0.xml.gz $(out)/be_2020-week_snz_episim_events_wt_100pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-1.0.xml.gz $(out)/be_2020-week_snz_episim_events_sa_100pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-1.0.xml.gz $(out)/be_2020-week_snz_episim_events_so_100pt.xml.gz

$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_split.xml.gz $(out)/be_2020-week_snz_episim_events_wt_100pt_split.xml.gz &: \
$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt_filtered.xml.gz $(out)/be_2020-week_snz_episim_events_wt_100pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/be_2020-week_snz_episim_events_wt_100pt.xml.gz\
	 --events $(out)/be_2020-week_snz_episim_events_sa_100pt.xml.gz\
	 --events $(out)/be_2020-week_snz_episim_events_so_100pt.xml.gz\
	 --shape-file $(out)/../shape-File/dilutionArea.shp\
	 --output $(out)

#################
# 10pct 5pct 1pct
#################


$(out)/samples/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_10pt.xml.gz $(out)/samples/be_2020-week_snz_episim_events_wt_10pt.xml.gz &: \
$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.1\
	 --population $<\
	 --events $(in)/de2020gsmwt_events_reduced.xml.gz\
	 --events $(in)/de2020gsmsa_events_reduced.xml.gz\
	 --events $(in)/de2020gsmso_events_reduced.xml.gz\
	 --output $(tmp)

	mkdir $(out)/samples

	mv $(tmp)/population0.1.xml.gz $(out)/samples//be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_10pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.1.xml.gz $(out)/samples/be_2020-week_snz_episim_events_wt_10pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.1.xml.gz $(out)/samples/be_2020-week_snz_episim_events_sa_10pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.1.xml.gz $(out)/samples/be_2020-week_snz_episim_events_so_10pt.xml.gz


$(out)/samples/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_5pt_split.xml.gz $(out)/samples/be_2020-week_snz_episim_events_wt_5pt_split.xml.gz &: \
$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.05\
	 --population $<\
	 --events $(in)/de2020gsmwt_events_reduced.xml.gz\
	 --events $(in)/de2020gsmsa_events_reduced.xml.gz\
	 --events $(in)/de2020gsmso_events_reduced.xml.gz\
	 --output $(tmp)

	mkdir $(out)/samples
	mv $(tmp)/population0.05.xml.gz $(out)/samples//be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_5pt_split.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.05.xml.gz $(out)/samples/be_2020-week_snz_episim_events_wt_5pt_split.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.05.xml.gz $(out)/samples/be_2020-week_snz_episim_events_sa_5pt_split.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.05.xml.gz $(out)/samples/be_2020-week_snz_episim_events_so_5pt_split.xml.gz

$(out)/samples/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_1pt_split.xml.gz $(out)/samples/be_2020-week_snz_episim_events_wt_1pt_split.xml.gz &: \
$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.01\
	 --population $<\
	 --events $(in)/de2020gsmwt_events_reduced.xml.gz\
	 --events $(in)/de2020gsmsa_events_reduced.xml.gz\
	 --events $(in)/de2020gsmso_events_reduced.xml.gz\
	 --output $(tmp)

	mkdir $(out)/samples
	mv $(tmp)/population0.01.xml.gz $(out)/samples//be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_1pt_split.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.01.xml.gz $(out)/samples/be_2020-week_snz_episim_events_wt_1pt_split.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.01.xml.gz $(out)/samples/be_2020-week_snz_episim_events_sa_1pt_split.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.01.xml.gz $(out)/samples/be_2020-week_snz_episim_events_so_1pt_split.xml.gz


$(out)/samples/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_10pt_split.xml.gz $(out)/samples/be_2020-week_snz_episim_events_wt_10pt_split.xml.gz &: \
$(out)/samples/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_10pt.xml.gz $(out)/samples/be_2020-week_snz_episim_events_wt_10pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/samples/be_2020-week_snz_episim_events_wt_10pt.xml.gz\
	 --events $(out)/samples/be_2020-week_snz_episim_events_sa_10pt.xml.gz\
	 --events $(out)/samples/be_2020-week_snz_episim_events_so_10pt.xml.gz\
	 --shape-file $(out)/../shape-File/dilutionArea.shp\
	 --output $(out)/samples