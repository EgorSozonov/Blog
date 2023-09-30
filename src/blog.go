//{{{ Package

package src

import (
    "fmt"
    "time"
    "strings"
    io "os"
    "sync"
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
    docCache DocCache
    navTree *NavTree
    navTopic *NavTree
    navTime *NavTree
    updatedAt Ts
    rootPath String
    mutex sync.Mutex
}

type DocCache struct {
    cache map[String]Document
    scripts map[String]bool
    coreJs String
    coreCss String
    notFound *Document
    footer *Document
    rootPage *Document
    termsOfUse *Document
}

type Document struct {
    cont String
    scriptDeps []String
    hasCss bool
    pathCaseSen String
    modified Ts
    pageId int
    wasAddedDb bool
}

func createDocument(cont String, scriptDeps []String, hasCss bool, pathCaseSen String,
                    modified Ts) *Document {
    return &Document {
        cont: cont, scriptDeps: scriptDeps,
        hasCss: hasCss, pathCaseSen: pathCaseSen,
        modified: modified, pageId: -1, wasAddedDb: false,
    }
}

func createIngested(js String, css String, htmlNotFound Ingested, htmlFooter Ingested,
        htmlRoot Ingested, htmlTermsUse Ingested) *IngestedCore {
    return &IngestedCore {
        js: js, css: css, htmlNotFound: htmlNotFound, htmlFooter: htmlFooter, htmlRoot: htmlRoot,
        htmlTermsUse: htmlTermsUse,
    }
}

func (t *Blog) buildGetResponse(subUrl String, queryParams []Tu[String, String]) String {
    t.checkNUpdate(t.rootPath)
    var documentContent *Document
    if subUrl.len() == 0 {
        documentContent = t.docCache.rootPage
    } else if subUrl.toLower() == "termsofuse" {
        documentContent = t.docCache.termsOfUse
    } else {
        documentContent = t.docCache.getDocument(subUrl)
        if documentContent == nil {
            documentContent = t.docCache.notFound
        }
    }
    var modeTemporal = false
    for _, qp := range queryParams {
        if qp.f1 == "temp" {
            modeTemporal = true
            break
        }
    }
    if modeTemporal {
        t.navTree = t.navTime
    } else {
        t.navTree = t.navTopic
    }

    var r strings.Builder
    r.WriteString(template0)
    return String(r.String())
}

//}}}
//{{{ DocCache

func (t DocCache) getModule(modId String) String {
    if _, ok := t.scriptCache[modId]; ok {
        return modId + ".js"
    } else {
        return ""
    }
}

func (t DocCache) getDocument(path0 String) *Document {
    if path0.endsWith(".html") {
        return t.cache[path0.subString(0, path0.length - 5).toLower()]
    } else {
        return t.cache[path0.toLower()]
    }
}

func (t DocCache) addDocument(path0 String, newDoc Document) {
    var path = path0.toLower()
    t.cache[path] = newDoc
}

func (t DocCache) ingestAndRefresh(rootPath String) {
    /// Loads/reloads a set of files and their contents into the cache.
    /// Strips HTML outside the <body> tag so that only the relevant contents make it into the cache.
    if len(t.cache) == 0 {
        // The order must be as follows: first read the core stuff, then the scripts, then the docs
        // This is because core stuff is not overwritten by scripts, and docs depend on scripts and core
        t.readCachedCore(rootPath)
        t.readCachedScripts(rootPath)
        t.readCachedDocs(rootPath)
    }

    fmt.Println("ingesting and refreshing at root path " + rootPath)
    ingestedDocs, ingestedCore = ingestFiles(rootPath, t)
    if ingestedTocs.len() > 0 {
        for i, iD := range ingestedDocs {
            var key = iD.fullPath.toLower().replace(" ", "")
            if iD.(type) == CreateUpdate {
                t.cache[key] = createDocument(
                    inFile.content, inFile.jsModules,
                    inFile.styleContent != "", inFile.fullPath.replace(" ", ""),
                    inFile.modifTime,
                )
            } else { // Delete
                if t.cache[key] != nil { delete(t.cache, key) }
            }
        }
    }

    if ingestedCore.js != "" { t.coreJs = ingestedCore.js }
    if ingestedCore.css != "" { t.coreCss = ingestedCore.css }
    t.notFound = updateCoreDocFromIngested(ingestedCore.htmlNotFound, t.notFound)
    t.rootPage = updateCoreDocFromIngested(ingestedCore.htmlRoot, t.rootPage)
    t.footer = updateCoreDocFromIngested(ingestedCore.htmlFooter, t.footer)
    t.termsOfuse = updateCoreDocFromIngested(ingestedCore.htmlTermsOfUse, t.termsOfUse)

}

