
in = $(WD)/snz/MunichV2/original-data
out = $(WD)/snz/MunichV2/episim-input
tmp = $(WD)/snz/MunichV2/processed-data

MunichWeek: $(JAR) $(out)/mu_2020-week_snz_episim_events_wt_25pt_split.xml.gz $(out)/mu_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz
	echo "Building Munich Week scenario"

$(out)/mu_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz:
	$(sc) convertPersonAttributes $(in)/populationAttributes.xml.gz\
   --ids $(in)/muenchen_umland/personIds.diluted.txt.gz\
   --requireAttribute "microm:modeled:age"\
   --output $@

$(out)/mu_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz: $(out)/mu_2020-week_snz_entirePopulation_emptyPlans_100pt.xml.gz
	$(sc) districtLookup $<\
 	 --output $@\
	 --shp ../public-svn/matsim/scenarios/countries/de/episim/original-data/landkreise-in-germany/landkreise-in-germany.shp

########
# 25pct
########

$(out)/mu_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/mu_2020-week_snz_episim_events_wt_25pt.xml.gz &: \
$(out)/mu_2020-week_snz_entirePopulation_emptyPlans_withDistricts_100pt.xml.gz
	$(sc) downSample 0.25\
   --population $<\
   --events $(in)/de2020gsmwt_events_reduced.xml.gz\
   --events $(in)/de2020gsmsa_events_reduced.xml.gz\
   --events $(in)/de2020gsmso_events_reduced.xml.gz\
   --output $(tmp)

	mv $(tmp)/population0.25.xml.gz $(out)/mu_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz
	mv $(tmp)/de2020gsmwt_events_reduced-0.25.xml.gz $(out)/mu_2020-week_snz_episim_events_wt_25pt.xml.gz
	mv $(tmp)/de2020gsmsa_events_reduced-0.25.xml.gz $(out)/mu_2020-week_snz_episim_events_sa_25pt.xml.gz
	mv $(tmp)/de2020gsmso_events_reduced-0.25.xml.gz $(out)/mu_2020-week_snz_episim_events_so_25pt.xml.gz

$(out)/mu_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz $(out)/mu_2020-week_snz_episim_events_wt_25pt_split.xml.gz &: \
$(out)/mu_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt.xml.gz $(out)/mu_2020-week_snz_episim_events_wt_25pt.xml.gz
	$(sc) splitHomeFacilities $<\
	 --events $(out)/mu_2020-week_snz_episim_events_wt_25pt.xml.gz\
	 --events $(out)/mu_2020-week_snz_episim_events_sa_25pt.xml.gz\
	 --events $(out)/mu_2020-week_snz_episim_events_so_25pt.xml.gz\
	 --shape-file $(in)/muenchen_umland/dilutionArea.shp\
	 --output $(out)