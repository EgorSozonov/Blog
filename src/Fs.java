package tech.sozonov.blog;
//{{{ Imports

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Stream;

//}}}

class Test {

static class FileInfo {
    String name;

    public FileInfo(String name)  {
        this.name = name;
    }
}


public static class Dir {
    public String cont;

    public Dir(Dir absDir, Subfolder subf)  {
        this.cont = Paths.get(absDir.cont, subf.cont).toString();
    }

    private Dir(String s)  {
        this.cont = Paths.get(s).toString();
    }

    public static Dir ofString(String absPath)  {
        if (!absPath.startsWith("/")) {
            throw new RuntimeException("An absolute path must start with `/`!");
        }
        return new Dir(absPath);
    }
}


public static class Subfolder {
    public String cont;
    public Subfolder(String cont) {
        this.cont = cont;
    }

    @Override
    public boolean equals(Object o) {
        return this.cont.equals(((Subfolder)o).cont);
    }

    @Override
    public int hashCode() {
        return this.cont.hashCode();
    }
}

static class UnvName {
    /// An unversioned file name, so if the whole name is "asdf-11.jpg", this will be "asdf.jpg"
    String cont;
    UnvName(String fn) {
        String withoutExt = shaveOffExtension(fn);
        int indDash = withoutExt.lastIndexOf("-");
        if (indDash < 0) {
            this.cont = fn;
            return;
        }
        this.cont = withoutExt.substring(0, indDash) + fn.substring(withoutExt.length());
    }

    String toVersion(int n) {
        String withoutExt = shaveOffExtension(this.cont);
        return withoutExt
                + "-" + Integer.toString(n) + this.cont.substring(withoutExt.length() + 1);
    }

    @Override
    public boolean equals(Object o) {
        return this.cont.equals(((UnvName)o).cont);
    }

    @Override
    public int hashCode() {
        return this.cont.hashCode();
    }
}
static String shaveOffExtension(String fN) {
    int indDot = fN.indexOf(".");
    if (indDot < 0) {
        return fN;
    }
    return fN.substring(0, indDot);
}




interface FileSys {
    boolean dirExists(Dir dir);
    L<FileInfo> listFiles(Dir dir); // immediate files in a dir
    L<Subfolder> listSubfolders(Dir dir); // immediate subfolders of child dirs
    L<Subfolder> listSubfoldersContaining(Dir dir, String fN); // recursively list all nested dirs
    String readTextFile(Dir dir, String fN);
    boolean saveOverwriteFile(Dir dir, String fN, String cont);
    boolean moveFileWithRename(Dir dir, String fN, Dir targetDir, String newName);
    boolean deleteIfExists(Dir dir, String fN);
    boolean deleteDirIfExists(Dir dir);
}



class BlogFileSys implements FileSys {
    @Override
    public boolean dirExists(Dir dir) {
        return Files.isDirectory(Paths.get(dir.cont));
    }

    @Override
    public L<FileInfo> listFiles(Dir dir) {
        return Stream.of(new File(dir.cont).listFiles())
                .filter(file -> !file.isDirectory())
                .map(x -> new FileInfo(x.getName()));
    }

    @Override
    public L<Subfolder> listSubfolders(Dir dir) {
        /// Immediate but full subfolders of a dir, like `a/b/c`
        L<Subfolder> result = new L();
        String prefixWithSl = dir.cont.endsWith("/") ? dir.cont : dir.cont + "/";
        try (Stream<Path> paths = Files.walk(Paths.get(dir.cont))) {
            walk.filter(Files::isDirectory)
                    .map(x -> result.add(new Subfolder(x.name.substring(prefixWithSl.length()))));
        }
        return result;
    }

