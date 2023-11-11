package tech.sozonov.blog;
//{{{ Imports

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Collection;
import java.util.Optional;
import java.time.Instant;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.function.Function;
import java.lang.reflect.Array;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import static tech.sozonov.blog.Blog.*;

//}}}

class Test {

//{{{ MockFileSys

static class MockFileSys implements FileSys {
    private Map<String, L<MockFile>> fs = new HashMap<String, L<MockFile>>();

    @Override
    public boolean dirExists(Dir dir) {
        return fs.containsKey(dir.cont);
    }

    @Override
    public L<FileInfo> listFiles(Dir dir) {
        if (!fs.containsKey(dir.cont))    {
            return new L();
        }
        return fs.get(dir.cont).trans(x -> new FileInfo(x.name));
    }

    @Override
    public L<Subfolder> listSubfolders(Dir dir) {
        String dirWithSl = (dir.cont.endsWith("/")) ? dir.cont : (dir.cont + "/");
        int prefixLength = dirWithSl.length();
        L<Subfolder> result = new L();
        for (String key : fs.keySet()) {
            if (key.startsWith(dirWithSl)) {
                result.add(new Subfolder(key.substring(prefixLength)));
            }
        }
        return result;
    }

    @Override
    public L<Subfolder> listSubfoldersContaining(Dir dir, String fN) {
        /// Gets the list of directories containing a filename, for example "i.html"
        String dirWithSl = (dir.cont.endsWith("/")) ? dir.cont : (dir.cont + "/");
        int prefixLength = dirWithSl.length();
        L<Subfolder> result = new L(10);
        for (var e : fs.entrySet()) {
            if (e.getKey().startsWith(dirWithSl)
                    && e.getValue().any(x -> x.name.equals("i.html"))) {
                result.add(new Subfolder(e.getKey().substring(prefixLength)));
            }
        }
        return result;
    }

    @Override
    public String readTextFile(Dir dir, String fN) {
        if (!fs.containsKey(dir.cont)) {
            return "";
        }
        return fs.get(dir.cont).first(x -> x.name.equals(fN)).map(x -> x.cont).orElse("");
    }

    @Override
    public boolean saveOverwriteFile(Dir dir, String fN, String cont) {
        var newFile = new MockFile(fN, cont, Instant.now());
        if (fs.containsKey(dir.cont)) {
            var existingFiles = fs.get(dir.cont);
            var indexExisting = existingFiles.findIndex(x -> x.name.equals(fN));
            if (indexExisting == -1) {
                existingFiles.add(newFile);
            } else {
                existingFiles.set(indexExisting, newFile);
            }
        } else {
            fs.put(dir.cont, L.of(newFile));
        }
        return true;
    }


    @Override
    public boolean moveFileWithRename(Dir dir, String fN, Dir targetDir, String newName) {
        var sourceFiles = fs.get(dir.cont);
        int indexSource = sourceFiles.findIndex(x -> x.name.equals(fN));
        MockFile theFile = sourceFiles.get(indexSource);
        theFile.name = newName;
        L<MockFile> targetFiles = fs.get(targetDir.cont);
        sourceFiles.remove(indexSource);
        if (targetFiles == null) {
            fs.put(targetDir.cont, L.of(theFile));
            return true;
        }

        var existingInd = targetFiles.indexOf(newName);
        if (existingInd < 0) {
            targetFiles.add(theFile);
        } else {
            targetFiles.set(existingInd, theFile);
        }

        return true;
    }

    @Override
    public boolean deleteIfExists(Dir dir, String fN) {
        if (!fs.containsKey(dir.cont)) {
            return false;
        }
        var existingFiles = fs.get(dir.cont);
        int indexExisting = existingFiles.findIndex(x -> x.name.equals(fN));
        if (indexExisting > -1) {
            existingFiles.remove(indexExisting);
            return true;
        }
        return false;
    }

    @Override
    public boolean deleteDirIfExists(Dir dir) {
        /// Deletes a dir with all its contents and subfolders
        for (String dirName : fs.keySet()) {
            if (dirName.startsWith(dir.cont)) {
                fs.remove(dirName);
                return true;
            }
        }
        return false;
    }
}


static class MockFile {
    String name;
    String cont;
    Instant modified;

