
in = $(WD)/snz/Dresden/original-data
out = $(WD)/snz/Dresden/episim-input
tmp = $(WD)/snz/Dresden/processed-data

Germany: $(out)/germany_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	echo "Building Germany scenario"

$(out)/germany_snz_entirePopulation_emptyPlans_100pt.xml.gz:
	$(sc) convertPersonAttributes $(in)/populationAttributes.xml.gz\
   --requireAttribute "microm:modeled:age"\
   --output $@

$(out)/germany_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz: $(out)/germany_snz_entirePopulation_emptyPlans_100pt.xml.gz
	$(sc) districtLookup $<\
 	 --output $@\
	 --shp ../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp

$(out)/germany_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz: $(out)/germany_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.25\
   --population $<\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population0.25.xml.gz $(out)/germany_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz
