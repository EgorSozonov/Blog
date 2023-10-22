package tech.sozonov.blog;
//{{{ Imports

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.stream.Stream;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import static tech.sozonov.blog.Utils.*;
//}}}

class Blog {

//{{{ Constants

static final String blogDir = "/var/www/blog";
static final String ingestDir = "/var/www/blogIngest";

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
String[] coreVersions; // the new full names of all the fixed core files
Map<String, String> globalVersions; // the new full names of the extra global scripts
                                    // Entries are like "graph" => "graph-3.js"

static final String stampOpen = "<!-- Dates -->";
static final String stampClose = "<!-- / -->";
static final String stampTemplate = "<div>Created: $created, updated: $updated</div>";

public Blog(FileSys fs)  {
    this.fs = fs;
    coreVersions = new String[fixedCoreFiles.length];
    globalVersions = new HashMap<String, String>();
}

void ingestCore() {
    String dir = Paths.get(ingestDir).toString();
    if (!fs.dirExists(dir))  {
        return;
    }
    var files = fs.listFiles(dir);
    for (FileInfo fi : files) {
        String fN = fi.name;
        int indFixed = -1;
        for (int j = 0; j < fixedCoreFiles.length; j++)  {
            if (fixedCoreFiles[j].equals(fN)) {
                indFixed = j;
            }
        }

        if (indFixed > -1) {
            String newVersionOfFixed = fs.moveFileToNewVersion(dir, fN, blogDir);
            coreVersions[indFixed] = newVersionOfFixed;
        } else if (fN.endsWith(".js")) {
            String newVersionOfExtra = fs.moveFileToNewVersion(dir, fN, blogDir);
            globalVersions.put(shaveOffExtension(fN), newVersionOfExtra);

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
    L<Doc> allDirs = new L();
    Set<String> oldDirs = new HashSet();
    String todayDt = formatter.format(Instant.now());
    try (Stream<Path> walk = Files.walk(Paths.get(blogDir))) {
        walk.filter(Files::isDirectory)
            .forEach(x -> {
                oldDirs.add(x.toString());
            });
    } catch (Exception e) {
        System.out.println(e.getMessage());
    }

    L<String> ingestDirs = fs.listDirs(ingestDir);
    L<String> targetDirs = ingestDirs.trans(x -> convertToTargetDir(x));
    for (int i = 0; i < ingestDirs.size(); i++) {
        String inSourceDir = ingestDirs.get(i);
        String inTargetDir = targetDirs.get(i);
        String newContent = "";
        var inFiles = fs.listFiles(Paths.get(ingestDir, inSourceDir).toString());
        int mbIndex = inFiles.findIndex(x -> x.name.equals("i.html"));
        if (oldDirs.contains(inTargetDir)) {
            String oldContent = fs.readTextFile(inTargetDir, "i.html");

            if (mbIndex > -1) {
                newContent = fs.readTextFile(inSourceDir, "i.html");
                if (newContent.length() <= 1) {
                    // a 0 or 1 byte-long i.html means "delete this document"
                    deleteDirs.add(inTargetDir);
                    continue;
                }
            }
            //String updatedContent = buildDocument(oldContent, todayDt, newContent);
            updateDirs.add(new CreateUpdate(inSourceDir, inTargetDir));
            allDirs.add(new Doc(inTargetDir,
                        parseCreatedDate(oldContent), todayDt));
        } else if (mbIndex > -1) {
            //String freshContent = buildDocument("", todayDt, newContent);
            createDirs.add(new CreateUpdate(inSourceDir, inTargetDir));
            allDirs.add(new Doc(inTargetDir, todayDt, ""));
        }
    }
    return new Ingestion(createDirs, updateDirs, deleteDirs, allDirs);
}

String buildDocument(String old, String updatedDt, String newContent) {
    if (old == "" && newContent == "") {
        throw new RuntimeException("Can't build a document with no inputs!");
    }
    String mainSource = (newContent != "") ? newContent : old;
    String createdDt = "";
    if (old == "") {
        createdDt = updatedDt;
    } else {
        createdDt = parseCreatedDate(old);
    }

    String dateStamp = buildDateStamp(createdDt, updatedDt);

    L<String> globalScripts = new L();
    boolean hasLocalScript = parseHead(mainSource, globalScripts);
    L<Substitution> subs = parseBodySubstitutions(mainSource, dateStamp);

    var result = new StringBuilder();
    buildHead(hasLocalScript, globalScripts, result);
    buildBody(mainSource, subs, result);
    return result.toString();
}

static void buildHead(boolean hasLocalScript, L<String> globalScripts, StringBuilder result) {

}


static void buildBody(String html, L<Substitution> subs, StringBuilder result) {
}

static String buildDateStamp(String createdDt, String updatedDt) {
    return stampOpen
        + stampTemplate.replace("$created", createdDt).replace("$updated", updatedDt)
        + stampClose;
}

static boolean parseHead(String old, /* out */ L<String> globalCoreScripts) {
    /// Parses the <head> tag of the old HTML and determines if it has the local script "local.js"
    /// as well as the list of core extra scripts this document requires
    boolean hasLocal = false;
    int start = old.indexOf("<head>");
    int end = old.indexOf("</head>");
    String head = old.substring(start + 6, end);
    L<String> scripts = parseSrcAttribs(head, "script");
    for (String scrName : scripts) {
        if (!scrName.endsWith(".js")) {
            throw new RuntimeException("Script extension must be .js!");
        }
        if (scrName.startsWith("../")) {
            globalCoreScripts.add(shaveOffExtension(scrName.substring(3))); // 3 for the `../`
        } else if (scrName.equals("local.js")) {
            hasLocal = true;
        } else {
            throw new
                RuntimeException("Scripts must either start with `../` or be named `local.js`!");
        }
    }
    return hasLocal;
}

static L<Substitution> parseBodySubstitutions(String mainSource, String dateStamp) {
    /// Produces a list of substitutions sorted by start byte.
    L<Substitution> result = new L();
    int start = mainSource.indexOf("<body>") + 6; // +6 for the length of `<body>`
    int end = mainSource.indexOf("</body>");
    String body = mainSource.substring(start, end);

    // the date stamp
    int indStampOpen = body.indexOf(stampOpen);
    if (indStampOpen == -1) { // A new doc being created
        result.add(new Substitution(start, start, dateStamp));
    } else {
        int indStampClose = body.indexOf(stampClose);
        result.add(new Substitution(indStampOpen, indStampClose + stampClose.length(), dateStamp));
    }
    return result;
}

static L<String> parseSrcAttribs(String html, String tag) {
    /// (`<foo src="asdf">` `foo`) => `asdf`
    L<String> result = new L();
    String opener = "<" + tag;
    int ind = html.indexOf(opener);
    while (ind > -1) {
        int indClose = html.indexOf(">", ind);
        if (indClose == -1) {
            throw new RuntimeException("Unclosed tag in the HTML");
        }
        int indSrc = html.indexOf("src=\"", ind) + 5;
        int indEndSrc = html.indexOf("\"", indSrc); // 5 for the `src="`
        String attrib = html.substring(indSrc, indEndSrc);
        result.add(attrib);
        ind = html.indexOf(opener);
    }
    return result;
}

static String parseCreatedDate(String old) {
    /// Parses the created date from the old document
    int indStart = old.indexOf(stampOpen);
    int indEnd = old.indexOf(stampClose);
    int indDateStart = indStart + stampOpen.length();
    String datePart = old.substring(indDateStart, indDateStart + stampClose.length());
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
    String dirParts = ingestDir.replace(" ", "").replace(".", "/");
    return Paths.get(dirParts).toString();
}

//}}}
//{{{ Ingestion

static class Ingestion {
    L<CreateUpdate> createDocs;
    L<CreateUpdate> updateDocs;
    L<String> deleteDocs; // list of dirs like `a/b/c`
    L<Doc> allDocs; // list of dirs like `a/b/c`
    NavTree thematic;

    public Ingestion(L<CreateUpdate> createDirs, L<CreateUpdate> updateDirs, L<String> deleteDirs,
                     L<Doc> allDirs) {
        this.createDocs = createDirs;
        this.updateDocs = updateDirs;
        this.deleteDocs = deleteDirs;
        this.allDocs = allDirs;
        this.thematic = buildThematic(allDirs);
    }

    NavTree buildThematic(L<Doc> allDirs) {
        Collections.sort(allDirs, (x, y) ->{
            int folderLengthCommon = Math.min(x.spl.size(), y.spl.size());
            for (int i = 0; i < folderLengthCommon; i++) {
                int cmp = x.spl.get(i).compareTo(y.spl.get(i));
                if (cmp != 0) {
                    return cmp;
                }
            }
            if (x.spl.size() != y.spl.size()) {
                return y.spl.size() - x.spl.size();
            } else {
                return x.spl.last().compareTo(y.spl.last());
            }
        });
        var docsByName = allDirs;

        var st = new L<NavTree>();
        var root = new NavTree("", new L<NavTree>());
        st.add(root);

        for (int i = 0; i < docsByName.size(); i++) {
            var spl = docsByName.get(i).spl;
            int lenSamePrefix = Math.min(st.size() - 1, spl.size()) - 1;
            while (lenSamePrefix > -1
                    && !st.get(lenSamePrefix + 1).name.equals(spl.get(lenSamePrefix))) {
                --lenSamePrefix;
            }
            for (int j = lenSamePrefix + 1; j < spl.size(); j++) {
                var newElem = (j == spl.size() - 1)
                                ? new NavTree(docsByName.get(i).dir, new L())
                                : new NavTree(spl.get(j), new L());
                if (j + 1 < st.size())  {
                    st.set(j + 1, newElem);
                } else {
                    st.add(newElem);
                }
                var prev = st.get(j);
                prev.children.add(newElem);
            }
        }
        return root;
    }
}

static class CreateUpdate {
    String sourceDir; // source dir like `a.b.c`
    String targetDir; // target dir like `a/b/c`
    String newContent; // content of the new "i.html" file, if it's present
    Map<String, String> localVersions; // map from prefix to full filename for local files
    public CreateUpdate(String sourceDir, String targetDir)  {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.newContent = "";
        localVersions = new HashMap();
    }

    public CreateUpdate(String sourceDir, String targetDir, String newContent)  {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.newContent = newContent;
        localVersions = new HashMap();
    }
}

static class Doc {
    String dir;
    String createdDate;
    String updatedDate;
    L<String> spl;

    public Doc(String dir, String createdDate, String updatedDate) {
        this.dir = dir;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
        this.spl = L.of(dir.split("/"));
    }

    static void ofNew() {

    }

    static void ofUpdate() {

    }
}

static class NavTree {
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
        return null;
    }

    static int[] createBreadcrumbsTemporal(String subAddress) {
        /// Make breadcrumbs that trace the way to this file from the root.
        /// Searches the leaves only, which is necessary for temporal nav trees.
        return null;
    }

    String toJson() {
         var st = new L<NavTree>();
         if (this.children.size() == 0) {
             return "";
         }

         var result = new StringBuilder(100);
         st.add(this);
         while (st.nonEmpty()) {
             var top = st.last();

             if (top.currInd < top.children.size()) {
                 var next = top.children.get(top.currInd);
                 if (next.children.size() > 0) {
                     result.append("[\"");
                     result.append(next.name);
                     result.append("\", [");
                     st.add(next);
                 } else {
                     result.append("[\"");
                     result.append(next.name);
                     if (top.currInd == top.children.size() - 1) {
                         result.append("\", [] ] ");
                     } else {
                         result.append("\", [] ], ");
                     }
                 }
             } else {
                  st.removeLast();

                  if (st.nonEmpty()) {
                      var parent = st.last();
                      if (parent.currInd < parent.children.size()) {
                          result.append("]], ");
                      } else {
                          result.append("]] ");
                      }
                  }
             }
             ++top.currInd;
         }
         return result.toString();
    }
}


static class DocBuild {
    String input;
    L<String> globalScripts; // like "graph" => Ingestion object will let us find the full name
    L<Substitution> substitutions; // "the bytes from 10 to 16 should be replaced with `a-2.png`"
    DocBuild(String input, L<String> globalScripts, L<Substitution> substitution) {
        this.input = input;
        this.globalScripts = globalScripts;
        this.substitutions = substitutions;
    }
}


record Substitution(int startByte, int endByte, String replacement) {}


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
