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

import org.connectbot.data.sync.SyncBaseline

interface SyncSnapshotStore {
    suspend fun readLocal(): List<SyncRecord>
    suspend fun applyMerged(records: List<SyncRecord>)
}

interface SyncBaselineStore {
    suspend fun read(): SyncBaseline?
    suspend fun save(baseline: SyncBaseline)
}

sealed class WebDavSyncResult {
    data object Success : WebDavSyncResult()
    data class Conflict(val conflicts: List<SyncConflict>) : WebDavSyncResult()
    data object MissingConfiguration : WebDavSyncResult()
    data object Failed : WebDavSyncResult()
}

interface WebDavSyncOperation {
    suspend fun sync(
        config: WebDavBackupConfig,
        deviceId: String,
        now: Long = System.currentTimeMillis(),
    ): WebDavSyncResult
}

/** Coordinates one conflict-safe sync attempt. */
class WebDavSyncCoordinator(
    private val snapshots: SyncSnapshotStore,
    private val baselineStore: SyncBaselineStore,
    private val transport: WebDavBackupTransport,
    private val crypto: BackupCryptoEngine,
) : WebDavSyncOperation {
    override suspend fun sync(
        config: WebDavBackupConfig,
        deviceId: String,
        now: Long,
    ): WebDavSyncResult {
        if (!config.isValid()) return WebDavSyncResult.MissingConfiguration

        return try {
            val baseline = baselineStore.read()
            val local = snapshots.readLocal()
            val remoteFile = try {
                transport.downloadVersioned(config, config.normalizedRemotePath())
            } catch (e: WebDavException) {
                if (e.statusCode != 404) throw e
                null
            }
            val remoteDocument = remoteFile?.let {
                val remoteJson = crypto.decrypt(
                    it.bytes.toString(Charsets.UTF_8),
                    config.encryptionPassword,
                ).toString(Charsets.UTF_8)
                SyncProtocol.decodeDocument(remoteJson)
            }
            val remote = remoteDocument?.records.orEmpty()
            val base = baseline?.let { SyncProtocol.decode(it.recordsJson) }.orEmpty()
            val merge = SyncProtocol.merge(base, local, remote)

            if (merge.conflicts.isNotEmpty()) {
                return WebDavSyncResult.Conflict(merge.conflicts)
            }

            if (remoteFile != null && remoteFile.etag.isNullOrBlank()) {
                return WebDavSyncResult.Failed
            }

            val generation = maxOf(
                baseline?.generation ?: 0L,
                remoteDocument?.generation ?: 0L,
            ) + 1L
            val mergedJson = SyncProtocol.encode(merge.records, deviceId, generation)
            val encrypted = crypto.encrypt(mergedJson.toByteArray(), config.encryptionPassword)
            transport.uploadConditional(
                config,
                config.normalizedRemotePath(),
                encrypted.toByteArray(),
                remoteFile?.etag,
                createOnly = remoteFile == null,
            )
            snapshots.applyMerged(merge.records)
            baselineStore.save(
                SyncBaseline(
                    deviceId = deviceId,
                    generation = generation,
                    recordsJson = SyncProtocol.encode(merge.records, deviceId, generation),
                    updatedAt = now,
                ),
            )
            WebDavSyncResult.Success
        } catch (_: Exception) {
            WebDavSyncResult.Failed
        }
    }

    private fun WebDavBackupConfig.isValid(): Boolean = baseUrl.isNotBlank() &&
        username.isNotBlank() && password.isNotBlank() &&
        remotePath.isNotBlank() && encryptionPassword.isNotBlank()

    private fun WebDavBackupConfig.normalizedRemotePath(): String = remotePath.trim().trimStart('/')
}
