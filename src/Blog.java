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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.stream.Stream;

//}}}

class Blog {

//{{{ Constants

static final Dir webRoot = Dir.ofString("/var/www");
static final Dir blogDir = new Dir(webRoot, new Subfolder("blog/blog"));
static final Dir ingestDir = new Dir(webRoot, new Subfolder("blogIngest"));

static final String appSuburl = "/blog/"; // The URL prefix
static final String contentStartMarker = "<div id=\"_content\">\n";
static final String contentEndMarker = "<!-- _contentEnd -->\n";

// All fixed core files must be unique even without the file extension
static final String[] fixedCoreFiles =
            { "notFound.html", "img404.png", "style.css", "blog.html", "script.js",
              "favicon.ico", "footer.html", "no.png", "yes.png", "termsOfUse.html"};

static final DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

//}}}
//{{{ Blog

FileSys fs;
String[] coreVersions; // the new full names of all the fixed core files
Map<String, String> globalVersions; // the new full names of the extra global scripts
                                    // Entries are like "graph" => "graph-3.js"
String todayDt = "";

static final String stampOpen = "<!-- Dates -->";
static final String stampClose = "<!-- / -->";
static final String stampTemplate = "<div id=\"_dtSt\">Created: $created, updated: $updated</div>";
static final String stampShortTemplate = "<div id=\"_dtSt\">Created: $created</div>";
// must have the same "div" prefix as stampTemplate

public Blog(FileSys fs)  {
    this.fs = fs;
    coreVersions = new String[fixedCoreFiles.length];
    globalVersions = new HashMap<String, String>();
    todayDt = formatter.format(Instant.now());
}

void run() {
    try {
        boolean coreIsUpdated = ingestCore();
        ingestDocs(coreIsUpdated);
    } catch (Exception e) {
        e.printStackTrace();
    }
}

boolean ingestCore() {
    /// Ingest the core files. Return true iff there were any updates to the core files
    if (!fs.dirExists(ingestDir))  {
        return false;
    }
    var inFiles = fs.listFiles(ingestDir);
    var existingFiles = fs.listFiles(blogDir);
    for (FileInfo fi : inFiles) {
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
                throw new RuntimeException(
                    "Error, no core fixed file found for " + fixedCoreFiles[i]);
            }
            coreVersions[i] = getNameWithMaxVersion(existingNames).f1;
        }
    }
    return inFiles.size() > 0;
}

void ingestDocs(boolean coreIsUpdated) {
    Ingestion ing = buildIngestion(coreIsUpdated);
    createUpdateDocs(ing, false); // create docs
    createUpdateDocs(ing, true); // update docs
    deleteDocs(ing);
}


Ingestion buildIngestion(boolean coreIsUpdated) {
    /// Moves the local files and builds the full document lists
    Ingestion ing = new Ingestion();
    ing.allSubfs = fs.listSubfoldersContaining(blogDir, "i.html").toSet();

    L<Subfolder> ingestDirs = fs.listSubfolders(ingestDir);
    L<Subfolder> targetDirs = ingestDirs.trans(x -> convertToTargetDir(x));
    for (int i = 0; i < ingestDirs.size(); i++) {
        Subfolder inSourceSubf = ingestDirs.get(i);
        Dir inSourceDir = new Dir(ingestDir, inSourceSubf);
        Subfolder inTargetSubf = targetDirs.get(i);
        Dir inTargetDir = new Dir(blogDir, inTargetSubf);

        String newContent = "";
        var inFiles = fs.listFiles(inSourceDir);
        int mbHtmlInd = inFiles.findIndex(x -> x.name.equals("i.html"));
        if (mbHtmlInd > -1) {
            newContent = fs.readTextFile(inSourceDir, "i.html");

            if (newContent.length() <= 1) {
                // a 0- or 1-byte long i.html means "delete this document"
                ing.deleteDocs.add(inTargetSubf);
                continue;
            }
        }
        LocalFiles localFiles = moveAndReadLocalFiles(inFiles, inSourceDir, inTargetDir);
        
        if (ing.allSubfs.contains(inTargetSubf)) {
            if (mbHtmlInd > -1) {
                newContent = fs.readTextFile(inSourceDir, "i.html");
                ing.updateDocs.add(
                    new CreateUpdate(inSourceSubf, inTargetSubf, localFiles, newContent));
            } else {
                ing.updateDocs.add(
                    new CreateUpdate(inSourceSubf, inTargetSubf, localFiles, true));
            }
        } else if (mbHtmlInd > -1) {
            ing.createDocs.add(
                new CreateUpdate(inSourceSubf, inTargetSubf, localFiles, newContent));
            ing.allSubfs.add(inTargetSubf);
        }
    }
    if (coreIsUpdated) {
        for (Subfolder old : ing.allSubfs)  {
            if (!targetDirs.contains(old)) {
                print("adding an update because of core: " + old.cont); 
                ing.updateDocs.add(new CreateUpdate(null, old, new LocalFiles(), false));
            }
        }
    }
    ing.finalize();
    return ing;
}


