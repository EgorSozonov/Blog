package tech.sozonov.blog;

class Blog {
//{{{ Constants

static final String blogDir = "/var/www/blst";
static final String ingestDir = "/var/www/blog";

static final String appSuburl = "blog/"; // The URL prefix
static final int updateFreq = 300; // seconds before the cache gets rescanned
// All fixed core files must be unique even without the file extension
static final String[] fixedCoreFiles =
            { "notFound.html", "img404.png", "style.css", "blog.html", "script.js",
              "favicon.ico", "footer.html", "no.png", "yes.png", "termsOfUse.html"};

static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

//}}}
//{{{ Blog

FileSys fs;
String[] fixedVersions; // the new full names of all the fixed core files
Map<String, String> extraVersions; // the new full names of the extra core scripts
// Entries are like "graph" => "graph-3.js"


static final String datesOpen = "<!-- Dates -->";
static final String datesClose = "<!-- / -->";
static final String datesTemplate = "<div>Created: $created, updated: $updated</div>";


public Blog(FileSys fs)  {
    this.fs = fs;
    fixedVersions = new String[fixedCoreFiles.length];
    extraVersions = new HashMap<String, String>();
}

void ingestCore() {
    String dir = Paths.get(ingestDir).toString();
    if (!fs.dirExists(dir))  {
        return;
    }
    var files = fs.listFiles(dir);
    for (FileInfo fi : files) {
        String fN = fi.name;
        int indFixed = findIndex(fixedCoreFiles, x -> x.equals(fN));
        if (indFixed > -1) {
            String newVersionOfFixed = fs.moveFileToNewVersion(dir, fN, blogDir);
            fixedVersions[indFixed] = newVersionOfFixed;
        } else if (fN.endsWith(".js")) {
            String newVersionOfExtra = fs.moveFileToNewVersion(dir, fN, blogDir);
            extraVersions.put(shaveOffExtension(fN), newVersionOfExtra);
        }
    }
}

void ingestDocs() {
    // determine the 4 lists
    // calculate the nav trees
    // create new docs
    // update changing docs local files
    // update changing docs
    // delete old local files
    // delete the to-delete docs
    Ingestion ing = buildIngestion();
    createNewDocs(ing);
    updateDocs(ing);
    deleteDocs(ing);
}

Ingestion buildIngestion() {
    L<CreateUpdate> createDirs = new L();
    L<CreateUpdate> updateDirs = new L();
    L<String> deleteDirs = new L();
    L<String> allDirs = new L();
    Set<String> oldDirs = new HashSet();
    try (Stream<Path> walk = Files.walk(path)) {
        walk.filter(Files::isDirectory)
            .forEach(x -> {
                allDirs.add(x.toString());
                oldDirs.add(x.toString());
            });
    }

    L<String> ingestDirs = fs.listDir(ingestDir);
    L<String> targetDirs = ingestDirs.trans(x -> Blog::convertToTargetDir(x));
    for (int i = 0; i < ingestDirs.size; i++) {
        String inSourceDir = sourceDirs.get(i);
        String inTargetDir = targetDirs.get(i);
        if (oldDirs.contains(inTargetDir)) {
            var inFiles = fs.listFiles(Paths.get(ingestDir, ingestDirs.get(i))).toString();
            int mbIndex = inFiles.first(x -> x.name.equals("i.html"));
            // a 0 or 1 byte-long i.html means "delete this document"
            if (mbIndex > -1 && inFiles.get(mbIndex).cont.size() <= 1) {
                deleteDirs.add(inTargetDir);
            } else {
                updateDirs.add(new CreateUpdate(inSourceDir, inTargetDir));
            }
        } else {
            createDirs.add(new CreateUpdate(inSourceDir, inTargetDir));
            allDirs.add(inTargetDir);
        }
    }
}


static String buildDocument(String old, Instant newModified, String newContent) {
    if (old == "" && newContent == "") {
        throw new RuntimeException("Can't build a document with no inputs!");
    }
    var result = new StringBuilder();
    String mainSource = (newContent != "") ? newContent : old;
    String createdDate = "";
    if (old == "") {
        createdDate = formatter.format(newModified);
    } else {
        createdDate = parseCreatedDate(old);
    }
    return result.toString();
}

static String parseCreatedDate(String old) {
    /// Parses the created date from the old document
    int indStart = old.indexOf(datesOpen);
    int indEnd = old.indexOf(datesClose);
    String datePart = old.substring(indStart + datesOpen.length, datesClose);
    return datePart.substring(14, 24); // Skipping length of `<div>Created: `
}

void createNewDocs(Ingestion ing) {

}

void updateDocs(Ingestion ing) {

}


void deleteDocs(Ingestion ing) {

}

static String convertToTargetDir(String ingestDir) {
    /// Changes an ingestion dir like "a.b.foo" to the nested subfolder "a/b/foo"
    return Paths.get(ingestDir.split(".").replace(" ", "")).toString();
}





//}}}
//{{{ Ingestion


static class Ingestion {
    L<CreateUpdate> createDocs;
    L<CreateUpdate> updateDocs;
    L<String> deleteDocs; // list of dirs like `a/b/c`
    L<String> allDocs; // list of dirs like `a/b/c`
    NavTree thematic;
    NavTree temporal;

