#include <iostream>
#include <ctime>
#include <vector>
#include <array>
#include <map>
#include <algorithm>

using namespace std;

#define Ts time_t
#define CHUNK_QUANT 32768
#define null nullptr
#define Int int32_t

//{{{ Arena

class Arena final {
public:
   vector<byte*> memory;
   byte* currChunk;
   int currLen;
   int currCap;

public:
   Arena(Int initCap, vector<byte*> _memory): currCap(initCap), currLen(0),
      memory(_memory), currChunk(memory[0]) {
   }

   static Arena* create() {
      Int firstChunkSize = CHUNK_QUANT - 32;

      vector<byte*> newMem;
      newMem.reserve(4);

      byte* firstChunk = new byte[firstChunkSize];
      newMem.push_back(firstChunk);

      Arena* result = new Arena(firstChunkSize, newMem); //(Arena*)malloc(sizeof(Arena));

      return result;
   }

   void* allocate(size_t allocSize)  {
      /// Allocate memory in the arena, malloc'ing a new chunk if needed
      if (currLen + allocSize >= currCap) {
         size_t newSize = calculateChunkSize(allocSize);
         byte* newChunk = new byte[newSize];
         this->memory.push_back(newChunk);
         this->currChunk = newChunk;
         this->currLen = 0;
         this->currCap = newSize;
      }
      void* result = (void*)(currChunk + currLen);
      currLen += allocSize;
      return result;
   }

   ~Arena() {
      cout << "freeing the arena" << endl;
      this->currChunk = null;
      for (auto element : memory) {
         delete[] element;
      }
      cout << "freed all the chunks" << endl;
   }

   size_t calculateChunkSize(size_t allocSize) {
      /// Calculates memory for a new chunk. Memory is quantized and is always 32 bytes less
      /// 32 for any possible padding malloc might use internally,
      /// so that the total allocation size is a good even number of OS memory pages.
      size_t fullMemory = allocSize + 32;
      // struct header + main memory chunk + space for malloc bookkeep

      int mallocMemory = fullMemory < CHUNK_QUANT
                           ? CHUNK_QUANT
                           : (fullMemory % CHUNK_QUANT > 0
                                 ? (fullMemory/CHUNK_QUANT + 1)*CHUNK_QUANT
                                 : fullMemory);
      return mallocMemory - 32;
   }

};

//~   Arena* aPtr = Arena::create();
//~   unique_ptr<Arena> a = unique_ptr<Arena>(aPtr);

//}}}
//{{{ Vector

template<typename T>
test/test.cppclass VecChunk<T> {
public:
   Int len;
   Int cap;
   T cont[2];
}

template<typename T>
class Vec<T> {
private:
   VecChunk<T>* cont;
   
public:
   T& operator[](Int index) {
      return cont->cont[index];
   }
   
   static Vec<T> create()  {
      VecChunk<T>* newCont = (VecChunk<T>*)malloc(sizeof(VecChunk) + 4*sizeof(T));
      newCont->len = 0;
      newCont->cap = 4;
   }
   
   void push(T newElem) {
      cont->cont[cont->len] = newElem;
      ++cont->len;
      if (cont->len == cont->cap) {
         VecChunk<T>* newCont = (VecChunk<T>*)malloc(sizeof(VecChunk) + 2*cont->cap*sizeof(T));
         newCont->len = cont->len;
         newCont->cap = 2*cont->cap;
         cont = newCont; 
      }
   }
   
   Int indexOf(UnaryPredicate pred) {
      
   }
}

//}}}
//{{{ Utils

string* shaveOffExtension(const string& fN) {
   Int indDot = fN.indexOf(".");
   if (indDot < 0) {
      return fN;
   }
   return fN.substr(0, indDot);
}

