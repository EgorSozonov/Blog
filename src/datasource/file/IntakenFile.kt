package tech.sozonov.blog.datasource.file
import java.time.LocalDateTime


sealed class IntakenFile {
    class CreateUpdate(val fullPath: String, val content: String, val styleContent: String,
                       val modifTime: LocalDateTime) : IntakenFile()
    class Delete(val fullPath: String) : IntakenFile()
}