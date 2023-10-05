declare global {
   interface Array<T> {
       last(): T;
   }
   interface String { 
        pathize(): string;
   } 
}

Array.prototype.last = function() {
    return this[this.length - 1]
}


String.prototype.pathize = function(this: string): string {
    return this.endsWith(`/`) ? this : (this + `/`);
}

export {}