Int getMaxVersion(vector<string> filenames) {
   /// For a list like "file.txt, file-2.txt, file-3.txt", returns 3.
   vector<string> withoutExts = filenames.map((x: string) => shaveOffExtension(x));
   Int result = 1;
   for (auto& shortName : withoutExts) {
      const indDash = shortName.find_last_of("-");
      if (indDash < 0 || indDash == (shortName.length - 1))  {
         continue;
      }
      const mbNumber = parseInt(shortName.substr(indDash + 1));
      if (Number.isNaN(mbNumber)) {
         continue;
      } else if (result < mbNumber) {
         result = mbNumber;
      }
   }
   return result;
}

class Blog {
private:
   FileSys fs;
public:
   bool ingestCore() {
      /// Processes the core ingest folder. Returns true iff it had any core files to ingest
      /// (which implies that all HTML in the Blog needs to be regenerated)
      const dir = getCoreDir();
      if (fs.find(dir) == fs.end()) {
         return false;
      }
      auto fileList = this.fs.listFiles(dir);
      for (let inFile of fileList) {
         if (isFixedCore(inFile.name)) {
            this.moveFixedCore(dir, inFile);
         }
      }
      return true;
   }
private:
   string moveFixedCore(dir: string, inFile: FileInfo) {
      /// Moves a fixed core file (i.e. not an additional script module) and returns its version
      string sourceDir = getCoreDir();
      string targetDir = staticDir + coreSubfolder;
      string newVersion = fs.moveFileToNewVersion(sourceDir, inFile.name, targetDir);
      return newVersion;
   }
}

struct FileInfo {
   string name;
   Ts modified;
};

struct MockFile {
   string name;
   string cont;
   Ts modified;
};


struct TestResults {
   Int countRun;
   Int countFailed;
};


void assertArrsEqual(vector<string> a, vector<string> b) {
   if(a.size() != b.size())   {
      throw new runtime_error("Arrays have different lengths");
   } else if (a.size() == 0) {
      return;
   }
   sort(a.begin(), a.end());
   sort(b.begin(), b.end());
   for(Int i = 0; i < a.size(); i++) {
      if(a[i] != b[i]) {
         throw new runtime_error("Arrays differ at " + i + ", \"" + a[i] + "\" vs \"" + b[i] + "\"");
      }
   }
}


bool isFixedCore(string fN) {
   for (Int i = 0; i < coreFiles.size(); i++) {
      if (coreFiles[i] == fN) {
         return true;
      }
   }
   return false;
}

string getCoreDir() {
   return ingestDir + coreSubfolder;
}

template<typename T>
vector<T>::iterator indexOf(vector<T> v, UnaryPredicate pred) {
   return find_if(v.start(), v.end(), pred);
}

//}}}
//{{{ Mock FileSys

class IFileSys {
public:
   virtual bool dirExists(string dir) = 0;
   virtual vector<FileInfo> listFiles(string dir) = 0;
   virtual vector<string> listDirs(string dir) = 0;
   virtual string readTextFile(string dir, string fN) = 0;
   virtual bool saveOverwriteFile(string dir, string fN, string cont) = 0;
   virtual bool moveFile(string dir, string fN, string targetDir) = 0;
   virtual string moveFileToNewVersion(string dir, string fN, string targetDir) = 0;
   virtual bool deleteIfExists(string dir, string fN) = 0;
   virtual vector<string> getNamesWithPrefix(string dir, string prefix) = 0;
};

class MockFileSys : IFileSys {
private:
   map<string, vector<MockFile>> fs;

public:
   bool dirExists(string dir) override {
      return fs.find(dir) != fs.end();
   }

   vector<FileInfo> listFiles(string dir) override {
      map<string, vector<MockFile>>::iterator it = fs.find(dir);
      if (it == fs.end()) {
         return {};
      }
      auto existingFiles = it->second;
      vector<string> result;
      for (auto& x: existingFiles) {
         if (x->name.startsWith(prefix)) {
            result.push_back(FileInfo {x.name, x.modified});
         }
      }
   }