LocalFiles moveAndReadLocalFiles(L<FileInfo> inFiles, Dir inSourceDir, Dir inTargetDir) {
    /// Moves all local files (except of course the `i.html`) to target dir and determines
    /// their new filenames
    LocalFiles result = new LocalFiles();
    Map<UnvName, Integer> maxVersions = new HashMap();

    var existingFiles = fs.listFiles(inTargetDir);
    for (var fInfo : inFiles) {
        if (fInfo.name.equals("i.html")) {
            continue;
        }
        print("inFile: " + fInfo.name);
        UnvName fn = new UnvName(fInfo.name);
        String newVersion = makeNameBumpedVersion(fn, existingFiles);
        print("new version " + newVersion + " to be moved to " + inTargetDir.cont);
        fs.moveFileWithRename(inSourceDir, fInfo.name, inTargetDir, newVersion);
    }
    existingFiles = fs.listFiles(inTargetDir).filter(x -> !x.name.equals("i.html"));
    for (var f : existingFiles) {
        UnvName unvName = new UnvName(f.name);
        int version = getFileVersion(f.name);
        if (maxVersions.containsKey(unvName))  {
            if (version > maxVersions.get(unvName)) {
                print("new max version " + version + " instead of " + maxVersions.get(unvName)); 
                result.filesToDelete.add(result.versions.get(unvName));
                result.versions.put(unvName, f.name); 
                maxVersions.put(unvName, version);
            } else {
                result.filesToDelete.add(f.name);
                print("planning to delete " + f.name); 
            }
        } else {
            maxVersions.put(unvName, version);
            result.versions.put(unvName, f.name); 
            print("new version " + version + "  for " + unvName.cont); 
        }
    }
    return result;
}


String buildDocument(CreateUpdate createUpdate, String old, String updatedDt, Ingestion ing) {
    if (old == "" && createUpdate.newContent == "") {
        throw new RuntimeException("Can't build a document with no inputs!");
    }
    String mainSource;
    boolean isOld = false;
    if (createUpdate.newContent != "") {
        mainSource = createUpdate.newContent;
    } else {
        mainSource = old;
        isOld = true;
    }
    L<String> globalScripts = new L();

    String content = extractContent(mainSource, isOld);
    String dateStamp = buildDateStamp(old, createUpdate.bumpTheDate, updatedDt);
    print("built the datestamp: " + dateStamp); 
    String localScriptName =
            parseHead(mainSource, new Dir(blogDir, createUpdate.targetDir), isOld, globalScripts);
    L<Substitution> subs = parseBodySubstitutions(content, dateStamp, createUpdate.localFiles);

    var result = new StringBuilder();
    buildHead(localScriptName, globalScripts, createUpdate.targetDir, ing, result);
    buildBody(content, subs, result);
    return result.toString();
}


String buildDateStamp(String old, boolean bumpTheDate, String updatedDt) {
    if (!bumpTheDate) { // Just the core files were updated, the doc itself wasn't
        int indStart = old.indexOf(stampOpen);
        int indEnd = old.indexOf(stampClose);
        return old.substring(indStart, indEnd + stampClose.length());
    }
    String createdDt = "";
    if (old == "") {
        createdDt = updatedDt;
    } else {
        createdDt = parseCreatedDate(old);
    }
    
    if (createdDt.equals(updatedDt)) {
        return stampOpen + stampShortTemplate.replace("$created", createdDt) + stampClose;
    }
    return stampOpen
        + stampTemplate.replace("$created", createdDt).replace("$updated", updatedDt)
        + stampClose;
}


