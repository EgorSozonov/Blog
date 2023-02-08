package tech.sozonov.blog.core
import tech.sozonov.blog.templates.BlogTemplate
import tech.sozonov.blog.utils.Tuple
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.time.*

object Blog {


private val docCache = DocumentCache()
private var navTopic: NavTree = NavTree()
private var navTime: NavTree = NavTree()
private var dtNavUpdated: Temporal = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0)
var rootPath: String = ""
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")


//region GetResponse

fun buildGetResponse(subUrl: String, queryParams: List<Pair<String, String>>): String {
    try {
        checkNUpdate(this, rootPath)
        val documentContent = if (subUrl.isEmpty()) {
            docCache.rootPage
        } else if (subUrl == "termsofuse") {
            docCache.termsOfUse
        } else {
            docCache.getDocument(subUrl) ?: docCache.notFound
        }

        val modeTemporal = queryParams.any { it.first == "temp" }

        val navTree = if (modeTemporal) {
            navTime
        } else {
            navTopic
        }

        val result = buildString {
            append(BlogTemplate.template0)

            printDeviceMeta(::append)
            printStylePart(documentContent, this@Blog, ::append)

            for (scriptDep in documentContent.scriptDeps) {
                val scriptModule = docCache.getModule(scriptDep) ?: return ""
                append("""<script type="module" src="/${C.appSubfolder}${C.scriptsSubfolder}$scriptModule"></script>""")
                append("\n")
            }
            append("""<script type="module" src="/${C.appSubfolder}${C.scriptsSubfolder}_g/core.js"></script>""")
            append("\n")

            val breadCrumbs = navTree.mkBreadcrumbs(subUrl).joinToString(", ")
            printScriptPart(breadCrumbs, modeTemporal, navTopic, navTime, ::append)
            append("""<link rel="icon" type="image/x-icon" href="/${C.appSubfolder}${C.mediaSubfolder}favicon.ico"/>""")
            append(BlogTemplate.templateHeadCloseBodyStart)

            append(documentContent.content)

            append(this@Blog.docCache.footer.content)
            append(BlogTemplate.template4)
        }
        return result
    } catch (e: Exception) {
        System.err.println(e.message)
        return ""
    }

}

private fun printDeviceMeta(wr: (t: String) -> StringBuilder) {
    wr("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
}

private fun printStylePart(doc: Document, self: Blog, wr: (t: String) -> StringBuilder) {
    wr("""<link rel="stylesheet" href="/${C.appSubfolder}${C.mediaSubfolder}${C.globalsSubfolder}core.css" />
""")
    if (!doc.hasCSS) return

    wr("""<link rel="stylesheet" href="/${C.appSubfolder}${C.mediaSubfolder}${doc.pathCaseSen}.css" />
""")
}

private fun printScriptPart(breadCrumbs: String, modeTemporal: Boolean, navTopic: NavTree, navTime: NavTree, wr: (t: String) -> StringBuilder) {
    val strTopics = navTopic.toJson()
    val strTemp = navTime.toJson()
    wr("""<script type="application/json" id="_navState">{
""")
    wr("""    "cLoc": [$breadCrumbs],
""")
    wr("""    "modeTemp": $modeTemporal,
""")
    wr("""    "navThematic": [$strTopics],
""")
    wr("""    "navTemporal": [$strTemp]
""")
    wr("}</script>")
}

// endregion

// region UpdateCaches

/**
 * Top-level updater function for documents and templates
 */
private fun checkNUpdate(self: Blog, rootPath: String){ //, conn: Connection) {
    val dtNow = LocalDateTime.now()
    val dur = Duration.between(self.dtNavUpdated, dtNow)
    if (dur.toMinutes() <= 5) { return }

    synchronized(self) {
        self.docCache.ingestAndRefresh(rootPath)
        val navTrees = buildNavTrees(self.docCache)
        self.navTopic = navTrees.first
        self.navTime = navTrees.second
        self.dtNavUpdated = dtNow
    }
}


private fun buildNavTrees(docCache: DocumentCache): Tuple<NavTree, NavTree> {
    val (topical, temporal) = NavTree.of(docCache)
    return Tuple(topical, temporal)
}

// endregion


}
