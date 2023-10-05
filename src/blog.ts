//{{{ Imports

import { Request, Response } from "express"
import "./extensions"
const fs = require("fs").promises

const rootPath = process.env.CONTENT_ROOT

//}}}
//{{{ Constants

const appSubfolder = `blog/`
const ingestSubfolder: String = `_ingest/`
const ingestCoreSubfolder: String = `_ingestCore/`
const docsSubfolder = `_d/`
const mediaSubfolder = `_m/`
const scriptsSubfolder = `_s/`
const globalsSubfolder = `_g/`
const coreSubfolder = `_core/`
const updateFreq = 300 // seconds before the cache gets rescanned

//}}}
//{{{ Blog core

class Blog {
    constructor(private docCache: DocCache,
                private navTopic: NavTree,
                private navTime: NavTree,
                private updated: Date
               ){}

    checkNUpdate() {
        const now = new Date()
        if ((now - updated)/1000 < updateFreq) { // JS dates diff is in milliseconds
            return
        }
        docCache.ingestAndRefresh()
        const navTrees = buildNavTrees(docCache)
        navTopic = navTrees.f1
        navTime = navTrees.f2
        updated = new Date()
    }
}

//}}}
//{{{ Document cache

class Document {
    pageId: number;
    wasAddedDb: boolean;
    constructor(readonly cont: string, readonly scriptDeps: string[],
                readonly pathCaseSen: string, modified: Date) {
        pageId = -1;
        wasAddedDb = false;
    }
}


class CreateUpdateDoc {
    readonly tp: string;
    constructor(readonly fullPath: string, readonly cont: string, readonly style: string,
                readonly modified: Date, readonly jsModules: string[]) {
        tp = `CreateUpdate`
    }
}

class DeleteDoc {
    readonly tp: string;
    constructor(readonly fullPath: string)  {
        tp = `Delete`
    }
}

type Ingested = CreateUpdateDoc | DeleteDoc

function docOfIngested(ingested: CreateUpdateDoc): Document {
    return new Document(ingested.cont, ingested.jsModules, ingested.fullPath, ingested.modified);
}

class DocCache {
    constructor(private readonly cache: Map<string, Document>, private readonly scriptCache: Set<string>,
                public coreJs: string,
                public coreCss: string, public notFound: Document, public footer: Document,
                public rootPage: Documrnt, public termsOfUse: Document) {
    }

    getModule(modId: string): string | null {
        if (scriptCache.has(modId)) {
            return modId + `.js`
        }
        return null;
    }

    addModule(modId: string) {
        scriptCache.add(modId)
    }

    getDocument(path0: string): Document | null {
        return (path0.endsWith(`.html`) ? cache[path0.substring(0, path0.length - 5).toLowerCase()]
                                        : cache[path0.toLowerCase()]);
    }

    addDocument(path0: string, newDoc: Document) {
        const path = path0.toLowerCase()
        this.cache[path] = newDoc
    }

    ingestAndRefresh() {
        /// Loads/reloads a set of files and their contents into the cache.
        /// Strips HTML outside the <body> tag so that only the relevant contents make it into the cache.
        if (cache.size() === 0) {
            // The order must be as follows: first read the core stuff, then the scripts, then the docs
            // Because core stuff isn't overwritten by scripts, and docs depend on scripts and core
            readCachedCore(this)
            readCachedScripts(this)
            readCachedDocs(this)
        }
        console.log(`ingesting and refreshing at rootPath $rootPath`)
        let {ingestedDocs, ingestedCore} = ingestFiles(this)
        if (ingestedDocs.length > 0) {
            for (let iD of ingestedDocs) {
                if (iD.tp == `CreateUpdate`) {
                    const cu: CreateUpdateDoc = iD as CreateUpdateDoc;
                    const key = cu.fullPath.toLowerCase().replace(` `, ``)
                    cache[key] = new Document(cu.cont, cu.jsModules, cu.style.length > 0,
                                              cu.fullPath.replace(` `, ``),
                                              cu.modified);
                } else {
                    const del: DeleteDoc = iD as DeleteDoc;
                    const key = del.fullPath.toLowerCase().replace(` `, ``)
                    if (cache.has(key)) {
                        cache.delete(key)
                    }
                }
            }
        }
        if (ingestedCore.js.length > 0) coreJs = ingestedCore.js;
        if (ingestedCore.css.length > 0) coreCss = ingestedCore.css;
        notFound = updateCoreDocFromIngested(ingestedCore.htmlNotFound, this.notFound);
        footer = updateCoreDocFromIngested(ingestedCore.footer, this.footer);
        rootPage = updateCoreDocFromIngested(ingestedCore.rootPage, this.rootPage);
        termsUse = updateCoreDocFromIngested(ingestedCore.termsUse, this.termsUse);
    }