    public MockFile(String name, String cont, Instant modified)  {
        this.name = name;
        this.cont = cont;
        this.modified = modified;
    }
}

//}}}
//{{{ Utils
//{{{ Action

@FunctionalInterface
interface Action {
    void run();
}

//}}}

static void blAssert(boolean predicate) {
    if (!predicate) {
        String methodName = getFuncName(3);
        throw new RuntimeException(methodName);
    }
}

static String getFuncName(int depth) {
    StackWalker walker = StackWalker.getInstance();
    var frames = walker.walk(frameStream -> frameStream
            .limit(depth)
            .toList());
    return frames.get(frames.size() - 1).getMethodName();
}


static void assertArrsEqual(L<String> a, L<String> b) {
    if(a.size() != b.size()) {
        throw new RuntimeException("Arrays have different lengths");
    } else if (a.size() == 0) {
        return;
    }
    Collections.sort(a);
    Collections.sort(b);
    for(int i = 0; i < a.size(); i++) {
        if(!a.get(i).equals(b.get(i))) {
            throw new RuntimeException(
                    "Arrays differ at " + i + ", \"" + a.get(i) + "\" vs \"" + b.get(i) + "\"");
        }
    }
}


static boolean isFixedCore(String fN) {
    for (int i = 0; i < fixedCoreFiles.length; i++) {
        if (fixedCoreFiles[i].equals(fN)) {
            return true;
        }
    }
    return false;
}


static void printIngestion(Ingestion ing) {
    /// Print the full structure for testing purposes


}

//~static String stringDiff(String a, String b) {
//~
//~}

static void runTest(Action theTest, TestResults counters) {
    try {
        theTest.run();
    } catch (Exception e) {
        System.out.println("Failed: " + e.getMessage());
        e.printStackTrace();
        counters.countFailed++;
    }
    counters.countRun++;
}

static <X> void print(X x) { // No relation to the band Static-X
    System.out.println(x);
}

static <X> int findIndex(X[] arr, Predicate<X> pred) {
    for (int i = 0; i < arr.length; i++) {
        if (pred.test(arr[i])) {
            return i;
        }
    }
    return -1;
}


//}}}
//{{{ Tests

//{{{ Internal tests

static class TestResults {
    int countRun;
    int countFailed;
    TestResults()  {
        countRun = 0;
        countFailed = 0;
    }
}

static void testSaveFile() {
    FileSys fs = new MockFileSys();
    Subfolder subf = new Subfolder("a/b");
    Dir target = new Dir(blogDir, subf);
    boolean resSave = fs.saveOverwriteFile(target, "myFile.txt", "An ode to joy");
    blAssert(resSave);
}


static void testFilePrefixes() {
    FileSys fs = new MockFileSys();
    Dir target = new Dir(blogDir, new Subfolder("a/b"));
    fs.saveOverwriteFile(target, "myFile.txt", "An ode to joy");
    fs.saveOverwriteFile(target, "myFile-2.txt", "An ode to joy 2");
    fs.saveOverwriteFile(target, "myFile-3.txt", "An ode to joy 3");
    var versions = getNamesWithPrefix(new UnvName("myFile"), fs.listFiles(target));
    assertArrsEqual(versions, L.of("myFile.txt", "myFile-2.txt", "myFile-3.txt"));
}

static void testMaxVersion() {
    var mv = getNameWithMaxVersion(L.of("file.txt", "file-2.txt", "file-3.txt"));
    blAssert(mv.f1.equals("file-3.txt"));
    blAssert(mv.f2 == 3);
}

//}}}

static void ingestCore() {
    /// First ingestion of fixed core files
    var fs = new MockFileSys();
    Dir inDir = ingestDir;
    fs.saveOverwriteFile(inDir, "script.js", "core script");
    fs.saveOverwriteFile(inDir, "style.css", "core styles");
    Blog b = new Blog(fs);

    b.ingestCore();

    var coreNames = fs.listFiles(blogDir).trans(x -> x.name);
    assertArrsEqual(coreNames, L.of("script.js", "style.css"));
}


static void updateCore() {
    /// Update of a fixed core file to a new version
    var fs = new MockFileSys();
    Dir inDir = ingestDir;
    print("saving to dir " + inDir);
    fs.saveOverwriteFile(blogDir, "termsOfUse.html", "Terms of Use");
    fs.saveOverwriteFile(inDir, "img404.png", "img");
    fs.saveOverwriteFile(inDir, "termsOfUse.html", "New terms of use");
    Blog b = new Blog(fs);

    b.ingestCore();

    var coreNames = fs.listFiles(blogDir).trans(x -> x.name);
    assertArrsEqual(coreNames,
            L.of("termsOfUse.html", "termsOfUse-2.html", "img404.png"));
    int indInFixed = -1;
    for (int i = 0; i < fixedCoreFiles.length; i++) {
        if (fixedCoreFiles[i].equals("termsOfUse.html")) {
            indInFixed = i;
            break;
        }
    }
    blAssert(b.coreVersions[indInFixed].equals("termsOfUse-2.html"));
}

static void parseDateStamp() {
    String input =
        "<!-- Dates --><div id=\"_dtSt\">Created: 2023-04-05, updated: 2023-04-06</div><!-- / -->";
    String dateOld = Blog.parseCreatedDate(input);
    blAssert(dateOld.equals("2023-04-05"));
}

static void parseContentTest() {
    String newInput = "<html><head></head><body>expected</body></html>";
    String extracted = Blog.extractContent(newInput, false);
    blAssert(extracted.equals("expected"));
    String oldInput = "<html><head></head><body><div>"
            + Blog.contentStartMarker + "expected" + Blog.contentEndMarker
            + "</div></body></html>";
    extracted = Blog.extractContent(oldInput, true);
    blAssert(extracted.equals("expected"));

}

static void createSimpleDocForTest(FileSys fs, Dir docDir) {
    fs.saveOverwriteFile(blogDir, "termsOfUse.html", "Terms of Use");
    fs.saveOverwriteFile(blogDir, "script.js", "Terms of Use");
    fs.saveOverwriteFile(blogDir, "style.css", "Terms of Use");
    fs.saveOverwriteFile(blogDir, "blog.html", "Terms of Use");
    String inputContent = """
<html>
<head>
    <script type="text/javascript" src="local.js"></script>
</head>
<body>
    <div>Hello world!</div><img src="myImg.png">
</body>
</html>""";

    fs.saveOverwriteFile(docDir, "myImg.png", "Some image content");
    fs.saveOverwriteFile(docDir, "local.js", "Local script");
    fs.saveOverwriteFile(docDir, "i.html", inputContent);
}

static void makeNameBumpedVersion() {
    var result = Blog.makeNameBumpedVersion(new UnvName("local.js"),
            L.of(new FileInfo("asdf.png"),
                 new FileInfo("local.js"),
                 new FileInfo("local-2.js"),
                 new FileInfo("local-3.js")));
    blAssert(result.equals("local-4.js"));
}

static void unvName() {
    var result = new UnvName("asdf-2.jpg");
    blAssert(result.cont.equals("asdf.jpg"));
}


static void moveAndReadLocalFilesTest() {
    var fs = new MockFileSys();
    Blog blog = new Blog(fs);
    Dir sourceDir = new Dir(ingestDir, new Subfolder("a.b"));
    Dir targetDir = new Dir(blogDir, new Subfolder("a/b"));
    fs.saveOverwriteFile(targetDir, "a.txt", "old");
    fs.saveOverwriteFile(targetDir, "b.txt", "old");
    fs.saveOverwriteFile(sourceDir, "b.txt", "new");
    fs.saveOverwriteFile(sourceDir, "c.txt", "new");

    LocalFiles local = blog.moveAndReadLocalFiles(fs.listFiles(sourceDir), sourceDir, targetDir);

    blAssert(local.versions.size() == 3
            && local.versions.get(new UnvName("a.txt")).equals("a.txt") // unchanged old
            && local.versions.get(new UnvName("b.txt")).equals("b-2.txt") // updated file
            && local.versions.get(new UnvName("c.txt")).equals("c.txt") // new file
    );
    blAssert(local.filesToDelete.size() == 1
            && local.filesToDelete.get(0).equals("b.txt"));
}


static void createNewDoc() {
    /// With core files in place, create a simple first doc
    var fs = new MockFileSys();
    Blog b = new Blog(fs);
    Dir docDir = new Dir(ingestDir, new Subfolder("a.b.c"));
    createSimpleDocForTest(fs, docDir);
    createSimpleDocForTest(fs, new Dir(ingestDir, new Subfolder("other.d")));

    b.ingestDocs();

    String nowStamp = formatter.format(Instant.now());
    String expectedContent = """
<!-- Dates --><div id="_dtSt">Created:""" + " " + nowStamp + ", updated: " + nowStamp + """
</div><!-- / -->
    <div>Hello world!</div><img src="myImg.png">
""";

    Dir pathNewDoc = new Dir(blogDir, new Subfolder("a/b/c"));
    L<FileInfo> result = fs.listFiles(pathNewDoc);
    String fullFile = fs.readTextFile(pathNewDoc, "i.html");
    String cont = Blog.extractContent(fullFile, true);
    blAssert(cont.equals(expectedContent));
}


static void updateDoc() {
    /// Update a document with new content and new versions of local script and image
    var fs = new MockFileSys();
    Blog b = new Blog(fs);

    Dir pathExistingDoc = new Dir(blogDir, new Subfolder("a/b/c"));
    fs.saveOverwriteFile(pathExistingDoc, "local-2.js", "old local script");
    fs.saveOverwriteFile(pathExistingDoc, "myImg.png", "old image");
    fs.saveOverwriteFile(pathExistingDoc, "i.html", """
        <head></head>
            <body>
            <div id="_content">
                <!-- Dates --><div id="_dtSt">Created: 2023-04-05</div><!-- / -->
                Old content
            </div>
            <!-- _contentEnd -->
            </body></html>
        """);
    Dir docDir = new Dir(ingestDir, new Subfolder("a.b.c"));
    createSimpleDocForTest(fs, docDir);
    fs.saveOverwriteFile(docDir, "local.js", "new local script");
    fs.saveOverwriteFile(docDir, "myImg.png", "new image");

    b.ingestDocs();

    String nowStamp = formatter.format(Instant.now());
    String expectedContent = """
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Security-Policy"
        content="default-src 'self'; script-src 'self'; base-uri 'self';" />
    <script type="text/javascript" src="/blog/script.js"></script>
    <script type="text/javascript" src="local-3.js"></script>
    <link rel="stylesheet" href="/blog/style.css" />
</head>
<body><!-- Dates --><div id="_dtSt">Created: 2023-04-05, updated: """ + nowStamp + """
</div><!-- / -->
    <div>Hello world!</div><img src="myImg-2.png">
</body>
</html>""";

    L<FileInfo> result = fs.listFiles(pathExistingDoc);
    String cont = fs.readTextFile(pathExistingDoc, "i.html");
    print("content:");
    print("");
    print(cont);
    blAssert(cont.equals(expectedContent));
}

public static void main(String[] args) {
    TestResults counters = new TestResults();

//~    runTest(Test::testSaveFile, counters);
//~    runTest(Test::testFilePrefixes, counters);
//~    runTest(Test::testMaxVersion, counters);

//~    runTest(Test::testIngestCore, counters);
//~    runTest(Test::updateCore, counters);
//~    runTest(Test::parseDateStamp, counters);

//~    runTest(Test::makeNameBumpedVersion, counters);
//~    runTest(Test::unvName, counters);

//~    runTest(Test::moveAndReadLocalFilesTest, counters);
//~    runTest(Test::parseContentTest, counters);
//~    runTest(Test::createNewDoc, counters);
    runTest(Test::updateDoc, counters);

    if (counters.countFailed > 0)  {
        System.out.println("Failed " + counters.countFailed + " tests");
    } else {
        System.out.println("Successfully run " + counters.countRun + " tests");
    }
}



//}}}

}
