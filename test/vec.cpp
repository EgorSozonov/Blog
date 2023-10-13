#include <iostream>
#include <functional>
#include <string>
#include <cstring>
#include <memory>

using namespace std;

typedef int32_t Int;
typedef time_t Ts;
template<typename T>
using Uni = unique_ptr<T>;

#define CHUNK_QUANT 32768
#define null nullptr

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
class VecChunk {
public:
   Int len;
   Int cap;
   Arena* a;
   T cont[2];
};

template<typename T>
class Vec {
private:
   VecChunk<T>* cont;

public:
   T& operator[](Int index) {
      return cont->cont[index];
   }

   static Vec<T> create(Arena* a) {
      VecChunk<T>* newCont = (VecChunk<T>*)malloc(sizeof(VecChunk<T>) + 2*sizeof(T));
      newCont->len = 0;
      newCont->cap = 4;
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
         //VecChunk<T>* newCont = (VecChunk<T>*)malloc(sizeof(VecChunk<T>) + (2*cont->cap - 2)*sizeof(T));
         VecChunk<T>* newCont = static_cast<VecChunk<T>*>(cont->a->allocate(
               sizeof(VecChunk<T>) + (2*cont->cap - 2)*sizeof(T)));
         memcpy(newCont->cont, cont->cont, cont->len*sizeof(T));
         newCont->len = cont->len;
         newCont->cap = 2*cont->cap;
         cont = newCont;
      }
   }

   Int len() const {
      return cont->len;
   }

   Int findIf(std::function<bool(T)> pred) const {
      if (cont->len == 0) {
         return -1;
      }
      for (Int j = 0; j < cont->len; j++) {
         if (pred(cont->cont[j])) {
            return j;
         }
      }
      return -1;
   }


   Int lastFindIf(std::function<bool(T)> pred) const {
      if (cont->len == 0) {
         return -1;
      }
      for (Int j = cont->len - 1; j > -1; j--) {
         if (pred(cont->cont[j])) {
            return j;
         }
      }
      return -1;
   }
};

//}}}

struct Foo {
   Int id;
   string name;
};

int main() {
   cout << "Hw" << endl;
   Arena* a = Arena::create();
   Uni<Arena> aGuard = unique_ptr<Arena>(a);

   Vec<Foo> v = Vec<Foo>::create(a);
   v.push(Foo { 1, "asdf" });
   v.push(Foo { 2, "foo" });
   v.push(Foo { 3, "asdf" });
   v.push(Foo { 4, "foo" });
   v.push(Foo { 5, "asdf" });
   v.push(Foo { 6, "asdf" });
   cout << "length is " << v.len() << endl;
   string sFoo = "foo";
   Int indLast5 = v.lastFindIf([](const Foo& x) { return x.name == "foo"; });
   cout << "ind of last foo is " << indLast5 << endl;
   Foo myFoo = { 11, "asdf" };
   cout << "1st elem " << v[0].id << " " << v[0].name << endl;
   for (Int i = 0; i < v.len(); i++) {
      cout << v[i].id << endl;
   }
}
