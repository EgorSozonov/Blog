package tech.sozonov.blog.datasource.file
import tech.sozonov.blog.core.C
import tech.sozonov.blog.core.Document
import tech.sozonov.blog.core.DocumentCache
import tech.sozonov.blog.core.Rewriter
import java.io.File
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
    val ingestCorePath = rootPath + C.ingestCoreSubfolder
    val lengthCorePrefix = ingestCorePath.length
    val ingestedCore = ingestCoreFiles(rootPath, ingestCorePath, docCache, lengthCorePrefix)

    val ingestPath = rootPath + C.ingestSubfolder
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

    val mediaDir = File(rootPath + C.mediaSubfolder)
    if (!mediaDir.exists()) { mediaDir.mkdirs() }
    val docDir = File(rootPath + C.docsSubfolder)
    if (!docDir.exists()) {docDir.mkdirs()}

    moveMediaFiles(mediaFiles, rootPath, lengthPrefix)
    ingestScripts(scriptNames, rootPath, docCache, false)
    moveDocs(docFiles, rootPath, C.ingestSubfolder, C.docsSubfolder)

    return Pair(docFiles, ingestedCore)
}


private fun ingestCoreFiles(rootPath: String, intakeCorePath: String, docCache: DocumentCache, lengthPrefix: Int):
        IngestedCore {
    val js = moveFile(intakeCorePath, rootPath + C.scriptsSubfolder + C.globalsSubfolder, "core.js")
    val css = moveFile(intakeCorePath, rootPath + C.mediaSubfolder + C.globalsSubfolder, "core.css")
    val favicon = moveFile(intakeCorePath, rootPath + C.mediaSubfolder, "favicon.ico")

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
    moveDocs(ingestedCoreHtml, rootPath, C.ingestCoreSubfolder, C.coreSubfolder)

    return IngestedCore(js, css, htmlNotFound, htmlFooter, htmlBasePage, htmlTermsUse)
}

/**
 * Performs ingestion of an input file: reads its contents, detects referenced
 * media files and script modules, rewrites the links to them, and decides whether this is an update/new doc or
 * a delete.
 */
private fun ingestDoc(file: File, ingestPath: String, mediaFiles: HashSet<String>): IngestedFile {
    val lastModified = file.lastModified()
    val dateLastModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneOffset.UTC)
    val indLastPeriod = file.path.lastIndexOf('.')
    val fileSubpath = file.path.substring(ingestPath.length, indLastPeriod) // "topic/subt/file" without the extension
    val indLastSlash = file.path.lastIndexOf('/')
    val fileSubfolder =  file.path.substring(ingestPath.length, indLastSlash + 1) // "topic/subt/"
    val fileContent = file.readText()
    val (content, styleContent) = Rewriter.getHTMLBodyStyle(fileContent, ingestPath, fileSubpath, mediaFiles)
    if (content.length < 10 && content.trim() == "") {
        return IngestedFile.Delete(fileSubpath)
    }

    val jsModuleNames = Rewriter.parseJSModuleNames(fileContent, fileSubfolder)

    return IngestedFile.CreateUpdate(
            fileSubpath,
            content, styleContent,
            dateLastModified, jsModuleNames)
}

/**
 * Ingests script module files, determines their filenames, and puts them into the cache folder.
 */
private fun ingestScripts(scriptNames: List<String>, rootPath: String, docCache: DocumentCache, isGlobal: Boolean) {
    val targetPath = rootPath + C.scriptsSubfolder
    Files.createDirectories(Path.of(targetPath))

    val prefixLength = rootPath.length + (if (isGlobal) {C.ingestCoreSubfolder.length} else {C.ingestSubfolder.length})
    for (fN in scriptNames) {
        File(fN).let { sourceFile ->
            val scriptContent = sourceFile.readText()
            val subfolder = fN.substring(prefixLength, fN.length - sourceFile.name.length)
            val rewrittenContent = Rewriter.rewriteScriptImports(scriptContent, subfolder)

            val modId = if (isGlobal) {
                C.globalsSubfolder + fN.substring(prefixLength, fN.length - 3) // -3 for ".js"
            } else {
                subfolder + sourceFile.name.substring(0, sourceFile.name.length - 3)
            }

            docCache.insertModule(modId)
            val targetFile = File(targetPath + modId + ".js")

            if (subfolder.length > 0) {
                Files.createDirectories(Path.of(targetPath + subfolder))
            } else {
                Files.createDirectories(Path.of(targetPath + C.globalsSubfolder))
            }

            targetFile.writeText(rewrittenContent)
            sourceFile.delete()
        }
    }
}

