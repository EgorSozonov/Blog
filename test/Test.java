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
import static tech.sozonov.blog.Utils.*;
import static tech.sozonov.blog.Blog.*;

//}}}

class Test {

static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
        return fs.get(dir.cont).trans(x -> new FileInfo(x.name, x.modified));
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
            if (e.getKey().startsWith(dir.cont) && e.getValue().any(x -> x.name.equals("i.html"))) {
                result.add(new Subfolder(e.getKey().substring(prefixLength)));
            }
        }
        return result;
    }

    @Override
    public L<String> getNamesWithPrefix(Dir dir, String prefix) {
        if (!fs.containsKey(dir.cont)) {
            return new L();
        }
        var existingFiles = fs.get(dir.cont);
        return existingFiles.transIf(x -> x.name.startsWith(prefix), y -> y.name);
    }

    @Override
    public String readTextFile(Dir dir, String fN) {
        if (!fs.containsKey(dir.cont)) {
            return "";
        }
        return fs.get(dir.cont).first(x -> x.name.equals(fN)).map(x -> x.cont).orElse("");
    }

    @Override
    public boolean createDir(Dir dir) {
        if (fs.containsKey(dir.cont)) {
            return true;
        }
        fs.put(dir.cont, new L());
        return true;
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
    public boolean moveFile(Dir dir, String fN, Dir targetDir) {
        var sourceFiles = fs.get(dir.cont);
        int indexSource = sourceFiles.findIndex(x -> x.name.equals(fN));
        MockFile sourceFile = sourceFiles.get(indexSource);
        L<MockFile> targetFiles = fs.get(targetDir.cont);

        var existingInd = targetFiles.indexOf(fN);
        String newName = fN;
        if (existingInd < 0) {
            targetFiles.add(sourceFile);
        } else {
            targetFiles.set(existingInd, sourceFile);
        }
        sourceFiles.remove(indexSource);
        return true;
    }

    @Override
    public String moveFileToNewVersion(Dir dir, String fN, Dir targetDir) {
        var sourceFiles = fs.get(dir.cont);
        int indexSource = sourceFiles.findIndex(x -> x.name.equals(fN));
        MockFile sourceFile = sourceFiles.get(indexSource);

        createDir(targetDir);
        var targetFiles = fs.get(targetDir.cont);

        var existingWithThisPrefix = getNamesWithPrefix(targetDir, shaveOffExtension(fN));
        String newName = fN;
        if (existingWithThisPrefix.size() == 0) {
            targetFiles.add(sourceFile);
        } else {
            newName = makeNameBumpedVersion(fN, existingWithThisPrefix);
            targetFiles.add(new MockFile(newName, sourceFile.cont, sourceFile.modified));
        }
        sourceFiles.remove(indexSource);
        return newName;
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
    var versions = fs.getNamesWithPrefix(target, "myFile");
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
    String input = "<!-- Dates --><div>Created: 2023-04-05, updated: 2023-04-06</div><!-- / -->";
    String dateOld = Blog.parseCreatedDate(input);
    blAssert(dateOld.equals("2023-04-05"));
}

static void createNewDoc() {
    /// With core files in place, create a simple first doc
    var fs = new MockFileSys();
    Blog b = new Blog(fs);
    Dir inDir = ingestDir;
    fs.saveOverwriteFile(blogDir, "termsOfUse.html", "Terms of Use");
    fs.saveOverwriteFile(blogDir, "script.js", "Terms of Use");
    fs.saveOverwriteFile(blogDir, "style.css", "Terms of Use");
    fs.saveOverwriteFile(blogDir, "blog.html", "Terms of Use");
    fs.saveOverwriteFile(new Dir(inDir, new Subfolder("a.b.c")), "i.html", """
<html>
<head>
</head>
<body>
    <div>Hello world!</div>
</body>
</html>
    """);

    b.ingestDocs();
    
    String expectedContent = """
<html>
<head>
    <style "style.css">
    <script "script.js">
</head>
<body>
<!-- Dates --><div>Created: 2023-04-05, updated: 2023-04-06</div><!-- / -->
    <div>Hello world!</div>
</body>
</html>
    """;

    Dir pathNewDoc = new Dir(blogDir, new Subfolder("a/b/c"));
    L<FileInfo> result = fs.listFiles(pathNewDoc);
    print("size " + result.size() + ", name " + result.get(0).name); 
    blAssert(result.size() == 1 && result.get(0).name.equals("i.html")); 
    String cont = fs.readTextFile(pathNewDoc, "i.html");
    print(cont); 
    blAssert(cont.equals(expectedContent));
}

public static void main(String[] args) {
    System.out.println("Hw");
    TestResults counters = new TestResults();

//~    runTest(Test::testSaveFile, counters);
//~    runTest(Test::testFilePrefixes, counters);
//~    runTest(Test::testMaxVersion, counters);
//~    runTest(Test::testIngestCore, counters);
//~    runTest(Test::updateCore, counters);
//~    runTest(Test::parseDateStamp, counters);
    runTest(Test::createNewDoc, counters);

    if (counters.countFailed > 0)  {
        System.out.println("Failed " + counters.countFailed + " tests");
    } else {
        System.out.println("Successfully run " + counters.countRun + " tests");
    }
}



//}}}

}