func (t DocCache) toPageArray() []Tu[String, Ts] {
    /// Returns an array of [(originalPath) date] useful for constructing navigation trees.
    var result = make([]Tu[String, Ts])
    for _, v := range t.cache { result = result.add(Tu{ f1: v.pathCaseSen, f2: v.modifiedDate }) }
    return result
}

func (t DocCache) getNewDocuments() []Document {
    var result = make([]Document)
    for _, v := range t.cache { if !v.wasAddedDb { result = result.add(v) } }
    return result
}

func (t DocCache) updateCoreDocFromIngested(
        ingested Ingested, existing Document) Document {
    if ingested != nil && ingested.(type) == CreateUpdate {
        return createDocument(
            ingested.content, ingested.jsModules, ingested.styleContent != "", "", ingested.updatedAt,
        )
    } else {
        return existing
    }
}

func (t *Blog) checkNUpdate(rootPath String) { //, conn: Connection) {
    /// Top-level updater function for documents and templates
    var dtNow = time.Now()
    if (time.Sub(t.dtNavUpdated, dtNow).Minutes() <= 5) {
        return
    }

    t.mutex.Lock()
    defer t.mutex.Unlock()

    t.docCache.ingestAndRefresh(rootPath)
    var navTrees = createNavTrees(t.docCache)
    t.navTopic = navTrees.first
    t.navTime = navTrees.second
    t.dtNavUpdated = dtNow
}



//}}}
//{{{ NavTree

type NavTree struct {
    name String
    children []NavTree
}

func (t NavTree) createBreadcrumbs(subAddress String) []int {
    spl = subAddress.split("/")
    result = make([]int, len(spl))
    if t.name != "" || len(t.children) == 0 {
        return make([]int, 0)
    }

    var curr = t.children
    for i, part := range spl {
        result[i] = curr.indexOf(func(a) { a.name == spl[i] })
        // if address is not found in spine, then this is a temporal tree
        // and it can be found in the leaves
        if result[i] < 0 {
            return createBreadcrumbsTemporal(subAddress)
        }
        curr = curr[result[i]].children
    }
    var leafIndex = curr.indexOf(func(a) { a.name == subAddress })
    if leafIndex < 0 {
        return make([]int, 0)
    }
    result[len(spl)] = leafIndex
    return result
}

func (t NavTree) createBreadcrumbsTemporal(subAddress String) []int {
    /// Make breadcrumbs that trace the way to this file through the navigation tree.
    /// Searches the leaves only, which is necessary for tempooral nav trees.
    var st = createStack[Tu[NavTree, int]]()
    st.push(tu(t, 0))
    for st.hasItems() {
        var top = st.peek()
        if top.f2 < top.f1.children.len() {
            var next = top.f1.children[top.f2]
            if next.children.len() > 0 {
                st.push(tu(next, 0))
            } else {
                if next.name == subAddress {
                    result = make([]int, st.len())
                    for i := range st {
                        result[i] = st[i].f2
                    }
                    return result
                }
                top.f2++
            }
        } else {
            st.pop()
            if st.len() > 0 {
                var prevTop = st.peek()
                prevTop.f2++
            }
        }
    }
    return make([]int, 0)
}

