#include <iostream>
#include <ctime>
#include <vector>
#include <array>
#include <map>
#include <algorithm>
#include <functional>
#include "test.h"
using namespace std;

#define CHUNK_QUANT 32768
#define null nullptr

string getCoreDir();
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
class VecChunk final {
public:
   Int size;
   Int cap;
   Arena* a;
   T cont[2];
};

template<typename T>
class Vec final {
private:
   VecChunk<T>* cont;

public:
   T& operator[](Int index) {
      return cont->cont[index];
   }

   static Vec<T> create(Arena* a) {
      VecChunk<T>* newCont = static_cast<VecChunk<T>*>(a->allocate(
            sizeof(VecChunk<T>) + 2*sizeof(T)));
      newCont->len = 0;
      newCont->cap = 4;
      newCont->a = a;
      Vec<T> result;
      result.cont = newCont;
      return result;
   }
   
   static Vec<T> createWithSize(Arena* a, Int size) {
      VecChunk<T>* newCont = static_cast<VecChunk<T>*>(a->allocate(
            sizeof(VecChunk<T>) + (size - 2)*sizeof(T)));
      newCont->len = 0;
      newCont->cap = size;
      newCont->a = a;
      Vec<T> result;
      result.cont = newCont;
      return result;
   }

   void push(T newElem) {
      cont->cont[cont->len] = newElem;
      ++cont->len;
      if (cont->len == cont->cap) {
         cout << "allocatin'!\n";
         VecChunk<T>* newCont = static_cast<VecChunk<T>*>( 
               cont->a->allocate(sizeof(VecChunk<T>) + (2*cont->cap - 2)*sizeof(T))
         );
         memcpy(newCont->cont, cont->cont, cont->len*sizeof(T));
         newCont->size = cont->size;
         newCont->cap = 2*cont->cap;
         cont = newCont;
      }
   }

   Int size() const {
      return cont->size;
   }

   Int findIf(function<bool(T)> pred) const {
      if (cont->size == 0) {
         return -1;
      }
      for (Int j = 0; j < cont->size; j++) {
         if (pred(cont->cont[j])) {
            return j;
         }
      }
      return -1;
   }


   Int findIfReverse(function<bool(T)> pred) const {
      if (cont->size == 0) {
         return -1;
      }
      for (Int j = cont->size - 1; j > -1; j--) {
         if (pred(cont->cont[j])) {
            return j;
         }
      }
      return -1;
   }
   
   template<typename U>
   Vec<U> transform(function<U(const T&)> transformer) {
      Vec<U> result = Vec<U>::createWithSize(this->cont.len);
      const Int len = this->cont.len;
      T* source = this->cont.cont;
      U* target = result->cont.cont;
      for (Int i = 0; i < len; i++) {
         target[i] = transformer(source[i]);
      }
   }
   
   T* begin() {
      return &cont->cont;
   }
   
