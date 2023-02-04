package tech.sozonov.blog.datasource.file
import java.time.LocalDateTime


sealed class IngestedFile {
    data class CreateUpdate(val fullPath: String, val content: String, val styleContent: String,
                            val modifTime: LocalDateTime, val jsModules: List<String>) : IngestedFile()
    data class Delete(val fullPath: String) : IngestedFile()
}