/**
 * Moves media files references to which were detected to the /_m subfolder.
 */
private fun moveMediaFiles(mediaFiles: HashSet<String>, rootPath: String, lengthPrefix: Int,
                   targetSubFolder: String = "") {
    val targetPath = rootPath + C.mediaSubfolder + targetSubFolder
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
private fun moveDocs(incomingFiles: List<IngestedFile>, rootPath: String, sourceSubfolder: String,
                     targetSubfolder: String) {
    val targetPrefix = rootPath + targetSubfolder
    val targetMediaPrefix = rootPath + C.mediaSubfolder
    for (iFile in incomingFiles) {
        when (iFile) {
            is IngestedFile.CreateUpdate -> {
                val nameId = iFile.fullPath.replace(" ", "")
                val sourceHtml = File(rootPath + sourceSubfolder + iFile.fullPath + ".html")

                val fTarget = File("$targetPrefix$nameId.html")
                if (fTarget.exists()) { fTarget.delete() }
                fTarget.parentFile.mkdirs()
                fTarget.writeText(iFile.content)

                val fStyleTarget = File("$targetMediaPrefix$nameId.css")
                if (fStyleTarget.exists()) { fStyleTarget.delete() }
                if (iFile.styleContent != "") {
                    fStyleTarget.parentFile.mkdirs()
                    fStyleTarget.writeText(iFile.styleContent)
                }

                if (iFile.jsModules.isNotEmpty()) {
                    val depsTarget = File("$targetPrefix$nameId.deps")
                    if (depsTarget.exists()) { depsTarget.delete() }

                    depsTarget.writeText(iFile.jsModules.joinToString("\n"))
                }

                if (sourceHtml.exists()) { sourceHtml.delete() }
            }
            is IngestedFile.Delete -> {
                val sourceFile = File(rootPath + C.ingestSubfolder + iFile.fullPath + ".html")

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
    val docsDirN = rootPath + C.docsSubfolder
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

        val hasCSS = File("$rootPath${C.mediaSubfolder}$address.css").exists()

        val doc = Document(it.readText(), deps, hasCSS, address,
                           LocalDateTime.ofInstant(
                               Instant.ofEpochMilli(it.lastModified()),
                               ZoneId.systemDefault()),
                           -1, false)
        cache.addDocument(address, doc) }
}


fun readCachedCore(rootPath: String, cache: DocumentCache) {
    val intakeDirN = rootPath + C.coreSubfolder

    val fileJs = File(intakeDirN + "core.js")
    if (fileJs.exists()) { cache.coreJS = fileJs.readText() }

    val fileHtmlNotFound = File(intakeDirN + "notFound.html")
    if (fileHtmlNotFound.exists()) {
        cache.notFound = Document(fileHtmlNotFound.readText(), mutableListOf(),
                                  false, "", LocalDateTime.MIN, -1, false)
    }

    val fileHtmlFooter = File(intakeDirN + "footer.html")
    if (fileHtmlFooter.exists()) {
        cache.footer = Document(fileHtmlFooter.readText(), mutableListOf(),
                                false, "", LocalDateTime.MIN, -1, false)
    }

    val fileHtmlTermsUse = File(intakeDirN + "termsOfUse.html")
    if (fileHtmlTermsUse.exists()) {
        cache.termsOfUse = Document(fileHtmlTermsUse.readText(), mutableListOf(),
                                    false, "", LocalDateTime.MIN, -1, false)
    }

    val fileCss = File(intakeDirN + "core.css")
    if (fileCss.exists()) { cache.coreCSS = fileCss.readText() }
}


fun readCachedScripts(rootPath: String, docCache: DocumentCache) {
    val intakeDirN = rootPath + C.scriptsSubfolder
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


}
