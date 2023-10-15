package site.sozonov.blog.test;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.Collections;

class Test {

interface FileSys {
   boolean dirExists(String dir);
   L<FileInfo> listFiles(String dir);
   L<String> listDirs(String dir);
   String readTextFile(String dir, String fN);
   boolean saveOverwriteFile(String dir, String fN, String cont);
   boolean moveFile(String dir, String fN, String targetDir);
   String moveFileToNewVersion(String dir, String fN, String targetDir);
   boolean deleteIfExists(String dir, String fN);
   L<String> getNamesWithPrefix(String dir, String prefix);
}
//{{{ List

static class L<T> implements List<T>, RandomAccess {
   private static final int DEFAULT_CAPACITY = 10;
   private int size;
   private T[] data;
   
   public L(int initCap) {
      if (capacity < 0) {
         throw new IllegalArgumentException();
      } 
      data = (T[]) new Object[initCap];
   }

   public L() {
      this(4);
   }
   
   public L(Collection<? extends E> c) {
      this((int) (c.size() * 1.1f));
      addAll(c);
   }
   
   public int size()  {
      return size;
   }

   public boolean isEmpty() {
      return size == 0;
   }
   
   public boolean notEmpty()  {
      return size > 0;
   }
   
   public int indexOf(Object e) {
      for (int i = 0; i < size; i++) {
         if (equals(e, data[i])) {
            return i;
         }
      }
      return -1;
   }
 
   public int lastIndexOf(Object e) {
      for (int i = size - 1; i >= 0; i--) {
         if (equals(e, data[i])) {
            return i;
            } 
      } 
      return -1;
   }
   
