//{{{ Package

package src

import (
    "fmt"
    "time"
    s "strings"
    io "io/ioutil"
)

type Ts time.Time

//}}}
//{{{ Constants

const appSubfolder = "blog/"
const ingestSubfolder = "_ingest/"
const ingestCoreSubfolder = "_ingestCore/"
const docsSubfolder = "_d/"
const mediaSubfolder = "_m/"
const scriptsSubfolder = "_s/"
const globalsSubfolder = "_g/"
const coreSubfolder = "_core/"

//}}}
//{{{ Core

type Blog struct {
    docCache DocumentCache
    navTopic NavTree
    navTime NavTree
    updatedAt Ts
    rootPath string
}

type DocumentCache struct {
    cache map[string]Document
    scripts map[string]bool
    coreJS string
    coreCSS string
    notFound Document
    footer Document
    rootPage Document
    termsOfUse Document
}

type Document struct {
    content string
    scriptDeps string[]
    hasCSS bool
    pathCaseSen string
    modified Ts
    pageId int
    wasAddedDb bool
}

//}}}
//{{{ DocumentCache

func (t DocumentCache) getModule(modId string) String {
    if _, ok := t.scriptCache[modId]; ok {
        return modId + ".js"
    } else {
        return ""
    }
}

func (t DocumentCache) getDocument(path0 string) Document {
    if s.HasSuffix(path0, ".html") {
        return t.cache[s.ToLower(path0[0, path0.length - 5])]
    } else {
        return t.cache[s.ToLower(path0)]
    }
}

func (t DocumentCache) addDocument(path0 string, newDoc Document) {
    var path = s.ToLower(path0)
    t.cache[path] = newDoc
}

func (t DocumentCache) ingestAndRefresh(rootPath string) {
    /// Loads/reloads a set of files and their contents into the cache.
    /// Strips HTML outside the <body> tag so that only the relevant contents make it into the cache.
    if len(t.cache) == 0 {
        // The order must be as follows: first read the core stuff, then the scripts, then the docs
        // This is because core stuff is not overwritten by scripts, and docs depend on scripts and core
        readCachedCore(rootPath, t)
        readCachedScripts(rootPath, t)
        readCachedDocs(rootPath, t)
    }

    fmt.Println("ingesting and refreshing at root path $rootPath")
    ingestedDocs, ingestedCore = ingestFiles(rootPath, t)
    if len(ingestedTocs) > 0 {
        for i := range ingestedDocs {
            ???
        }
    }
    if ingestedCore.js != "" { t.coreJS = ingestedCore.js }
    if ingestedCore.css != "" { t.coreCSS = ingestedCore.css }
    t.notFound = updateCoreDocFromIngested(ingestedCore.htmlNotFound, t.notFound)
    t.rootPage = updateCoreDocFromIngested(ingestedCore.htmlRoot, t.rootPage)
    t.footer = updateCoreDocFromIngested(ingestedCore.htmlFooter, t.footer)
    t.termsOfuse = updateCoreDocFromIngested(ingestedCore.htmlTermsOfUse, t.termsOfUse)

}

func (t DocumentCache) toPageArray() Document[] {
    /// Returns an array of [(original path) date] useful for constructing navigation trees.
    return t.cache.entries.filter(func(a) {!a.value.wasAddedDb}).map(func(a) {a.value})
}

func (t DocumentCache) getNewDocuments() Document[] {
    return t.cache.entries.filter(func(a) {!a.value.wasAddedDb}).map(func(a) {a.value})
}

func (t DocumentCache) updateCoreDocFromIngested(ingested IngestedFile,
                                                 existing Document) Document {
    return if ingested != nil && ingested == CreateUpdate {
        Document{ingested.content, ingested.jsModules, ingested.styleContent != "", "",
                ingested.updatedAt, -1, false}
    } else {
        existing
    }
}

//}}}
//{{{ Datasource: BlogFile

type BlogFile struct {
    /// Ingests all input files (core, scripts and docs), moves them to the internal file cache
    ///  as well as returns the results so that the docs and core files can be updated in memory
}

func ingestFiles(rootPath string, docCache DocumentCache) Tu[[]IngestedFile, IngestedCore] {
    var ingestCorePath = rootPath + ingestCoreSubfolder
    var lengthCorePrefix = ingestCorePath.length
    var ingestedCore = ingestCoreFiles(rootPath, ingestCorePath, docCache, lengthCorePrefix)

    var ingestPath = rootPath + ingestSubfolder
    var lengthPrefix = ingestPath.length
    var ingestDir = File(ingestPath)
    var arrFiles, err = io.ReadDir(ingestPath)
    if err != nil {
        return nil
    }
    var mediaFiles = make(map[string]bool, 10)
    var scriptNames = arrFiles .filter () .map() .toList()
    var docFiles = arrFiles .filter() .map() .toList()

    var mediaDir = File(rootPath + mediaSubfolder)
    if !mediaDir.exists {
        mediaDir.mkDirs()
    }
    var docDir = File(rootPath + docsSubfolder)
    if !docDir.exists {
        docDir.mkDirs()
    }
    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    ingestScripts(scriptNames, rootPath, docCache, false)
    moveDocs(docFiles, rootPath, ingestSubfolder, docsSubfolder)

    return Tu.new(docFiles, ingestedCore)
}

func ingestCoreFiles(rootPath string, intakeCorePath string,
                     docCache DocumentCache, lengthPrefix int) IngestedCore {
    var js = moveFile()
    var css = moveFile()
}

//}}}
//{{{ Utils

type Tu[T any, U any] struct {
    f1 T
    f2 U
}

func createSet(es ...string) map[string]bool {
    var result = map[string]bool{}
    for _, v := range es {
        result[v] = true
    }
    return result
}

//}}}
