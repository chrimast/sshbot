/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.backup

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Collections

class WebDavClientTest {

    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val storedFiles = Collections.synchronizedMap(mutableMapOf<String, ByteArray>())
    private val createdDirectories = Collections.synchronizedList(mutableListOf<String>())
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> handle(exchange) }
        start()
    }
    private val baseUrl = "http://127.0.0.1:${server.address.port}/dav/root/"

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun uploadCreatesMissingDirectoriesAndPutsBytes() = runBlocking {
        val client = WebDavClient(
            baseUrl = baseUrl,
            username = "alice",
            password = "secret",
        )

        client.upload("connectbot/backups/latest.json", "payload".toByteArray())

        assertEquals(
            listOf(
                "MKCOL /dav/root/connectbot/",
                "MKCOL /dav/root/connectbot/backups/",
                "PUT /dav/root/connectbot/backups/latest.json",
            ),
            requests.map { "${it.method} ${it.path}" },
        )
        assertEquals("Basic YWxpY2U6c2VjcmV0", requests.last().authorization)
        assertArrayEquals("payload".toByteArray(), storedFiles["/dav/root/connectbot/backups/latest.json"])
        assertEquals(listOf("/dav/root/connectbot/", "/dav/root/connectbot/backups/"), createdDirectories)
    }

    @Test
    fun downloadReturnsRemoteBytes() = runBlocking {
        storedFiles["/dav/root/connectbot/backups/latest.json"] = "remote".toByteArray()
        val client = WebDavClient(baseUrl = baseUrl, username = "", password = "")

        val result = client.download("connectbot/backups/latest.json")

        assertArrayEquals("remote".toByteArray(), result)
        assertEquals("GET /dav/root/connectbot/backups/latest.json", "${requests.single().method} ${requests.single().path}")
    }

    @Test(expected = WebDavException::class)
    fun downloadMissingFileThrowsWebDavException() {
        runBlocking {
            val client = WebDavClient(baseUrl = baseUrl, username = "", password = "")

            client.download("missing.json")
        }
    }

    @Test
    fun createOnlyUploadUsesIfNoneMatchWildcard() = runBlocking {
        val client = WebDavClient(baseUrl = baseUrl, username = "", password = "")

        client.uploadConditional("sync.json", "payload".toByteArray(), ifMatch = null, createOnly = true)

        assertEquals("*", requests.single().ifNoneMatch)
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes()
        val path = exchange.requestURI.rawPath
        requests.add(
            RecordedRequest(
                method = exchange.requestMethod,
                path = path,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                ifNoneMatch = exchange.requestHeaders.getFirst("If-None-Match"),
                body = body,
            ),
        )

        when (exchange.requestMethod) {
            "MKCOL" -> {
                createdDirectories.add(path)
                exchange.sendResponseHeaders(201, -1)
            }

            "PUT" -> {
                storedFiles[path] = body
                exchange.sendResponseHeaders(201, -1)
            }

            "GET" -> {
                val data = storedFiles[path]
                if (data == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.sendResponseHeaders(200, data.size.toLong())
                    exchange.responseBody.use { it.write(data) }
                }
            }

            else -> exchange.sendResponseHeaders(405, -1)
        }
        exchange.close()
    }

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val authorization: String?,
        val ifNoneMatch: String?,
        val body: ByteArray,
    )
}