   public Object[] toArray() {
      E[] array = (E[]) new Object[size];
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
   
   public E get(int index) {
      checkBoundExclusive(index);
      return data[index];
   }
 
   public E set(int index, E e) {
      checkBoundExclusive(index);
      E result = data[index];
      data[index] = e;
      return result;
   }
 
   public boolean add(E e) {
      modCount++;
      if (size == data.length) {
         ensureCapacity(size + 1);
      } 
      data[size++] = e;
      return true;
   }
 
   public void add(int index, E e) {
      checkBoundInclusive(index);
      modCount++;
      if (size == data.length) { ensureCapacity(size + 1); } 
      if (index != size) { System.arraycopy(data, index, data, index + 1, size - index); } 
      data[index] = e;
      size++;
   }
 
   public E remove(int index) {
      checkBoundExclusive(index);
      E r = data[index];
      modCount++;
      if (index != --size) { System.arraycopy(data, index + 1, data, index, size - index); }
      // Aid for garbage collection by releasing this pointer.
      data[size] = null;
      return r;
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
 
   public boolean addAll(Collection<? extends E> c) {
      return addAll(size, c);
   }
 
   public boolean addAll(int index, Collection<? extends E> c) {
      checkBoundInclusive(index);
      Iterator<? extends E> itr = c.iterator();
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
 
   public void removeRange(int fromIndex, int toIndex) {
      int change = toIndex - fromIndex;
      if (change > 0) {
         modCount++;
         System.arraycopy(data, toIndex, data, fromIndex, size - toIndex);
         size -= change;
      } else if (change < 0) {
         throw new IndexOutOfBoundsException();
      } 
   }
 
   private void checkBoundInclusive(int index) {
      // Implementation note: we do not check for negative ranges here, since
      // use of a negative index will cause an ArrayIndexOutOfBoundsException,
      // a subclass of the required exception, with no effort on our part.
      if (index > size) {
         throw new Exception("Index: " + index + ", Size: " + size);
      } 
   }
 
   private void checkBoundExclusive(int index) {
      // Implementation note: we do not check for negative ranges here, since
      // use of a negative index will cause an ArrayIndexOutOfBoundsException,
      // a subclass of the required exception, with no effort on our part.
      if (index >= size) {
          throw new Exception("Index: " + index + ", Size: " + size);
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
 
   boolean retainAllInternal(Collection<?> c) {
      int i;
      int j;
      for (i = 0; i < size; i++) {
         if (! c.contains(data[i])) {
            break;
         } 
      } 
      if (i == size) {
         return false;
      } 
 
      modCount++;
      for (j = i++; i < size; i++) {
         if (c.contains(data[i])) {
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
}

//}}}
//{{{ Constants

static final String staticDir = "/var/www/blst";
static final String ingestDir = "/var/www/blog";

static final String appSuburl = "blog/";
static final String coreSubfolder = "_c/";
static final int updateFreq = 300; // seconds before the cache gets rescanned
static final L<String> coreFiles = 
   new L("notFound.png", "notFound.html", "core.css", "core.html", "core.js",
           "favicon.ico", "footer.html", "no.png", "yes.png", "termsOfUse.html");

//}}}
//{{{ MockFileSys

static class MockFileSys implements FileSys {
   private Map<String, L<MockFile>> fs = new HashMap<String, L<MockFile>>();

   @Override
   boolean dirExists(String dir) {
      return fs.containsKey(dir);
   }

   @Override
   L<FileInfo> listFiles(String dir) {
      if (!fs.containsKey(dir))   {
         return new ArrayList();
      }
      return fs.get(dir).stream().map(x -> new FileInfo(x.name, x.modified)).toList();
   }

   @Override
   L<String> listDirs(String dir) {
      String dirWithSl = (dir.endsWith("/")) ? dir : (dir + "/");
      L<String> subfolders = fs.keySet().stream().filter(x -> x.startsWith(dirWithSl)).toList();
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
      return fs.get(dir).stream().filter(x -> x.name.equals(fN)).findFirst().map(x -> x.cont).orElse("");
   }

   @Override
   boolean saveOverwriteFile(String dir, String fN, String cont) {
      var newFile = new MockFile(fN, cont, Instant.now());
      if (fs.containsKey(dir)) {
         var existingFiles = fs.get(dir);
         var indexExisting = findIndex(existingFiles, x -> x.name.equals(fN));
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
      int indexSource = findIndex(sourceFiles, x -> x.name.equals(fN));
      MockFile sourceFile = sourceFiles.get(indexSource);
      L<MockFile> targetFiles = fs.get(targetDir);

      var existingInd = findIndex(targetFiles, fN);
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
   String moveFileToNewVersion(String dir, String fN, String targetDir) {
      var sourceFiles = fs.get(dir);
      int indexSource = findIndex(sourceFiles, x -> x.name.equals(fN));
      MockFile sourceFile = sourceFiles.get(indexSource);
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
   boolean deleteIfExists(String dir, String fN) {
      if (!fs.containsKey(dir)) {
         return true;
      }
      var existingFiles = fs.get(dir);
      int indexExisting = findIndex(existingFiles, x -> x.name.equals(fN));
      if (indexExisting > -1) {
         existingFiles.remove(indexExisting);
      }
      return true;
   }

   @Override
   L<String> getNamesWithPrefix(String dir, String prefix) {
      if (!fs.containsKey(dir)) {
         return List.of();
      }
      var existingFiles = fs.get(dir);
      return existingFiles.stream().filter(x -> x.name.startsWith(prefix))
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
   Instant modified;
   public MockFile(String name, String cont, Instant modified)  {
      this.name = name;
      this.cont = cont;
      this.modified = modified;
   }
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

@FunctionalInterface
public interface Action {
    void run();
}

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

static void blAssert(boolean predicate) {
   if (!predicate) {
      String methodName = getFuncName(2);
      throw new Exception(methodName);
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
      throw new Exception("Arrays have different lengths");
   } else if (a.size() == 0) {
      return;
   }
   Collections.sort(a);
   Collections.sort(b);
   for(int i = 0; i < a.size(); i++) {
      if(a.get(i) != b.get(i)) {
         throw new Exception("Arrays differ at " + i + ", \"" + a.get(i) + "\" vs \"" + b.get(i) + "\"");
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

static String getCoreDir() {
   return ingestDir + coreSubfolder;
}


static void runTest(Action theTest, TestResults counters) {
   try {
      theTest.run();
   } catch (Exception e) {
      counters.countFailed++;
   }
   counters.countRun++;
}

static int getMaxVersion(L<String> filenames) {
   /// For a list like `file.txt, file-2.txt, file-3.txt`, returns 3.
   L<String> withoutExts = filenames.stream().map(Test::shaveOffExtension).toList();
   var versions = new L();
   int result = 1;
   for (var shortName : withoutExts) {
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

static <T> int findIndex(L<T> lst, Function<T, Boolean> predicate) {
   for (int i = 0; i < lst.size(); i++) {
      if (predicate.apply(lst.get(i))) {
         return i;
      }
   }
   return -1;
}

//}}}
//{{{ Tests

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
   fs.saveOverwriteFile("a/b", "myFile.txt", "An ode to joy");
   fs.saveOverwriteFile("a/b", "myFile-2.txt", "An ode to joy 2");
   fs.saveOverwriteFile("a/b", "myFile-3.txt", "An ode to joy 3");
   var versions = fs.getNamesWithPrefix("a/b", "myFile");
   assertArrsEqual(versions, new L("myFile.txt", "myFile-2.txt", "myFile-3.txt"));
}

static void testMaxVersion() {
   int mv = getMaxVersion(List.of("file.txt", "file-2.txt", "file-3.txt"));
   blAssert(mv == 3);
}

public static void main(String[] args) {
   TestResults counters = new TestResults();
   runTest(Test::testSaveFile, counters);
   runTest(Test::testFilePrefixes, counters);
   runTest(Test::testMaxVersion, counters);
   System.out.println("Hw");
}

//}}}

}
