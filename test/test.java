package site.sozonov.blog.test;
import java.util.ArrayList;
import java.time.Instant;

class Test {

interface FileSys {
   boolean dirExists(String dir);
   List<FileInfo> listFiles(String dir);
   List<String> listDirs(String dir);
   String readTextFile(String dir, String fN);
   boolean saveOverwriteFile(String dir, String fN, cont: string);
   boolean moveFile(String dir, String fN, String targetDir);
   String moveFileToNewVersion(String dir, String fN, String targetDir);
   boolean deleteIfExists(String dir, String fN);
   List<String> getNamesWithPrefix(String dir, String prefix);
}

//{{{ Constants

static final String staticDir = "/var/www/blst"
static final String ingestDir = "/var/www/blog"

static final appSuburl = "blog/";
static final coreSubfolder = "_c/";
static final updateFreq = 300; // seconds before the cache gets rescanned
static final coreFiles = List.of("notFound.png", "notFound.html", "core.css", "core.html", "core.js",
                            "favicon.ico", "footer.html", "no.png", "yes.png", "termsOfUse.html");

//}}}
//{{{ MockFileSys

static class MockFileSys implements FileSys {
   private Map<String, List<MockFile>> fs = new HashMap<String, List<MockFile>>();

   @Override
   boolean dirExists(String dir) {
      return fs.containsKey(dir);
   }

   @Override
   List<FileInfo> listFiles(String dir) {
      if (!fs.containsKey(dir))   {
         return new ArrayList();
      }
      return fs.get(dir).map(x -> new FileInfo(x.name, x.modified)).toList();
   }

   @Override
   List<String> listDirs(String dir) {
      String dirWithSl = (dir.endsWith("/")) ? dir : (dir + "/");
      var subfolders = fs.keys().filter(x -> x.startsWith(dirWithSl));
      return subfolders.stream().map((String x) -> {
         int indSlash = x.indexOf("/");
         return indSlash < 0 ? x : x.substring(0, indSlash);
      });
   }

   @Override
   String readTextFile(String dir, String fN) {
      if (!fs.containsKey(dir)) {
         return "";
      }
      var mbFile = fs.get(dir).stream().findAny(x -> x.name.equals(fN));
      return (mbFile.isPresent()) ? mbFile.cont : "";
   }

   @Override
   boolean saveOverwriteFile(String dir, String fN, cont: string) {
      var newFile = new MockFile(fN, cont, Instant.now());
      if (fs.containsKey(dir)) {
         var existingFiles = fs.get(dir);
         var indexExisting = existingFiles.indexOf(x -> x.name.equals(fN));
         if (indexExisting == -1) {
            existingFiles.add(newFile);
         } else {
            existingFiles.set(indexExisting, newFile);
         }
      } else {
         fs.put(dir, new ArrayList(List.of(newFile)));
      }
      return true;
   }

   @Override
   boolean moveFile(String dir, String fN, String targetDir) {
      var sourceFiles = fs.get(dir);
      int indexSource = sourceFiles.indexOf(x -> x.name.equals(fN));
      MockFile sourceFile = sourceFiles.get(indexSource);
      var targetFiles = fs.get(targetDir);

      var existingWithThisPrefix = getNamesWithPrefix(targetDir, shaveOffExtension(fN));
      String newName = fN;
      if (existingWithThisPrefix.size() == 0) {
         targetFiles.push(sourceFile);
      } else {
         targetFiles.set(indexTarget, sourceFile);
      }
      sourceFiles.remove(indexSource);
      return true;
   }