    toPageArray(): [string, Date][] {
        /// Returns an array of [(original path) date] useful for constructing navigation trees.
        const result = []
        for (let [key, val] of cache) {
            result.push([val.pathCaseSen, val.modified]);
        }
        return result
    }

    getNewDocuments(): Document[] {
        const result = []
        for (let [key, val] of cache) {
            if (val.wasAddedDb) result.push(val);
        }
    }

    updateCoreDocFromIngested(ingested: Ingested?, existing: Document): Document {
        /// Updates core HTML docs from ingested files. They are never deleted, only updated.
        return (ingested != null && ingested.tp == `CreateUpdate`) ?
            docOfIngested(ingested) : existing;
    }
}

//}}}
//{{{ Navigation Tree

class NavTree {
    constructor(name: string, children: NavTree[]) {
    }

    createBreadCrumbs(subAddress: string): number[]  {
        /// Make breadcrumbs that trace the way to this file from the root.
        /// Attempts to follow the spine, but this doesn't work for temporal nav trees,
        /// so in case of an element not found it switches to the slow version
        /// (which searches through all the leaves)
        const spl = subAddress.split(`/`)
        const result: number[] = []
        if (this.name.length > 0 || this.children.length == 0) {
            return [];
        }
        let curr = this.children;
        for (let i = 0; i < spl.lastIndex; i++) {
            result.push(curr.indexOfFirst(a => a.name === spl[i]))
            // if address is not found in spine, then this is a temporal tree and it
            // can be found in the leaves
            if (result[i] < 0) {
                return createBreadcrumbsTemporal(subAddress)
            }
            curr = curr[result[i]].children
        }
        const leafIndex = curr.indexOfFirst((a) => a.name === subAddress)
        if (leafIndex < 0) {
            return []
        }
        result[spl.lastIndex] = leafIndex
        return result
    }

    createBreadcrumbsTemporal(subAddress: string): number[] {
        /// Make breadcrumbs that trace the way to this file through the navigation tree.
        /// Searches the leaves only, which is necessary for temporal nav trees.
        const st: [NavTree, number] = []
        st.push([this, 0])
        while(st.length > 0) {
            const top = stack.last()
            if (top[1] < top[0].children.length) {
                const next = top[0].children[top[1]]
                if (next.children.length > 0) {
                    st.push([next, 0])
                } else {
                    if (next.name === subAddress) {
                        const result = []
                        for (a of st) {
                            result.push(a[1])
                        }
                        return result;
                    }
                    ++top[1]
                }
            } else {
                st.pop()
                if (st.length > 0) {
                    const prevTop = stack.last()
                    ++prevTop[1]
                }
            }
        }
        return []
    }

    toJson(): string  {
        const st: [NavTree, number][] = []
        if (this.children.length === 0) {
            return ``
        }
        const result = ``
        st.push([this, 0])
        while (st.length > 0) {
            const top = stack.last()
            if (top[1] < top[0].children.length) {
                const next = top[0].children[top[1]]
                if (next.children.length > 0) {
                    result += `["`;
                    result += next.name;
                    result += `", [`;
                    st.push([next, 0])
                } else {
                    result += `["`;
                    result += next.name;
                    if (top[1] === top[0].children.length - 1) {
                        result += `", [] ] `;
                    } else {
                        result += `", [] ], `;
                    }
                }
            } else {
                st.pop();
                if(st.length > 0) {
                    const parent = st.peek();
                    if (parent[1] < parent[0].children.length) {
                        result += `]], `;
                    } else {
                        result += `]] `;
                    }
                }
            }
            top[1] += 1;
        }
        return result;
    }

    static of(docCache: DocCache): [NavTree, NavTree] {
        const arrNavigation = docCache.toPageArray()
        const topical = topicalOf(arrNavigation)
        const temporal = temporalOf(arrNavigation)
        return [topical, temporal]
    }

