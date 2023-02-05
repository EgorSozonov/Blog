package tech.sozonov.blog.datasource.file
import tech.sozonov.blog.core.Constant
import tech.sozonov.blog.core.Document
import tech.sozonov.blog.core.DocumentCache
import tech.sozonov.blog.utils.pathize
import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object BlogFile {

/** Ingests all input files (core, scripts and docs), moves them to the internal file cache
 * as well as returns the results so that the docs and core files can be updated in memory */
fun ingestFiles(rootPath: String, docCache: DocumentCache): Pair<List<IngestedFile>, IngestedCore> {
    val ingestCorePath = rootPath + Constant.ingestCoreSubfolder
    val lengthCorePrefix = ingestCorePath.length
    val ingestedCore = ingestCoreFiles(rootPath, ingestCorePath, docCache, lengthCorePrefix)

    val ingestPath = rootPath + Constant.ingestSubfolder
    val lengthPrefix: Int = ingestPath.length
    val ingestDir = File(ingestPath)
    val arrFiles: FileTreeWalk = ingestDir.walk()
    val mediaFiles = HashSet<String>(10)
    val scriptNames = arrFiles
        .filter { file -> file.isFile && file.name.endsWith(".js")
                && file.name.length > 5 && file.length() < 500000 }
        .map { it.absolutePath }
        .toMutableList()
    val docFiles = arrFiles
            .filter { file -> file.isFile && file.name.endsWith(".html")
                              && file.name.length > 5 && file.length() < 2000000 }
            .map { ingestDoc(it, ingestPath, mediaFiles) }
            .toMutableList()

    val mediaDir = File(rootPath + Constant.mediaSubfolder)
    if (!mediaDir.exists()) { mediaDir.mkdirs() }
    val docDir = File(rootPath + Constant.docsSubfolder)
    if (!docDir.exists()) {docDir.mkdirs()}

    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    ingestScripts(scriptNames, rootPath, docCache, false)
    moveDocs(docFiles, rootPath, Constant.ingestSubfolder, Constant.docsSubfolder)

    return Pair(docFiles, ingestedCore)
}


private fun ingestCoreFiles(rootPath: String, intakeCorePath: String, docCache: DocumentCache, lengthPrefix: Int):
        IngestedCore {
    val js = moveFile(intakeCorePath, rootPath + Constant.coreSubfolder, "core.js")
    val css = moveFile(intakeCorePath, rootPath + Constant.coreSubfolder, "core.css")
    val favicon = moveFile(intakeCorePath, rootPath + Constant.mediaSubfolder, "favicon.ico")

    val mediaFiles = HashSet<String>(10)
    val ingestedCoreHtml = ArrayList<IngestedFile>(3)

    val fileNotFound = File(intakeCorePath + "notFound.html")
    val fileFooter = File(intakeCorePath + "footer.html")
    val fileBasePage = File(intakeCorePath + "core.html")
    val fileTerms = File(intakeCorePath + "termsOfUse.html")
    var htmlNotFound: IngestedFile? = null
    var htmlFooter: IngestedFile? = null
    var htmlBasePage: IngestedFile? = null
    var htmlTermsUse: IngestedFile? = null

    if (fileNotFound.exists()) {
        htmlNotFound = ingestDoc(fileNotFound, intakeCorePath, mediaFiles)
        ingestedCoreHtml.add(htmlNotFound)
    }
    if (fileFooter.exists()) {
        htmlFooter = ingestDoc(fileFooter, intakeCorePath, mediaFiles)
        ingestedCoreHtml.add(htmlFooter)
    }
    if (fileBasePage.exists()) {
        htmlBasePage = ingestDoc(fileBasePage, intakeCorePath, mediaFiles)
        ingestedCoreHtml.add(htmlBasePage)
    }
    if (fileTerms.exists()) {
        htmlTermsUse = ingestDoc(fileTerms, intakeCorePath, mediaFiles)
        ingestedCoreHtml.add(htmlTermsUse)
    }

    val arrFiles: FileTreeWalk = File(intakeCorePath).walk()

    val scriptNames = arrFiles
        .filter { file -> file.isFile && file.name.endsWith(".js")
                && file.name.length > 5 && file.length() < 500000 }
        .map { it.absolutePath }
        .toMutableList()

    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    ingestScripts(scriptNames, rootPath, docCache, true)
    moveDocs(ingestedCoreHtml, rootPath, Constant.ingestCoreSubfolder, Constant.coreSubfolder)

    return IngestedCore(js, css, htmlNotFound, htmlFooter, htmlBasePage, htmlTermsUse)
}

/**
 * Performs ingestion of an input file: reads its contents, detects referenced
 * media files and script modules, rewrites the links to them, and decides whether this is an update/new doc or
 * a delete.
 */
private fun ingestDoc(file: File, ingestPath: String, mediaFiles: HashSet<String>): IngestedFile {
    val lastModified = file.lastModified()
    val dateLastModified = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(lastModified), ZoneOffset.UTC
    )
    val indLastPeriod = file.path.lastIndexOf('.')
    val fileSubpath = file.path.substring(ingestPath.length, indLastPeriod) // "topic/subt/file" without the extension
    val indLastSlash = file.path.lastIndexOf('/')
    val fileSubfolder =  file.path.substring(ingestPath.length, indLastSlash + 1) // "topic/subt/"
    val fileContent = file.readText()
    val (content, styleContent) = getHTMLBodyStyle(fileContent, ingestPath, fileSubpath, mediaFiles)
    if (content.length < 10 && content.trim() == "") {
        return IngestedFile.Delete(fileSubpath)
    }

    val jsModuleNames = parseJSModuleNames(fileContent, fileSubfolder)

    return IngestedFile.CreateUpdate(
            fileSubpath,
            content, styleContent,
            dateLastModified, jsModuleNames)
}

