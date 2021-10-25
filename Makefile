

# Working directory
WD := ../shared-svn/projects/episim/matsim-files

# All available Scenarios
ALL := BerlinWeek MunichWeek

JAR := matsim-episim-*.jar
# Shortcut to the scenario creation tool
sc = java -Xmx20G -cp $(JAR) org.matsim.run.ScenarioCreation

.PHONY: all clean $(ALL)

# Default target
all: $(JAR) $(ALL)

$(JAR):
	mvn package -DskipTests

clean:
	rm -rf target


# Includes all the scenarios with local variables
# https://stackoverflow.com/questions/32904790/can-i-have-local-variables-in-included-makefiles
SUBDIRS := scenarios/Cologne.mk
define INCLUDE_FILE
path = $S
include $S
endef

$(foreach S,$(SUBDIRS),$(eval $(INCLUDE_FILE)))