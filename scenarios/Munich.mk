
in = $(WD)/snz/Munich/original-data
out = $(WD)/snz/Munich/episim-input
tmp = $(WD)/snz/Munich/processed-data

# TODO: variable names are overwritten and thus wrong when the task is run

Munich: $(out)/mu_snz_episim_events.xml.gz $(out)/mu_entirePopulation_noPlans.xml.gz
	$(info "Building SNZ Munich into $(out)")


$(out)/mu_snz_episim_events.xml.gz:
	echo "Build this file: $@"

$(out)/mu_entirePopulation_noPlans.xml.gz:
	echo "Build this file: $@"