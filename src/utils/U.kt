package tech.sozonov.blog.utils

fun String.pathize(): String {
    return if (this.endsWith("/")) { this } else { "$this/" }
}