    @Override
    public L<Subfolder> listSubfoldersContaining(Dir dir, String fn) {
        /// Gets the list of directories containing a filename, for example "i.html"
        L<Subfolder> result = new L();
        String prefixWithSl = dir.cont.endsWith("/") ? dir.cont : dir.cont + "/";
        try (Stream<Path> paths = Files.walk(Paths.get(dir.cont))) {
            walk.filter(x -> Files.isDirectory(x) && (new File(Paths.get(x.path, fn))).exists())
                    .map(x -> result.add(new Subfolder(x.name.substring(prefixWithSl.length()))));
        }
        return result;
    }

    @Override
    public String readTextFile(Dir dir, String fn) {
        Path thePath = Paths.get(dir.cont, fn);
        File theFile = new File(thePath);
        if (!theFile.exists()) {
            return "";
        }
        return Files.readString(thePath);
    }

    @Override
    public boolean saveOverwriteFile(Dir dir, String fn, String cont) {
        // Target dir must exist
        File targetOsDir = new File(Paths.get(dir.cont));
        if (!targetOsDir.exists() || !Files.isDirectory(dir)) {
            return false;
        }
        Path targetPath = Paths.get(dir.cont, fn);
        File targetFile = new File(targetPath);
        if (targetFile.exists()) {
            if (targetFile.isDirectory()) {
                return false;
            }
            targetFile.delete();
        }
        Files.write(targetPath, cont.getBytes(StandardCharsets.UTF_8));
        return true;
    }


    @Override
    public boolean moveFileWithRename(Dir dir, String fn, Dir targetDir, String newName) {
        /// Source file and target dir must exist
        File sourceFile = new File(Paths.get(dir.cont, fn));
        if (!sourceFile.exists() || Files.isDirectory(sourceFile)) {
            return false;
        }
        File targetOsDir = new File(Paths.get(targetDir.cont));
        if (!targetOsDir.exists() || !Files.isDirectory(targetOsDir)) {
            return false;
        }
        File targetFile = new File(Paths.get(targetDir.cont, newName));
        if (targetFile.exists()) {
            if (targetFile.isDirectory()) {
                return false;
            }
            targetFile.delete();
        }
        Files.move(sourceFile, targetFile);
        return true;
    }

    @Override
    public boolean deleteIfExists(Dir dir, String fn) {
        Path thePath = Paths.get(dir.cont, fn);
        File theFile = new File(thePath);
        if (!theFile.exists() || Files.isDirectory(theFile)) {
            return false;
        }
        theFile.delete();
        return true;
    }

    @Override
    public boolean deleteDirIfExists(Dir dir) {
        /// Deletes a dir with all its contents and subfolders
        Path thePathToDelete = Paths.get(dir.cont);
        var theFolder = new File(thePathToDelete);
        if (!theFolder.exists() || !Files.isDirectory(theFolder)) {
            return false;
        }
        Files.walk(thePathToDelete)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        return true;
    }
}



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

    public void append(L<T> other) {
        if (other.isEmpty()) {
            return;
        }
        ensureCapacity(size + other.size());
        for (int j = 0, i = size; j < other.size(); j++, i++) {
            data[i] = other.get(j);
        }
        size += other.size();
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

    public L<T> filter(Predicate<T> pred) {
        L<T> result = new L();
        int j = 0;
        for (int i = 0; i < size; i++) {
            T elt = data[i];
            if (pred.test(elt)) {
                result.add(elt);
                j++;
            }
        }
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
//{{{ MockFileSys



//}}}
//{{{ Utils
//{{{ Action

@FunctionalInterface
interface Action {
    void run();
}

//}}}

//~static String stringDiff(String a, String b) {
//~
//~}


static <X> void print(X x) { // No relation to the band Static-X
    System.out.println(x);
}

static <X> int findIndex(X[] arr, Predicate<X> pred) {
    for (int i = 0; i < arr.length; i++) {
        if (pred.test(arr[i])) {
            return i;
        }
    }
    return -1;
}


//}}}
//{{{ Tests

//{{{ Internal tests

static class TestResults {
    int countRun;
    int countFailed;
    TestResults()  {
        countRun = 0;
        countFailed = 0;
    }
}


public static void main(String[] args) {
    print("hw");
}



//}}}

}
