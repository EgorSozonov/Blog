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
import tech.sozonov.blog.Utils.Tu;

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
String todayDt = "";

static final String stampOpen = "<!-- Dates -->";
static final String stampClose = "<!-- / -->";
static final String stampTemplate = "<div>Created: $created, updated: $updated</div>";

public Blog(FileSys fs)  {
    this.fs = fs;
    coreVersions = new String[fixedCoreFiles.length];
    globalVersions = new HashMap<String, String>();
    todayDt = formatter.format(Instant.now());
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
    for (int i = 0; i < fixedCoreFiles.length; i++) {
        if (coreVersions[i] == null) {
            L<String> existingNames = fs.getNamesWithPrefix(blogDir, 
                    shaveOffExtension(fixedCoreFiles[i]));
            if (existingNames.size() == 0)  {
                print("Error, no core fixed file found for " + fixedCoreFiles[i]);
                return;
            }
            coreVersions[i] = getNameWithMaxVersion(existingNames).f1;
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
    L<Doc> allDocs = new L();
    Set<String> oldDirs = fs.listSubfoldersContaining(blogDir, "i.html").toSet();

    L<String> ingestDirs = fs.listDirs(ingestDir);
    L<String> targetDirs = ingestDirs.trans(x -> convertToTargetDir(x));
    for (int i = 0; i < ingestDirs.size(); i++) {
        String inSourceDir = ingestDirs.get(i);
        String inTargetDir = targetDirs.get(i);
        String newContent = "";
        var inFiles = fs.listFiles(Paths.get(ingestDir, inSourceDir).toString());
        int mbIndex = inFiles.findIndex(x -> x.name.equals("i.html"));
        if (oldDirs.contains(inTargetDir)) {

            if (mbIndex > -1) {
                newContent = fs.readTextFile(inSourceDir, "i.html");
                if (newContent.length() <= 1) {
                    // a 0- or 1-byte long i.html means "delete this document"
                    deleteDirs.add(inTargetDir);
                    continue;
                }
            }
            updateDirs.add(new CreateUpdate(inSourceDir, inTargetDir));
            allDocs.add(new Doc(inTargetDir));
        } else if (mbIndex > -1) {
            createDirs.add(new CreateUpdate(inSourceDir, inTargetDir));
            allDocs.add(new Doc(inTargetDir));
        }
    }
    return new Ingestion(createDirs, updateDirs, deleteDirs, allDocs);
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


void buildHead(boolean hasLocalScript, L<String> globalScripts, StringBuilder result) {
    result.append(template0);
    result.append("<script type=\"text/javascript\" src=\"/blog/script.js\"></script>\n");
    for (String gs : globalScripts) {
        if (globalVersions.containsKey(gs)) {
            result.append("<script type=\"text/javascript\" src=\"/blog/"
                    + globalVersions.get(gs) + "\"></script>\n");
        }
    }
    if (hasLocalScript) {
        result.append("<script type=\"text/javascript\" src=\"/blog/local.js\"></script>\n");
    }
    result.append("</head>\n");
}


static void buildBody(String html, L<Substitution> subs, StringBuilder result) {
    int curr = html.indexOf("<body>");
    for (var sub : subs) {
        result.append(html.substring(curr, sub.startByte));
        result.append(sub.replacement);
        curr = sub.endByte;
    }
    result.append(html.substring(curr, html.length()));
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
    for (CreateUpdate cre : ing.createDocs) {
        String freshContent = buildDocument("", todayDt, cre.newContent);
    } 
}

void updateDocs(Ingestion ing) {
    for (CreateUpdate upd : ing.updateDocs) {

        String oldContent = fs.readTextFile(upd.targetDir, "i.html");
        String updatedContent = buildDocument(oldContent, todayDt, upd.newContent);
    }
}

void deleteDocs(Ingestion ing) {
    for (String toDel : ing.deleteDocs) {
        fs.deleteDirIfExists(toDel);
    }
}

static String convertToTargetDir(String ingestDir) {
    /// Changes an ingestion dir like "a.b.foo" to the nested subfolder "a/b/foo"
    String dirParts = ingestDir.replace(" ", "").replace(".", "/");
    return Paths.get(dirParts).toString();
}


static Tu<String, Integer> getNameWithMaxVersion(L<String> filenames) {
    /// For a list like `file.txt, file-2.txt, file-3.txt`, returns 3.
    L<String> withoutExts = filenames.trans(Utils::shaveOffExtension);
    var versions = new L();
    int maxVersion = 0;
    String result = filenames.get(0); 
    for (int i = 0; i < filenames.size(); i++) {
        String shortName = withoutExts.get(i);
        int indDash = shortName.lastIndexOf("-");
        if (indDash < 0 || indDash == (shortName.length() - 1)) {
            continue;
        }
        var mbNumber = parseInt(shortName.substring(indDash + 1));
        if (mbNumber.isPresent() && maxVersion < mbNumber.get()) {
            maxVersion = mbNumber.get();
            result = filenames.get(i);
        } else {
            continue;
        }
    }
    return new Tu(result, maxVersion);
}


static String makeNameBumpedVersion(String unversionedName, L<String> existingNames) {
    /// `file.js` (`file-2.js` `file-3.js`) => `file-4.js`
    if (existingNames.size() == 0) {
        return unversionedName;
    } else {
        int maxExistingVersion = getNameWithMaxVersion(existingNames).f2;
        int indLastDot = unversionedName.lastIndexOf(".");
        return unversionedName.substring(0, indLastDot) + "-" + (maxExistingVersion + 1)
                + unversionedName.substring(indLastDot);
    }
}

//}}}
//{{{ Ingestion

static class Ingestion {
    L<CreateUpdate> createDocs;
    L<CreateUpdate> updateDocs;
    L<String> deleteDocs; // list of dirs like `a/b/c`
    L<Doc> allDocs;
    NavTree nav;

    public Ingestion(L<CreateUpdate> createDirs, L<CreateUpdate> updateDirs, L<String> deleteDirs,
                     L<Doc> allDirs) {
                     
        print("Ingestion constructor, count of create " + createDocs.size()
                + ", updateDocs count = " + updateDocs.size() + ", deleteDocs count = " 
                + deleteDocs.size() + ", allDirs = " + allDocs.size()); 
        this.createDocs = createDirs;
        this.updateDocs = updateDirs;
        this.deleteDocs = deleteDirs;
        this.allDocs = allDirs;
        this.nav = buildThematic(allDirs);
    }

    NavTree buildThematic(L<Doc> allDirs) {
        print("BuildThematic, count of allDirs " + allDirs.size()); 
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
                                ? new NavTree(docsByName.get(i).targetDir, new L())
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
    String targetDir;
    L<String> spl;

    public Doc(String targetDir) {
        this.targetDir = targetDir;
        this.spl = L.of(targetDir.split("/"));
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

    static int[] createBreadcrumbs(String subAddress) {
        /// Make breadcrumbs that trace the way to this file from the root.
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
    L<String> globalScripts; // like "graph". Ingestion object will let us find the full name
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
    L<FileInfo> listFiles(String dir); // immediate files in a dir
    L<String> listDirs(String dir); // immediate children dirs
    L<String> listSubfoldersContaining(String dir, String fN); // recursively list all nested dirs
    L<String> getNamesWithPrefix(String dir, String prefix);
    String readTextFile(String dir, String fN);
    boolean createDir(String dir);
    boolean saveOverwriteFile(String dir, String fN, String cont);
    boolean moveFile(String dir, String fN, String targetDir);
    String moveFileToNewVersion(String dir, String fN, String targetDir);
    boolean deleteIfExists(String dir, String fN);
    boolean deleteDirIfExists(String dir);
}


// Implementation
//~    try (Stream<Path> walk = Files.walk(Paths.get(blogDir))) {
//~        walk.filter(Files::isDirectory)
//~            .forEach(x -> {
//~                oldDirs.add(x.toString());
//~            });
//~    } catch (Exception e) {
//~        System.out.println(e.getMessage());
//~    }

//}}}
//{{{ Templates

static final String template0 = """
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Security-Policy"
        content="default-src 'self'; script-src 'self'; base-uri 'self';" />
""";


static final String templateHeadCloseBodyStart = """
<body>
    <div class="__wrapper">
        <div class="__navbar" id="_theNavBar">
            <div class="__menuTop">
                <div class="__svgButton" title="Temporal sorting">
                    <a id="_reorderTemporal" title="Temporal sorting">
                        <svg id="_sorterTemp" class="__swell" width="30" height="30"
                            viewBox="0 0 100 100">
                            <circle r="48" cx="50" cy="50" />
                            <path d="M 35 20 h 30 c 0 30 -30 30 -30 60 h 30
                                     c 0 -30 -30 -30 -30 -60" />
                        </svg>
                    </a>
                </div>
                <div class="__svgButton">
                    <a href="http://sozonov.site" title="Home page">
                        <svg id="__homeIcon" class="__swell" width="30" height="30"
                         viewBox="0 0 100 100">
                            <circle r="48" cx="50" cy="50" />
                            <path d="M 30 45 h 40 v 25 h -40 v -25 " />
                            <path d="M 22 50 l 28 -25 l 28 25" />
                        </svg>
                    </a>
                </div>
            </div>
            <div class="__menu" id="__theMenu"></div>
        </div>

        <div class="__divider" id="_divider">&lt;</div>
        <div class="__menuToggler __hidden" id="_menuToggler">
            <div class="__svgButton" title="Open menu">
                    <a id="_toggleNavBar">
                        <svg class="__swell" width="30" height="30" viewBox="0 0 100 100">
                            <circle r="48" cx="50" cy="50"></circle>
                            <path d="M 30 35 h 40" stroke-width="6"></path>
                            <path d="M 30 50 h 40" stroke-width="6"></path>
                            <path d="M 30 65 h 40" stroke-width="6"></path>
                        </svg>
                    </a>
            </div>
        </div>


        <div class="__content">
""";



static final String template4 = """
        </div>
    </div>
</body>
</html>
""";

//}}}
}
