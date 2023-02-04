package tech.sozonov.blog
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import tech.sozonov.blog.core.Blog
import tech.sozonov.blog.core.Constant
import java.io.File


@Suppress("unused")
fun Application.module() {
    println("hello world")
    val rootPath = environment.config.propertyOrNull("ktor.fileRoot")?.getString()!!
    Blog.rootPath = rootPath
    configureRouting()
}


fun Application.configureRouting() {
    val mainUrl = "/" + Constant.appSubfolder + "{subUrl...}"
    val mediaUrl = "/" + Constant.appSubfolder + Constant.mediaSubfolder
    val scriptUrl = "/" + Constant.appSubfolder + Constant.scriptsSubfolder

    routing {
        trace { application.log.trace(it.buildText()) }

        get(mainUrl) {
            val subUrlChunks: List<String>? = call.parameters.getAll("subUrl")
            val subUrl: String = subUrlChunks?.map{ it.lowercase() }?.joinToString("/") ?: ""

            val queryParams = call.request.queryParameters.flattenEntries()

            val responseHTML = Blog.buildGetResponse(subUrl, queryParams)
            call.respondText(responseHTML, ContentType.Text.Html, HttpStatusCode.OK)
        }
        static(mediaUrl) {
            this.staticRootFolder = File(Blog.rootPath + Constant.mediaSubfolder)
            files(".")
        }
        static(scriptUrl) {
            this.staticRootFolder = File(Blog.rootPath + Constant.scriptsSubfolder)
            files(".")
        }
    }
}