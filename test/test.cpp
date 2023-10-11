#include <iostream>
#include <ctime>

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
//{{{ Mock FileSys
struct FileInfo {
   string name;
   Ts modified;
};

struct MockFile {
   string name;
   string cont;
   Ts modified;
}

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
            result.push_back(indSlash ? x : x.substring(0, indSlash);
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
      Int indexSource = find_if(sourceFiles.begin(), sourceFiles.end(), [](x) {x.name == fN});
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
      Int indexSource = find_if(sourceFiles.begin(), sourceFiles.end(), [](x) {x.name == fN});
      auto targetFiles = fs.find(targetDir)->second;
      auto sourceFile = sourceFiles[indexSource];

      auto existingWithThisPrefix = getNamesWithPrefix(targetDir, shaveOffExtension(fN));
      string newName = fN;
         
      if (existingWithThisPrefix.length === 0)  {
         targetFiles.push_back(sourceFile);
      } else {
         auto maxExistingVersion = getMaxVersion(existingWithThisPrefix); 
         auto indLastDot = fN.lastIndexOf(`.`);
         newName = fN.substring(0, indLastDot) + `-` + (maxExistingVersion + 1) 
                   + fN.substring(indLastDot);
         
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
      Int indexExisting = existingFiles.findIndex([](x) { x.name == fN });
      if (indexExisting > -1) {
         existingFiles.erase(existingFiles.begin() + 1);
      }
      return true;
   }
   
   vector<string> getNamesWithPrefix(string dir, string prefix) override {
      map<string, vector<MockFile>>::iterator it = fs.find(dir);
      
      if (it == fs.end()) {
         return {};
      }
      vector<MockFile> existingFiles = it->second;
      vector<string> result;
      for (auto& exi: existingFiles) {
         if (exi->name.startsWith(prefix)) {
            result.push_back(exi->name);
         }
      }
      return result;
   }
   
   
   string readTextFile(string dir, string fN) override {
      
   }
};
//}}}
//{{{ Constants

const staticDir = `/var/www/blst`
const ingestDir = `/var/www/blog`

const appSuburl = `blog/`;
const coreSubfolder = `_c/`;
const updateFreq = 300; // seconds before the cache gets rescanned
const coreFiles = [`notFound.png`, `notFound.html`, `core.css`, `core.html`, `core.js`, 
                            `favicon.ico`, `footer.html`, `no.png`, `yes.png`, `termsOfUse.html`];
//}}}
//{{{ Utils

function print(a: any) {
   console.log(a)
}

function shaveOffExtension(fN: string): string {
   const indDot = fN.indexOf(`.`)
   if (indDot < 0) {
      return fN
   }
   return fN.substring(0, indDot)
}

function getMaxVersion(filenames: string[]): number {
   /// For a list like `file.txt, file-2.txt, file-3.txt`, returns 3.
   const withoutExts = filenames.map((x: string) => shaveOffExtension(x));
   const versions = []
   let result = 1
   for (let shortName of withoutExts) {
      const indDash = shortName.lastIndexOf(`-`)
      if (indDash < 0 || indDash === (shortName.length - 1))  {
         continue;
      }
      const mbNumber = parseInt(shortName.substring(indDash + 1));
      if (Number.isNaN(mbNumber)) {
         continue;
      } else if (result < mbNumber) {
         result = mbNumber
      }
   }
   return result
}

class Blog {
   constructor(private fs: FileSys) {
   }
   
   async ingestCore(): Promise<boolean> {
      /// Processes the core ingest folder. Returns true iff it had any core files to ingest 
      /// (which implies that all HTML in the Blog needs to be regenerated)
      const dir = getCoreDir(); 
      if (!(await this.fs.dirExists(dir))) {
         return false;
      }
      const fileList = await this.fs.listFiles(dir);
      for (let inFile of fileList) {
         if (isFixedCore(inFile.name)) {
            this.moveFixedCore(dir, inFile)
         }
      }
      return true;
   }
   
   private async moveFixedCore(dir: string, inFile: FileInfo): Promise<string> {
      /// Moves a fixed core file (i.e. not an additional script module) and returns its version
      const sourceDir = getCoreDir();
      const targetDir = staticDir + coreSubfolder;
      const newVersion = await this.fs.moveFileToNewVersion(sourceDir, inFile.name, targetDir)
      return newVersion
   }
}

type FileInfo = {
   name: string;
   modified: Date;
}

type MockFile = {
   name: string;
   cont: string;
   modified: Date;
}


type TestResults = {
   countRun: number;
   countFailed: number;
}


function assertArrsEqual(a: string[], b: string[]) {
   if(a.length !== b.length)   {
      throw new Error(`Arrays have different lengths`)
   } else if (a.length === 0) {
      return
   }
   a.sort()
   b.sort()
   for(let i = 0; i < a.length; i++) {
      if(a[i] !== b[i]) {
         throw new Error(`Arrays differ at ` + i + `, "` + a[i] + `" vs "` + b[i] + `"`)
      }
   }
}


function isFixedCore(fN: string): boolean  {
   for (let i = 0; i < coreFiles.length; i++) {
      if (coreFiles[i] === fN) {
         return true
      }
   }
   return false;
}

function getCoreDir(): string  {
   return ingestDir + coreSubfolder;
}

//}}}
