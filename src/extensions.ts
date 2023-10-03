declare global {
   interface Array<T> {
       last(): T;
   }
}
Array.prototype.last = function(): T {
    return this[this.length - 1]
}
