package tech.sozonov.blog.datasource.file

import java.time.LocalDateTime

data class IngestedJSModule(val fullPath: String, val content: String, val modifTime: LocalDateTime)