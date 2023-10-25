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

//}}}

class Test {

static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

//{{{ MockFileSys

static class MockFileSys implements FileSys {
    private Map<String, L<MockFile>> fs = new HashMap<String, L<MockFile>>();

    @Override
    public boolean dirExists(String dir) {
        return fs.containsKey(dir);
    }

    @Override
    public L<FileInfo> listFiles(String dir) {
        if (!fs.containsKey(dir))    {
            return new L();
        }
        return fs.get(dir).trans(x -> new FileInfo(x.name, x.modified));
    }

    @Override
    public L<String> listDirs(String dir) {
        String dirWithSl = (dir.endsWith("/")) ? dir : (dir + "/");
        List<String> subfolders = fs.keySet().stream().filter(x -> x.startsWith(dirWithSl)).toList();
        return (L<String>)subfolders.stream().map((String x) -> {
            int indSlash = x.indexOf("/");
            return indSlash < 0 ? x : x.substring(0, indSlash);
        }).toList();
    }
    
    @Override

    public L<String> listSubfoldersContaining(String dir, String fN) {
        
    }

    @Override
    public String readTextFile(String dir, String fN) {
        if (!fs.containsKey(dir)) {
            return "";
        }
        return fs.get(dir).first(x -> x.name.equals(fN)).map(x -> x.cont).orElse("");
    }

