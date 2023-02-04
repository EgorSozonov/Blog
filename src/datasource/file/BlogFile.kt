package tech.sozonov.blog.datasource.file
import tech.sozonov.blog.core.Constant
import tech.sozonov.blog.core.Document
import tech.sozonov.blog.core.DocumentCache
import tech.sozonov.blog.utils.pathize
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object BlogFile {


fun ingestFiles(rootPath: String): Pair<List<IngestedFile>, IngestedCore> {
    val ingestPath = rootPath + Constant.intakeSubfolder
    println("ingest path $ingestPath")
    val lengthPrefix: Int = ingestPath.length

    val intakeDir = File(ingestPath)
    val arrFiles: FileTreeWalk = intakeDir.walk()

    val mediaFiles = HashSet<String>(10)
    val ingestedFiles = arrFiles
            .filter { file -> file.isFile && file.name.endsWith(".html")
                              && file.name.length > 5 && file.length() < 2000000 }
            .map { ingestFile(it, ingestPath, lengthPrefix, mediaFiles) }
            .toMutableList()

    val mediaDir = File(rootPath + Constant.mediaSubfolder)
    if (!mediaDir.exists()) { mediaDir.mkdirs() }
    val docDir = File(rootPath + Constant.docsSubfolder)
    if (!docDir.exists()) {docDir.mkdirs()}

    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    moveDocs(ingestedFiles, rootPath, Constant.docsSubfolder)

    val intakeCorePath = rootPath + Constant.intakeCoreSubfolder
    val lengthCorePrefix = intakeCorePath.length
    val intakenCore = ingestCoreFiles(rootPath, intakeCorePath, lengthCorePrefix)
    return Pair(ingestedFiles, intakenCore)
}


/**
 * Performs ingestion of an input file: reads its contents, detects referenced
 * media files, rewrites the links to them, and decides whether this is an update,
 * a delete or a new document.
 */
private fun ingestFile(file: File, intakePath: String, lengthPrefix: Int,
                       mediaFiles: HashSet<String>): IngestedFile {
    val lastModified = file.lastModified()
    val dateLastModified = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(lastModified), ZoneOffset.UTC
    )
    val indLastPeriod = file.path.lastIndexOf('.')
    val fileSubpath = file.path.substring(lengthPrefix, indLastPeriod)

    val fileContent = file.readText()
    val (content, styleContent) = getHTMLBodyStyle(fileContent, intakePath, fileSubpath, mediaFiles)
    if (content.length < 10 && content.trim() == "") {
        return IngestedFile.Delete(fileSubpath)
    }

    val jsModuleNames = getJSModuleNames(fileContent)

    return IngestedFile.CreateUpdate(
            fileSubpath,
            content, styleContent,
            dateLastModified, jsModuleNames)
}

fun getJSModuleNames(fileContent: String): List<String> {
    val result = mutableListOf<String>()
    val headStart = fileContent.indexOf("<head>", 0, true)
    if (headStart < 0) return result

    val headEnd = fileContent.indexOf("</head>", headStart, true)
    if (headEnd < 0) return result

    val headString = fileContent.substring(headStart + 5, headEnd)
    var i = 0
    while (true) {
        i = headString.indexOf("<script", i, true)
        if (i < 0) break
        i += 7

        val j = headString.indexOf(">", i, true)
        val spl = headString.substring(i, j).split(" ")

        val srcString = spl.firstOrNull { it.startsWith("src=\"") } ?: continue

        val mjs = srcString.indexOf(".mjs", 5, true) // 5 for `src="`
        if (mjs < 0) continue

        result.add(srcString.substring(5, mjs))
    }
    return result

}

/**
 * Moves media files references to which were detected to the /_media subfolder.
 */
private fun moveMediaFiles(mediaFiles: HashSet<String>, rootPath: String, lengthPrefix: Int,
                   targetSubFolder: String = "") {
    val targetPath = rootPath + Constant.mediaSubfolder + targetSubFolder
    for (fN in mediaFiles) {
        File(fN).let { sourceFile ->
            sourceFile.copyTo(File(targetPath + fN.substring(lengthPrefix)))
            sourceFile.delete()
        }
    }
}


/**
 * Updates the document stockpile on disk according to the list of intaken files.
 */
private fun moveDocs(intakens: List<IngestedFile>, rootPath: String, targetSubfolder: String) {
    val targetPrefix = rootPath + targetSubfolder
    for (iFile in intakens) {
        when (iFile) {
            is IngestedFile.CreateUpdate -> {
                val sourceFile = File(rootPath + Constant.intakeSubfolder
                                      + File(iFile.fullPath) + ".html")

                println("!!! trying to save file as ${targetPrefix + iFile.fullPath.replace(" ", "")
                        + ".html"}")

                val fTarget = File(targetPrefix + iFile.fullPath.replace(" ", "")
                                    + ".html")
                if (fTarget.exists()) { fTarget.delete() }
                fTarget.parentFile.mkdirs()
                fTarget.writeText(iFile.content)

                val fStyleTarget = File(targetPrefix + iFile.fullPath.replace(" ", "")
                                        + ".css")
                if (fStyleTarget.exists()) { fStyleTarget.delete() }
                if (iFile.styleContent != "") {
                    fStyleTarget.parentFile.mkdirs()
                    fStyleTarget.writeText(iFile.styleContent)
                }

                //sourceFile.copyTo(File(fNTarget))
                if (sourceFile.exists()) { sourceFile.delete() }
            }
            is IngestedFile.Delete -> {
                val sourceFile = File(rootPath + Constant.intakeSubfolder
                        + File(iFile.fullPath) + ".html")

                val fNTarget = File(targetPrefix + iFile.fullPath.replace(" ", "")
                                    + ".html")
                if (fNTarget.exists()) fNTarget.delete()

                val fNStyleTarget = targetPrefix + iFile.fullPath.replace(" ", "") +
                                    ".css"
                if (File(fNStyleTarget).exists()) { File(fNStyleTarget).delete() }

                if (sourceFile.exists()) { sourceFile.delete() }
            }
        }
    }
}


