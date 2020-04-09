

# Working directory
WD := ../shared-svn/projects/episim/matsim-files

# All available Scenarios
ALL := OpenBerlin Berlin Munich Heinsberg

JAR := matsim-episim-*.jar
# Shortcut to the scenario creation tool
sc = java -cp $(JAR) org.matsim.run.ScenarioCreation

.PHONY: all clean battery $(ALL)

# Default target
all: $(ALL)


$(JAR):
	mvn package -DskipTests

clean:
	rm -rf target

# Helper script for deploying the battery
battery:
	rsync -rvPc battery $(USER)@blogin.hlrn.de:/scratch/usr/$(USER)



# Includes all the scenarios with local variables
# https://stackoverflow.com/questions/32904790/can-i-have-local-variables-in-included-makefiles
SUBDIRS := scenarios/*.mk
define INCLUDE_FILE
path = $S
include $S
endef

$(foreach S,$(SUBDIRS),$(eval $(INCLUDE_FILE)))