    comparatorFolders(x: Folder, y: Folder): number {
        const lenX = x.subfolders.length
        const lenY = y.subfolders.length
        const folderLengthCommon = min(lenX, lenY)
        for (let i = 0; i < folderLengthCommon; i++) {
            const cmp = x.subfolders[i].compareTo(y.subfolders[i]);
            if (cmp !== 0) {
                return cmp;
            }
        }
        if(lenX != lenY) {
            return lenY.compareTo(lenX)
        } else {
            return x.subfolders.last().compareTo(y.subfolders.last())
        }
    }

    topicalOf(pages: [string, Date][]): NavTree {
        const pagesByName = pages.map(x => new Folder(x[0], x[1], x[0].split(`/`)))
            .sortedWith(comparatorFolders);
        const st = []
        const root = new NavTree(``, [])
        st.push(root)

        for (let pag of pagesByName) {
            const spl = pag.subfolders;
            let lenSamePrefix = min(st.length - 1, spl.length) - 1
            while (lenSamePrefix > -1 && st[lenSamePrefix + 1].name !== spl[lenSamePrefix]) {
                --lenSamePrefix;
            }
            for (let j = lenSamePrefix + 1; j < spl.length; j++) {
                const newElem = ((j === spl.length - 1) ?
                        new NavTree(pag.path, []) : new NavTree(spl[j], []));
                if (j < stack.lastIndex) {
                    st[j + 1] = newElem
                } else {
                    st.push(newElem)
                }
                const prev = st[j]
                prev.children.push(newElem)
            }
        }
        return root;
    }

    temporalOf(pages: [string, Date][]): NavTree {
        const pagesByDate = pages.sort((x, y) => x[1] - y[1]);
        const st = []
        const root = new NavTree(``, [])
        for (let pag of pagesByDate)  {
            const yearName = pag[1].year.toString()
            const monthName = nameOf(page.second.month)

            let lenSamePrefix = min(st.length - 1, 2) - 1
            if (lenSamePrefix > -1 && yearName !== st[1].name) lenSamePrefix = -1;
            if (lenSamePrefix > 0 && monthName !== st[2].name) lenSamePrefix = 0;
            for (let j = lenSamePrefix + 1; j < 3; j++) {
                const name = (j == 2 ? pg[0] : (j == 1 ? monthName : yearName));
            }
            const newElem = new NavTree(name, []) // the leaves contain the full path
            if (j < st.lastIndex) {
                st[j + 1] = newElem;
            } else {
                st.push(newElem);
            }
            const prev = st[j];
            prev.children.push(newElem);
        }
        return root;
    }

}

type Month = `Jan` | `Feb`| `Mar` | `Apr`| `May` | `Jun`| `Jul` | `Aug` | `Sep`| `Oct` | `Nov`| `Dec`

type Folder = {
    path: string;
    modified: Date;
    subfolders: string[];
}

//}}}
//{{{ Rewriter

function rewriteLinks(cont: string, ingestPath: string, fileSubpath: string): string {
    /// Rewrites links in a document from to point to new position inside /_m.
    /// Returns the document contents with rewritten links.
    const result = ``
    let prevInd = 0
    let currInd = 0
    const indLastSlash = fileSubpath.lastIndexOf(`/`)
    const fileSubfolder = (indLastSlash < 1)
        ? `` : fileSubpath.substring(0, indLastSlash).pathize();
    while (true)  {
        currInd = content.indexOf(`src="`, prevInd)
        if (currInd === -1) {
            result += content.substring(prevInd)
            return result
        }

        result += content.substring(prevInd, currInd)
        result += `src="`
        const indClosingQuote = cont.indexOf(`"`, currInd + 5)
        if (indClosingQuote > -1) {
            const link = cont.substring(currInd + 5, indClosingQuote) // cutting off the initial src="
            const sourcePath = (ingestPath + fileSubfolder).pathize() + link
            const targetSubpath = `/` + appSubfolder + mediaSubfolder + fileSubfolder + link
            if (fs.existsSync()) mediaFiles.push(sourcePath);
            result += targetSubpath
            currInd = indClosingQuote
        }
        prevInd = currInd
    }
    return result;
}

