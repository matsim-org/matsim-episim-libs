

JAR := ../target/matsim-episim-*-jar-with-dependencies.jar

WD := ../../shared-svn/projects/episim/matsim-files

ALL:= OpenBerlin snz/Berlin snz/Munich

# Default target
all: $(ALL)

$(JAR):
	mvn package

clean:
	$(foreach var,$(ALL),rm -rf $(var)/output;)

OpenBerlin: $(JAR)
	$(info "Building OpenBerlin")


snz/Berlin: $(JAR)
	$(info "Building SNZ Berlin")


snz/Munich: $(JAR)
	$(info "Building SNZ Munich")

