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
import java.time.ZoneId;
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

static final Dir webRoot = Dir.ofString("/var/www");
static final Dir blogDir = new Dir(webRoot, new Subfolder("blog"));
static final Dir ingestDir = new Dir(webRoot, new Subfolder("blogIngest"));

static final String appSuburl = "blog/"; // The URL prefix
static final int updateFreq = 300; // seconds before the cache gets rescanned
// All fixed core files must be unique even without the file extension
static final String[] fixedCoreFiles =
            { "notFound.html", "img404.png", "style.css", "blog.html", "script.js",
              "favicon.ico", "footer.html", "no.png", "yes.png", "termsOfUse.html"};

static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

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
    if (!fs.dirExists(ingestDir))  {
        return;
    }
    var files = fs.listFiles(ingestDir);
    var existingFiles = fs.listFiles(blogDir);
    for (FileInfo fi : files) {
        String fn = fi.name;
        int indFixed = -1;
        for (int j = 0; j < fixedCoreFiles.length; j++)  {
            if (fixedCoreFiles[j].equals(fn)) {
                indFixed = j;
            }
        }

        if (indFixed > -1) {
            String newVersionOfFixed = makeNameBumpedVersion(new UnvName(fn), existingFiles);
            fs.moveFileWithRename(ingestDir, fn, blogDir, newVersionOfFixed);
            coreVersions[indFixed] = newVersionOfFixed;
        } else if (fn.endsWith(".js")) {
            String newVersionOfExtra = makeNameBumpedVersion(new UnvName(fn), existingFiles);
            fs.moveFileWithRename(ingestDir, fn, blogDir, newVersionOfExtra);
            globalVersions.put(shaveOffExtension(fn), newVersionOfExtra);
        }
    }
    for (int i = 0; i < fixedCoreFiles.length; i++) {
        if (coreVersions[i] == null) {
            L<String> existingNames =
                    getNamesWithPrefix(new UnvName(fixedCoreFiles[i]), existingFiles);
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
    /// Moves the local files and builds the full document lists
    Ingestion ing = new Ingestion();
    Set<Subfolder> oldDirs = fs.listSubfoldersContaining(blogDir, "i.html").toSet();

    L<Subfolder> ingestDirs = fs.listSubfolders(ingestDir);
    L<Subfolder> targetDirs = ingestDirs.trans(x -> convertToTargetDir(x));
    for (int i = 0; i < ingestDirs.size(); i++) {
        Subfolder inSourceSubf = ingestDirs.get(i);
        Dir inSourceDir = new Dir(ingestDir, inSourceSubf);
        Subfolder inTargetSubf = targetDirs.get(i);
        Dir inTargetDir = new Dir(blogDir, inTargetSubf);

        String newContent = "";
        var inFiles = fs.listFiles(inSourceDir);
        int mbIndex = inFiles.findIndex(x -> x.name.equals("i.html"));
        if (mbIndex > -1) {
            newContent = fs.readTextFile(inSourceDir, "i.html");
            if (newContent.length() <= 1) {
                // a 0- or 1-byte long i.html means "delete this document"
                ing.deleteDocs.add(inTargetSubf);
                continue;
            }
        }
        LocalFiles localFiles = moveAndReadLocalFiles(inFiles, inSourceDir, inTargetDir);
        if (oldDirs.contains(inTargetSubf)) {
            if (mbIndex > -1) {
                newContent = fs.readTextFile(inSourceDir, "i.html");
                ing.updateDocs.add(
                        new CreateUpdate(inSourceSubf, inTargetSubf, localFiles, newContent));
            } else {
                ing.updateDocs.add(new CreateUpdate(inSourceSubf, inTargetSubf, localFiles));
            }
            ing.allDocs.add(new Doc(inTargetSubf));
        } else if (mbIndex > -1) {
            newContent = fs.readTextFile(inSourceDir, "i.html");
            ing.createDocs.add(
                    new CreateUpdate(inSourceSubf, inTargetSubf, localFiles, newContent));
            ing.allDocs.add(new Doc(inTargetSubf));
        }
    }
    return ing;
}


LocalFiles moveAndReadLocalFiles(L<FileInfo> inFiles, Dir inSourceDir, Dir inTargetDir) {
    /// Determines the new filenames for all local files of a document (the script if any,
    /// and the media files)
    LocalFiles result = new LocalFiles();
    Map<UnvName, Tu<Integer, L<String>>> versions = new HashMap();
    // fn -> (maxEncounteredVersion deleteList)

    var existingFiles = fs.listFiles(inTargetDir);
    for (var fInfo : inFiles) {
        UnvName fn = new UnvName(fInfo.name);
        String newVersion = makeNameBumpedVersion(fn, existingFiles);
        fs.moveFileWithRename(inSourceDir, fInfo.name, inTargetDir, newVersion);
        //result.versions.put(fn, newVersion);
    }
    existingFiles = fs.listFiles(inTargetDir);
    for (var f : existingFiles) {
        UnvName unvName = new UnvName(f.name);
        int version = getFileVersion(f.name);
        if (versions.containsKey(unvName)) {
            Tu<Integer, L<String>> v = versions.get(unvName);
            if (version > v.f1) {
                v.f1 = version;
                v.f2.add(result.versions.get(unvName));
                result.versions.put(unvName, f.name);
            } else {
                v.f2.add(f.name);
            }
        } else {
            versions.put(unvName, new Tu(version, new L()));
            result.versions.put(unvName, f.name);
        }
    }
    for (Tu<Integer, L<String>> v : versions.values()) {
        result.filesToDelete.append(v.f2);
    }
    return result;
}


String buildDocument(CreateUpdate createUpdate, String old, String updatedDt) {
    if (old == "" && createUpdate.newContent == "") {
        throw new RuntimeException("Can't build a document with no inputs!");
    }
    String mainSource = (createUpdate.newContent != "") ? createUpdate.newContent : old;
    String createdDt = "";
    if (old == "") {
        createdDt = updatedDt;
    } else {
        createdDt = parseCreatedDate(old);
    }

    String dateStamp = buildDateStamp(createdDt, updatedDt);

    L<String> globalScripts = new L();
    int indBody = mainSource.indexOf("<body>");
    if (indBody < 0) {
        throw new RuntimeException("Body not found in the HTML document");
    }
    String body = mainSource.substring(indBody);

    String localScriptName =
            parseHead(mainSource, new Dir(blogDir, createUpdate.targetDir), globalScripts);
    L<Substitution> subs = parseBodySubstitutions(body, dateStamp);
    for (var s : subs)  {
        print("Subst from byte " + s.startByte + " to " + s.endByte);
    }

    var result = new StringBuilder();
    buildHead(localScriptName, globalScripts, result);
    buildBody(body, subs, result);
    return result.toString();
}


void buildHead(String localScriptName, L<String> globalScripts, StringBuilder result) {
    result.append(template0);
    result.append("    <script type=\"text/javascript\" src=\"/blog/script.js\"></script>\n");
    for (String gs : globalScripts) {
        if (globalVersions.containsKey(gs)) {
            result.append("    <script type=\"text/javascript\" src=\"/blog/"
                    + globalVersions.get(gs) + "\"></script>\n");
        }
    }
    if (localScriptName.length() > 0) {
        result.append("    <script type=\"text/javascript\" src=\"" + localScriptName
                + "\"></script>\n");
    }
    result.append("    <link rel=\"stylesheet\" href=\"/blog/style.css\" />");
    result.append("\n</head>\n");
}


static void buildBody(String body, L<Substitution> subs, StringBuilder result) {
    print("body " + body);
    int curr = 6; // 6 for the `<body>`
    for (var sub : subs) {
        result.append(body.substring(curr, sub.startByte));
        result.append(sub.text);
        curr = sub.endByte;
    }
    result.append(body.substring(curr, body.length()));
}


static String buildDateStamp(String createdDt, String updatedDt) {
    return stampOpen
        + stampTemplate.replace("$created", createdDt).replace("$updated", updatedDt)
        + stampClose;
}


String parseHead(String old, Dir targetDir, /* out */ L<String> globalCoreScripts) {
    /// Parses the <head> tag of the old HTML and determines if it has the local script "local.js"
    /// as well as the list of core extra scripts this document requires
    String localScriptName = "";
    int start = old.indexOf("<head>");
    int end = old.indexOf("</head>");
    String head = old.substring(start + 6, end);
    L<Substitution> scripts = parseSrcAttribs(head, "script");
    for (var script : scripts) {
        String scrName = script.text;
        if (!scrName.endsWith(".js")) {
            throw new RuntimeException("Script extension must be .js!");
        }
        if (scrName.startsWith("../")) {
            globalCoreScripts.add(shaveOffExtension(scrName.substring(3))); // 3 for the `../`
        } else if (scrName.equals("local.js")) {
            var existingFiles = fs.listFiles(targetDir);
            L<String> existingLocals = getNamesWithPrefix(new UnvName("local.js"), existingFiles);
            localScriptName = getNameWithMaxVersion(existingLocals).f1;
        } else {
            throw new
                RuntimeException("Scripts must either start with `../` or be named `local.js`!");
        }
    }
    return localScriptName;
}


static L<Substitution> parseBodySubstitutions(String body, String dateStamp) {
    /// Produces a list of substitutions sorted by start byte.
    L<Substitution> result = new L();
    int start = 6; // 6 for the length of `<body>`

    // the date stamp
    int indStampOpen = body.indexOf(stampOpen);
    if (indStampOpen == -1) { // A new doc being created
        result.add(new Substitution(start, start, dateStamp));
    } else {
        int indStampClose = body.indexOf(stampClose);
        result.add(new Substitution(indStampOpen, indStampClose + stampClose.length(), dateStamp));
    }
    result.append(parseSrcAttribs(body, "img"));
    return result;
}


static L<Substitution> parseSrcAttribs(String html, String tag) {
    /// (`<foo src="asdf">` `foo`) => `asdf`
    L<Substitution> result = new L();
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
        result.add(new Substitution(indSrc, indEndSrc, attrib));
        ind = html.indexOf(opener, ind + 1);
    }
    return result;
}