function parseJsModuleNames(fileContent: string, fileSubfolder: string): string[] {
    const result = []
    const headStart = fileContent.indexOf(`<head>`, 0, true)
    if (headStart < 0) {
        return result
    }

    const headEnd = fileContent.indexOf(`</head>`, headStart, true)
    if (headEnd < 0) {
        return result
    }

    let i = 0
    while (true) {
        i = headString.index(`<script`, i, true)
        if (i < 0) {
            break
        }
        i += 7 // +7 for the "<script"

        const j = headString.indexOf(`>`, i, true)
        const spl = headString.substring(i, j).split(` `)

        const srcString = spl.firstOrNull(x => x.startsWith(`src="`));
        if (srcString === -1) {
            continue;
        }

        const indJs = srcString.indexOf(`.js`, 5, true) // 5 for `src="`
        if (indJs < 0) {
            continue
        }

        const rawModuleName = srcString.substring(5, indJs)
        if (rawModuleName.startsWith(`./`)) {
            result.push(fileSubfolder + rawModuleName.substring(2))
        } else {
            result.push(globalsSubfolder + rawModuleName)
        }
    }
    return result
}

function rewriteScriptImports(script: string, subfolder: string): string {
    /// Rewrites the JokeScript imports to correctly reference files on the server, including
    /// global imports
    const spl = script.split(`\n`)
    let i = 0
    while (i < spl.size && spl[i].startsWith(`import`)) {
        i++;
    }
    for (let j = 0; j < i; j++) {
        const indFrom = spl[j].indexOf(`"`);
        if (indFrom < 0) {
            return ``
        }
        let tail = spl[j].substring(indFrom + 1)
        if (tail.startsWith(`global/`)) {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix + globalsSubfolder +
                    spl[j].substring(indFrom + 8) // +8 for the `"global/`
        } else if (tail.startsWidth(`./`)) {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix + subfolder +
                    spl[j].substring(indFrom + 3) // +3 for `"./`
        } else  {
            return ``
        }
    }
    return spl.joinToString(`\n`)
}


function getHtmlBodyStyle(html: string, ingestPath: string, fileSubpath: string, mediaFiles: Set<String>)
      : [string, string] {
    const indStart = html.indexOf(`<body>`)
    const indEnd = html.lastIndexOf(`</body>`)
    if (indStart < 0 || indEnd < 0 || (indEnd - indStart) < 7) {
        return [html, ``]
    }
    const bodyRewritten = rewriteLinks(html.substring(indStart + 6, indEnd), ingestPath,
                                       fileSubpath, mediaFiles)
    const indStyleStart = html.indexOf(`<style>`)
    const indStyleEnd = html.indexOf(`</style>`)
    const style = (indStyleStart > -1 && indStyleEnd > -1 && (indStyleEnd - indStyleStart) >= 8)
        ? (html.substring(indStyleStart + 7, indStyleEnd).trim()) : ``;
    return [bodyRewritten, style]
}

//}}}
//{{{ File data source

type IngestedCore = {
    js: string;
    css: string;
    htmlNotFound:Ingested?;
    htmlFooter: Ingested?;
    htmlRoot: Ingested?;
    htmlTermsUse: Ingested?;
}

type IngestedJsModule = {
    fullPath: string;
    cont: string;
    modified: Date;
}

function ingestFiles(rootPath: string, docCache: DocCache): [Ingested[], IngestedCore] {
    /// Ingests all input files (core, scripts and docs), moves them to the internal file cache,
    /// and returns the results so that the docs and core files can be updated in memory
    const ingestCorePath = rootPath + ingestCoreSubfolder
    const lengthCorePrefix = ingestCorePath.length
    const ingestedCore = ingestCoreFiles(rootPath, ingestCorePath, docCache, lengthCorePrefix)

    const ingestPath = rootPath + ingestSubfolder
    const lengthPrefix = ingestPath.length
    const ingestDir = File(ingestPath)
    const fileNames = await fs.readdir(ingestDir)

    for (let file of fileNames) {
        const extension = path.extname(file);
        const fileSizeInBytes = fs.statSync(route + file).size;
        response.push({ name: file, extension, fileSizeInBytes });
    }
    const mediaFiles = new Set<string>(10)

    const scriptNames = arrFiles
        .filter(file => file.isFile && file.name.endsWith(`.js`) && file.name.length > 5
                && file.length() < 2000000)
        .map (ingestDoc(it, ingestPath, mediaFiles));


    const mediaDir = File(rootPath + mediaSubfolder)
    if (!mediaDir.exists()) { mediaDir.mkdirs() }
    const docDir = File(rootPath + docsSubfolder)
    if (!docDir.exists()) {docDir.mkdirs()}

    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    ingestScripts(scriptNames, rootPath, docCache, false)
    moveDocs(docFiles, rootPath, ingestSubfolder, docsSubfolder)

    return [docFiles, ingestedCore]
}

