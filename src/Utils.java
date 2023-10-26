package tech.sozonov.blog;
//{{{ Imports

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Collection;
import java.util.Optional;
import java.time.Instant;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.function.Function;
import java.lang.reflect.Array;
import java.nio.file.Paths;
import java.nio.file.Files;

//}}}

class Utils {

//{{{ Println

static <T> void printNoLn(T t) {
    System.out.print(t);
}

static <T> void print(T t) {
    System.out.println(t);
}

//}}}
//{{{ List

static final class L<T> implements List<T> {
    private static final int DEFAULT_CAPACITY = 10;
    private int size;
    private int modCount = 0;
    private T[] data;

    public L(int initCap) {
        if (initCap < 0) {
            throw new RuntimeException();
        }
        data = (T[]) new Object[initCap];
    }

    public L() {
        this(4);
    }

    public static <T> L<T> of(T... values)  {
        L<T> result = new L(values.length);
        for (int i = 0; i < values.length; i++) {
            result.data[i] = values[i];
        }
        result.size = values.length;
        return result;
    }

    public int size()  {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean nonEmpty()  {
        return size > 0;
    }

    public int indexOf(Object e) {
        return -1;
    }

    public int lastIndexOf(Object e) {
        return -1;
    }

    public Object[] toArray() {
        T[] array = (T[]) new Object[size];
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

    public T get(int index) {
        checkBoundExclusive(index);
        return data[index];
    }

    public T set(int index, T e) {
        checkBoundExclusive(index);
        T result = data[index];
        data[index] = e;
        return result;
    }

    public boolean add(T e) {
        modCount++;
        if (size == data.length) {
            ensureCapacity(size + 1);
        }
        data[size++] = e;
        return true;
    }

    public void add(int index, T e) {
        checkBoundInclusive(index);
        modCount++;
        if (size == data.length) { ensureCapacity(size + 1); }
        if (index != size) { System.arraycopy(data, index, data, index + 1, size - index); }
        data[index] = e;
        size++;
    }

    public T remove(int index) {
        checkBoundExclusive(index);
        T r = data[index];
        modCount++;
        --size; 
        if (index != size) { System.arraycopy(data, index + 1, data, index, size - index); }
        // Aid for garbage collection by releasing this pointer.
        data[size] = null;
        return r;
    }
    
    public void removeLast() {
        checkBoundExclusive(size - 1);
        ++modCount; 
        --size;
        data[size] = null;
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

    public void ensureCapacity(int minCapacity) {
        int current = data.length;

        if (minCapacity > current) {
            T[] newData = (T[]) new Object[Math.max(current * 2, minCapacity)];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
    }

    public boolean addAll(Collection<? extends T> c) {
        return addAll(size, c);
    }

    public boolean addAll(int index, Collection<? extends T> c) {
        checkBoundInclusive(index);
        Iterator<? extends T> itr = c.iterator();
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

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return null;
    }

    @Override
    public ListIterator<T> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<T> listIterator(final int i) {
        return new LIterator<T>(this);
    }

    @Override
    public Iterator<T> iterator() {
        return listIterator();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    public void removeRange(int fromIndex, int toIndex) {
        int change = toIndex - fromIndex;
        if (change > 0) {
            modCount++;
            System.arraycopy(data, toIndex, data, fromIndex, size - toIndex);
            size -= change;
        } else if (change < 0) {
            throw new RuntimeException();
        }
    }

    private void checkBoundInclusive(int index) {
        // Implementation note: we do not check for negative ranges here, since
        // use of a negative index will cause a RuntimeException,
        // a subclass of the required exception, with no effort on our part.
        if (index > size) {
            throw new RuntimeException("Index: " + index + ", Size: " + size);
        }
    }

    private void checkBoundExclusive(int index) {
        // Implementation note: we do not check for negative ranges here, since
        // use of a negative index will cause a RuntimeException,
        // a subclass of the required exception, with no effort on our part.
        if (index >= size) {
             throw new RuntimeException("Index: " + index + ", Size: " + size);
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

    public int findIndex(Function<T, Boolean> pred)  {
        for (int i = 0; i < size; i++) {
            if (pred.apply(data[i])) {
                return i;
            }
        }
        return -1;
    }

    public <U> L<U> trans(Function<T, U> transformer) {
        L<U> result = new L(this.size);
        for (int i = 0; i < size; i++) {
            result.data[i] = transformer.apply(data[i]);
        }
        result.size = this.size;
        return result;
    }

    public <U> L<U> transIf(Predicate<T> pred, Function<T, U> transformer) {
        L<U> result = new L(this.size);
        int j = 0;
        for (int i = 0; i < size; i++) {
            T elt = data[i];
            if (pred.test(elt)) {
                result.data[j] = transformer.apply(elt);
                j++;
            }
        }
        result.size = j;
        return result;
    }


    public Optional<T> first(Predicate<T> pred) {
        /// Find index of first element satisfying predicate. 
        /// The method missing from the Java streams
        for (int i = 0; i < size; i++) {
            if (pred.test(data[i])) {
                return Optional.of(data[i]);
            }
        }
        return Optional.empty();
    }

    public boolean any(Predicate<T> pred) {
        /// Find index of first element satisfying predicate. 
        /// The method missing from the Java streams
        for (int i = 0; i < size; i++) {
            if (pred.test(data[i])) {
                return true;
            }
        }
        return false;
    }

    public T last() {
        /// Returns the last element. Assumes the list is non-empty
        return data[size - 1];
    }
    
    
    public Set<T> toSet() {
        Set<T> result = new HashSet<T>();
        for (int i = 0; i < size; ++i) {
            result.add(data[i]);
        }
        return result;
    }
    
    
    public static class LIterator<T> implements ListIterator<T> {
        private int i;
        private int size;
        private L<T> lst;
        private int expectedModCount;

        public LIterator(L<T> lst) {
            this.i = -1;
            this.size = lst.size;
            this.lst = lst;
            expectedModCount = lst.modCount;
        }

        public boolean hasNext() {
            return i < size - 1;
        }

        public T next() {
            if (i < size - 1) {
                i++;
                return lst.data[i];
            } else {
                throw new RuntimeException();
            }
        }

        public boolean hasPrevious() {
            return i > 0;
        }

        public T previous() {
            if (i > 0) {
                i--;
                return lst.data[i];
            } else {
                throw new RuntimeException();
            }
        }

        public int nextIndex() {
            return i + 1;
        }

        public int previousIndex() {
            return i - 1;
        }

        public void remove() {
            lst.remove(i);
            lst.modCount++;
        }

        public void set(T e) {
            lst.set(i, e);
        }

        public void add(T e) {
            lst.add(e);
            expectedModCount++;
        }

        void checkForComodification() {
            if (lst.modCount != expectedModCount) {
                throw new RuntimeException();
            }
        }
        
        
    }
}

//}}}
//{{{ Tu

static class Tu<F, S> {
    public F f1;
    public S f2;
    public Tu(F f, S s) {
        f1 = f;
        f2 = s;
    }
}

//}}}
//{{{ Misc

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


static final String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "July", "Aug",
    "Sep", "Oct", "Nov", "Dec" };


static String monthNameOf(String dt) {
    /// "2023-04-01" => "Apr"
    int monthNum = Integer.parseInt(dt.substring(5, 7));
    return months[monthNum - 1];
}

//}}}

}
