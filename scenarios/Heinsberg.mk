
in = $(WD)/snz/Heinsberg/Heinsberg/original-data
out = $(WD)/snz/Heinsberg/Heinsberg/episim-input
tmp = $(WD)/snz/Heinsberg/Heinsberg/processed-data


Heinsberg: $(JAR) $(out)/he_events_total.gz $(out)/he_entirePopulation_noPlans.xml.gz

$(out)/he_events_total.gz:
	echo "Build this file"

$(out)/he_entirePopulation_noPlans.xml.gz:
	echo "Build this file"

