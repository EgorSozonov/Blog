//{{{ Imports

import { Request, Response } from "express"
import "./extensions"
const fs = require("fs")

//}}}
//{{{ Constants

const appSubfolder = "blog/"
const ingestSubfolder: String = "_ingest/"
const ingestCoreSubfolder: String = "_ingestCore/"
const docsSubfolder = "_d/"
const mediaSubfolder = "_m/"
const scriptsSubfolder = "_s/"
const globalsSubfolder = "_g/"
const coreSubfolder = "_core/"
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
        if (now - updated < updateFreq) {
            return
        }
        mutex {
            docCache.ingestAndRefresh()
            const navTrees = buildNavTrees(docCache)
            navTopic = navTrees.f1
            navTime = navTrees.f2
            updated = new Date()
        }
    }
}

//}}}
//{{{ Document cache

class Document {
    pageId: number;
    wasAddedDb: boolean;
    constructor(const cont: string, const scriptDeps: string[], const hasCss: boolean,
                const pathCaseSen: string, modified: Date) {
        pageId = -1;
        wasAddedDb = false;
    }
}

class CreateUpdateDoc {
    readonly tp: string;
    constructor(const fullPath: string, const cont: string, const style: string, const modified: Date,
                const jsModules: string[]) {
        tp = "CreateUpdate"
    }
}

class DeleteDoc {
    readonly tp: string;
    constructor(const fullPath: string)  {
        tp = "Delete"
    }
}

type Ingested = CreateUpdateDoc | DeleteDoc

class DocCache {
    constructor(private const cache: Map<string, Document>, private scriptCache: Set<string>,
                private coreJs: string,
                private coreCss: string, private notFound: Document, private footer: Document,
                private rootPage: string, private termsUse: string) {
    }

    getModule(modId: string): string? {
        if (scriptCache.has(modId)) {
            return modId + ".js"
        }
        return null;
    }

    addModule(modId: string) {
        scriptCache.add(modId)
    }

