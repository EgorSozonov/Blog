.RECIPEPREFIX = /

ifndef VERBOSE
.SILENT: # Silent mode unless you run it like "make all VERBOSE=1"
endif

ifndef $(EXES)
EXES = ../../exes
endif

.PHONY: all build clean test help binFolder

APP=blog
BUILDDIR=$(EXES)/$(APP)

all: $(BUILDDIR) ## Build the whole project
/ @echo "========================================="
/ @echo "              BUILD SUCCESS              "
/ @echo "========================================="

run:
/ node _bin/$(BIN).js

binFolder:
/ mkdir -p $(BUILDDIR)

build: | binFolder ## Build the project
/ javac -d $(BUILDDIR) src/Blog.java src/Utils.java

clean: ## Delete cached build results
/ rm -rf _bin

_bin/test: | _bin
/ mkdir -p _bin/test

test: ## Run unit tests
/ echo 'testing'
/ java test/test.java



#g++ -g3 --std=c++23 -I/usr/local/include/oatpp-1.3.0/oatpp -o _bin/cache/blog.o -c src/main.cpp

#g++ -rdynamic "_bin/cache/blog.o" -o oaplay /usr/local/lib/oatpp-1.3.0/liboatpp-test.a /usr/local/lib/oatpp-1.3.0/liboatpp.a -latomic 

help: ## Show this help
/ @egrep -h '\s##\s' $(MAKEFILE_LIST) | sort | awk 'BEGIN {print "-- Help --";print ""; FS = ":.*?## "}; {printf "\033[32m%-10s\033[0m %s\n", $$1, $$2}'
# MAKEFILE_LIST lists the contents of this present file
# egrep selects only lines with the double sharp, they are then sorted
# BEGIN in AWK means an action to be executed once before the linewise
# FS means "field separator" - the separator between parts of a single line
# the printf looks so scary because of the ASCII color codes