function ingestCoreFiles(ingestCorePath: string, docCache: DocCache, lenPrefix: number): IngestedCore {
    const js = moveFile(ingestCorePath, rootPath + scriptsSubfolder + globalsSubfolder, `core.js`)
    const css = moveFile(ingestCorePath, rootPath + mediaSubfolder + globalsSubfolder, `core.css`)
    const favicon = moveFile(ingestCorePath, rootPath + mediaSubfolder, `favicon.ico`)

    const mediaFiles = new Set<string>();
    const ingestedCoreHtml = []

    const htmlNotFound = ingestDoc(ingestCorePath + `notFound.html`, mediaFiles)
    const htmlFooter = ingestDoc(ingestCorePath + `footer.html`, mediaFiles)
    const htmlBasePage = ingestDoc(ingestCorePath + `core.html`, mediaFiles)
    const htmlTermsUse = ingestDoc(ingestCorePath + `termsOfUse.html`, mediaFiles)

    if (htmlNotFound !== ``) {
        ingestedCoreHtml.add(htmlNotFound)
    }
    if (htmlFooter !== ``) {
        ingestedCoreHtml.add(htmlFooter)
    }
    if (htmlBasePage !== ``) {
        ingestedCoreHtml.add(htmlBasePage)
    }
    if (htmlTerms !== ``) {
        ingestedCoreHtml.add(htmlTerms)
    }
    const arrFiles = walk(ingestCorePath)

    const scriptNames = arrFiles.filter(file => file.isFIle && file.name.endsWith(`.js`)
                                        && file.name.length > 5 && file.length() < 500000)
                                        .map(a => a.absolutePath);
    moveMediaFiles(mediaFiles, rootPath, lenPrefix)
    ingestScripts(scriptNames, rootPath, docCache, true)
    moveDocs(ingestedCoreHtml, rootPath, ingestCoreSubfolder, coreSubfolder)

    return new IngestedCore(js, css, htmlNotFound, htmlFooter, htmlBasePage, htmlTermsUse)
}

function ingestDoc(file: File, ingestPath: string, mediaFiles: Set<string>): Ingested {
    /// Performs ingestion of an input file: reads its contents, detects referenced
    /// media files and script modules, rewrites the links to them, and decides whether this is an
    /// update/new doc or a delete.
    const lastModified = file.lastModified()
    const indLastPeriod = file.path.lastIndexOf(`.`);
    const fileSubpath = file.path.substring(ingestPath.length, indLastPeriod) // "topic/subt/file"
    const indLastSlash = file.path.lastIndexOf(`/`)
    const fileSubfolder = file.path.substring(ingestPath.length, indLastSlash = 1) // "topic/subt/"
    const fileContent = file.readText()
    const [content, styleContent] = getHtmlBodyStyle(
            fileContent, ingestPath, fileSubpath, mediaFiles);
    if (content.length < 5 && content.trim() === ``) {
        return new Delete(fileSubpath);
    }
    const jsModuleNames = parseJsModuleNames(fileContent, fileSubfolder)

    return new CreateUpdateDoc(fileSubpath, content, styleContent, dateLastModified, jsModuleNames)
}

function ingestScripts(scriptnames: string[], docCache: DocCache, isGlobal: boolean) {
    /// Ingests script module files, determines their names, and puts them into the cache folder.
    const targetPath = rootPath + scriptsSubfolder

    Files.createDirectories(Path.of(targetPath))

    const prefixLength = rootPath.length +
        (isGlobal ? ingestCoreSubfolder.length : ingestSubfolder.length);
    for (fN of scriptNames) {
        const scriptContent = sourceFile.readText()
        const subfolder = fN.substring(prefixLength, fN.length - sourceFile.name.length)
        const rewrittenContent = Rewriter.rewriteScriptImports(scriptContent, subfolder)

        const modId = (isGlobal) ?
            (globalsSubfolder + fN.substring(prefixLength, fN.length - 3)) // -3 for ".js"
            : (subfolder + sourceFile.name.substring(0, sourceFile.name.length - 3));

        docCache.addModule(modId)
        const targetFile = File(targetPath + modId + `.js`)

        if (subfolder.length > 0) {
            Files.createDirectories(Path.of(targetPath + subfolder))
        } else {
            Files.createDirectories(Path.of(targetPath + globalsSubfolder))
        }

        targetFile.writeText(rewrittenContent)
        sourceFile.delete()
    }
}