func (t NavTree) toJson() String {
    var st = createStack[Tu[NavTree, int]]()
    if t.children.len() == 0 {
        return ""
    }
    var result strings.Builder
    st.push(tu(t, 0))
    for st.len() > 0 {
        var top = st.peek()
        if top.f2 < top.f1.children.len() {
            var next = top.f1.children[top.f2]
            if next.children.len() > 0 {
                result.append("[\"")
                result.append(next.name)
                result.append("\", [")
                st.push(tu(next, 0))
            } else {
                result.append("[\"")
                result.append(next.name)
                if top.f2 == top.f1.children.len() - 1 {
                    result.append("\" [] ] ")
                } else {
                    result.append("\", [] ], ")
                }
            }
        } else {
            st.pop()
            if st.len() > 0 {
                var parent = st.peek()
                if parent.f2 < parent.f1.children.len() {
                    result.append("]], ")
                } else {
                    result.append("]] ")
                }
            }
        }
        top.f2++
    }
    return result.toString()
}

func createNavTrees(docCache DocCache) Tu[NavTree, NavTree] {
    var arrNavigation = docCache.toPageArray()
    var topical = topicalOf(arrNavigation)
    var temporal = temporalOf(arrNavigation)
    return tu(topical, temporal)
}

func comparatorFolders(x, y Tri[String, Ts, []String]) {
    var folderLengthCommon = min(x.f3.len(), y.f3.len())
    for i := range folderLengthCommon {
        var cmp = x.f3[i].compareTo(y.f3[i])
        if cmp != 0 {
            return cmp
        }
    }
    if x.f3.len() != y.f3.len() {
        return y.f3.len().compareTo(x.f3.len())
    } else {
        return x.f3.last().compareTo(y.f3.last())
    }
}

func topicalOf(pages []Tu[String, Ts]) NavTree {
    var pagesByName Tri[String, Ts, []String]
    for _, pg := range pages {
        pagesByName.add(tri(x.f1, x.f2, x.f1.split("/")))
    }
    pagesByName.sort(comparatorFolders)
    var st = make([]NavTree, 4)
    var root = NavTree{ name: "", children: make([]NavTree) }
    st.add(root)
    for i, pg := range pagesByName {
        var spl = pg.f3
        var lenSamePrefix = min(st.len() - 1, spl.len()) - 1
        for lenSamePrefix > -1 && st.cont[lenSamePrefix + 1].name != spl[lenSamePrefix] {
            lenSamePrefix--
        }
        for j := lenSamePrefix + 1; j < spl.len(); j++ {
            var newElem NavTree
            if (j == spl.len() - 1) {
                newElem = NavTree { name: pagesByname[i].f1, children: make([]NavTree) }
            } else {
                newElem = NavTree { name: spl[j], children: make([]NavTree) }
            }
            if j < st.cont.len() {
                st.cont[j + 1] = newElem
            } else {
                st.add(newElem)
            }
            var prev = st.cont[j]
            prev.children.add(newElem)
        }
    }
    return root
}


func temporalOf(pages []Tu[String, Ts]) NavTree {
    var pagesByDate = pages.sort(func(x y) { return x.compareTo(y) })
    var st = createStack[NavTree]()
    var root = NavTree { name: "", children: make([]NavTree) }
    st.add(root)

    for i, pg := range pagesByDate {
        var yearName = page.f2.year.toString()
        var monthName = toName(page.f2.month)
        var lenSamePrefix = min(st.len() - 1, 2) - 1
        if lenSamePrefix > -1 && yearName != st.cont[1].name {
            lenSamePrefix = -1
        }
        if lenSamePrefix > 0 && monthName != st.cont[2].name {
            lenSamePrefix = 0
        }
        for j := lenSamePrefix + 1; j < 3; j++ {
            var name String
            if j == 2 {
                name = page.f1
            } else if j == 1 {
                name = monthName
            } else {
                name = yearName
            }

            var newElem = NavTree{ name: name, children: make([]NavTree) } // leaves contain the full path
            if j < st.lastIndex {
                st.cont[j + 1] = newElem
            } else {
                st.add(newElem)
            }
            var prev = st.cont[j]
            prev.children.add(newElem)
        }
    }
    return root
}