void buildHead(String localScriptName, L<String> globalScripts, Subfolder subf, Ingestion ing,
               StringBuilder result) {
    result.append(templateHtmlStart);
    result.append("    <script type=\"text/javascript\" src=\"");
    result.append(appSuburl + coreVersions[indexOf(fixedCoreFiles, "script.js")]);
    result.append("\"></script>\n");
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
    result.append("    <link rel=\"stylesheet\" href=\"");
    result.append(appSuburl + coreVersions[indexOf(fixedCoreFiles, "style.css")]);
    result.append("\" />\n");
    buildNavPart(ing.navPart, subf, result);
    result.append("\n</head>\n");
}
 
 
void buildNavPart(String navTree, Subfolder subf, StringBuilder result) {
    result.append("""
        <script type="application/json" id="_navState">{
            "address":""");
    result.append(" \"");
    result.append(subf.cont);
    result.append("\",\n");
    result.append("    \"nav\": [\n");
    result.append(navTree);
    result.append("\n    ]\n");
    result.append("}</script>");
}


static String extractContent(String html, boolean isOld) {
    /// For an old file, it extracts the contents of the `<div id="_content">` tag
    /// For a new file, it extracts the contents of the `<body>` tag
    int indStart;
    int indEnd;
    if (isOld) {
        indStart = html.indexOf(contentStartMarker) + contentStartMarker.length();
        indEnd = html.indexOf(contentEndMarker);
    } else {
        indStart = html.indexOf("<body>") + 6;
        indEnd = html.indexOf("</body>");
    }
    return html.substring(indStart, indEnd);
}


static void buildBody(String content, L<Substitution> subs, StringBuilder result) {
    result.append(templateBodyStart);
    buildContent(content, subs, result);
    result.append(contentEndMarker);
    result.append(templateEnd);
}


static void buildContent(String content, L<Substitution> subs, StringBuilder result) {
    int curr = 0;
    for (var sub : subs) {
        result.append(content.substring(curr, sub.startByte));
        result.append(sub.text);
        curr = sub.endByte;
    }
    result.append(content.substring(curr, content.length()));
}


String parseHead(String html, Dir targetDir, boolean isOld, /* out */ L<String> globalCoreScripts) {
    /// Parses the <head> tag of the HTML and determines if it has the local script "local.js"
    /// as well as a list of core extra scripts this document may require
    String localScriptName = "";
    int start = html.indexOf("<head>");
    int end = html.indexOf("</head>");
    String head = html.substring(start + 6, end);
    L<Substitution> scripts = parseSrcAttribs(head, "script");
    for (var script : scripts) {
        String scrName = script.text;
        if (!scrName.endsWith(".js")) {
            throw new RuntimeException("Script extension must be .js!");
        }
        if (scrName.equals("local.js")) {
            var existingFiles = fs.listFiles(targetDir);
            L<String> existingLocals = getNamesWithPrefix(new UnvName("local.js"), existingFiles);
            localScriptName = getNameWithMaxVersion(existingLocals).f1;
        } else {
            if (scrName.startsWith(appSuburl)) {
                globalCoreScripts.add(shaveOffExtension(scrName.substring(appSuburl.length())));
            } else if (scrName.startsWith("../")) {
                globalCoreScripts.add(shaveOffExtension(scrName.substring(3)));
            } else {
                throw new
                    RuntimeException("Scripts must either be named `local.js`, start with `../`" 
                            + " for new/updated documents, or start with " + appSuburl + " for existing "
                            + " unchanged docs, but was " + scrName + "!");
            }
        }
    }
    return localScriptName;
}


static L<Substitution> parseBodySubstitutions(String body, String dateStamp,
                                              LocalFiles localFiles) {
    /// Produces a list of substitutions sorted by start byte.
    L<Substitution> result = new L();
    int start = 0; // 6 to skip the `<body>`

    // the date stamp
    int indStampOpen = body.indexOf(stampOpen);
    if (indStampOpen == -1) { // A new doc being created
        result.add(new Substitution(start, start, dateStamp));
    } else {
        int indStampClose = body.indexOf(stampClose);
        result.add(new Substitution(indStampOpen, indStampClose + stampClose.length(), dateStamp));
    }
    var existingAttribs = parseSrcAttribs(body, "img");
    result.append(existingAttribs.trans(x ->
            new Substitution(x.startByte, x.endByte,
                    localFiles.versions.get(new UnvName(x.text)))));
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
        if (indSrc < 5)  {
            ind = html.indexOf(opener, ind + 1);
            continue; // a <script> with no src, for example JSON
        }
        int indEndSrc = html.indexOf("\"", indSrc); // 5 for the `src="`
        String attrib = html.substring(indSrc, indEndSrc);
        print("attrib:");
        print(attrib);
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
    return datePart.substring(25, 35); // Skipping length of `<div id="_dtSt">Created: `
}


