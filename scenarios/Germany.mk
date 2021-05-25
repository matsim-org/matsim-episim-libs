
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