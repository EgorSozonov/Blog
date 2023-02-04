package tech.sozonov.blog.core
import tech.sozonov.blog.datasource.file.BlogFile
import tech.sozonov.blog.datasource.file.IngestedFile
import tech.sozonov.blog.utils.*
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap


class DocumentCache {
    private val cache: HashMap<String, Document> = HashMap()
    /** Cache of modules and their current versions */
    private val scriptCache: HashMap<String, Int> = HashMap()
    var coreJS: String = ""
    var coreCSS: String = ""
    var notFound: Document = Document("", mutableListOf(), "", "", LocalDateTime.MIN, 0, false)
    var footer: Document = Document("", mutableListOf(),"", "", LocalDateTime.MIN, 0, false)
    var rootPage: Document = Document("", mutableListOf(),"", "", LocalDateTime.MIN, 0, false)
    var termsOfUse: Document = Document("", mutableListOf(),"", "", LocalDateTime.MIN, 0, false)

    companion object {
        private val emptyDocument = Document("", mutableListOf(),"", "", LocalDateTime.MIN, 0, false)
    }


    fun getModule(modId: String): String? {
        if (scriptCache.containsKey(modId)) {
            return modId + "-" + (scriptCache[modId]!!) + ".mjs"
        }
        return null
    }

    /** This method should be called inside a synchronized block.
     * It adds a new script module to the cache, or updates and increments its version if it already existed.
     */
    fun insertModule(modId: String): String {
        val newVersion = if (scriptCache.containsKey(modId)) {
            val newV = scriptCache[modId]!! + 1
            scriptCache[modId] = newV
            newV
        } else {
            scriptCache[modId] = 1
            1
        }
        return "$modId-$newVersion.mjs"
    }


    fun getDocument(path0: String): Document? {
        return if (path0.endsWith(".html")) {
            cache[path0.substring(0, path0.length - 5).lowercase()]
        } else {
            cache[path0.lowercase()]
        }
    }


    fun addDocument(path0: String, newDocument: Document) {
        val path = path0.lowercase()
        this.cache[path] = newDocument
    }

    /**
     * Loads/reloads a set files and their contents into the cache.
     * Strips HTML outside the <body> tag so that only the relevant contents make it into the cache.
     */
    fun ingestAndRefresh(rootPath: String) {
        if (cache.isEmpty()) {
            // The order must be this: first read the core stuff, then the scripts, then the docs
            // This is because core stuff is not overwritten by scripts, and docs depend on scripts and core
            BlogFile.readCachedCore(rootPath, this)
            BlogFile.readCachedScripts(rootPath, this)
            BlogFile.readCachedDocs(rootPath, this)
        }

        println("ingesting and refreshing at root path $rootPath")
        val (ingestedDocs, ingestedCore) = BlogFile.ingestFiles(rootPath, this)

        if (!ingestedDocs.isEmpty()) {
            for (i in ingestedDocs.indices) {
                val inFile = ingestedDocs[i]
                when (inFile) {
                    is IngestedFile.CreateUpdate -> {
                        val key = inFile.fullPath.lowercase().replace(" ", "")
                        cache[key] = Document(inFile.content, inFile.jsModules, inFile.styleContent,
                                              inFile.fullPath.replace(" ", ""),
                                              inFile.modifTime, -1, false)
                    }
                    is IngestedFile.Delete -> {
                        val key = inFile.fullPath.lowercase().replace(" ", "")
                        if (cache.containsKey(key)) {
                            cache.remove(key)
                        }
                    }
                }
            }
        }

        if (ingestedCore.js != "") this.coreJS = ingestedCore.js
        if (ingestedCore.css != "") this.coreCSS = ingestedCore.css
        this.notFound = coreDocFromIngested(ingestedCore.htmlNotFound)
        this.rootPage = coreDocFromIngested(ingestedCore.htmlRoot)
        this.footer = coreDocFromIngested(ingestedCore.htmlFooter)
        this.termsOfUse = coreDocFromIngested(ingestedCore.htmlTermsUse)
    }

    /**
     * Returns an array of [(original path) date] useful for constructing navigation trees.
     */
    fun toPageArray(): List<Tuple<String, LocalDateTime>> {
        val result: MutableList<Tuple<String, LocalDateTime>> = ArrayList(this.cache.size)
        for((_, v) in this.cache) {
            result.add(Tuple(v.pathCaseSen, v.modifiedDate))
        }
        return result
    }

    /**
     * Gets the list of all documents without wasAddedDB flag.
     */
    fun getNewDocuments(): Sequence<Document> {
        return cache.entries.asSequence().filter { !it.value.wasAddedDB }.map{ it.value }
    }


    private fun coreDocFromIngested(intaken: IngestedFile?): Document {
        return if (intaken != null && intaken is IngestedFile.CreateUpdate) {
            Document(
                    intaken.content, intaken.jsModules,
                    intaken.styleContent,
                    "",
                    intaken.modifTime, -1, false)
        } else {
            emptyDocument
        }
    }
}


data class Document(val content: String, val scriptDeps: List<String>, val contentStyle: String,
                    val pathCaseSen: String, val modifiedDate: LocalDateTime, var pageId: Int, var wasAddedDB: Boolean)