    @Override
    L<String> getDirsContainingFile(String fN) {
        /// Gets the list of directories containing a filename, for example "i.html"
        L<String> result = new L(10);
        for (var e : fs.entrySet()) {
            if (e.getValue().any(x -> x.name.equals("i.html"))) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    @Override
    public boolean createDir(String dir) {
        if (fs.containsKey(dir)) {
            return true;
        }
        fs.put(dir, new L());
        return true;
    }

    @Override
    public boolean saveOverwriteFile(String dir, String fN, String cont) {
        var newFile = new MockFile(fN, cont, Instant.now());
        if (fs.containsKey(dir)) {
            var existingFiles = fs.get(dir);
            var indexExisting = existingFiles.findIndex(x -> x.name.equals(fN));
            if (indexExisting == -1) {
                existingFiles.add(newFile);
            } else {
                existingFiles.set(indexExisting, newFile);
            }
        } else {
            fs.put(dir, L.of(newFile));
        }
        return true;
    }

    @Override
    public boolean moveFile(String dir, String fN, String targetDir) {
        var sourceFiles = fs.get(dir);
        int indexSource = sourceFiles.findIndex(x -> x.name.equals(fN));
        MockFile sourceFile = sourceFiles.get(indexSource);
        L<MockFile> targetFiles = fs.get(targetDir);

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
    public String moveFileToNewVersion(String dir, String fN, String targetDir) {
        var sourceFiles = fs.get(dir);
        int indexSource = sourceFiles.findIndex(x -> x.name.equals(fN));
        MockFile sourceFile = sourceFiles.get(indexSource);

        createDir(targetDir);
        var targetFiles = fs.get(targetDir);

        var existingWithThisPrefix = getNamesWithPrefix(targetDir, shaveOffExtension(fN));
        String newName = fN;
        if (existingWithThisPrefix.size() == 0) {
            targetFiles.add(sourceFile);
        } else {
            int maxExistingVersion = getMaxVersion(existingWithThisPrefix);
            int indLastDot = fN.lastIndexOf(".");
            newName = fN.substring(0, indLastDot) + "-" + (maxExistingVersion + 1)
                    + fN.substring(indLastDot);
            targetFiles.add(new MockFile(newName, sourceFile.cont, sourceFile.modified));
        }
        sourceFiles.remove(indexSource);
        return newName;
    }

    @Override
    public boolean deleteIfExists(String dir, String fN) {
        if (!fs.containsKey(dir)) {
            return true;
        }
        var existingFiles = fs.get(dir);
        int indexExisting = existingFiles.findIndex(x -> x.name.equals(fN));
        if (indexExisting > -1) {
            existingFiles.remove(indexExisting);
        }
        return true;
    }

    @Override
    public L<String> getNamesWithPrefix(String dir, String prefix) {
        if (!fs.containsKey(dir)) {
            return new L();
        }
        var existingFiles = fs.get(dir);
        var tra = existingFiles.transIf(x -> x.name.startsWith(prefix), y -> y.name);
        return tra;
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

static String getIngestDir() {
    return Paths.get(ingestDir).toString();
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

static int getMaxVersion(L<String> filenames) {
    /// For a list like `file.txt, file-2.txt, file-3.txt`, returns 3.
    L<String> withoutExts = filenames.trans(Test::shaveOffExtension);
    var versions = new L();
    int result = 1;
    for (int i = 0; i < filenames.size(); i++) {
        String shortName = withoutExts.get(i);
        int indDash = shortName.lastIndexOf("-");
        if (indDash < 0 || indDash == (shortName.length() - 1)) {
            continue;
        }
        var mbNumber = parseInt(shortName.substring(indDash + 1));
        if (mbNumber.isPresent() && result < mbNumber.get()) {
            result = mbNumber.get();
        } else {
            continue;
        }
    }
    return result;
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
    boolean resSave = fs.saveOverwriteFile("a/b", "myFile.txt", "An ode to joy");
    blAssert(resSave);
}


static void testFilePrefixes() {
    FileSys fs = new MockFileSys();
    fs.saveOverwriteFile("a/b", "myFile.txt",    "An ode to joy");
    fs.saveOverwriteFile("a/b", "myFile-2.txt", "An ode to joy 2");
    fs.saveOverwriteFile("a/b", "myFile-3.txt", "An ode to joy 3");
    var versions = fs.getNamesWithPrefix("a/b", "myFile");
    assertArrsEqual(versions, L.of("myFile.txt", "myFile-2.txt", "myFile-3.txt"));
}

static void testMaxVersion() {
    int mv = getMaxVersion(L.of("file.txt", "file-2.txt", "file-3.txt"));
    blAssert(mv == 3);
}

//}}}

static void ingestCore() {
    /// First ingestion of fixed core files
    var fs = new MockFileSys();
    String inDir = getIngestDir();
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
    String inDir = getIngestDir();
    print("saving to dir " + inDir);
    fs.saveOverwriteFile(blogDir, "termsOfUse.html", "Terms of Use");
    fs.saveOverwriteFile(inDir, "img404.png", "img");
    fs.saveOverwriteFile(inDir, "termsOfUse.html", "New terms of use");
    Blog b = new Blog(fs);

    b.ingestCore();

    var coreNames = fs.listFiles(blogDir).trans(x -> x.name);
    assertArrsEqual(coreNames,
            L.of("termsOfUse.html", "termsOfUse-2.html", "img404.png"));
    int indInFixed = findIndex(b.coreVersions, x -> x.equals("termsOfUse.html"));
    blAssert(b.coreVersions[indInFixed].equals("termsOfUse-2.html"));
}

static void parseDate() {
    String input = "<!-- Dates --><div>Created: 2023-04-05, updated: 2023-04-06</div><!-- / -->";
    String dateOld = Blog.parseCreatedDate(input);
    blAssert(dateOld.equals("2023-04-05"));
}

public static void main(String[] args) {
    System.out.println("Hw");
    TestResults counters = new TestResults();

//~    runTest(Test::testSaveFile, counters);
//~    runTest(Test::testFilePrefixes, counters);
//~    runTest(Test::testMaxVersion, counters);
//~    runTest(Test::testIngestCore, counters);
    runTest(Test::updateCore, counters);
    runTest(Test::parseDate, counters);

    if (counters.countFailed > 0)  {
        System.out.println("Failed " + counters.countFailed + " tests");
    } else {
        System.out.println("Successfully run " + counters.countRun + " tests");
    }
}



//}}}

}