//~func nameOfMonth(month Month) String {
//~    switch month{
//~    case january: return "Jan"
//~    }
//~}

//}}}
//{{{ Datasource: BlogFile

type BlogFile struct {
    /// Ingests all input files (core, scripts and docs), moves them to the internal file cache
    ///  as well as returns the results so that the docs and core files can be updated in memory
}

type Ingested interface {
    IngestedDummy()  // empty method just to satisfy the interface
}

type CreateUpdate struct {
    fullPath String
    cont String
    styleContent String
    modified Ts
    jsModules []String
}

type Delete struct {
    fullPath String
}

func (*CreateUpdate) IngestedDummy() {}
func (*Delete) IngestedDummy() {}


type IngestedCore struct {
    js String
    css String
    htmlNotFound Ingested
    htmlFooter Ingested
    htmlRoot Ingested
    htmlTermsUse Ingested
}

type IngestedJsModule struct {
    fullPath String
    cont String
    modifTime Ts
}

func ingestFiles(rootPath String, docCache DocCache) Tu[[]IngestedFile, Ingested] {
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
    var mediaFiles = make(map[String]bool, 10)
    var scriptNames = make([]String)
    var docFiles = make([]String)
    for _, af := range arrFiles {
        if af.isFile() && file.Name().len() > 5 && file.length < 500000 {
            if file.Name().endsWith(".js") {
                scriptNames.add(af.absolutePath)
            } else if file.Name().endsWith(".html") {
                docFiles.add(ingestDoc(af, ingestPath, mediaFiles))
            }
        }
    }

    var mediaDir = File(rootPath + mediaSubfolder)
    createDirIfNotExists(mediaDir)
    var docDir = rootPath + docsSubfolder
    createDirIfNotExists(docDir)

    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    ingestScripts(scriptNames, rootPath, docCache, false)
    moveDocs(docFiles, rootPath, ingestSubfolder, docsSubfolder)

    return Tu{ docFiles, ingestedCore }
}

func ingestCoreFiles(rootPath String, ingestCorePath String,
                     docCache DocCache, lengthPrefix int) Ingested {
    var js = moveFile(ingestCorePath, rootPath + scriptsSubfolder + globalsSubfolder, "core.js")
    var css = moveFile(ingestCorePath, rootPath + mediaSubfolder + globalsSubfolder, "core.css")
    var favicon = moveFile(ingestCorePath, rootPath + mediaSubfolder, "favicon.ico")

    var mediaFiles = make(map[String]bool)
    var ingestedCoreHtml = make([]IngestedFile)

    var fileNotFound = File(ingestCorePath + "notFound.html")
    var fileFooter = File(ingestCorePath + "footer.html")
    var fileBasePage = File(ingestCorePath + "core.html")
    var fileTerms = File(ingestCorePath + "termsOfUse.html")

    var htmlNotFound *IngestedFIle
    var htmlFooter *IngestedFIle
    var htmlBasePage *IngestedFIle
    var htmlTermsUse *IngestedFIle

    if fileNotFound.exists() {
        htmlNotFound = ingestDoc(fileNotFound, ingestCorePath, mediaFiles)
        ingestedCoreHtml.add(htmlNotFound)
    }
    if fileFooter.exists() {
        htmlFooter = ingestDoc(fileFooter, ingestCorePath, mediaFiles)
        ingestedCoreHtml.add(htmlFooter)
    }
    if fileBasePage.exists() {
        htmlBasePage = ingestDoc(fileBasePage, ingestCorePath, mediaFiles)
        ingestedCoreHtml.add(htmlBasePage)
    }
    if fileTerms.exists() {
        htmlTermsOfUse = ingestDoc(fileTerms, ingestCorePath, mediaFiles)
        ingestedCoreHtml.add(htmlTermsOfUse)
    }

    var arrFiles, err = io.ReadDir(ingestCorePath)
    if err != nil {
        return nil
    }

    var scriptNames = make([]String)
    for _, file := range arrFiles {
        if file.isFile() && file.name.endsWith(".js") && file.name.len() > 5 &&
            file.length < 500000 {
            scriptNames.add(file.absolutePath)
        }
    }
    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    ingestScripts(scriptNames, rootPath, docCache, true)
    moveDocs(ingestedCoreHtml, rootPath, ingestCoreSubfolder, coreSubfolder)

    return Ingested{ js, css, htmlNotFound, htmlFooter, htmlBasePage, htmlTermsUse }
}

