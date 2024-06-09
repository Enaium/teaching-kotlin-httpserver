package cn.enaium.httpserver

import java.io.BufferedReader
import java.net.ServerSocket

/**
 * @author Enaium
 */
fun main() {
    HttpServer(8080).start()
}

class HttpServer(port: Int) {
    private val serverSocket = ServerSocket(port)

    fun start() {
        val router = Router()
        router.get("/") { _ ->
            HttpResponse(Version.HTTP_1_1, 200, "OK")
        }
        router.get("/hello") { _ ->
            HttpResponse(
                Version.HTTP_1_1,
                200,
                "OK",
                headers = mapOf(
                    "Content-Type" to "text/html"
                ),
                body = "<h1>Hello World!</h1>"
            )
        }
        router.handle(serverSocket)
    }
}

class Router {
    private val routes = mutableListOf<Route>()

    fun get(path: String, handler: Handler) {
        routes.add(Route(Method.GET, path, handler))
    }

    fun handle(socket: ServerSocket) {
        while (true) {
            val client = socket.accept()
            val reader = client.getInputStream().bufferedReader()
            val writer = client.getOutputStream().bufferedWriter()
            val httpRequest = HttpRequest.parse(reader)
            routes.findLast { it.method == httpRequest.method && it.path == httpRequest.path }?.let {
                val toString = it.handler.invoke(httpRequest).toString()
                writer.write(toString)
                writer.flush()
            } ?: let {
                writer.write(
                    HttpResponse(
                        Version.HTTP_1_1,
                        404,
                        "NotFound",
                        headers = mapOf("Content-Type" to "text/html"),
                        body = "<h1>404 Not Found</h1>"
                    ).toString()
                )
                writer.flush()
            }
            client.close()
        }
    }
}

data class Route(
    val method: Method,
    val path: String,
    val handler: Handler
)

typealias Handler = (HttpRequest) -> HttpResponse

data class HttpRequest(
    val method: Method,
    val path: String,
    val version: Version,
    val headers: Map<String, String>,
    val body: String
) {
    companion object {
        fun parse(reader: BufferedReader): HttpRequest {
            var method = Method.UNKNOWN
            var path = ""
            var version = Version.UNKNOWN
            val headers = mutableMapOf<String, String>()
            var body = ""

            val request = StringBuilder()
            var readed: String?
            while (reader.readLine().also { readed = it } != null && readed!!.isNotEmpty()) {
                request.append(readed).append("\n")
            }

            request.lines().forEachIndexed { index, line ->
                if (index == 0) {
                    val splitWithSpace = line.split(" ")
                    method = Method.parse(splitWithSpace[0])
                    path = splitWithSpace[1]
                    version = Version.parse(splitWithSpace[2])
                } else if (line.contains(": ")) {
                    val split = line.split(": ")
                    headers[split[0]] = split[1]
                } else if (line.isEmpty()) {

                } else {
                    body = line
                }
            }
            return HttpRequest(
                method, path, version, headers, body
            )
        }
    }
}

data class HttpResponse(
    val version: Version = Version.HTTP_1_1,
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = ""
) {
    override fun toString(): String {
        return """
            $version $statusCode $statusText
            ${headers.map { "${it.key}: ${it.value}" }.joinToString("\n")}
            
            $body
        """.trimIndent()
    }
}

enum class Method {
    GET,
    POST,
    UNKNOWN;

    companion object {
        fun parse(method: String): Method = when (method) {
            "GET" -> GET
            "POST" -> POST
            else -> UNKNOWN
        }
    }
}

enum class Version {
    HTTP_1_1,
    UNKNOWN;

    companion object {
        fun parse(version: String): Version = when (version) {
            "HTTP/1.1" -> HTTP_1_1
            else -> UNKNOWN
        }
    }

    override fun toString(): String {
        return when (this) {
            HTTP_1_1 -> "HTTP/1.1"
            UNKNOWN -> "UNKNOWN"
        }
    }
}