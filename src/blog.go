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

func (t *Blog) buildGetResponse(subUrl string, queryParams []Tu[string, string]) string {
    checkNUpdate(t, rootPath)
    var documentContent
    if subUrl.len() == 0 {
        documentContent = docCache.rootPage
    } else if subUrl.toLower() == "termsofuse" {
        documentContent = docCache.termsOfUse
    } else {
        documentContent = getDocument(subUrl)
        if documentContent == nil {
            documentContent = docCache.notFound
        }
    }
    var modeTemporal = false
    for _, qp := queryParams {
        if qp.f1 == "temp" {
            modeTemporal = true
            break
        }
    }
    var navTree
    if modeTemporal {
        navTree = navTime
    } else {
        navTree = navTopic
    }

    var r s.Builder
    r.WriteString(template0)
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
//{{{ NavTree

type NavTree struct {
    name string
    children []NavTree
}

func (t NavTree) createBreadcrumbs(subAddress string) []int {
    spl = s.Split(subAddress, "/")
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

func (t NavTree) createBreadcrumbsTemporal(subAddress string) []int {
    /// Make breadcrumbs that trace the way to this file through the navigation tree.
    /// Searches the leaves only, which is necessary for tempooral nav trees.
    var stack = Stack[Tu[NavTree, int]]{}
    stack.push(Tu { t, 0 })
    while stack.any() {
        var top = stack.peek()
        if top.f2 < top.f1.children.len() {
            var next =
                stack.push(Tu { next, 0 })
            } else {
                if next.name == subAddress {
                    result = make([]int, stack.len())
                    for i := range stack {
                        result[i] = stack[i].f2
                    }
                    return result
                }
                ++top.f2
            }
        } else {
            stack.pop()
            if stack.len() > 0 {
                var prevTop = stack.peek()
                ++prevTop.f2
            }
        }
    }
    return make([]int, 0)
}

func (t NavTree) toJson() string {
    var stack = Stack[Tu[NavTree, int]]
    if t.children.len() == 0 {
        return ""
    }
    val result = StringBuilder(100)
    stack.push(Tu{t, 0})
    while stack.len() > 0 {
        var top = stack.peek()
        if top.f2 < top.f1.children.len() {
            var next = top.f1.children[top.f2]
            if next.children.len() > 0 {
                result.append("[\"")
                result.append(next.name)
                result.append("\", [")
                stack.push(Tu{ next, 0 })
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
            stack.pop()
            if stack.len() > 0 {
                var parent = stack.peek()
                if parent.f2 < parent.f1.children.len() {
                    result.append("]], ")
                } else {
                    result.append("]] ")
                }
            }
        }
        ++top.f2
    }
    return result.toString()
}

func createNavTree(docCache DocumentCache) Tu[NavTree, NavTree] {
    var arrNavigation = docCache.toPageArray()
    var topical = topicalOf(arrNavigation)
    var temporal = temporalOf(arrNavigation)
    return Tu { topical, temporal }
}

func comparatorFolders(x, y Tri[string, Ts, []string) {
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

func topicalOf(pages []Tu[string, Ts]) NavTree {
    var pagesByName = pages.map(func(x) { x.f1, x.f2, x.f1.split("/") })
                            .sortWith(comparatorFolders)
    var stack = make([]NavTree, 4)
    var root = NavTree{ "", make([], 0) }
    stack.add(root)
    for i, pg := range pagesByName {
        var spl = pg.f3
        var lenSamePrefix = min(stack.len() - 1, spl.len()) - 1
        while lenSamePrefix > -1 && stack[lenSamePrefix + 1].name != spl[lenSamePrefix]  {
            --lenSamePrefix
        }
        for j range(lenSamePrefix + 1, spl.len()) {
            var newElem
            if (j == spl.len() - 1) {
                newElem = NavTree { pagesByname[i].f1, make([], 0) }
            } else {
                newElem = NavTree { spl[j], make([], 0) }
            }
            if j < stack.lastIndex {
                stack[j + 1] = newElem
            } else {
                stack.add(newElem)
            }
            var prev = stack[j]
            prev.children.add(newElem)
        }
    }
    return root
}


func temporalOf(pages []Tu[string, Ts]) NavTree {
    var pagesByDate = pages.sort(func(x y) { return x.compareTo(y) })
    var stack = make([], 0)
    var root = NavTree{ "", make([], 0) }
    stack.add(root)

    for i, pg := range pagesByDate {
        var yearName = page.f2.year.toString()
        var monthName = toName(page.f2.month)
        var lenSamePrefix = min(stack.len() - 1, 2) - 1
        if lenSamePrefix > -1 && yearName != stack[1].name {
            lenSamePrefix = -1
        }
        if lenSamePrefix > 0 && monthName != stack[2].name {
            lenSamePrefix = 0
        }
        for j := range(lenSamePrefix + 1, 3) {
            var name
            if j == 2 {
                name = page.f1
            } else if j == 1 {
                name = monthName
            } else {
                name = yearName
            }

            var newElem = NavTree{ name, make([], 0) } // leaves contain the full path
            if j < satck.lastIndex {
                stack[j + 1] = newElem
            } else {
                stack.add(newElem)
            }
            var prev = stack[j]
            prev.children.add(newElem)
        }
    }
    return root
}

func nameOfMonth(month Month) string {
    switch month{
    case january: return "Jan"
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

    return Tu{ docFiles, ingestedCore }
}

func ingestCoreFiles(rootPath string, ingestCorePath string,
                     docCache DocumentCache, lengthPrefix int) IngestedCore {
    var js = moveFile(ingestCorePath, rootPath + scriptsSubfolder + globalsSubfolder, "core.js")
    var css = moveFile(ingestCorePath, rootPath + mediaSubfolder + globalsSubfolder, "core.css")
    var favicon = moveFile(ingestCorePath, rootPath + mediaSubfolder, "favicon.ico")

    var mediaFiles = make(map[string]bool)
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

    var scriptNames = make([]string)
    for _, file := arrFiles {
        if file.isFile() && file.name.endsWith(".js") && file.name.len() > 5 &&
            file.length < 500000 {
            scriptNames.add(file.absolutePath)
        }
    }
    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    ingestScripts(scriptNames, rootPath, docCache, true)
    moveDocs(ingestedCoreHtml, rootPath, ingestCoreSubfolder, coreSubfolder)

    return IngestedCore{ js, css, htmlNotFound, htmlFooter, htmlBasePage, htmlTermsUse }
}

func ingestDoc(file File, ingestPath string, mediaFiles map[string]bool) *IngestedFile {
    /// Performs ingestion of an input file: reads its contents, detects referenced
    /// media files and script modules, rewrites the links to them, and decides whether this is an
    /// update/new doc or a delete.
    var lastModified = file.lastModified()
    var indLastDot = file.path.lastIndexOf(".")
    var fileSubpath = file.path.substring(ingestPath.len(), indLastDot) // "topic/subt/file" w/o ext
    var indLastSlash = file.path.lastIndexOf("/")
    var fileSubfolder = file.path.substring(ingestPath.len(), indLastSlash + 1) // "topic/sub"
    var fileContent = file.readText()
    var content = getHtmlBodyStyle(fileContent, ingestPath, fileSubpath, mediaFiles)
    if content.len() < 10 && content.trim() == "" {
        return IngestedFile { Delete , fileSubpath }
    }

    var jsModuleNames = parseJokescriptModuleNames(fileContent, fileSubfolder)

    return IngestedFile { CreateUpdate, fileSubpath, content, styleContent, lastModified, jsModuleNames)

}

func ingestScripts(scriptNames []string, rootPath string, docCache DocumentCache, isGlobal bool) {
    /// Ingests script module files, determines their filenames, puts them into the cache folder.
    targetPath = rootPath + scriptsSubfolder
    io.CreateDirectories(targetPath)
}

func moveMediaFiles() {
    /// Moves media files references to which were detected to the /_m subfolder.
}

func moveDocs() {
    /// Updates the document stockpile on disk according to the list of ingested files.
}

func readCachedDocs() {

}

func readCachedCore() {
}

func readCachedScripts() {

}

func moveFile() {
}

//}}}
//{{{ Rewriter


func rewriteLinks(content string, ingestPath string, fileSubpath string,
                  mediaFiles map[string]bool) {
    /// Rewrites links in a document from to point to new position inside /_m.
    /// Returns the document contents with rewritten links.
    var result = StringBuilder(content.len() + 100)
    var prevInd = 0
    var currInd
    var indLastSlash = fileSubpath.lastIndexOf("/")
    var fileSubfolder
    if indLastSlash < 1 {
        fileSubpath = ""
    } else {
        fileSubfolder = fileSubpath.substring(0, indLastSlash).pathize()
    }
    while true {
        currInd = content.indexOf("src=\"", prevInd)
        if currInd == -1 {
            result.append(content.substring(prevInd))
            return result.toString()
        }
        result.append(content.substring(prevInd, currInd))
        result.append("src=\"")
        var indClosingQuote = content.indexOf("\"", currInd + 5)
        if indClosingQuote > -1 {
            var link = content.substring(currInd + 5, indClosingQuote) // cutting original `src="`
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

func parseJokescriptModuleNames(fileContent string, fileSubfolder string) []string {
    var result = make([], 0)
    var headStart = fileContent.indexOf("<head>", 0, true)
    if headStart < 0 {
        return result
    }

    var headEnd = fileContent.indexOf("</head>", headStart, true)
    if headEnd < 0 {
        return result
    }

    var headString = fileContent.substring(headStart + 6, headEnd) // +6 for `<head>`
    var i = 0
    while true {
        i = headString.indexOf("<script", i, true)
        if i < 0 {
            break
        }
        i += 7 // +7 for the `<script`

        var j = headString.indexOf(">", i, true)
        var spl = headString.substring(i, j).split(" ")

        var srcString = spl.firstOrNull(func(a) { a.startsWith("src=\"") } )
        if srcString == nil {
            continue
        }
        var indJs = srcString.indexOf(".js", 5, true) // 5 for `src="`
        if indJs < 0 {
            continue
        }

        var rawModuleName = srcString.substring(5, indJs)
        if rawModuleName.startsWith("./")) { // adjacent file
            result.add(fileSubfolder = rawModuleName.substring(2))
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
    while i < spl.len() && spl[i].startsWith("import") {
        ++i
    }
    var scriptPrefix = "/" + appSubfolder + scriptsSubfolder
    for j := range i {
        var indFrom = spl[j].indexOf("\"")
        if indFrom < 0 {
            return ""
        }
        var tail = spl[j].substring(indFrom + 1)
        if tail.startsWith("global/")) {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix +
                globalsSubfolder + spl[j].substring(indFrom + 8) // +8 for `"global/`
        } else if tail.startsWith("./") {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix +
                subfolder + spl[j].substring(indFrom + 3) // +3 for `"./`
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
    var bodyRewritten = rewriteLinks(html.substring(indStart + 6, indEnd), ingestPath,
        fileSubpath, mediaFiles)
    var indStyleStart = html.indexOf("<style>")
    var indStyleEnd = html.indexOf("</style>")
    var style
    if indStyleStart > -1 && indStyleEnd > -1 && (indStyleEnd - indStyleStart) >= 8 {
        style = html.substring(indStyleStart + 7, indStyleEnd).trim()
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

func createSet(es ...string) map[string]bool {
    var result = map[string]bool{}
    for _, v := range es {
        result[v] = true
    }
    return result
}

func (t []T) indexOf[T any](needle T) int {
    /// IndexOf returns the first index of needle in haystack
    for i, v := range haystack {
        if v == needle {
            return i
        }
    }
    return -1
}

func (t *string) pathize() *string {
    if t.endsWith("/") {
        return t
    } else {
        return t + "/"
    }
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
