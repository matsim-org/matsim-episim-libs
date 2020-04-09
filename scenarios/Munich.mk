
in = $(WD)/snz/Munich/original-data
out = $(WD)/snz/Munich/episim-input
tmp = $(WD)/snz/Munich/processed-data

Munich: $(JAR) $(out)/mu_snz_episim_events.xml.gz $(out)/mu_entirePopulation_noPlans.xml.gz

$(out)/mu_snz_episim_events.xml.gz:
	echo "Build this file"

$(out)/mu_entirePopulation_noPlans.xml.gz:
	echo "Build this file"