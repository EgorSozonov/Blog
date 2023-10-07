/// Unit testing for blog
import assert from 'node:assert';

interface FileSys {
   dirExists(dir: string): Promise<boolean>;
   listFiles(dir: string): Promise<FileInfo[]>;
   listDirs(dir: string): Promise<string[]>;
   saveOverwriteFile(dir: string, fN: string, cont: string): Promise<boolean>;
   moveFile(dir: string, fN: string, targetDir: string): Promise<boolean>;
   moveFileToNewVersion(dir: string, fN: string, targetDir: string): Promise<string>;
   deleteIfExists(dir: string, fN: string): Promise<boolean>;
   getNamesWithPrefix(dir: string, prefix: string): Promise<string[]>;
}

//{{{ MockFileSys

class MockFileSys implements FileSys {
   private fs: Map<string, MockFile[]>;

   constructor() {
      this.fs = new Map<string, MockFile[]>();
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


   async dirExists(dir: string): Promise<boolean> {
      return this.fs.has(dir)
   }

   async listFiles(dir: string): Promise<FileInfo[]> {
      if (!this.fs.has(dir)) {
         return []
      }
      const files = this.fs.get(dir)!!;
      return files.map((x: MockFile): FileInfo => {return { name: x.name, modified: x.modified }})
   }

   async listDirs(dir: string): Promise<string[]> {
      const dirWithSl = (dir.endsWith(`/`)) ? dir : dir + `/`;
      const subfolders = Array.from(this.fs.keys()).filter((x: string) => x.startsWith(dirWithSl));
      return subfolders.map((x: string): string => {
         const indSlash = x.indexOf(`/`);
         return (indSlash < 0) ? x : x.substring(0, indSlash);
      });
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

   async moveFile(dir: string, fN: string, targetDir: string): Promise<boolean> {
      const sourceFiles = this.fs.get(dir)!!;
      const indexSource = sourceFiles.findIndex(x => x.name == fN);
      const targetFiles = this.fs.get(targetDir)!!;
      const sourceFile = sourceFiles[indexSource];

      const indexTarget = targetFiles.findIndex(x => x.name == fN);
      if (indexTarget === -1)  {
         targetFiles.push(sourceFile);
      } else {
         targetFiles[indexTarget] = sourceFile;
      }

      sourceFiles.splice(indexSource, 1)
      return true;
   }

   async moveFileToNewVersion(dir: string, fN: string, targetDir: string): Promise<string> {
      const sourceFiles = this.fs.get(dir)!!;
      const indexSource = sourceFiles.findIndex(x => x.name == fN);
      const targetFiles = this.fs.get(targetDir)!!;
      const sourceFile = sourceFiles[indexSource];

      const existingWithThisPrefix = await this.getNamesWithPrefix(targetDir, shaveOffExtension(fN));
      let newName = fN;

      if (existingWithThisPrefix.length === 0)  {
         targetFiles.push(sourceFile);
      } else {
         const maxExistingVersion = getMaxVersion(existingWithThisPrefix);
         const indLastDot = fN.lastIndexOf(`.`);
         newName = fN.substring(0, indLastDot) + `-` + (maxExistingVersion + 1)
                   + fN.substring(indLastDot);

         targetFiles.push({
            name: newName, cont: sourceFile.cont, modified: sourceFile.modified
         });
      }

      sourceFiles.splice(indexSource, 1)
      return newName;
   }

   async deleteIfExists(dir: string, fN: string): Promise<boolean> {
      if(!this.fs.has(dir)) {
         return true;
      }
      const existingFiles = this.fs.get(dir)!!;
      const indexExisting = existingFiles.findIndex(x => x.name === fN);
      if(indexExisting > -1)   {
         existingFiles.splice(indexExisting, 1) // removeAt()
      }
      return true;
   }

}

//}}}
//{{{ Tests

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

async function testMaxVersion() {
   const a = getMaxVersion([`file.txt`, `file-2.txt`, `file-3.txt`])
   assert(a === 3, `testMaxVersion`)
}

async function testIngestCore() {
   const fs = new MockFileSys()
   await fs.saveOverwriteFile(`/var/www/blog/a.b`, `myFile.txt`, `An ode to joy`)
   await fs.saveOverwriteFile(`/var/www/blog/a.b`, `myFile-2.txt`, `An ode to joy`)
   await fs.saveOverwriteFile(`/var/www/blog/_c`, `core.css`, `Styles`)
   await fs.saveOverwriteFile(`/var/www/blog/_c`, `graph.js`, `Flexible core script`)

   const blog = new Blog(fs);
   blog.ingestCore()

}

//}}}
//{{{ Constants

const staticDir = `/var/www/blst`
const ingestDir = `/var/www/blog`

const appSuburl = `blog/`;
const coreSubfolder = `_c/`;
const updateFreq = 300; // seconds before the cache gets rescanned
const coreFiles = [`notFound.png`, `notFound.html`, `core.css`, `core.html`, `core.js`,
                            `favicon.ico`, `footer.html`, `no.png`, `yes.png`, `termsOfUse.html`];
//}}}
//{{{ Utils

function print(a: any) {
   console.log(a)
}

function shaveOffExtension(fN: string): string {
   const indDot = fN.indexOf(`.`)
   if (indDot < 0) {
      return fN
   }
   return fN.substring(0, indDot)
}

function getMaxVersion(filenames: string[]): number {
   /// For a list like `file.txt, file-2.txt, file-3.txt`, returns 3.
   const withoutExts = filenames.map((x: string) => shaveOffExtension(x));
   const versions = []
   let result = 1
   for (let shortName of withoutExts) {
      const indDash = shortName.lastIndexOf(`-`)
      if (indDash < 0 || indDash === (shortName.length - 1))  {
         continue;
      }
      const mbNumber = parseInt(shortName.substring(indDash + 1));
      if (Number.isNaN(mbNumber)) {
         continue;
      } else if (result < mbNumber) {
         result = mbNumber
      }
   }
   return result
}

class Blog {
   constructor(private fs: FileSys) {
   }

   async ingestCore(): Promise<boolean> {
      /// Processes the core ingest folder. Returns true iff it had any core files to ingest
      /// (which implies that all HTML in the Blog needs to be regenerated)
      const dir = getCoreDir();
      if (!(await this.fs.dirExists(dir))) {
         return false;
      }
      const fileList = await this.fs.listFiles(dir);
      for (let inFile of fileList) {
         if (isFixedCore(inFile.name)) {
            this.moveFixedCore(dir, inFile)
         }
      }
      return true;
   }

   private async moveFixedCore(dir: string, inFile: FileInfo): Promise<string> {
      /// Moves a fixed core file (i.e. not an additional script module) and returns its version
      const sourceDir = getCoreDir();
      const targetDir = staticDir + coreSubfolder;
      const newVersion = await this.fs.moveFileToNewVersion(sourceDir, inFile.name, targetDir)
      return newVersion
   }
}

type FileInfo = {
   name: string;
   modified: Date;
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


function assertArrsEqual(a: string[], b: string[]) {
   if(a.length !== b.length)   {
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


function isFixedCore(fN: string): boolean  {
   for (let i = 0; i < coreFiles.length; i++) {
      if (coreFiles[i] === fN) {
         return true
      }
   }
   return false;
}

function getCoreDir(): string  {
   return ingestDir + coreSubfolder;
}


//}}}
//{{{ Main
async function runTest(testFun: () => void, res: TestResults) {
   try {
      await testFun()
   } catch (err: unknown) {
      if (err instanceof Error) {
         console.dir(err)
      }
      res.countFailed += 1
   }
   res.countRun += 1
}


async function main() {
   const results: TestResults = { countRun: 0, countFailed: 0 };

   await runTest(testSaveFile, results)
   await runTest(testFilePrefixes, results)
   await runTest(testMaxVersion, results)

   console.dir(results)
}


main();

export {}

//}}}