   T* end() {
      return cont->cont + cont->cont.len;
   }
   
private:
   Vec<T> createWithSize(Int size) {
      VecChunk<T>* newCont = (VecChunk<T>*)malloc(sizeof(VecChunk<T>) + (size - 2)*sizeof(T));
      newCont->len = 0;
      newCont->cap = size;
      newCont->a = cont.a;
      Vec<T> result;
      result.cont = newCont;
      return result;
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
//{{{ Utils

optional<Int> parseInt(Str s) {
    try {
       return optional { stoi(s) };
    } catch (...) {
       return {};
    }
}

string shaveOffExtension(Str fN) {
   Int indDot = fN.find(".");
   if (indDot < 0) {
      return fN;
   }
   return fN.substr(0, indDot);
}

Int getMaxVersion(Vec<string> filenames) {
   /// For a list like "file.txt, file-2.txt, file-3.txt", returns 3.
   function<string(const string&)> transformer = shaveOffExtension;
   Vec<string> withoutExts = filenames.transform(transformer);
   Int result = 1;
   for (auto& shortName : withoutExts) {
      const Int indDash = shortName.find_last_of("-");
      if (indDash < 0 || indDash == (shortName.size() - 1))  {
         continue;
      }
      const optional<Int> mbNumber = parseInt(shortName.substr(indDash + 1));
      if (!mbNumber) {
         continue;
      } else if (result < mbNumber) {
         result = mbNumber.value();
      }
   }
   return result;
}


void assertArrsEqual(Vec<string> a, Vec<string> b) {
   if(a.size() != b.size())   {
      throw new runtime_error("Arrays have different sizes");
   } else if (a.size() == 0) {
      return;
   }
   sort(a.begin(), a.end());
   sort(b.begin(), b.end());
   for(Int i = 0; i < a.size(); i++) {
      if(a[i] != b[i]) {
         string msg =  string("Arrays differ at " + i) + ", \"" + a[i] + "\" vs \"" + b[i] + "\"";
         throw new runtime_error(msg);
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

//}}}
//{{{ Blog

class Blog {

private:
   IFileSys* fs;
public:
   bool ingestCore() {
      /// Processes the core ingest folder. Returns true iff it had any core files to ingest
      /// (which implies that all HTML in the Blog needs to be regenerated)
      auto dir = getCoreDir();
      if (!fs->dirExists(dir)) {
         return false;
      }
      auto fileList = fs->listFiles(dir);
      for (auto& inFile : fileList) {
         if (isFixedCore(inFile.name)) {
            moveFixedCore(dir, inFile);
         }
      }
      return true;
   }
   
private:
   string moveFixedCore(Str dir, FileInfo inFile) {
      /// Moves a fixed core file (i.e. not an additional script module) and returns its version
      string sourceDir = getCoreDir();
      string targetDir = staticDir + coreSubfolder;
      string newVersion = fs->moveFileToNewVersion(sourceDir, inFile.name, targetDir);
      return newVersion;
   }
};

//}}}
//{{{ Mock FileSys

class MockFileSys : IFileSys {
private:
   map<string, Vec<MockFile>> fs;

public:
   bool dirExists(Str dir) override {
      return fs.find(dir) != fs.end();
   }

   Vec<FileInfo> listFiles(Str dir) override {
      map<string, Vec<MockFile>>::iterator it = fs.find(dir);
      if (it == fs.end()) {
         return {};
      }
      auto existingFiles = it->second;
      Vec<FileInfo> result;
      for (auto& x: existingFiles) {
         result.push(FileInfo {x.name, x.modified});
      }
   }

   Vec<string> listDirs(Str dir) override {
      string dirWithSl = (dir.ends_with("/")) ? dir : dir + "/";
      Vec<string> result;
      for (auto& x: fs) {
         if (x.first.starts_with(dirWithSl)) {
            Int indSlash = x.first.find("/");
            result.push(indSlash ? x.first : x.first.substr(0, indSlash));
         }
      }
      return result;
   }

   bool saveOverwriteFile(Str dir, Str fN, Str cont) override {
      MockFile newFile = MockFile { fN, cont, time::now() };
      map<string, Vec<MockFile>>::iterator it = fs.find(dir);
      if (it != fs.end()) {
         Vec<MockFile> existingFiles = it->second;
         Int indexExisting = existingFiles.findIf([](x) { x.name == fN });
         if (indexExisting == -1) {
            existingFiles.push(newFile);
         } else {
            existingFiles[indexExisting] = newFile;
         }
      } else {
         fs[dir] = {newFile};
      }
      return true;
   }

   bool moveFile(Str dir, Str fN, Str targetDir) override {
      auto sourceFiles = fs.find(dir)->second;
      Int indexSource = indexOf(sourceFiles, [&fN](MockFile x) {x.name == fN});
      auto targetFiles = fs.find(targetDir)->second;
      auto sourceFile = sourceFiles[indexSource];

      Int indexTarget = targetFiles.findIndex([](x) { x.name == fN });
      if (indexTarget == -1) {
         targetFiles.push(sourceFile);
      } else {
         targetFiles[indexTarget] = sourceFile;
      }
      sourceFiles.erase(sourceFiles.begin() + indexSource);
      return true;
   }

   string moveFileToNewVersion(Str dir, Str fN, Str targetDir) override {
      auto sourceFiles = fs.find(dir)->second;
      Int indexSource = indexOf(sourceFiles, [&fN](MockFile x) {x.name == fN});
      auto targetFiles = fs.find(targetDir)->second;
      auto sourceFile = sourceFiles[indexSource];

      auto existingWithThisPrefix = getNamesWithPrefix(targetDir, shaveOffExtension(fN));
      string newName = fN;

      if (existingWithThisPrefix.length == 0)  {
         targetFiles.push(sourceFile);
      } else {
         auto maxExistingVersion = getMaxVersion(existingWithThisPrefix);
         auto indLastDot = fN.find_last_of(".");
         newName = fN.substr(0, indLastDot) + "-" + (maxExistingVersion + 1)
                   + fN.substr(indLastDot);

         targetFiles.push({
            name: newName, cont: sourceFile.cont, modified: sourceFile.modified
         });
      }
      sourceFiles.erase(sourceFiles.begin() + indexSource);
      return newName;
   }

   bool deleteIfExists(Str dir, Str fN) override {
      map<string, Vec<MockFile>>::iterator it = fs.find(dir);
      if (it == fs.end()) {
         return true;
      }
      auto existingFiles = it->second;
      Vec<string>::iterator indexExisting = indexOf(existingFiles, [&fN](MockFile x) { x.name == fN });
      if (indexExisting != existingFiles.end()) {
         existingFiles.erase(indexExisting);
      }
      return true;
   }

   Vec<string> getNamesWithPrefix(Str dir, Str prefix) override {
      map<string, Vec<MockFile>> ::iterator it = fs.find(dir);

      if (it == fs.end()) {
         return {};
      }
      Vec<MockFile> existingFiles = it->second;
      Vec<string> result;
      for (auto& exi: existingFiles) {
         if (exi.name.starts_with(prefix)) {
            result.push(exi.name);
         }
      }
      return result;
   }


   string readTextFile(Str dir, Str fN) override {
      map<string, Vec<MockFile>> ::iterator it = fs.find(dir);
      if (it == fs.end()) {
         return "";
      }
      return "";
   }
};
//}}}