func ingestDoc(file File, ingestPath String, mediaFiles map[String]bool) *IngestedFile {
    /// Performs ingestion of an input file: reads its contents, detects referenced
    /// media files and script modules, rewrites the links to them, and decides whether this is an
    /// update/new doc or a delete.
    var lastModified = file.lastModified()
    var indLastDot = file.path.lastIndexOf(".")
    var fileSubpath = file.path.subString(ingestPath.len(), indLastDot) // "topic/subt/file" w/o ext
    var indLastSlash = file.path.lastIndexOf("/")
    var fileSubfolder = file.path.subString(ingestPath.len(), indLastSlash + 1) // "topic/sub"
    var fileContent = file.readText()
    var content = getHtmlBodyStyle(fileContent, ingestPath, fileSubpath, mediaFiles)
    if content.len() < 10 && content.trim() == "" {
        return Delete { fileSubpath }
    }

    var jsModuleNames = parseJokescriptModuleNames(fileContent, fileSubfolder)

    return CreateUpdate {
        fullPath: fileSubpath, cont: content, styleContent: styleContent,
        modified: lastModified, jsModules: jsModuleNames,
    }
}

func ingestScripts(scriptNames []String, rootPath String, docCache DocCache, isGlobal bool) {
    /// Ingests script module files, determines their filenames, puts them into the cache folder.
    targetPath = rootPath + scriptsSubfolder
    io.CreateDirectories(targetPath)

    var prefixLength = rootPath.len()
    if isGlobal {
        prefixLength += ingestCoreSubfolder.len()
    } else {
        prefixLength += ingestSubfolder.len()
    }
    for _, fN := range scriptNames {
        var sourceFile = File(fN)
        var scriptContent = sourceFile.readText()
        var subfolder = fN.subString(prefixLength, fN.len() - sourceFile.name.len())
        var rewrittenContent = rewriteScriptImports(scriptContent, subfolder)

        var modId String
        if isGlobal {
            modId = globalsSubfolder + fN.subString(prefixLength, fN.length - 3) // -3 for ".js"
        } else {
            modId = subfolder + sourceFile.name.subString(0, sourceFile.name.len() - 3)
        }
        docCache.insertModule(modId)
        var targetFile = File(targetPath + modId + ".js")

        if subfolder.len() > 0 {
            io.CreateDirs(targetPath + subfolder)
        } else {
            io.CreateDirs(targetPath + globalsSubfolder)
        }
        targetFile.writeText(rewrittenContent)
        sourceFile.delete()
    }
}

func moveMediaFiles(mediaFiles map[String]bool, rootPath String, lengthPrefix int,
                    targetSubfolder String) {
    /// Moves media files references to which were detected to the /_m subfolder.
    var targetPath = rootPath + mediaSubfolder + targetSubfolder
    for _, fN := range mediaFiles {
        var sourceFile = File(fN)
        var targetFile = File(targetPath + fN.subString(lengthPrefix))
        if targetFile.exists() {
            targetFile.delete()
        }
        sourceFile.copyTo(File(targetPath + fN.subString(lengthPrefix)))
        sourceFile.delete()
    }
}