static String parseCreatedDate(String old) {
    /// Parses the created date from the old document
    int indStart = old.indexOf(stampOpen);
    int indEnd = old.indexOf(stampClose);
    int indDateStart = indStart + stampOpen.length();
    String datePart = old.substring(indDateStart, indEnd);
    return datePart.substring(14, 24); // Skipping length of `<div>Created: `
}


void createNewDocs(Ingestion ing) {
    for (CreateUpdate cre : ing.createDocs) {
        Dir targetDir = new Dir(blogDir, cre.targetDir);
        String freshContent = buildDocument(cre, "", todayDt);
        fs.saveOverwriteFile(new Dir(blogDir, cre.targetDir), "i.html", freshContent);
    }
}


void updateDocs(Ingestion ing) {
    for (CreateUpdate upd : ing.updateDocs) {
        Dir targetDir = new Dir(blogDir, upd.targetDir);
        String oldContent = fs.readTextFile(targetDir, "i.html");
        String updatedContent = buildDocument(upd, oldContent, todayDt);
        fs.saveOverwriteFile(new Dir(blogDir, upd.targetDir), "i.html", updatedContent);
    }
}


void deleteDocs(Ingestion ing) {
    for (Subfolder toDel : ing.deleteDocs) {
        fs.deleteDirIfExists(new Dir(blogDir, toDel));
    }
}


