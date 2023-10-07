/// Unit testing for blog
const assert = require('node:assert').strict;

interface FileSys {
    saveOverwriteFile(dir: string, fN: string, cont: string): Promise<boolean>;
    deleteIfExists(dir: string, fN: string): Promise<boolean>;
    getNamesWithPrefix(dir: string, prefix: string): Promise<string[]>;
}

class MockFileSys implements FileSys {
    private fs: Map<string, MockFile[]>;
    
    constructor() {
        this.fs = new Map<string, MockFile[]>();
    }


    async saveOverwriteFile(dir: string, fN: string, cont: string): Promise<boolean> {
        const newFile: MockFile = { name: fN, cont: cont, modified: new Date() }; 
        if(this.fs.has(dir)) {
            const existingFiles = cont[dir];
            const indexExisting = existingFiles.indexOf(x => x.name == fN);
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
        const existingFiles = this.fs[dir];
        const indexExisting = existingFiles.indexOf(x => x.name == fN);
        if(indexExisting > -1)  {
            existingFiles.splice(indexExisting, 1) // removeAt()
        }
        return true;
    }
    
    async getNamesWithPrefix(dir: string, prefix: string): Promise<string[]> {
        if(!this.fs.has(dir)) {
            return [];
        }
        const existingFiles = this.fs[dir];
        return existingFiles.filter(x => x.name.startsWith(prefix)).map(x => x.name);
    }
}

class MockFile {
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

async function runTest(testFun: () => void, res: TestResults) {
    try {
        testFun()
    } catch (e: any) {
        res.countFailed += 1
    }
    res.countRun += 1
}

const results: TestResults = { countRun: 0, countFailed: 0 };

runTest(testSaveFile, results)

console.asgdir(results)



export {}
