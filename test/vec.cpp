#include <iostream>
#include <functional>

using namespace std;

//{{{ Vector

typedef int32_t Int;

template<typename T>
class VecChunk {
public:
   Int len;
   Int cap;
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
   
   static Vec<T> create() {
      VecChunk<T>* newCont = (VecChunk<T>*)malloc(sizeof(VecChunk<T>) + 2*sizeof(T));
      newCont->len = 0;
      newCont->cap = 4;
   }
   
   void push(T newElem) {
      cont->cont[cont->len] = newElem;
      ++cont->len;
      if (cont->len == cont->cap) {
         VecChunk<T>* newCont = (VecChunk<T>*)malloc(sizeof(VecChunk<T>) + (2*cont->cap - 2)*sizeof(T));
         newCont->len = cont->len;
         newCont->cap = 2*cont->cap;
         cont = newCont; 
      }
   }
   
   Int len() const {
      return cont->len;
   }
   
   Int indexOf(std::function<bool(T)> pred) {
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
   
   
   Int lastIndexOf(std::function<bool(T)> pred) {
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

int main() {
   cout << "Hw" << endl;
}
