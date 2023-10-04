declare global {
   interface Array<T> {
       last(): T;
   }
   interface String { 
        pathize(): string;
   } 
}

Array.prototype.last = function(): T {
    return this[this.length - 1]
}


String.prototype.pathize = function(): string {
    return this.endsWith("/") ? this : (this + "/");
}