   @Override
   String moveFileToNewVersion(String dir, String fN, String targetDir) {
      var sourceFiles = fs.get(dir);
      int indexSource = sourceFiles.indexOf(x -> x.name.equals(fN));
      MockFile sourceFile = sourceFiles.get(indexSource);
      var targetFiles = fs.get(targetDir);

      var existingWithThisPrefix = getNamesWithPrefix(targetDir, shaveOffExtension(fN));
      String newName = fN;
      if (existingWithThisPrefix.size() == 0) {
         targetFiles.push(sourceFile);
      } else {
         int maxExistingVersion = getMaxVersion(existingWithThisPrefix);
         int indLastDot = fn.lastIndexOf(".");
         newName = fN.substring(0, indLastDot) + "-" + (maxExistingVersion + 1)
               + fN.substring(indLastDot);
         targetFiles.add(new MockFile(newName, sourceFile.cont, sourceFile.modified));
      }
      sourceFiles.remove(indexSource);
      return newName;
   }

   @Override
   boolean deleteIfExists(String dir, String fN) {
      if (!fs.containsKey(dir)) {
         return true;
      }
      var existingFiles = fs.get(dir);
      int indexExisting = existingFiles.indexOf(x -> x.name.equals(fN));
      if (indexExisting > -1) {
         existingFiles.remove(indexExisting);
      }
      return true;
   }

   @Override
   List<String> getNamesWithPrefix(String dir, String prefix) {
      if (!fs.containsKey(dir)) {
         return List.of();
      }
      var existingFiles = fs.get(dir);
      return existingFiles.stream().filtered(x -> x.name.startsWith(prefix))
         .map(x -> x.name).toList();
   }
}


static class FileInfo {
   String name;
   Instant modified;
}

static class MockFile {
   String name;
   String cont;
   String modified;
}




//}}}
//{{{ Utils


interface Printer {
    static <T> void printNoLn(T t) {
       System.out.print(t);
    }

    static <T> void print(T t) {
       System.out.println(t);
    }
}

String shaveOffExtension(String fN) {
   int indDot = fN.indexOf(".")
   if (indDot < 0) {
      return fN;
   }
   return fN.substring(0, indDot);
}

void assert(boolean predicate) {
   if (!predicate) {
      String methodName = getFuncName(2);
      throw new Exception(msg);
   }
}

String getFuncName(int depth) {
   StackWalker walker = StackWalker.getInstance();
   return walker.walk(frames -> frames
      .get(depth)
      .map(StackWalker.StackFrame::getMethodName)).get();
}


boolean assertArrsEqual(List<String> a, List<String> b) {
   if(a.size() !== b.size()) {
      throw new Exception("Arrays have different lengths");
   } else if (a.size() === 0) {
      return;
   }
   a.sort();
   b.sort();
   for(int i = 0; i < a.size(); i++) {
      if(a.get(i) !== b.get(i)) {
         throw new Exception("Arrays differ at " + i + ", \"" + a[i] + "\" vs \"" + b[i] + "\"");
      }
   }
}


boolean isFixedCore(String fN) {
   for (int i = 0; i < coreFiles.size(); i++) {
      if (coreFiles.get(i).equals(fN)) {
         return true;
      }
   }
   return false;
}

String getCoreDir() {
   return ingestDir + coreSubfolder;
}

//}}}
//{{{ Tests

static class TestResults {
   int countRun;
   int countFailed;
}

static void testSaveFile() {
   FileSys fs = new MockFileSys();
   boolean resSave = fs.saveOverwriteFile("a/b", "myFile.txt", "An ode to joy");
   assert(resSave);
}

static void testFilePrefixes() {
   FileSys fs = new MockFileSys();
   fs.saveOverwriteFile("a/b", "myFile.txt", "An ode to joy");
   fs.saveOverwriteFile("a/b", "myFile-2.txt", "An ode to joy 2");
   fs.saveOverwriteFile("a/b", "myFile-3.txt", "An ode to joy 3");
   var versions = fs.getNamesWithPrefix("a/b", "myFile");
   assertArrsEqual(versions, List.of("myFile.txt", "myFile-2.txt", "myFile-3.txt"));
}

static void testMaxVersion() {
   int mv = getMaxVersion(List.of("file.txt", "file-2.txt", "file-3.txt"));
   assert(mv == 3);
}

public static void main(String[] args) {
   var mfs = new MockFileSys();
   mfs.foo();
   System.out.println("Hw");
}

//}}}