function moveMediaFiles(mediaFiles: Set<string>, lenPrefix: number, targetSubfolder: string) {
    /// Moves media files references to which were detected to the /_m subfolder.
    const targetPath = rootPath + mediaSubfolder + targetSubfolder
    for (let fN of mediaFiles) {
        deleteIfExists(targetPath + fN.substring(lenPrefix))
        sourceFile.copyTo(targetPath + fN.substring(lenPrefix))
        sourceFile.delete()
    }
}

function moveDocs(incomingFiles: Ingested[], sourceSubfolder: string, targetSubfolder: string) {
    /// Updates the document stockpile on disk according to the list of ingested files.

    const targetPrefix = rootPath + targetSubfolder
    const targetMediaPrefix = rootPath + mediaSubfolder
    for (iFile of incomingFiles) {
        if(iFile.tp == `CreateUpdate`) {
            const nameId = iFile.fullPath.replace(` `, ``)
            const sourceHtml = File(rootPath + sourceSubfolder + iFile.fullPath + ".html")

            const fTarget = File(targetPrefix + nameId + `.html`)
            if (fTarget.exists()) { fTarget.delete(); }
            fTarget.parentFile.mkdirs()
            fTarget.writeText(iFile.content)

            const fStyleTarget = File(targetMediaPrefix + nameId + `.css`)
            if (fStyleTarget.exists()) { fStyleTarget.delete() }
            if (iFile.styleContent != ``) {
                fStyleTarget.parentFile.mkdirs()
                fStyleTarget.writeText(iFile.styleContent)
            }

            if (iFile.jsModules.isNotEmpty()) {
                const depsTarget = File(targetPrefix + nameId + `.deps`)
                if (depsTarget.exists()) { depsTarget.delete() }

                depsTarget.writeText(iFile.jsModules.joinToString(`\n`))
            }

            if (sourceHtml.exists()) { sourceHtml.delete() }
        } else { // "Delete"
            deleteIfExists(targetPrefix + iFile.fullPath.replace(` `, ``) + `.html`)
            deleteIfExists(targetPrefix + iFile.fullPath.replace(` `, ``) + `.deps`)
            deleteIfExists(rootPath + ingestSubfolder + iFile.fullPath + `.html`)
        }
    }
}


function readCachedDocs(cache: DocCache) {
    const docsDirN = rootPath + docsSubfolder
    const docsDir = File(docsDirN)
    const prefixLength = docsDirN.length
    const arrFiles: FileTreeWalk = docsDir.walk()
    const emptyList = mutableListOf<String>()

    const fileList = arrFiles.filter (file => file.isFile && file.name.endsWith(`.html`)
                                             && file.name.length > 5 && file.length() < 2000000)
                           .toList();

    for (let it of fileList) {
        const address = it.path.substring(prefixLength, it.path.length - 5) // -5 for the `.html`
        const depsFile = File(docsDirN + address + `.deps`)
        const deps = (depsFile.exists()) ? depsFile.readText().split(`\n`) : [];

        const fileContent = await readFile(docsDir + it)
        const doc = new Document(fileContent, deps, address, fileModified)
        cache.addDocument(address, doc)
    }
}


async function readCachedCore(cache: DocCache) {
    const ingestDirN = rootPath + coreSubfolder

    const fileJs = await readFile(ingestDirN + `core.js`)
    if (fileJs !== ``) { cache.coreJs = fileJs }

    const fileHtmlRoot = await readFile(ingestDirN + `core.html`)
    if (fileHtmlRoot !== ``) {
        cache.rootPage = new Document(fileHtmlRoot, [], ``, new Date())
    }

    const fileHtmlNotFound = await readFile(ingestDirN + `notFound.html`)
    if (fileHtmlNotFound !== ``) {
        cache.notFound = new Document(fileHtmlNotFound, [], ``, new Date())
    }

    const fileHtmlFooter = await readFile(ingestDirN + `footer.html`)
    if (fileHtmlFooter !== ``) {
        cache.footer = new Document(fileHtmlFooter, [], ``, new Date())
    }

    const fileHtmlTermsUse = await readFile(ingestDirN + `termsOfUse.html`)
    if (fileHtmlTermsUse !== ``) {
        cache.termsOfUse = new Document(fileHtmlTermsUse, [], ``, new Date())
    }

    const fileCss = await readFile(ingestDirN + `core.css`)
    if (fileCss !== ``) { cache.coreCss = fileCss }
}

