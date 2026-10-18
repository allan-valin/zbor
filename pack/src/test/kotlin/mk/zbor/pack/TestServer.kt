package mk.zbor.pack

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress

/**
 * Serves a directory with Range support. [cutAfter] bytes into a response it
 * drops the connection (spotty wifi); [ignoreRange] answers 200 to range requests.
 */
class TestServer(private val root: File) : AutoCloseable {
    var cutAfter: Long = Long.MAX_VALUE
    var ignoreRange = false
    val rangeRequests = mutableListOf<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext("/") { ex ->
            val f = File(root, ex.requestURI.path.removePrefix("/"))
            if (!f.isFile) { ex.sendResponseHeaders(404, -1); ex.close(); return@createContext }
            val range = ex.requestHeaders.getFirst("Range")
            var start = 0L
            if (range != null && !ignoreRange) {
                rangeRequests += range
                start = range.removePrefix("bytes=").substringBefore('-').toLong()
                ex.responseHeaders.add("Content-Range", "bytes $start-${f.length() - 1}/${f.length()}")
                ex.sendResponseHeaders(206, f.length() - start)
            } else {
                ex.sendResponseHeaders(200, f.length())
            }
            try {
                f.inputStream().use { input ->
                    input.skip(start)
                    val buf = ByteArray(8192)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (sent + n > cutAfter) {
                            ex.responseBody.write(buf, 0, (cutAfter - sent).toInt())
                            ex.responseBody.flush()
                            Thread.sleep(300) // let the client read what was sent before the reset
                            cutAfter = Long.MAX_VALUE // only once
                            throw java.io.IOException("cut")
                        }
                        ex.responseBody.write(buf, 0, n); sent += n
                    }
                }
            } catch (_: java.io.IOException) {
            } finally { ex.close() }
        }
        server.start()
    }

    val url get() = "http://127.0.0.1:${server.address.port}/"
    override fun close() = server.stop(0)
}
