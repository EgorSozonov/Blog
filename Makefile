.RECIPEPREFIX = /

ifndef VERBOSE
.SILENT: # Silent mode unless you run it like "make all VERBOSE=1"
endif

ifndef EXEDIR
EXEDIR = $(abspath ../../exes)
endif

ifndef DEBUGDIR
DEBUGDIR = $(abspath ../../debug)
endif

.PHONY: all build clean test help binFolder

APP=blog
BUILDDIR=$(EXEDIR)/$(APP)
TESTDIR=$(DEBUGDIR)/$(APP)

all: build  ## Build the whole project
/ @echo "========================================="
/ @echo "              BUILD SUCCESS              "
/ @echo "========================================="


binFolder:
/ mkdir -p $(BUILDDIR)

build: | binFolder ## Build the project
/ javac -d $(BUILDDIR) src/Blog.java
/ jar -c -f $(BUILDDIR)/blog.jar -e tech.sozonov.blog.Blog -C $(BUILDDIR) tech/sozonov/blog

clean: ## Delete cached build results
/ rm -rf _bin

_bin/test: | _bin
/ mkdir -p _bin/test

test: ## Run unit tests
/ echo 'testing...'
/ javac -d '$(TESTDIR)' src/Blog.java test/Test.java
/ jar -c -f $(TESTDIR)/test.jar -e tech.sozonov.blog.Test -C $(TESTDIR) tech/sozonov/blog
/ java -jar $(TESTDIR)/test.jar


help: ## Show this help
/ @egrep -h '\s##\s' $(MAKEFILE_LIST) | sort | awk 'BEGIN {print "-- Help --";print ""; FS = ":.*?## "}; {printf "\033[32m%-10s\033[0m %s\n", $$1, $$2}'
# MAKEFILE_LIST lists the contents of this present file
# egrep selects only lines with the double sharp, they are then sorted
# BEGIN in AWK means an action to be executed once before the linewise
# FS means "field separator" - the separator between parts of a single line
# the printf looks so scary because of the ASCII color codes