static Subfolder convertToTargetDir(Subfolder ingestDir) {
    /// Changes an ingestion subfolder like "a.b.foo" to the nested subfolder "a/b/foo"
    String dirParts = ingestDir.cont.replace(" ", "").replace(".", "/");
    return new Subfolder(Paths.get(dirParts).toString());
}


static int getFileVersion(String fn) {
    /// `file-123.txt` => 123
    String withoutExt = shaveOffExtension(fn);
    int indDash = withoutExt.lastIndexOf("-");
    if (indDash < 0) {
        return 1;
    }
    var mbNumber = parseInt(withoutExt.substring(indDash + 1));
    if (mbNumber.isPresent()) {
        return mbNumber.get();
    } else {
        return 1;
    }
}

static Tu<String, Integer> getNameWithMaxVersion(L<String> filenames) {
    /// For a list like `file.txt, file-2.txt, file-3.txt`, returns 3.
    if (filenames.size() == 0) {
        return new Tu("", 0);
    }
    L<String> withoutExts = filenames.trans(Utils::shaveOffExtension);
    var versions = new L();
    int maxVersion = 1;
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


static String makeNameBumpedVersion(UnvName unversionedName, L<FileInfo> existingFiles) {
    /// `file.js` (`file-2.js` `file-3.js`) => `file-4.js`
    if (existingFiles.size() == 0) {
        return unversionedName.cont;
    } else {
        var versionsOfSameFile = existingFiles.transIf(
                x -> new UnvName(x.name).equals(unversionedName),
                x -> x.name);

        int maxExistingVersion =
                getNameWithMaxVersion(existingFiles.transIf(
                        x -> new UnvName(x.name).equals(unversionedName),
                        x -> x.name)).f2;
        if (maxExistingVersion == 0) {
            return unversionedName.cont;
        }
        int newVersion = maxExistingVersion + 1;
        int indLastDot = unversionedName.cont.lastIndexOf(".");
        return unversionedName.cont.substring(0, indLastDot) + "-" + newVersion
                + unversionedName.cont.substring(indLastDot);
    }
}


//}}}
//{{{ Ingestion

static class Ingestion {
    L<CreateUpdate> createDocs = new L();
    L<CreateUpdate> updateDocs = new L();
    L<Subfolder> deleteDocs = new L(); // list of dirs like `a/b/c`
    L<Doc> allDocs = new L();
    NavTree nav;

    public void printOut() {
        print("Ingestion constructor, count of create " + createDocs.size()
                + ", updateDocs count = " + updateDocs.size() + ", deleteDocs count = "
                + deleteDocs.size() + ", allDirs = " + allDocs.size());
    }

    public void finalize() {
        this.nav = buildThematic();
    }

    private NavTree buildThematic() {
        Collections.sort(allDocs, (x, y) ->{
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
        var docsByName = allDocs;

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
                                ? new NavTree(docsByName.get(i).targetDir.cont, new L())
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


static class LocalFiles {
    Map<UnvName, String> versions = new HashMap();
    L<String> filesToDelete = new L();
}

static class CreateUpdate {
    Subfolder sourceDir; // source dir like `a.b.c`
    Subfolder targetDir; // target dir like `a/b/c`
    String newContent; // content of the new "i.html" file, if it's present
    LocalFiles localFiles; // map from prefix to full filename for local files

    public CreateUpdate(Subfolder sourceDir, Subfolder targetDir, LocalFiles localFiles)  {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.newContent = "";
        localFiles = localFiles;
    }

    public CreateUpdate(Subfolder sourceDir, Subfolder targetDir, LocalFiles localFiles,
                        String newContent)  {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.newContent = newContent;
        this.localFiles = localFiles;
    }
}


static class Doc {
    Subfolder targetDir;
    L<String> spl;

    public Doc(Subfolder targetDir) {
        this.targetDir = targetDir;
        this.spl = L.of(targetDir.cont.split("/"));
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


record Substitution(int startByte, int endByte, String text) {}


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
