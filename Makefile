.RECIPEPREFIX = /

ifndef VERBOSE
.SILENT: # Silent mode unless you run it like "make all VERBOSE=1"
endif

.PHONY: all clean help

DEPFLAGS=
BIN=blog

CFLAGS = $(CONFIG) $(WARN) $(OPT) $(DEPFLAGS) $(LIBS)

SOURCE=$(wildcard src/*.hs)


all: _bin/$(BIN) ## Build the whole project
/ @echo "========================================="
/ @echo "              BUILD SUCCESS              "
/ @echo "========================================="

build: | _bin ## Build the project
/ CARGO_TARGET_DIR=_bin cargo build

run: 
/ cabal run

_bin:
/ mkdir _bin

clean: ## Delete cached build results
/ rm -rf _bin

help: ## Show this help
/ @egrep -h '\s##\s' $(MAKEFILE_LIST) | sort | awk 'BEGIN {print "-- Help --";print ""; FS = ":.*?## "}; {printf "\033[32m%-10s\033[0m %s\n", $$1, $$2}'
# MAKEFILE_LIST lists the contents of this present file
# egrep selects only lines with the double sharp, they are then sorted
# BEGIN in AWK means an action to be executed once before the linewise
# FS means "field separator" - the separator between parts of a single line
# the printf looks so scary because of the ASCII color codes
