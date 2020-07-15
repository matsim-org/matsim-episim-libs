
in = $(WD)/snz/BerlinV2/original-data
out = $(WD)/snz/BerlinV2/episim-input
tmp = $(WD)/snz/BerlinV2/processed-data

BerlinWeek: $(JAR) $(out)/be_2020-week_snz_episim_events_wt_25pt_split.xml.gz $(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz
	echo "Building Berlin Week scenario"

$(out)/be_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz:
	$(sc) convertPersonAttributes $(in)/populationAttributes.xml.gz\
   --ids $(in)/berlin_umland_wt/personIds.diluted.txt.gz\
   --requireAttribute "microm:modeled:age"\
   --output $@

$(out)/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz: $(out)/be_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz
	$(sc) districtLookup $<\
 	 --output $@\
	 --shp ../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp

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