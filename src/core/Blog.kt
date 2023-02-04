package tech.sozonov.blog.core
import io.ktor.server.application.*
import io.ktor.server.response.*
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
        val documentContent = if (subUrl.isEmpty()) {
            docCache.rootPage
        } else if (subUrl == "termsofuse") {
            docCache.termsOfUse
        } else {
            docCache.getDocument(subUrl) ?: docCache.notFound
        }

        val modeTemporal = queryParams.any { it.first == "temp" }

        checkNUpdate(this, rootPath)

        val navTree = if (modeTemporal) {
            navTime
        } else {
            navTopic
        }

        val result = buildString {
            append(BlogTemplate.template0)

            val navString = templateNav(navTopic, navTime)
            printDeviceMeta(::append)
            printStylePart(documentContent, this@Blog, ::append)

            for (scriptDep in documentContent.scriptDeps) {
                val scriptModule = docCache.getModule(scriptDep) ?: return ""
                append("""<script type="module" src="${Constant.appSubfolder}${Constant.scriptsSubfolder}$scriptModule"/>""")
                append("\n")
            }

            val breadCrumbs = navTree.mkBreadcrumbs(subUrl).joinToString(", ")
            printScriptPart(navString, breadCrumbs, modeTemporal, this@Blog, ::append)
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
    wr("<style>\n")
    wr(self.docCache.coreCSS)
    if (doc.contentStyle != "") { wr(doc.contentStyle) }
    wr("</style>")
}

private fun printScriptPart(navString: String, breadCrumbs: String, modeTemporal: Boolean, self: Blog, wr: (t: String) -> StringBuilder) {
    wr("<script>")
    wr(navString)

    val fullLoc = "let cLoc = [$breadCrumbs];"
    wr(fullLoc)

    wr("let modeTemp = $modeTemporal;")
    wr(self.docCache.coreJS)
    wr("</script>")
}


private fun templateNav(navTopic: NavTree, navTime: NavTree): String {
    val strTopics = navTopic.toJokeScript()
    val strTemp = navTime.toJokeScript()
    return """
'use strict;'
const navTopics = [$strTopics];
const navTemporal = [$strTemp];
"""
}

// endregion

// region 404Response
suspend fun build404Response(webExchange: ApplicationCall) {
    webExchange.respondText("404 error")
}

// endregion

// region UpdateCaches

/**
 * Top-level updater function for documents and templates
 */
private fun checkNUpdate(self: Blog, rootPath: String){ //, conn: Connection) {
    val dtNow = LocalDateTime.now()
    val dur = Duration.between(self.dtNavUpdated, dtNow)
    if (dur.toSeconds() <= 5) { return }

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
