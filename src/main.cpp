
#include <iostream>
#include <vector>

using namespace std;

#define CHUNK_QUANT 32768
#define null nullptr

class ArenaChunk {
public:
   ArenaChunk* next;
   vector<byte> memory;
   static ArenaChunk* create(size_t sz) {
      ArenaChunk* res = (ArenaChunk*)malloc(sizeof(ArenaChunk));
      res->memory.reserve(sz);
      return res;
   }
};

class Arena final {
public:
   static Arena* create() {
      int firstChunkSize = CHUNK_QUANT - 32; 
      Arena* result = (Arena*)malloc(sizeof(Arena));
      
      ArenaChunk* firstChunk = (ArenaChunk*)malloc(firstChunkSize);
      vector<byte> firstMemory;
      firstMemory.reserve(firstChunkSize);
      firstChunk->memory = firstMemory;
      firstChunk->next = null;
      result->firstChunk = firstChunk;
      result->currChunk = firstChunk;
      result->currInd = 0;
      return result;
   }
   
   void* allocate(size_t allocSize)  {
      /// Allocate memory in the arena, malloc'ing a new chunk if needed
      if (currInd + allocSize >= currChunk->memory.capacity()) {
         size_t newSize = calculateChunkSize(allocSize);
         ArenaChunk* newChunk = ArenaChunk::create(newSize);
         newChunk->next = null;
         this->currChunk->next = newChunk;
         this->currChunk = newChunk;
         this->currInd = 0;
         
      }
      void* result = (void*)(currChunk->memory.data() + currInd);
      currInd += allocSize;
      return result;      
   }
   
   ~Arena()  {
      ArenaChunk* curr = this->firstChunk;
      ArenaChunk* nextToFree = curr;
      while (curr != null) {
         nextToFree = curr->next;
         free(curr);
         curr = nextToFree;
      }
      cout << "freeing the arena"; 
      free(this);
   }
private:
   ArenaChunk* firstChunk;
   ArenaChunk* currChunk;
   int currInd;
   
   size_t calculateChunkSize(size_t allocSize) {
      /// Calculates memory for a new chunk. Memory is quantized and is always 32 bytes less
      /// 32 for any possible padding malloc might use internally,
      /// so that the total allocation size is a good even number of OS memory pages.
      size_t fullMemory = sizeof(ArenaChunk) + allocSize + 32;
      // struct header + main memory chunk + space for malloc bookkeep
      
      int mallocMemory = fullMemory < CHUNK_QUANT
                           ? CHUNK_QUANT
                           : (fullMemory % CHUNK_QUANT > 0
                                 ? (fullMemory/CHUNK_QUANT + 1)*CHUNK_QUANT
                                 : fullMemory);
      return mallocMemory - 32;
   }
   
};

struct Foo {
public:
   int x;
   double y;
   Foo(int x, double y): x(x), y(y) {}
};

int main() {
    cout << "Hello World!" << endl;
    
    Arena* a = Arena::create();
    Foo* foo = (Foo*)a->allocate(sizeof(Foo));
    foo->x = 123;
    foo->y = 123.4;
    cout << " x = " << foo->x << ", y = " << foo->y << endl;
    return 0;
}
