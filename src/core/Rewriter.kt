package tech.sozonov.blog.core
import tech.sozonov.blog.utils.pathize
import java.io.File

object Rewriter {

/**
 * Rewrites links in a document from to point to new position inside /_m.
 * Returns the document contents with rewritten links.
 */
fun rewriteLinks(content: String, intakePath: String, fileSubpath: String,
                         mediaFiles: HashSet<String>): String {
    val result = StringBuilder(content.length + 100)
    var prevInd = 0
    var currInd: Int
    val indLastSlash = fileSubpath.lastIndexOf("/")
    val fileSubfolder = if (indLastSlash < 1) { "" }
    else { fileSubpath.substring(0, indLastSlash).pathize() }
    while (true) {
        currInd = content.indexOf("src=\"", prevInd)
        if (currInd == -1) {
            result.append(content.substring(prevInd))
            return result.toString()
        }

        result.append(content.substring(prevInd, currInd))
        result.append("src=\"")
        val indClosingQuote = content.indexOf("\"", currInd + 5)
        if (indClosingQuote > -1) {
            val link = content.substring(currInd + 5, indClosingQuote) // cutting off the initial 'src="'

            val sourcePath = (intakePath + fileSubfolder).pathize() + link
            val targetSubPath = "/" + C.appSubfolder + C.mediaSubfolder + fileSubfolder + link
            if (File(sourcePath).exists()) { mediaFiles.add(sourcePath) }

            result.append(targetSubPath)
            currInd = indClosingQuote
        }

        prevInd = currInd
    }
}


fun parseJSModuleNames(fileContent: String, fileSubfolder: String): List<String> {
    val result = mutableListOf<String>()
    val headStart = fileContent.indexOf("<head>", 0, true)
    if (headStart < 0) return result

    val headEnd = fileContent.indexOf("</head>", headStart, true)
    if (headEnd < 0) return result

    val headString = fileContent.substring(headStart + 6, headEnd) // +6 for "<head>"
    var i = 0
    while (true) {
        i = headString.indexOf("<script", i, true)
        if (i < 0) break
        i += 7 // +7 for the "<script"

        val j = headString.indexOf(">", i, true)
        val spl = headString.substring(i, j).split(" ")

        val srcString = spl.firstOrNull { it.startsWith("src=\"") } ?: continue

        val indJs = srcString.indexOf(".js", 5, true) // 5 for `src="`
        if (indJs < 0) continue

        val rawModuleName = srcString.substring(5, indJs)
        if (rawModuleName.startsWith("./")) { // adjacent file
            result.add(fileSubfolder + rawModuleName.substring(2))
        } else { // global script module
            result.add(C.globalsSubfolder + rawModuleName)
        }
    }
    return result
}

/** Rewrites the JokeScript imports to correctly reference files on the server, including global imports */
fun rewriteScriptImports(script: String, subfolder: String): String {
    val spl: MutableList<String> = script.split("\n").toMutableList()
    var i = 0
    while (i < spl.size && spl[i].startsWith("import")) {
        i++
    }
    val scriptPrefix = "/${C.appSubfolder}${C.scriptsSubfolder}"
    for (j in 0 until i) {
        val indFrom = spl[j].indexOf("\"")
        if (indFrom < 0 ) return ""
        val tail = spl[j].substring(indFrom + 1)
        if (tail.startsWith("global/")) {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix + C.globalsSubfolder + spl[j].substring(indFrom + 8) // +8 for `"global/`
        } else if (tail.startsWith("./")) {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix + subfolder + spl[j].substring(indFrom + 3) // +3 for `"./`
        } else {
            return ""
        }
    }
    return spl.joinToString("\n")
}

/**
 * Gets the contents of the "body" tag inside the HTML, as well as contents of the "style" tag inside the head
 */
fun getHTMLBodyStyle(html: String, intakePath: String, fileSubpath: String,
                             mediaFiles: HashSet<String>): Pair<String, String> {
    val indStart = html.indexOf("<body>")
    val indEnd = html.lastIndexOf("</body>")
    if (indStart < 0 || indEnd < 0 || (indEnd - indStart) < 7) return Pair(html, "")
    val bodyRewritten = rewriteLinks(html.substring(indStart + 6, indEnd), intakePath,
        fileSubpath, mediaFiles)
    val indStyleStart = html.indexOf("<style>")
    val indStyleEnd = html.indexOf("</style>")
    val style = if (indStyleStart > -1 && indStyleEnd > -1 && (indStyleEnd - indStyleStart) >= 8)
        { html.substring(indStyleStart + 7, indStyleEnd).trim() } else { "" }

    return Pair(bodyRewritten, style)
}


}