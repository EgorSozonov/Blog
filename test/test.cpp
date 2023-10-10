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
      
   }
   
   vector<FileInfo> listFiles(string dir) override {
      
   }
   
   vector<string> listDirs(string dir) override {
      
   }
   
   string readTextFile(string dir, string fN) override {
      
   }
   
   bool saveOverwriteFile(string dir, string fN, string cont) override {
      
   }
   
   bool moveFile(string dir, string fN, string targetDir) override {
      
   }
   
   string moveFileToNewVersion(string dir, string fN, string targetDir) override {
      
   }
   
   bool deleteIfExists(string dir, string fN) override {
      
   }
   
   vector<string> getNamesWithPrefix(string dir, string prefix) override {
      
   }

};
//}}}