func moveDocs(incomingFiles []IngestedFile, rootPath String,
              sourceSubfolder String, targetSubfolder String) {
    /// Updates the document stockpile on disk according to the list of ingested files.
    var targetPrefix = rootPath + targetSubfolder
    var targetMediaPrefix = rootPath + mediaSubfolder
    for _, iFile := range incomingFiles {
        if iFile.(type) == CreateUpdate {
            var nameId = iFlie.fullPath.replace(" ", "")
            var sourceHtml = File(rootPath + sourceSubfolder + iFile.fullPath + ".html")
            var fTarget = (targetPrefix + nameId + ".html")
            os.Remove(fTarget)
            fTarget.parentFile.mkdirs()
            fTarget.writeText(iFile.content)

            fStyleTarget = (targetMediaPrefix + nameId + ".css")
            os.Remove(fStyleTarget)
            if iFile.styleContent != "" {
                fStyleTarget.parentFile.mkdirs()
                fStyleTarget.writeText(iFile.styleContent)
            }

            if iFile.jsModules.len() > 0 {
                var depsTarget = File(targetPrefix + nameId + ".deps")
                os.Remove(depsTarget)
                depsTarget.writeText(iFile.jsModules.joinToString("\n"))
            }
            os.Remove(sourceHtml)
        } else if iFile.(type) == IsDelete {
            var sourceFile = File(rootPath + ingestSubfolder + iFile.fullPath + ".html")
            os.Remove(targetPrefix + iFile.fullPath.replace(" ", "") + ".html")
            os.Remove(targetPrefix + iFile.fullPath.replace(" ", "") + ".css")
            os.Remove(targetPrefix + iFile.fullPath.replace(" ", "") + ".deps")
            os.Remove(sourceFile)
        }
    }
}

func readCachedDocs(rootPath String, cache DocCache) {
    var docsDirN = rootPath + docsSubfolder
    var docsDir = File(docsDirN)
    var prefixLength = docsDirN.len()
    var fileList = make([]FileInfo)
    files, err := io.ReadDir(docsDir)
    if err != nil {
        log.fatal(err)
        return
    }
    for _, file := range files {
        if (file.isFile && file.name.endsWith(".html") && file.name.len() > 5 &&
              file.len() < 2000000) {
            fileList.add(file)
        }
    }
    for _, fi := range fileList {
        var address = fi.path.subString(prefixLength, fi.path.length - 5) // -5 for the ".html"
        var depsFile = readFile(docsDir + address + ".deps")
        var deps []String
        if depsFile != "" {
            deps = depsFile.split("\n")
        } else {
            deps = make([]String)
        }
        var hasCSS = File(rootPath + mediaSubfolder + address + ".css").exists()
        var doc = createDocument(fi.readText(), deps, hasCss, address, fi.lastModified)
        cache.addDocument(address, doc)
    }
}

func (t DocCache) readCachedCore(rootPath String) {
    var ingestDirN = rootPath + coreSubfolder

    var fileJs = readFile(ingestDirN + "core.js")
    if fileJs != "" { cache.coreJS = fileJs }

    var fileHtmlRoot = readFile(ingestDirN + "core.html")
    if fileHtmlRoot != "" {
        cache.rootPage = createDocument(fileHtmlRoot.readText(), make([]String), false, "", Time.MIN)
    }
    var fileCss = readText(ingestDirN + "core.css")
    if fileCss != "" { cache.coreCSS = fileCss }
}

func readCachedScripts(rootPath String, docCache DocCache) {
    var ingestDirN = rootPath + scriptsSubfolder
    var ingestDir = File(ingestDirN)
    var prefixLength = ingestDirN.len()
    if fileList, err := io.ReadDir(ingestDirN); err {
        log.Fatal(err)
    }
    for _, fi := range fileList {
        if fi.isFile && fi.name.endsWith(".js") && fi.name.len() > 5 && fi.len() < 500000 {
            var shortFileName = fi.path.subString(prefixLength, fi.path.len() - 3) // -3 for the ".js"
            docCache.insertModule(shortFileName)
        }
    }
}