    getDocument(path0: string): Document? {
        return (path0.endsWith(".html") ? cache[path0.substring(0, path0.length - 5).toLowerCase()]
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
        let [ingestedDocs, ingestedCore] = ingestFiles(this)
        if (ingestedDocs.length > 0) {
            for (let iD of ingestedDocs) {
                if (iD.tp == "CreateUpdate") {
                    const cu: CreateUpdateDoc = (CreateUpdateDoc)iD;
                    const key = cu.fullPath.toLowerCase().replace(" ", "")
                    cache[key] = new Document(cu.cont, cu.jsModules, cu.style.length > 0,
                                              cu.fullPath.replace(" ", ""),
                                              cu.modified);
                } else {
                    const del: DeleteDoc = (DeleteDoc)iD;
                    const key = del.fullPath.toLowerCase().replace(" ", "")
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
        return (ingested != null && ingested.tp == "CreateUpdate") ?
            new Document(ingested.cont, ingested.jsModules,...) : existing;
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
        const spl = subAddress.split("/")
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
        const st = [NavTree, number][]
        st.push([this, 0])
        while(st.length > 0) {
            const top = stack.last()
            if (top[1] < top[0].children.length) {
                const next = top[0].children[top[1]]
                if (next.children.length > ) {
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
            return ""
        }
        const result = ""
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
        const pagesByName = pages.map(x => new Folder(x[0], x[1], x[0].split("/")))
            .sortedWith(comparatorFolders);
        const st = []
        const root = new NavTree("", [])
        st.push(root)

        for (let pag of pagesByName) {
            const spl = pag.subfolders;
            let lenSamePrefix = min(st.length - 1, spl.length) - 1
            while (lenSamePrefix > -1 && st[lenSamePrefix + 1].name !== spl[lenSamePrefix) {
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
        const pagesByDate = pages.sortedWith(compareBy it[1]);
        const st = []
        const root = new NavTree("", [])
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


    protected fun toName(month: Month): String {
        return when (month) {
            Month.JANUARY -> "Jan"
            Month.FEBRUARY -> "Feb"
            Month.MARCH -> "Mar"
            Month.APRIL -> "Apr"
            Month.MAY -> "May"
            Month.JUNE -> "Jun"
            Month.JULY -> "Jul"
            Month.AUGUST -> "Aug"
            Month.SEPTEMBER -> "Sep"
            Month.OCTOBER -> "Oct"
            Month.NOVEMBER -> "Nov"
            Month.DECEMBER -> "Dec"
        }
    }
}

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
    const result = ""
    let prevInd = 0
    let currInd = 0
    const indLastSlash = fileSubpath.lastIndexOf("/")
    const fileSubfolder = (indLastSlash < 1)
        ? "" : fileSubpath.substring(0, indLastSlash).pathize();
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
            const targetSubpath = "/" + appSubfolder + mediaSubfolder + fileSubfolder + link
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
    const headStart = fileContent.indexOf("<head>", 0, true)
    if (headStart < 0) {
        return result
    }

    const headEnd = fileContent.indexOf("</head>", headStart, true)
    if (headEnd < 0) {
        return result
    }

    let i = 0
    while (true) {
        i = headString.index("<script", i, true)
        if (i < 0) {
            break
        }
        i += 7 // +7 for the "<script"

        const j = headString.indexOf(">", i, true)
        const spl = headString.substring(i, j).split(" ")

        const srcString = spl.firstOrNull { it.startsWith(`src="`) } ?: continue

        const indJs = srcString.indexOf(".js", 5, true) // 5 for `src="`
        if (indJs < 0) {
            continue
        }

        const rawModuleName = srcString.substring(5, indJs)
        if (rawModuleName.startsWith("./")) {
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
    const spl = script.split("\n")
    let i = 0
    while (i < spl.size && spl[i].startsWith("import")) {
        i++;
    }
    for (let j = 0; j < i; j++) {
        const indFrom = spl[j].indexOf(`"`);
        if (indFrom < 0) {
            return ""
        }
        let tail = spl[j].substring(indFrom + 1)
        if (tail.startsWith(`global/`)) {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix + globalsSubfolder +
                    spl[j].substring(indFrom + 8) // +8 for the `"global/`
        } else if (tail.startsWidth("./")) {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix + subfolder +
                    spl[j].substring(indFrom + 3) // +3 for `"./`
        } else  {
            return ""
        }
    }
    return spl.joinToString("\n")
}


getHtmlBodyStyle(html: string, ingestPath: string, fileSubpath: string, mediaFiles: Set<String>)
      : [string, string] {
    const indStart = html.indexOf(`<body>`)
    const indEnd = html.lastIndexOf(`</body>`)
    if (indStart < 0 || indEnd < 0 || (indEnd - indStart) < 7) {
        return [html, ""]
    }
    const bodyRewritten = rewriteLinks(html.substring(indStart + 6, indEnd), ingestPath,
                                       fileSubpath, mediaFiles)
    const indStyleStart = html.indexOf(`<style>`)
    const indStyleEnd = html.indexOf(`</style>`)
    const style = (indStyleStart > -1 && indStyleEnd > -1 && (indStyleEnd - indStyleStart) >= 8)
        ? (html.substring(indStyleStart + 7, indStyleEnd).trim()) : "";
    return [bodyRewritten, style]
}

//}}}
//{{{ File data source

class BlogFile {

}

//}}}
//{{{ Web app

const express = require( "express" );
const app = express();
const port = 8000; // default port to listen

// define a route handler for the default home page
app.get( "/", ( req: Request, res: Response ) => {
    res.send( "Hello world!" );
});

// start the Express server
app.listen( port, () => {
    console.log( `server started at http://localhost:${ port }` );
});

//}}}
//{{{ Utils

type Triple<T, U, V> = {
    f1: T;
    f2: U;
    f3: V;
}

//}}}
