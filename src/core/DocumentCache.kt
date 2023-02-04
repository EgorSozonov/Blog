package tech.sozonov.blog.core
import tech.sozonov.blog.datasource.file.BlogFile
import tech.sozonov.blog.datasource.file.IngestedFile
import tech.sozonov.blog.utils.*
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap


class DocumentCache {
    private val cache: HashMap<String, Document> = HashMap()
    var coreJS: String = ""
    var coreCSS: String = ""
    var notFound: Document = Document("", "", "", LocalDateTime.MIN, 0, false)
    var footer: Document = Document("", "", "", LocalDateTime.MIN, 0, false)
    var rootPage: Document = Document("", "", "", LocalDateTime.MIN, 0, false)
    var termsOfUse: Document = Document("", "", "", LocalDateTime.MIN, 0, false)
    companion object {
        private val emptyDocument = Document("", "", "", LocalDateTime.MIN, 0, false)
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
            BlogFile.readCachedDocs(rootPath, this)
            BlogFile.readCachedCore(rootPath, this)
        }

        println("ingesting and refreshing at root path $rootPath")
        val (ingestedDocs, ingestedCore) = BlogFile.ingestFiles(rootPath)

        if (!ingestedDocs.isEmpty()) {
            for (i in ingestedDocs.indices) {
                val inFile = ingestedDocs[i]
                when (inFile) {
                    is IngestedFile.CreateUpdate -> {
                        val key = inFile.fullPath.lowercase().replace(" ", "")
                        cache[key] = Document(inFile.content, inFile.styleContent,
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
                    intaken.content,
                    intaken.styleContent,
                    "",
                    intaken.modifTime, -1, false)
        } else {
            emptyDocument
        }
    }
}


data class Document(val content: String, val contentStyle: String, val pathCaseSen: String,
                    val modifiedDate: LocalDateTime, var pageId: Int, var wasAddedDB: Boolean)