/**
 * Ingests script module files, updates their versions, determines their filenames, and puts them into the cache folder.
 */
private fun ingestScripts(scriptNames: List<String>, rootPath: String, docCache: DocumentCache, isGlobal: Boolean) {
    val targetPath = rootPath + Constant.scriptsSubfolder
    Files.createDirectories(Path.of(targetPath))

    val prefixLength = rootPath.length + (if (isGlobal) {Constant.ingestCoreSubfolder.length} else {Constant.ingestSubfolder.length})
    for (fN in scriptNames) {
        // -3 for the ".js"

        // a/b/f.js
        // _g/m.js

        File(fN).let { sourceFile ->
            val scriptContent = sourceFile.readText()
            val subfolder = fN.substring(prefixLength, fN.length - sourceFile.name.length)
            val rewrittenContent = rewriteScriptImports(scriptContent, subfolder)

            val modId = if (isGlobal) {
                Constant.scriptsGlobalSubfolder + fN.substring(prefixLength, fN.length - 3) // -3 for ".js"
            } else {
                subfolder + sourceFile.name.substring(0, sourceFile.name.length - 3)
            }

            docCache.insertModule(modId)
            val targetFile = File(targetPath + modId + ".js")

//            if (targetFile.exists()) {
//                targetFile.delete()
//            }
            if (subfolder.length > 0) {
                Files.createDirectories(Path.of(targetPath + subfolder))
            } else {
                Files.createDirectories(Path.of(targetPath + Constant.scriptsGlobalSubfolder))
            }

            targetFile.writeText(rewrittenContent)
            sourceFile.delete()
        }
    }
}

/** Rewrites the Jokescript imports to correctly reference files on the server, including global imports */
private fun rewriteScriptImports(script: String, subfolder: String): String {
    val spl: MutableList<String> = script.split("\n").toMutableList()
    var i = 0
    while (i < spl.size && spl[i].startsWith("import")) {
        i++
    }
    val scriptPrefix = "/${Constant.appSubfolder}${Constant.scriptsSubfolder}"
    for (j in 0 until i) {
        val indFrom = spl[j].indexOf("\"")
        if (indFrom < 0 ) return ""
        val tail = spl[j].substring(indFrom + 1)
        if (tail.startsWith("global/")) {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix + Constant.scriptsGlobalSubfolder + spl[j].substring(indFrom + 8) // +8 for `"global/`
        } else if (tail.startsWith("./")) {
            spl[j] = spl[j].substring(0, indFrom + 1) + scriptPrefix + subfolder + spl[j].substring(indFrom + 3) // +3 for `"./`
        } else {
            return ""
        }
    }
    return spl.joinToString("\n")
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
            result.add(Constant.scriptsGlobalSubfolder + rawModuleName)
        }
    }
    return result
}

/**
 * Moves media files references to which were detected to the /_m subfolder.
 */
private fun moveMediaFiles(mediaFiles: HashSet<String>, rootPath: String, lengthPrefix: Int,
                   targetSubFolder: String = "") {
    val targetPath = rootPath + Constant.mediaSubfolder + targetSubFolder
    for (fN in mediaFiles) {
        File(fN).let { sourceFile ->
            val targetFile = File(targetPath + fN.substring(lengthPrefix))
            if (targetFile.exists()) {
                targetFile.delete()
            }
            sourceFile.copyTo(File(targetPath + fN.substring(lengthPrefix)))
            sourceFile.delete()
        }
    }
}

/**
 * Updates the document stockpile on disk according to the list of ingested files.
 */