fun readCachedDocs(rootPath: String, cache: DocumentCache) {
    val intakeDirN = rootPath + Constant.docsSubfolder
    val intakeDir = File(intakeDirN)
    val prefixLength = intakeDirN.length
    val arrFiles: FileTreeWalk = intakeDir.walk()

    val fileList = arrFiles.filter { file -> file.isFile && file.name.endsWith(".html")
            && file.name.length > 5 && file.length() < 2000000 }
            .toList()
    fileList.forEach {
        val address = it.path.substring(prefixLength, it.path.length - 5)
        val doc = Document(it.readText(), "", address,
                           LocalDateTime.ofInstant(
                               Instant.ofEpochMilli(it.lastModified()),
                               ZoneId.systemDefault()),
                           -1, false)
        cache.addDocument(address, doc) }
}


fun readCachedCore(rootPath: String, cache: DocumentCache) {
    val intakeDirN = rootPath + Constant.coreSubfolder

    val fileJs = File(intakeDirN + "core.js")
    if (fileJs.exists()) { cache.coreJS = fileJs.readText() }

    val fileHtmlNotFound = File(intakeDirN + "notFound.html")
    if (fileHtmlNotFound.exists()) {
        cache.notFound = Document(fileHtmlNotFound.readText(),
                                  "", "", LocalDateTime.MIN, -1, false)
    }

    val fileHtmlFooter = File(intakeDirN + "footer.html")
    if (fileHtmlFooter.exists()) {
        cache.footer = Document(fileHtmlFooter.readText(),
                                "", "", LocalDateTime.MIN, -1, false)
    }

    val fileHtmlTermsUse = File(intakeDirN + "termsOfUse.html")
    if (fileHtmlTermsUse.exists()) {
        cache.termsOfUse = Document(fileHtmlTermsUse.readText(),
                "", "", LocalDateTime.MIN, -1, false)
    }

    val fileCss = File(intakeDirN + "core.css")
    if (fileCss.exists()) { cache.coreCSS = fileCss.readText() }
}


private fun ingestCoreFiles(rootPath: String, intakeCorePath: String, lengthPrefix: Int):
        IngestedCore {
    val js = moveFile(intakeCorePath, rootPath + Constant.coreSubfolder, "core.js")
    val css = moveFile(intakeCorePath, rootPath + Constant.coreSubfolder, "core.css")
    val mediaFiles = HashSet<String>(10)
    val intakenCoreHtml = ArrayList<IngestedFile>(3)

    val fileNotFound = File(intakeCorePath + "notFound.html")
    val fileFooter = File(intakeCorePath + "footer.html")
    val fileBasePage = File(intakeCorePath + "core.html")
    val fileTerms = File(intakeCorePath + "termsOfUse.html")
    var htmlNotFound: IngestedFile? = null
    var htmlFooter: IngestedFile? = null
    var htmlBasePage: IngestedFile? = null
    var htmlTermsUse: IngestedFile? = null

    if (fileNotFound.exists()) {
        htmlNotFound = ingestFile(fileNotFound, intakeCorePath,
                lengthPrefix, mediaFiles)
        intakenCoreHtml.add(htmlNotFound)
    }
    if (fileFooter.exists()) {
        htmlFooter = ingestFile(fileFooter, intakeCorePath,
                lengthPrefix, mediaFiles)
        intakenCoreHtml.add(htmlFooter)
    }
    if (fileBasePage.exists()) {
        htmlBasePage = ingestFile(fileBasePage, intakeCorePath,
                lengthPrefix, mediaFiles)
        intakenCoreHtml.add(htmlBasePage)
    }
    if (fileTerms.exists()) {
        htmlTermsUse = ingestFile(fileTerms, intakeCorePath,
                lengthPrefix, mediaFiles)
        intakenCoreHtml.add(htmlTermsUse)
    }

    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    moveDocs(intakenCoreHtml, rootPath, Constant.coreSubfolder)

    return IngestedCore(js, css, htmlNotFound, htmlFooter, htmlBasePage, htmlTermsUse)
}


private fun moveFile(sourcePath: String, targetPath: String, fNShort: String): String {
    val file = File(sourcePath + fNShort)
    var result = ""
    if (file.exists()) {
        result = file.readText()
        val fTarget = File(targetPath + fNShort)
        if (fTarget.exists()) {
            fTarget.delete()
            fTarget.parentFile.mkdirs()
            fTarget.writeText(result)
        }
    }
    return result
}

/**
 * Rewrites links in a document from to point to new position inside /_media.
 * Returns the document contents with rewritten links.
 */
private fun rewriteLinks(content: String, intakePath: String, fileSubpath: String,
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
            val targetSubPath = "/" + Constant.appSubfolder + Constant.mediaSubfolder + fileSubfolder + link
            if (File(sourcePath).exists()) { mediaFiles.add(sourcePath) }

            result.append(targetSubPath)
            currInd = indClosingQuote
        }

        prevInd = currInd
    }
}

/**
 * Gets the contents of the "body" tag inside the HTML, as well as contents of the "style" tag inside the head
 */
private fun getHTMLBodyStyle(html: String, intakePath: String, fileSubpath: String,
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
