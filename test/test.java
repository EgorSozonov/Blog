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

//{{{ MockFileSys

static class MockFileSys implements FileSys  {
   private Map<String, List<MockFile>> fs;
   
   public void foo() {
      System.out.println("foo");
   }
   
   @Override
   boolean dirExists(String dir) {
      
   }
   
   @Override
   List<FileInfo> listFiles(String dir) {
      
   }
   
   @Override
   List<String> listDirs(String dir) {
      
   }
   
   @Override
   String readTextFile(String dir, String fN) {
      
   }
   
   @Override
   boolean saveOverwriteFile(String dir, String fN, cont: string) {
      
   }
   
   @Override
   boolean moveFile(String dir, String fN, String targetDir) {
      
   }
   
   @Override
   String moveFileToNewVersion(String dir, String fN, String targetDir) {
      
   }
   
   @Override
   boolean deleteIfExists(String dir, String fN) {
      
   }
   
   @Override
   List<String> getNamesWithPrefix(String dir, String prefix) {
      
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

//}}}
//{{{ Tests

static class TestResults {
   int countRun;
   int countFailed;
}

public static void main(String[] args) {
   var mfs = new MockFileSys();
   mfs.foo();
   System.out.println("Hw");
}

//}}}

}