   vector<string> listDirs(string dir) override {
      string dirWithSl = (dir.ends_with("/")) ? dir : dir + "/";
      vector<string> result;
      for (auto& x: fs) {
         if (x->first->name.starts_with(dirWithSl)) {
            Int indSlash = x.index_of("/");
            result.push_back(indSlash ? x : x.substr(0, indSlash);
         }
      }
   }

   bool saveOverwriteFile(string dir, string fN, string cont) override {
      MockFile newFile = MockFIle { fN, cont, time::now() };
      map<string, vector<MockFile>>::iterator it = fs.find(dir);
      if (it != fs.end()) {
         vector<MockFile> existingFiles = it->second;
         Int indexExisting = existingFiles.findIndex([](x) { x.name == fN });
         if (indexExisting == -1) {
            existingFiles.push_back(newFile);
         } else {
            existingFiles[indexExisting] = newFile;
         }
      } else {
         fs[dir] = {newFile};
      }
      return true;
   }

   bool moveFile(string dir, string fN, string targetDir) override {
      auto sourceFiles = fs.find(dir)->second;
      Int indexSource = indexOf(sourceFiles, [&fN](MockFile x) {x.name == fN});
      auto targetFiles = fs.find(targetDir)->second;
      auto sourceFile = sourceFiles[indexSource];

      Int indexTarget = targetFiles.findIndex([](x) { x.name == fN });
      if (indexTarget == -1) {
         targetFiles.push_back(sourceFile);
      } else {
         targetFiles[indexTarget] = sourceFile;
      }
      sourceFiles.erase(sourceFiles.begin() + indexSource);
      return true;
   }

   string moveFileToNewVersion(string dir, string fN, string targetDir) override {
      auto sourceFiles = fs.find(dir)->second;
      Int indexSource = indexOf(sourceFiles, [&fN](MockFile x) {x.name == fN});
      auto targetFiles = fs.find(targetDir)->second;
      auto sourceFile = sourceFiles[indexSource];

      auto existingWithThisPrefix = getNamesWithPrefix(targetDir, shaveOffExtension(fN));
      string newName = fN;

      if (existingWithThisPrefix.length == 0)  {
         targetFiles.push_back(sourceFile);
      } else {
         auto maxExistingVersion = getMaxVersion(existingWithThisPrefix);
         auto indLastDot = fN.find_last_of(".");
         newName = fN.substr(0, indLastDot) + "-" + (maxExistingVersion + 1)
                   + fN.substr(indLastDot);

         targetFiles.push_back({
            name: newName, cont: sourceFile.cont, modified: sourceFile.modified
         });
      }
      sourceFiles.erase(sourceFiles.begin() + indexSource);
      return newName;
   }

   bool deleteIfExists(string dir, string fN) override {
      map<string, vector<MockFile>>::iterator it = fs.find(dir);
      if (it == fs.end()) {
         return true;
      }
      auto existingFiles = it->second;
      vector<string>::iterator indexExisting = indexOf(existingFiles, [&fN](MockFile x) { x.name == fN });
      if (indexExisting != existingFiles.end()) {
         existingFiles.erase(indexExisting);
      }
      return true;
   }

   vector<string> getNamesWithPrefix(string dir, string prefix) override {
      map<string, vector<MockFile>> ::iterator it = fs.find(dir);

      if (it == fs.end()) {
         return {};
      }
      vector<MockFile> existingFiles = it->second;
      vector<string> result;
      for (auto& exi: existingFiles) {
         if (exi.name.starts_with(prefix)) {
            result.push_back(exi.name);
         }
      }
      return result;
   }


   string readTextFile(string dir, string fN) override {
      map<string, vector<MockFile>> ::iterator it = fs.find(dir);
      if (it == fs.end()) {
         return "";
      }
      return "";
   }
};
//}}}
//{{{ Constants

const string staticDir = "/var/www/blst";
const string ingestDir = "/var/www/blog";

const string appSuburl = "blog/";
const string coreSubfolder = "_c/";
const Int updateFreq = 300; // seconds before the cache gets rescanned
const array<string, 10> coreFiles = {"notFound.png", "notFound.html", "core.css", "core.html", "core.js",
                            "favicon.ico", "footer.html", "no.png", "yes.png", "termsOfUse.html"};
//}}}