void createUpdateDocs(Ingestion ing, boolean isUpdate) {
    L<CreateUpdate> cus = (isUpdate) ? ing.updateDocs : ing.createDocs;
    for (CreateUpdate cu : cus) {
        Dir targetDir = new Dir(blogDir, cu.targetDir);

        String oldContent = isUpdate ? fs.readTextFile(targetDir, "i.html") : "";
        String freshContent = buildDocument(cu, oldContent, todayDt, ing);
        fs.saveOverwriteFile(new Dir(blogDir, cu.targetDir), "i.html", freshContent);

        for (var localToDelete : cu.localFiles.filesToDelete) {
            fs.deleteIfExists(new Dir(blogDir, cu.targetDir), localToDelete);
        }
        if (cu.sourceDir != null)  { // it's null iff the update is caused by a core file change
            fs.deleteDirIfExists(new Dir(ingestDir, cu.sourceDir));
        }
    }
}


void deleteDocs(Ingestion ing) {
    for (Subfolder toDel : ing.deleteDocs) {
        fs.deleteDirIfExists(new Dir(blogDir, toDel));
    }
}


static Subfolder convertToTargetDir(Subfolder ingestDir) {
    /// "a.b.foo" -> "a/b/foo"
    String dirParts = ingestDir.cont.replace(" ", "").replace(".", "/");
    return new Subfolder(Paths.get(dirParts).toString());
}