private fun moveDocs(incomingFiles: List<IngestedFile>, rootPath: String, sourceSubfolder: String, targetSubfolder: String) {
    val targetPrefix = rootPath + targetSubfolder
    for (iFile in incomingFiles) {
        when (iFile) {
            is IngestedFile.CreateUpdate -> {
                val sourceHtml = File(rootPath + sourceSubfolder + iFile.fullPath + ".html")

                val fTarget = File(targetPrefix + iFile.fullPath.replace(" ", "") + ".html")
                if (fTarget.exists()) { fTarget.delete() }
                fTarget.parentFile.mkdirs()
                fTarget.writeText(iFile.content)

                val fStyleTarget = File(targetPrefix + iFile.fullPath.replace(" ", "") + ".css")
                if (fStyleTarget.exists()) { fStyleTarget.delete() }
                if (iFile.styleContent != "") {
                    fStyleTarget.parentFile.mkdirs()
                    fStyleTarget.writeText(iFile.styleContent)
                }

                if (iFile.jsModules.isNotEmpty()) {
                    val depsTarget = File(targetPrefix + iFile.fullPath.replace(" ", "") + ".deps")
                    if (depsTarget.exists()) { depsTarget.delete() }
                    depsTarget.writeText(iFile.jsModules.joinToString("\n"))
                }

                if (sourceHtml.exists()) { sourceHtml.delete() }
            }
            is IngestedFile.Delete -> {
                val sourceFile = File(rootPath + Constant.ingestSubfolder + iFile.fullPath + ".html")

                val fNTarget = File(targetPrefix + iFile.fullPath.replace(" ", "") + ".html")
                if (fNTarget.exists()) fNTarget.delete()

                val fNStyleTarget = File(targetPrefix + iFile.fullPath.replace(" ", "") + ".css")
                if (fNStyleTarget.exists()) { fNStyleTarget.delete() }

                val depsTarget = File(targetPrefix + iFile.fullPath.replace(" ", "") + ".deps")
                if (depsTarget.exists()) { depsTarget.delete() }

                if (sourceFile.exists()) { sourceFile.delete() }
            }
        }
    }
}


fun readCachedDocs(rootPath: String, cache: DocumentCache) {
    val docsDirN = rootPath + Constant.docsSubfolder
    val docsDir = File(docsDirN)
    val prefixLength = docsDirN.length
    val arrFiles: FileTreeWalk = docsDir.walk()
    val emptyList = mutableListOf<String>()

    val fileList = arrFiles.filter { file -> file.isFile && file.name.endsWith(".html")
            && file.name.length > 5 && file.length() < 2000000 }
            .toList()
    fileList.forEach {
        val address = it.path.substring(prefixLength, it.path.length - 5) // -5 for the ".html"
        val depsFile = File("$docsDirN$address.deps")
        val deps = if (depsFile.exists()) {
            depsFile.readText().split("\n")
        } else {
            emptyList
        }

        val styleFile = File("$docsDirN$address.css")
        val style = if (styleFile.exists()) {
            styleFile.readText()
        } else {
            ""
        }

        val doc = Document(it.readText(), deps, style, address,
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
        cache.notFound = Document(fileHtmlNotFound.readText(), mutableListOf(),
                                  "", "", LocalDateTime.MIN, -1, false)
    }

    val fileHtmlFooter = File(intakeDirN + "footer.html")
    if (fileHtmlFooter.exists()) {
        cache.footer = Document(fileHtmlFooter.readText(), mutableListOf(),
                                "", "", LocalDateTime.MIN, -1, false)
    }

    val fileHtmlTermsUse = File(intakeDirN + "termsOfUse.html")
    if (fileHtmlTermsUse.exists()) {
        cache.termsOfUse = Document(fileHtmlTermsUse.readText(), mutableListOf(),
                "", "", LocalDateTime.MIN, -1, false)
    }

    val fileCss = File(intakeDirN + "core.css")
    if (fileCss.exists()) { cache.coreCSS = fileCss.readText() }
}


fun readCachedScripts(rootPath: String, docCache: DocumentCache) {
    val intakeDirN = rootPath + Constant.scriptsSubfolder
    val intakeDir = File(intakeDirN)
    val prefixLength = intakeDirN.length
    val arrFiles: FileTreeWalk = intakeDir.walk()

    val fileList = arrFiles.filter { file -> file.isFile && file.name.endsWith(".js")
                                    && file.name.length > 5 && file.length() < 500000 }
                           .toList()
    fileList.forEach {
        val shortFileName = it.path.substring(prefixLength, it.path.length - 3) // -3 for the ".js"
        docCache.insertModule(shortFileName)
    }
}


private fun moveFile(sourcePath: String, targetPath: String, fNShort: String): String {
    val file = File(sourcePath + fNShort)
    var result = ""
    if (file.exists()) {
        result = file.readText()
        val fTarget = File(targetPath + fNShort)
        if (fTarget.exists()) {
            fTarget.delete()
        }
        fTarget.parentFile.mkdirs()
        fTarget.writeText(result)
        file.delete()
    }
    return result
}

/**
 * Rewrites links in a document from to point to new position inside /_m.
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