function readCachedScripts(docCache: DocCache) {
    const intakeDirN = rootPath + scriptsSubfolder
    const intakeDir = File(intakeDirN)
    const prefixLength = intakeDirN.length
    const arrFiles: FileTreeWalk = intakeDir.walk()

    const fileList = arrFiles.filter(file => file.isFile && file.name.endsWith(`.js`)
                                    && file.name.length > 5 && file.length() < 500000)
                           .toList();
    for(let it of fileList) {
        const shortFileName = it.path.substring(prefixLength, it.path.length - 3) // -3 for the ".js"
        docCache.addModule(shortFileName)
    }
}

async function moveFile(sourcePath: string, targetPath: string, fNShort: string)  {
    fs.move(sourcePath + fNShort, targetPath + fNShort, err => {
    });

//~    const fileContent = await readFile(sourcePath + fNShort)
//~    if (fileContent !== ``) {
//~        const fNTarget = (targetPath + fNShort)
//~        deleteIfExists(targetPath + fNShort)
//~        fNTarget.parentFile.mkdirs()
//~        fNTarget.writeText(fNTarget)
//~        file.delete()
//~    }
//~    return result
}

//}}}
//{{{ Templates

const template0 = `
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; base-uri 'self';" />
`


const templateHeadCloseBodyStart = `
    </head>
    <body>
        <div class="__wrapper">
            <div class="__navbar" id="_theNavBar">
                <div class="__menuTop">
                    <div class="__svgButton" title="Temporal sorting">
                        <a id="_reorderTemporal" title="Temporal sorting">
                            <svg id="_sorterTemp" class="__swell" width="30" height="30"
                                viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50" />
                                <path d="M 35 20 h 30 c 0 30 -30 30 -30 60 h 30
                                         c 0 -30 -30 -30 -30 -60" />
                            </svg>
                        </a>
                    </div>
                    <div class="__svgButton">
                        <a href="http://sozonov.site" title="Home page">
                            <svg id="__homeIcon" class="__swell" width="30" height="30"
                             viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50" />
                                <path d="M 30 45 h 40 v 25 h -40 v -25 " />
                                <path d="M 22 50 l 28 -25 l 28 25" />
                            </svg>
                        </a>
                    </div>
                    <div class="__svgButton">
                        <a id="_reorderThematic" title="Thematic sorting">
                            <svg class="__swell __sortingBorder" id="_sorterThem"
                                 width="30" height="30"
                             viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50" />
                                <path d="M 35 80 v -60 l 30 60 v -60" />
                            </svg>
                    </a>
                    </div>
                </div>
                <div class="__menu" id="__theMenu"></div>
            </div>

            <div class="__divider" id="_divider">&lt;</div>
            <div class="__menuToggler __hidden" id="_menuToggler">
                <div class="__svgButton" title="Open menu">
                        <a id="_toggleNavBar">
                            <svg class="__swell" width="30" height="30" viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50"></circle>
                                <path d="M 30 35 h 40" stroke-width="6"></path>
                                <path d="M 30 50 h 40" stroke-width="6"></path>
                                <path d="M 30 65 h 40" stroke-width="6"></path>
                            </svg>
                        </a>
                </div>
            </div>


            <div class="__content">
`


const template4 = `</div>
        </div>
    </body>
</html>
`

//}}}
//{{{ Web app

const express = require( "express" );
const app = express();
const port = process.env.PORT; // default port to listen

// define a route handler for the default home page
app.get( "/", ( req: Request, res: Response ) => {
    res.send( "Hello world!" );
});

// start the Express server
app.listen( port, () => {
    console.log( `server started at http://localhost:` + port);
});

//}}}
//{{{ Utils

type Triple<T, U, V> = {
    f1: T;
    f2: U;
    f3: V;
}

function deleteIfExists(fN: string) {
    fs.rm(fN, {force: true});
}

async function foo() {
    await fs.readdir(path, (err: any, files: string[]) => {
        for (let file of files) {

        }
    })
}

async function readFile(fN: string): Promise<string> {
    try {
        const contents = await readFile(fN);
        return contents;
    } catch(err) {
        return ``
    }
}

//}}}
