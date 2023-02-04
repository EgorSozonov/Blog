package tech.sozonov.blog.datasource.file

/** Fixed set of ingested core stuff, but not the global JS modules */
data class IngestedCore(val js: String, val css: String, val htmlNotFound: IngestedFile?,
                        val htmlFooter: IngestedFile?, val htmlRoot: IngestedFile?,
                        val htmlTermsUse: IngestedFile?)