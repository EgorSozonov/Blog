.RECIPEPREFIX = /

#ifndef VERBOSE
#.SILENT: # Silent mode unless you run it like "make all VERBOSE=1"
#endif

.PHONY: all clean help

TSFLAGS=--strict true --esModuleInterop true --module es2020
BIN=blog

SOURCE=$(wildcard src/*.ts)

all: _bin/$(BIN) ## Build the whole project
/ @echo "========================================="
/ @echo "              BUILD SUCCESS              "
/ @echo "========================================="

install:
/ npm install --save-dev typescript@$(TYPESCRIPT_VERSION)

build: | _bin ## Build the project
/ tsc --outdir _bin src/blog.ts

run:
/ node _bin/$(BIN).js

binFolder:
/ mkdir -p _bin

clean: ## Delete cached build results
/ rm -rf _bin

testFolder: | binFolder
/ mkdir -p _bin/test

test: | testFolder ## Run unit tests
#/ rm _bin/test/test.js
/ echo 'testing'
/ tsc --outdir _bin/test $(TSFLAGS) test/test.ts
/ node _bin/test/test.js

help: ## Show this help
/ @egrep -h '\s##\s' $(MAKEFILE_LIST) | sort | awk 'BEGIN {print "-- Help --";print ""; FS = ":.*?## "}; {printf "\033[32m%-10s\033[0m %s\n", $$1, $$2}'
# MAKEFILE_LIST lists the contents of this present file
# egrep selects only lines with the double sharp, they are then sorted
# BEGIN in AWK means an action to be executed once before the linewise
# FS means "field separator" - the separator between parts of a single line
# the printf looks so scary because of the ASCII color codes