func moveFile(sourcePath String, targetPath String, fNShort String) {
    result = readFile(sourcePath + fNShort)
    (targetPath + fNShort).deleteFile()
    if fTarget.exists() {
        fTarget.delete()
    }
    fTarget.parentFile.mkdirs()
    fTarget.writeText(result)
    file.delete()
    return result
}

//}}}
//{{{ Rewriter

func rewriteLinks(content String, ingestPath String, fileSubpath String,
                  mediaFiles map[String]bool) {
    /// Rewrites links in a document from to point to new position inside /_m.
    /// Returns the document contents with rewritten links.
    var result = StringBuilder(content.len() + 100)
    var prevInd = 0
    var currInd int
    var indLastSlash = fileSubpath.lastIndexOf("/")
    var fileSubfolder String
    if indLastSlash < 1 {
        fileSubfolder = ""
    } else {
        fileSubfolder = fileSubpath.subString(0, indLastSlash).pathize()
    }
    for {
        currInd = content.indexOf("src=\"", prevInd)
        if currInd == -1 {
            result.append(content.subString(prevInd))
            return result.toString()
        }
        result.append(content.subString(prevInd, currInd))
        result.append("src=\"")
        var indClosingQuote = content.indexOf("\"", currInd + 5)
        if indClosingQuote > -1 {
            var link = content.subString(currInd + 5, indClosingQuote) // cutting original `src="`
            var sourcePath = (ingestPath + fileSubfolder).pathize() + link
            var targetSubpath = "/" + appSubfolder + mediaSubfolder + fileSubfolder + link
            if File(sourcePath).exists() {
                mediaFiles.add(sourcePath)
            }
            result.append(targetSubPath)
            currInd = indClosingQuote
        }
        prevInd = currind
    }
}

func parseJokescriptModuleNames(fileContent String, fileSubfolder String) []String {
    var result = make([]String)
    var headStart = fileContent.indexOf("<head>", 0, true)
    if headStart < 0 {
        return result
    }

    var headEnd = fileContent.indexOf("</head>", headStart, true)
    if headEnd < 0 {
        return result
    }

    var headString = fileContent.subString(headStart + 6, headEnd) // +6 for `<head>`
    var i = 0
    for {
        i = headString.indexOf("<script", i, true)
        if i < 0 {
            break
        }
        i += 7 // +7 for the `<script`

        var j = headString.indexOf(">", i, true)
        var spl = headString.subString(i, j).split(" ")

        var srcString = spl.firstOrNull(func(a) { a.startsWith("src=\"") } )
        if srcString == nil {
            continue
        }
        var indJs = srcString.indexOf(".js", 5, true) // 5 for `src="`
        if indJs < 0 {
            continue
        }

        var rawModuleName = srcString.subString(5, indJs)
        if rawModuleName.startsWith("./") { // adjacent file
            result.add(fileSubfolder + rawModuleName.subStringToEnd(2))
        } else { // global script module
            result.add(globalsSubfolder + rawModuleName)
        }
    }
    return result
}

func rewriteScriptImports() {
    /// Rewrites the JokeScript imports to correctly reference files on the server,
    /// including global imports
    var spl = script.split("\n")
    var i = 0
    for i < spl.len() && spl[i].startsWith("import") {
        i++
    }
    var scriptPrefix = "/" + appSubfolder + scriptsSubfolder
    for j := range i {
        var indFrom = spl[j].indexOf("\"")
        if indFrom < 0 {
            return ""
        }
        var tail = spl[j].subString(indFrom + 1)
        if tail.startsWith("global/") {
            spl[j] = spl[j].subString(0, indFrom + 1) + scriptPrefix +
                globalsSubfolder + spl[j].subString(indFrom + 8) // +8 for `"global/`
        } else if tail.startsWith("./") {
            spl[j] = spl[j].subString(0, indFrom + 1) + scriptPrefix +
                subfolder + spl[j].subString(indFrom + 3) // +3 for `"./`
        } else {
            return ""
        }
    }
    return spl.joinToString("\n")
}

