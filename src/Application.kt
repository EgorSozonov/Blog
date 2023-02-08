package tech.sozonov.blog
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import tech.sozonov.blog.core.Blog
import tech.sozonov.blog.core.C
import java.io.File
import java.time.Duration
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.http.content.CachingOptions

@Suppress("unused")
fun Application.module() {
    println("hello world")
    //install(CachingHeaders)
    val rootPath = environment.config.propertyOrNull("ktor.fileRoot")?.getString()!!
    Blog.rootPath = rootPath
    configureRouting()
}


fun Application.configureRouting() {
    val mainUrl = "/" + C.appSubfolder + "{subUrl...}"
    val mediaUrl = "/" + C.appSubfolder + C.mediaSubfolder
    val scriptUrl = "/" + C.appSubfolder + C.scriptsSubfolder
    routing {
        trace { application.log.trace(it.buildText()) }

        // Expiration dates for different content types
        install(CachingHeaders) {
            options { call, content ->
                when (content.contentType?.withoutParameters()) {
                    ContentType.Text.JavaScript -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600))
                    ContentType.Text.Html -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 300))
                    else -> null
                }
            }
        }
        get("/" + C.appSubfolder) {
            val queryParams = call.request.queryParameters.flattenEntries()

            val responseHTML = Blog.buildGetResponse("", queryParams)

            call.respondText(responseHTML, ContentType.Text.Html, HttpStatusCode.OK)
        }
        get(mainUrl) {
            val subUrlChunks: List<String>? = call.parameters.getAll("subUrl")
            if (subUrlChunks == null) {
                call.respondText("Error: empty request!", ContentType.Text.Plain, HttpStatusCode.BadRequest)
                return@get
            }
            if (subUrlChunks.size > 256) {
                call.respondText("Error: request too long!", ContentType.Text.Plain, HttpStatusCode.BadRequest)
                return@get
            }
            val subUrl: String = subUrlChunks.joinToString("/")
            if (subUrl.length > 1024) {
                call.respondText("Error: request too long!", ContentType.Text.Plain, HttpStatusCode.BadRequest)
                return@get
            }
            val queryParams = call.request.queryParameters.flattenEntries()

            val responseHTML = Blog.buildGetResponse(subUrl, queryParams)
            call.respondText(responseHTML, ContentType.Text.Html, HttpStatusCode.OK)
        }

        static(mediaUrl) {
            this.staticRootFolder = File(Blog.rootPath + C.mediaSubfolder)
            files(".")
        }

        static(scriptUrl) {
            this.staticRootFolder = File(Blog.rootPath + C.scriptsSubfolder)
            files(".")
        }
    }
}