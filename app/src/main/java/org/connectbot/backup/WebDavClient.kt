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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun upload(path: String, bytes: ByteArray) = withContext(ioDispatcher) {
        val normalizedPath = normalizeRemotePath(path)
        createParentDirectories(normalizedPath)
        execute("PUT", normalizedPath, bytes).use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("WebDAV upload failed", response.code)
            }
        }
    }

    suspend fun uploadConditional(
        path: String,
        bytes: ByteArray,
        ifMatch: String?,
        createOnly: Boolean = false,
    ) = withContext(ioDispatcher) {
        val normalizedPath = normalizeRemotePath(path)
        createParentDirectories(normalizedPath)
        val headers = when {
            createOnly -> mapOf("If-None-Match" to "*")
            ifMatch != null -> mapOf("If-Match" to ifMatch)
            else -> emptyMap()
        }
        execute("PUT", normalizedPath, bytes, headers).use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("WebDAV conditional upload failed", response.code)
            }
        }
    }

    suspend fun download(path: String): ByteArray = withContext(ioDispatcher) {
        execute("GET", normalizeRemotePath(path)).use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("WebDAV download failed", response.code)
            }
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    suspend fun downloadVersioned(path: String): WebDavRemoteFile = withContext(ioDispatcher) {
        execute("GET", normalizeRemotePath(path)).use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("WebDAV download failed", response.code)
            }
            WebDavRemoteFile(response.body?.bytes() ?: ByteArray(0), response.header("ETag"))
        }
    }

    private fun createParentDirectories(path: String) {
        val segments = path.split('/').filter { it.isNotBlank() }.dropLast(1)
        var current = ""
        for (segment in segments) {
            current += "$segment/"
            execute("MKCOL", current).use { response ->
                if (response.code != 201 && response.code != 405) {
                    throw WebDavException("WebDAV directory creation failed", response.code)
                }
            }
        }
    }

    private fun execute(
        method: String,
        path: String,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
    ): okhttp3.Response {
        val requestBody = body?.toRequestBody("application/octet-stream".toMediaType())
        val requestBuilder = Request.Builder()
            .url(baseUrl.ensureTrailingSlash() + normalizeRemotePath(path))
            .header("Authorization", Credentials.basic(username, password))
        headers.forEach(requestBuilder::header)
        val request = requestBuilder.method(method, requestBody).build()

        try {
            return httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw WebDavException("WebDAV request failed", cause = e)
        }
    }

    private fun normalizeRemotePath(path: String): String = path.trim().trimStart('/')

    private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
}

class WebDavException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