func getHtmlBodyStyle() {
    /// Gets the contents of the "body" tag inside the HTML, as well as contents of the
    /// "style" tag inside the head
    var indStart = html.indexOf("<body>")
    var indEnd = html.lastIndexOf("</body>")
    if indStart < 0 || indEnd < 0 || (indEnd - indStart) < 7 {
        return Tu(html, "")
    }
    var bodyRewritten = rewriteLinks(html.subString(indStart + 6, indEnd), ingestPath,
        fileSubpath, mediaFiles)
    var indStyleStart = html.indexOf("<style>")
    var indStyleEnd = html.indexOf("</style>")
    var style = ""
    if indStyleStart > -1 && indStyleEnd > -1 && (indStyleEnd - indStyleStart) >= 8 {
        style = html.subString(indStyleStart + 7, indStyleEnd).trim()
    } else {
        style = ""
    }
    return Tu{bodyRewritten, style}
}

//}}}
//{{{ Utils

type Tu[T any, U any] struct {
    f1 T
    f2 U
}

func tu[T any, U any](f1 T, f2 U) Tu[T, U] {
    return Tu { f1: f1, f2: f2 }
}

type Tri[T any, U any, V any] struct {
    f1 T
    f2 U
    f3 V
}

func tri[T any, U any, V any](a T, b U, c V) {
    return Tri { f1: a, f2: b, f3: c }
}

func createSet(es ...String) map[String]bool {
    var result = map[String]bool{}
    for _, v := range es {
        result[v] = true
    }
    return result
}

type Slice[T any] []T

func (t Slice[T]) indexOf(needle T) int {
    /// IndexOf returns the first index of needle in haystack
    for i, v := range haystack {
        if v == needle {
            return i
        }
    }
    return -1
}


func (t String) pathize() String {
    if t.endsWith("/") {
        return t
    } else {
        return t + "/"
    }
}

func (t []T) add(elt T) []T {
    return append(t, elt)
}

type String string

func (t String) len() int {
    return len(t)
}

func (t map[T]U) len() int {
    return len(t)
}

func (t String) toLower() String {
    return strings.ToLower(t)
}

func (t String) endsWith(s String) int {
    return strings.HasSuffix(t, s)
}

func (t String) replace(a String, replaceWith String) String {
    return strings.Replace(t, a, replaceWith, -1)
}

func (t String) subString(start int, end int) String {
    return t[start:end]
}

func (t String) subStringToEnd(start int) String {
    return t[start:]
}

func (t String) split(s String) int {
    return strings.Split(t, s)
}

func printAllFiles(dirN String) {
    files, err := os.ReadDir(dirN)
    if err != nil {
        return
    }

    for _, fi := range files {
        fmt.Println(readFile(pathize(dirN) + fi.Name()))
    }
}


func readFile(fN String) String {
    fi, err := os.ReadFile(fN)
    if err == nil {
        return String(fi)
    } else {
        return ""
    }
}

func createDirIfNotExists(dirN String) {
    if _, err := os.Stat(dirN); os.IsNotExist(err) {
        if err := os.MkdirAll(dirN, os.ModePerm); err != nil {
            log.Fatal(err)
        }
    }
}

//{{{ Stack

type Stack[T any] struct {
    cont []T
}

func createStack[T any]() Stack[T] {
    var cont = make([]T)
    return Stack { cont: cont }
}

func (t Stack[T]) hasItems() bool {
    return len(t.cont) > 0
}

func (t Stack[T]) push(newV T) {
    t.cont = append(t.cont, newV)
}


func (t Stack[T]) pop() T {
    var result = t.cont[-1]
    t.cont = t.cont[:len(t.cont) - 1]
    return result
}

func (t []T) peek() bool {
    return len(t.cont) > 0
}

//}}}
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