static int getFileVersion(String fn) {
    /// `file-123.txt` -> 123
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
    L<String> withoutExts = filenames.trans(Blog::shaveOffExtension);
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
    Set<Subfolder> allSubfs = new HashSet();
    NavTree nav;
    String navPart; // the navigation JSON embedded in <head>

    public void printOut() {
        print("Ingestion constructor, count of create " + createDocs.size()
                + ", updateDocs count = " + updateDocs.size() + ", deleteDocs count = "
                + deleteDocs.size() + ", allDirs = " + allSubfs.size());
    }

    public void finalize() {
        this.nav = buildThematic();
        this.navPart = this.nav.toJson();
    }

    private NavTree buildThematic() {
        L<Doc> allDocs = new L();
        for (var a : allSubfs) {
            allDocs.add(new Doc(a)); 
        }
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
                lenSamePrefix -= 1;
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
    boolean bumpTheDate; // should we bump the updated date? we shouldn't for global file updates

    public CreateUpdate(Subfolder sourceDir, Subfolder targetDir, LocalFiles localFiles,
            boolean bumpTheDate)  {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.newContent = "";
        this.localFiles = localFiles;
        this.bumpTheDate = bumpTheDate;
    }

    public CreateUpdate(Subfolder sourceDir, Subfolder targetDir, LocalFiles localFiles,
                        String newContent)  {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.newContent = newContent;
        this.localFiles = localFiles;
        this.bumpTheDate = true; 
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

static final String templateHtmlStart = """
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Security-Policy"
          content="default-src 'self'; script-src 'self'; base-uri 'self';" />
    <link rel="icon" type="image/x-icon" href="/blog/favicon.ico"/>
""";


static final String templateBodyStart = """
<body>
<div class="_wrapper">
<div class="_navbar" id="_theNavBar">
    <div class="_menuTop">
        <div class="_svgButton" title="Toggle theme">
            <a id="_themeToggle" title="Toggle theme">
                <svg id="_themeToggler" class="_swell" width="30" height="30" viewBox="0 0 100 100">
                    <circle r="48" cx="50" cy="50" />
                    <path d="M 35 20 h 30 c 0 30 -30 30 -30 60 h 30
                             c 0 -30 -30 -30 -30 -60" />
                </svg>
            </a>
        </div>
        <div class="_svgButton">
            <a href="http://sozonov.site" title="Home page">
                <svg id="_homeIcon" class="_swell" width="30" height="30" viewBox="0 0 100 100">
                    <circle r="48" cx="50" cy="50" />
                    <path d="M 30 45 h 40 v 25 h -40 v -25 " />
                    <path d="M 22 50 l 28 -25 l 28 25" />
                </svg>
            </a>
        </div>
    </div>
    <div class="_menu" id="_theMenu"></div>
</div>

<div class="_divider" id="_divider">&lt;</div>
<div class="_menuToggler _hidden" id="_menuToggler">
    <div class="_svgButton" title="Open menu">
        <a id="_toggleNavBar">
            <svg class="_swell" width="30" height="30" viewBox="0 0 100 100">
                <circle r="48" cx="50" cy="50"></circle>
                <path d="M 30 35 h 40" stroke-width="6"></path>
                <path d="M 30 50 h 40" stroke-width="6"></path>
                <path d="M 30 65 h 40" stroke-width="6"></path>
            </svg>
        </a>
    </div>
</div>

<div id="_content">
""";



static final String templateEnd = """
<div class="_footer">© Egorr Sozonov | <a href="https://sozonov.site">Home</a> |
    <a href="/blog/termsOfUse.html">Terms of use</a>
</div>
</div>
</div>
</body>
</html>
""";

//}}}
//{{{ Utils
//{{{ Println

static <T> void printNoLn(T t) {
    System.out.print(t);
}

static <T> void print(T t) {
    System.out.println(t);
}

//}}}
//{{{ List

static final class L<T> implements List<T> {
    private static final int DEFAULT_CAPACITY = 10;
    private int size;
    private int modCount = 0;
    private T[] data;

    public L(int initCap) {
        if (initCap < 0) {
            throw new RuntimeException();
        }
        data = (T[]) new Object[initCap];
    }

    public L() {
        this(4);
    }

    public static <T> L<T> of(T... values)  {
        L<T> result = new L(values.length);
        for (int i = 0; i < values.length; i++) {
            result.data[i] = values[i];
        }
        result.size = values.length;
        return result;
    }

    public int size()  {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean nonEmpty()  {
        return size > 0;
    }

    public int indexOf(Object e) {
        return -1;
    }

    public int lastIndexOf(Object e) {
        return -1;
    }

    public Object[] toArray() {
        T[] array = (T[]) new Object[size];
        System.arraycopy(data, 0, array, 0, size);
        return array;
    }

    public <T> T[] toArray(T[] a) {
        if (a.length < size) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
        } else if (a.length > size) {
            a[size] = null;
        }
        System.arraycopy(data, 0, a, 0, size);
        return a;
    }

    public T get(int index) {
        checkBoundExclusive(index);
        return data[index];
    }

    public T set(int index, T e) {
        checkBoundExclusive(index);
        T result = data[index];
        data[index] = e;
        return result;
    }

    public boolean add(T e) {
        modCount++;
        if (size == data.length) {
            ensureCapacity(size + 1);
        }
        data[size++] = e;
        return true;
    }

    public void add(int index, T e) {
        checkBoundInclusive(index);
        modCount++;
        if (size == data.length) { ensureCapacity(size + 1); }
        if (index != size) { System.arraycopy(data, index, data, index + 1, size - index); }
        data[index] = e;
        size++;
    }

    public T remove(int index) {
        checkBoundExclusive(index);
        T r = data[index];
        modCount++;
        --size;
        if (index != size) { System.arraycopy(data, index + 1, data, index, size - index); }
        // Aid for garbage collection by releasing this pointer.
        data[size] = null;
        return r;
    }

    public void removeLast() {
        checkBoundExclusive(size - 1);
        ++modCount;
        --size;
        data[size] = null;
    }

    public void clear() {
        if (size == 0) {
            return;
        }
        modCount++;
        // Allow for garbage collection.
        Arrays.fill(data, 0, size, null);
        size = 0;
    }

    public void ensureCapacity(int minCapacity) {
        int current = data.length;

        if (minCapacity > current) {
            T[] newData = (T[]) new Object[Math.max(current * 2, minCapacity)];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
    }

    public boolean addAll(Collection<? extends T> c) {
        return addAll(size, c);
    }

    public boolean addAll(int index, Collection<? extends T> c) {
        checkBoundInclusive(index);
        Iterator<? extends T> itr = c.iterator();
        int csize = c.size();

        modCount++;
        if (csize + size > data.length) { ensureCapacity(size + csize); }
        int end = index + csize;
        if (size > 0 && index != size) { System.arraycopy(data, index, data, end, size - index); }
        size += csize;
        for ( ; index < end; index++) {
            data[index] = itr.next();
        }
        return csize > 0;
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return null;
    }

    @Override
    public ListIterator<T> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<T> listIterator(final int i) {
        return new LIterator<T>(this);
    }

    @Override
    public Iterator<T> iterator() {
        return listIterator();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    public void removeRange(int fromIndex, int toIndex) {
        int change = toIndex - fromIndex;
        if (change > 0) {
            modCount++;
            System.arraycopy(data, toIndex, data, fromIndex, size - toIndex);
            size -= change;
        } else if (change < 0) {
            throw new RuntimeException();
        }
    }

    private void checkBoundInclusive(int index) {
        // Implementation note: we do not check for negative ranges here, since
        // use of a negative index will cause a RuntimeException,
        // a subclass of the required exception, with no effort on our part.
        if (index > size) {
            throw new RuntimeException("Index: " + index + ", Size: " + size);
        }
    }

    private void checkBoundExclusive(int index) {
        // Implementation note: we do not check for negative ranges here, since
        // use of a negative index will cause a RuntimeException,
        // a subclass of the required exception, with no effort on our part.
        if (index >= size) {
            throw new RuntimeException("Index: " + index + ", Size: " + size);
        }
    }

    boolean removeAllInternal(Collection<?> c) {
        int i;
        int j;
        for (i = 0; i < size; i++) {
            if (c.contains(data[i])) {
                break;
            }
        }
        if (i == size) {
            return false;
        }

        modCount++;
        for (j = i++; i < size; i++) {
            if (!c.contains(data[i])) {
                data[j++] = data[i];
            }
        }
        size -= i - j;
        return true;
    }

    public int findIndex(Function<T, Boolean> pred)  {
        for (int i = 0; i < size; i++) {
            if (pred.apply(data[i])) {
                return i;
            }
        }
        return -1;
    }

    public void append(L<T> other) {
        if (other.isEmpty()) {
            return;
        }
        ensureCapacity(size + other.size());
        for (int j = 0, i = size; j < other.size(); j++, i++) {
            data[i] = other.get(j);
        }
        size += other.size();
    }

    public <U> L<U> trans(Function<T, U> transformer) {
        L<U> result = new L(this.size);
        for (int i = 0; i < size; i++) {
            result.data[i] = transformer.apply(data[i]);
        }
        result.size = this.size;
        return result;
    }

    public <U> L<U> transIf(Predicate<T> pred, Function<T, U> transformer) {
        L<U> result = new L(this.size);
        int j = 0;
        for (int i = 0; i < size; i++) {
            T elt = data[i];
            if (pred.test(elt)) {
                result.data[j] = transformer.apply(elt);
                j++;
            }
        }
        result.size = j;
        return result;
    }

    public L<T> filter(Predicate<T> pred) {
        L<T> result = new L();
        int j = 0;
        for (int i = 0; i < size; i++) {
            T elt = data[i];
            if (pred.test(elt)) {
                result.add(elt);
                j++;
            }
        }
        return result;
    }

    public Optional<T> first(Predicate<T> pred) {
        /// Find index of first element satisfying predicate.
        /// The method missing from the Java streams
        for (int i = 0; i < size; i++) {
            if (pred.test(data[i])) {
                return Optional.of(data[i]);
            }
        }
        return Optional.empty();
    }

    public boolean any(Predicate<T> pred) {
        /// Find index of first element satisfying predicate.
        /// The method missing from the Java streams
        for (int i = 0; i < size; i++) {
            if (pred.test(data[i])) {
                return true;
            }
        }
        return false;
    }

    public T last() {
        /// Returns the last element. Assumes the list is non-empty
        return data[size - 1];
    }


    public Set<T> toSet() {
        Set<T> result = new HashSet<T>();
        for (int i = 0; i < size; ++i) {
            result.add(data[i]);
        }
        return result;
    }


    public static class LIterator<T> implements ListIterator<T> {
        private int i;
        private int size;
        private L<T> lst;
        private int expectedModCount;

        public LIterator(L<T> lst) {
            this.i = -1;
            this.size = lst.size;
            this.lst = lst;
            expectedModCount = lst.modCount;
        }

        public boolean hasNext() {
            return i < size - 1;
        }

        public T next() {
            if (i < size - 1) {
                i++;
                return lst.data[i];
            } else {
                throw new RuntimeException();
            }
        }

        public boolean hasPrevious() {
            return i > 0;
        }

        public T previous() {
            if (i > 0) {
                i--;
                return lst.data[i];
            } else {
                throw new RuntimeException();
            }
        }

        public int nextIndex() {
            return i + 1;
        }

        public int previousIndex() {
            return i - 1;
        }

        public void remove() {
            lst.remove(i);
            lst.modCount++;
        }

        public void set(T e) {
            lst.set(i, e);
        }

        public void add(T e) {
            lst.add(e);
            expectedModCount++;
        }

        void checkForComodification() {
            if (lst.modCount != expectedModCount) {
                throw new RuntimeException();
            }
        }
    }
}

//}}}
//{{{ Tu

static class Tu<F, S> {
    public F f1;
    public S f2;
    public Tu(F f, S s) {
        f1 = f;
        f2 = s;
    }
}

//}}}
//{{{ Misc

static String shaveOffExtension(String fN) {
    int indDot = fN.indexOf(".");
    if (indDot < 0) {
        return fN;
    }
    return fN.substring(0, indDot);
}


static Optional<Integer> parseInt(String s) {
    try {
        return Optional.of(Integer.parseInt(s));
    } catch(Exception e) {
        return Optional.empty();
    }
}


static final String[] months = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "July", "Aug", "Sep", "Oct", "Nov", "Dec"
};


static String monthNameOf(String dt) {
    /// "2023-04-01" -> "Apr"
    int monthNum = Integer.parseInt(dt.substring(5, 7));
    return months[monthNum - 1];
}


static void writeCommaSeparatedToBuffer(L<Integer> ints, StringBuilder wr) {
    if (ints.size() == 0) {
        return;
    }
    wr.append(ints.get(0));
    for (int i = 1; i < ints.size(); i++) {
        wr.append(",");
        wr.append(ints.get(i));
    }
}


static L<String> getNamesWithPrefix(UnvName fn, L<FileInfo> existingFiles) {
    /// fN should be full (with extension)
    String fnWoExt = shaveOffExtension(fn.cont) + "-";
    String ext = fn.cont.substring(fnWoExt.length() - 1);
    return existingFiles
            .transIf(x -> x.name.equals(fn.cont)
                            || (x.name.startsWith(fnWoExt) && x.name.endsWith(ext)),
                    y -> y.name);
}

static <T> int indexOf(T[] haystack, T needle)  {
    for (int i = 0; i < haystack.length; i++) {
        if (haystack[i].equals(needle)) {
            return i;
        }
    }
    return -1;
}

//}}}
//{{{ Directories and files

public static class Dir {
    public String cont;

    public Dir(Dir absDir, Subfolder subf)  {
        this.cont = Paths.get(absDir.cont, subf.cont).toString();
    }

    private Dir(String s)  {
        this.cont = Paths.get(s).toString();
    }

    public static Dir ofString(String absPath)  {
        if (!absPath.startsWith("/")) {
            throw new RuntimeException("An absolute path must start with `/`!");
        }
        return new Dir(absPath);
    }
}


public static class Subfolder {
    public String cont;
    public Subfolder(String cont) {
        this.cont = cont;
    }

    @Override
    public boolean equals(Object o) {
        return this.cont.equals(((Subfolder)o).cont);
    }

    @Override
    public int hashCode() {
        return this.cont.hashCode();
    }
}

static class UnvName {
    /// An unversioned file name, so if the whole name is "asdf-11.jpg", this will be "asdf.jpg"
    String cont;
    UnvName(String fn) {
        String withoutExt = shaveOffExtension(fn);
        int indDash = withoutExt.lastIndexOf("-");
        if (indDash < 0) {
            this.cont = fn;
            return;
        }
        this.cont = withoutExt.substring(0, indDash) + fn.substring(withoutExt.length());
    }

    String toVersion(int n) {
        String withoutExt = shaveOffExtension(this.cont);
        return withoutExt
                + "-" + Integer.toString(n) + this.cont.substring(withoutExt.length() + 1);
    }

    @Override
    public boolean equals(Object o) {
        return this.cont.equals(((UnvName)o).cont);
    }

    @Override
    public int hashCode() {
        return this.cont.hashCode();
    }
}

//}}}
//}}}
//{{{ Filesys

static class FileInfo {
    String name;

    public FileInfo(String name)  {
        this.name = name;
    }
}

interface FileSys {
    boolean dirExists(Dir dir);
    L<FileInfo> listFiles(Dir dir); // immediate files in a dir
    L<Subfolder> listSubfolders(Dir dir); // immediate subfolders of a directory
    L<Subfolder> listSubfoldersContaining(Dir dir, String fN); // recursively list all nested dirs
    String readTextFile(Dir dir, String fN);
    boolean saveOverwriteFile(Dir dir, String fN, String cont);
    boolean moveFileWithRename(Dir dir, String fN, Dir targetDir, String newName);
    boolean deleteIfExists(Dir dir, String fN);
    boolean deleteDirIfExists(Dir dir);
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
//{{{ FileSys implementation

static class BlogFileSys implements FileSys {
    public BlogFileSys() {
    }

    @Override
    public boolean dirExists(Dir dir) {
        return Files.isDirectory(Paths.get(dir.cont));
    }

    @Override
    public L<FileInfo> listFiles(Dir dir) {
        var result = new L();
        var files0 = new File(dir.cont).listFiles();
        if (files0 == null) {
            return result;
        }
        var files = Stream.of(files0)
                .filter(file -> !file.isDirectory())
                .map(x -> new FileInfo(x.getName())).toList();
        result.addAll(files);
        return result;
    }

    @Override
    public L<Subfolder> listSubfolders(Dir dir) {
        /// Immediate but full subfolders of a dir, like `a/b/c`
        L<Subfolder> result = new L();
        String prefixWithSl = dir.cont.endsWith("/") ? dir.cont : dir.cont + "/";

        Path thePath = Paths.get(dir.cont);
        try (Stream<Path> paths = Files.walk(thePath)) {
            var allPaths = paths.toList();
            for (Path pt : allPaths) {
                if (Files.isDirectory(pt) && pt.toString().length() > prefixWithSl.length()) {
                    print(pt.toString());
                    result.add(new Subfolder(pt.toString().substring(prefixWithSl.length())));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public L<Subfolder> listSubfoldersContaining(Dir dir, String fn) {
        //! Gets the list of subfolders containing a filename, for example "i.html"
        L<Subfolder> result = new L();
        String prefixWithSl = dir.cont.endsWith("/") ? dir.cont : dir.cont + "/";
        try (Stream<Path> paths = Files.walk(Paths.get(dir.cont))) {
            var allPaths = paths.toList();
            for (Path pt : allPaths) {
                if (Files.isDirectory(pt)
                        && pt.toString().length() > prefixWithSl.length()
                        && (new File(Paths.get(pt.toString(), fn).toString())).exists()) {
                    result.add(new Subfolder(pt.toString().substring(prefixWithSl.length())));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public String readTextFile(Dir dir, String fn) {
        Path thePath = Paths.get(dir.cont, fn);
        File theFile = new File(thePath.toString());
        if (!theFile.exists()) {
            return "";
        }
        try {
            return Files.readString(thePath);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public boolean saveOverwriteFile(Dir dir, String fn, String cont) {
        Path targetOsPath = tryCreateMissingDir(dir);
        if (!Files.isDirectory(targetOsPath)) {
            return false;
        }
        Path targetPath = Paths.get(dir.cont, fn);
        File targetFile = new File(targetPath.toString());
        if (targetFile.exists()) {
            if (targetFile.isDirectory()) {
                return false;
            }
            targetFile.delete();
        }
        try {
            Files.write(targetPath, cont.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return true;
    }


    private Path tryCreateMissingDir(Dir dir)  {
        Path targetOsPath = Paths.get(dir.cont);
        File targetOsDir = new File(targetOsPath.toString());
        if (!targetOsDir.exists()) {
            try  {
                Files.createDirectories(targetOsPath);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return targetOsPath;
    }


    @Override
    public boolean moveFileWithRename(Dir dir, String fn, Dir targetDir, String newName) {
        /// Source file and target dir must exist
        Path sourcePath = Paths.get(dir.cont, fn);
        File sourceFile = new File(sourcePath.toString());
        if (!sourceFile.exists() || Files.isDirectory(sourcePath)) {
            return false;
        }
        Path targetOsPath = Paths.get(targetDir.cont);
        File targetOsDir = new File(targetOsPath.toString());
        tryCreateMissingDir(targetDir);
        if (!targetOsDir.exists() || !Files.isDirectory(targetOsPath)) {
            print("p2");
            return false;
        }
        Path targetPath = Paths.get(targetDir.cont, newName);
        File targetFile = new File(targetPath.toString());
        if (targetFile.exists()) {
            if (targetFile.isDirectory()) {
                print("p3");
                return false;
            }
            targetFile.delete();
        }
        try {
            Files.move(sourcePath, targetPath);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return true;
    }

    @Override
    public boolean deleteIfExists(Dir dir, String fn) {
        Path thePath = Paths.get(dir.cont, fn);
        File theFile = new File(thePath.toString());
        if (!theFile.exists() || Files.isDirectory(thePath)) {
            return false;
        }
        theFile.delete();
        return true;
    }

    @Override
    public boolean deleteDirIfExists(Dir dir) {
        /// Deletes a dir with all its contents and subfolders
        Path thePathToDelete = Paths.get(dir.cont);
        var theFolder = new File(dir.cont);
        if (!theFolder.exists() || !Files.isDirectory(thePathToDelete)) {
            return false;
        }
        try {
            Files.walk(thePathToDelete)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return true;
    }
}

//}}}
//{{{ Main

public static void main(String[] args) {
    Blog blog = new Blog(new BlogFileSys());
    blog.run();
}

//}}}
}
