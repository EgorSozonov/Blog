/// Unit testing for blog
import assert from 'node:assert';

interface FileSys {
   saveOverwriteFile(dir: string, fN: string, cont: string): Promise<boolean>;
   deleteIfExists(dir: string, fN: string): Promise<boolean>;
   getNamesWithPrefix(dir: string, prefix: string): Promise<string[]>;
}

function print(a: any) {
   console.log(a)
}

class MockFileSys implements FileSys {
   private fs: Map<string, MockFile[]>;
   
   constructor() {
      this.fs = new Map<string, MockFile[]>();
   }


   async saveOverwriteFile(dir: string, fN: string, cont: string): Promise<boolean> {
      const newFile: MockFile = { name: fN, cont: cont, modified: new Date() }; 
      if(this.fs.has(dir)) {
         const existingFiles = this.fs.get(dir)!!;
         const indexExisting = existingFiles.findIndex(x => x.name == fN);
         if (indexExisting === -1) {
            existingFiles.push(newFile)
         } else {
            existingFiles[indexExisting] = newFile
         }
      } else {    
         this.fs.set(dir, [newFile])
      }
      return true; 
   }
   
   async deleteIfExists(dir: string, fN: string): Promise<boolean> {
      if(!this.fs.has(dir)) {
         return true;
      }
      const existingFiles = this.fs.get(dir)!!;
      const indexExisting = existingFiles.findIndex(x => x.name === fN);
      if(indexExisting > -1)  {
         existingFiles.splice(indexExisting, 1) // removeAt()
      }
      return true;
   }
   
   async getNamesWithPrefix(dir: string, prefix: string): Promise<string[]> {
      if(!this.fs.has(dir)) {
         return [];
      }
      const existingFiles = this.fs.get(dir)!!;
      const filtered = existingFiles.filter((x: MockFile) => x.name.startsWith(prefix))
                             .map((x: MockFile) => x.name);
      return filtered ? filtered : []; 
   }
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


async function testSaveFile() {
   const fs = new MockFileSys()
   const resSave = await fs.saveOverwriteFile(`a/b`, `myFile.txt`, `An ode to joy`)
   assert(resSave === true, `testSaveFile`) 
}


async function testFilePrefixes() {
   const fs = new MockFileSys()
   await fs.saveOverwriteFile(`a/b`, `myFile.txt`, `An ode to joy`)
   await fs.saveOverwriteFile(`a/b`, `myFile-2.txt`, `An ode to joy`)
   await fs.saveOverwriteFile(`a/b`, `myFile-3.txt`, `An ode to joy`)
   const versions = await fs.getNamesWithPrefix(`a/b`, `myFile`)
   assertArrsEqual(versions, [`myFile.txt`, `myFile-2.txt`, `myFile-3.txt`])
}


function assertArrsEqual(a: string[], b: string[]) {
   if(a.length !== b.length)  {
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


async function runTest(testFun: () => void, res: TestResults) {
   try {
      await testFun()
   } catch (err: unknown) {
      if (err instanceof Error) {
         console.dir(err)        
      }
   }
   res.countRun += 1
}


async function main() {
   const results: TestResults = { countRun: 0, countFailed: 0 };

   await runTest(testSaveFile, results)
   await runTest(testFilePrefixes, results)
   
   console.dir(results)
}


main();


export {}
