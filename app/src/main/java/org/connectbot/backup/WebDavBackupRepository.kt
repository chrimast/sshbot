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

interface ConnectBotBackupExporter {
    fun exportJson(): String
    fun importJson(json: String)
}

interface BackupCryptoEngine {
    fun encrypt(plaintext: ByteArray, password: String): String
    fun decrypt(envelope: String, password: String): ByteArray
}

interface WebDavBackupTransport {
    suspend fun upload(config: WebDavBackupConfig, path: String, bytes: ByteArray)
    suspend fun download(config: WebDavBackupConfig, path: String): ByteArray

    suspend fun uploadConditional(
        config: WebDavBackupConfig,
        path: String,
        bytes: ByteArray,
        ifMatch: String?,
        createOnly: Boolean = false,
    ) = upload(config, path, bytes)

    suspend fun downloadVersioned(config: WebDavBackupConfig, path: String): WebDavRemoteFile =
        WebDavRemoteFile(download(config, path), null)
}

data class WebDavRemoteFile(val bytes: ByteArray, val etag: String?)

data class WebDavBackupConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    val remotePath: String,
    val encryptionPassword: String,
)

enum class WebDavBackupResult {
    Success,
    MissingConfiguration,
    Failed,
}

class WebDavBackupRepository(
    private val exporter: ConnectBotBackupExporter,
    private val crypto: BackupCryptoEngine,
    private val transport: WebDavBackupTransport,
) {
    suspend fun backup(config: WebDavBackupConfig): WebDavBackupResult {
        if (!config.isValid()) {
            return WebDavBackupResult.MissingConfiguration
        }
        return try {
            val json = exporter.exportJson()
            val encrypted = crypto.encrypt(json.toByteArray(), config.encryptionPassword)
            transport.upload(config, config.normalizedRemotePath(), encrypted.toByteArray())
            WebDavBackupResult.Success
        } catch (e: Exception) {
            WebDavBackupResult.Failed
        }
    }

    suspend fun restore(config: WebDavBackupConfig): WebDavBackupResult {
        if (!config.isValid()) {
            return WebDavBackupResult.MissingConfiguration
        }
        return try {
            val encrypted = transport.download(config, config.normalizedRemotePath()).toString(Charsets.UTF_8)
            val json = crypto.decrypt(encrypted, config.encryptionPassword).toString(Charsets.UTF_8)
            exporter.importJson(json)
            WebDavBackupResult.Success
        } catch (e: Exception) {
            WebDavBackupResult.Failed
        }
    }

    private fun WebDavBackupConfig.isValid(): Boolean = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() &&
        remotePath.isNotBlank() && encryptionPassword.isNotBlank()

    private fun WebDavBackupConfig.normalizedRemotePath(): String = remotePath.trim().trimStart('/')
}
