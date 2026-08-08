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
import okhttp3.OkHttpClient

class OkHttpWebDavBackupTransport(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WebDavBackupTransport {
    override suspend fun upload(config: WebDavBackupConfig, path: String, bytes: ByteArray) {
        WebDavClient(
            baseUrl = config.baseUrl,
            username = config.username,
            password = config.password,
            httpClient = httpClient,
            ioDispatcher = ioDispatcher,
        ).upload(path, bytes)
    }

    override suspend fun download(config: WebDavBackupConfig, path: String): ByteArray = WebDavClient(
        baseUrl = config.baseUrl,
        username = config.username,
        password = config.password,
        httpClient = httpClient,
        ioDispatcher = ioDispatcher,
    ).download(path)

    override suspend fun uploadConditional(
        config: WebDavBackupConfig,
        path: String,
        bytes: ByteArray,
        ifMatch: String?,
        createOnly: Boolean,
    ) {
        WebDavClient(
            baseUrl = config.baseUrl,
            username = config.username,
            password = config.password,
            httpClient = httpClient,
            ioDispatcher = ioDispatcher,
        ).uploadConditional(path, bytes, ifMatch, createOnly)
    }

    override suspend fun downloadVersioned(config: WebDavBackupConfig, path: String): WebDavRemoteFile = WebDavClient(
        baseUrl = config.baseUrl,
        username = config.username,
        password = config.password,
        httpClient = httpClient,
        ioDispatcher = ioDispatcher,
    ).downloadVersioned(path)
}