    public Ingestion(L<CreateUpdate> createDirs, L<CreateUpdate> updateDirs, L<String> deleteDirs,
                     L<Doc> allDirs) {
        this.createDocs = createDirs;
        this.updateDocs = updateDirs;
        this.deleteDocs = deleteDirs;
        this.allDocs = allDirs;
        var trees = buildNavTrees(allDirs);
        this.thematic = trees.get(0);
        this.temporal = trees.get(1);
    }

    L<NavTree> buildNavTrees(L<String> allDirs) {
        /// Returns a list of 2 items: l(topical temporal)
        var arrNavigation = toPageArray(allDirs);
        thematic = null;
    }

    static toPageArray(L<Doc> allDirs) {

    }
}


static class CreateUpdate {
    String sourceDir; // source dir like `a.b.c`
    String targetDir; // target dir like `a/b/c`
    Map<String, String> localVersions; // map from prefix to full filename for local files
    public CreateUpdate(String sourceDir, String targetDir)  {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        localVersions = new HashMap();
    }
}

static class Doc {
    String dir;
    String createdDate;
    String updatedDate;

    static ofNew() {

    }

    static ofUpdate() {

    }
}


static class NavTree() {
    String name;
    L<NavTree> children;
    int currInd; // current index into this tree, used during traversals

    public NavTree(String name, L<NavTree> children) {
        this.name = name;
        this.children = children;
        this.currInd = 0;
    }

    static int[] createBreadcrumbsThematic(String subAddress) {
        /// Make breadcrumbs that trace the way to this file from the root.
        /// Attempts to follow the spine, but this doesn't work for temporal nav trees, so in case
        /// of an element not found it switches to the slow version (which searches through
        /// all the leaves).
    }

    static int[] createBreadcrumbsTemporal(String subAddress) {
        /// Make breadcrumbs that trace the way to this file from the root.
        /// Searches the leaves only, which is necessary for temporal nav trees.
    }

    String toJson() {
         var st = L<NavTree>()
         if (this.children.size() == 0) {
             return "";
         }

         val result = new StringBuilder(100);
         stack.push(Tuple(this, 0));
         while (stack.any()) {
             val top = stack.peek()

             if (top.second < top.first.children.size) {
                 val next = top.first.children[top.second]
                 if (next.children.size > 0) {
                     result.append("[\"")
                     result.append(next.name)
                     result.append("\", [")
                     stack.push(Tuple(next, 0))
                 } else {
                     result.append("[\"")
                     result.append(next.name)
                     if (top.second == top.first.children.size - 1) {
                         result.append("\", [] ] ")
                     } else {
                         result.append("\", [] ], ")
                     }
                 }
             } else {
                  stack.pop()

                  if (stack.any()) {
                      val parent = stack.peek()
                      if (parent.second < parent.first.children.size) {
                          result.append("]], ")
                      } else {
                          result.append("]] ")
                      }
                  }
             }
             top.second++;
         }
         return result.toString();
    }
}


static class DocBuild {
    String input;
    L<String> coreExtraScripts; // like "graph" => Ingestion object will let us find the full name
    L<Substitution> substitutions; // "the bytes from 10 to 16 should be replaced with `a-2.png`"
}


static class Substitution {
    int startByte;
    int endByte;
    String replacement;
}

//}}}
//{{{ Filesys

static class FileInfo {
    String name;
    Instant modified;

    public FileInfo(String name, Instant modif)  {
        this.name = name;
        this.modified = modif;
    }
}

interface FileSys {
    boolean dirExists(String dir);
    L<FileInfo> listFiles(String dir);
    L<String> listDirs(String dir);
    String readTextFile(String dir, String fN);
    L<String> getDirsContainingFile(String fN);
    boolean createDir(String dir);
    boolean saveOverwriteFile(String dir, String fN, String cont);
    boolean moveFile(String dir, String fN, String targetDir);
    String moveFileToNewVersion(String dir, String fN, String targetDir);
    boolean deleteIfExists(String dir, String fN);
    L<String> getNamesWithPrefix(String dir, String prefix);
}

//}}}
}
