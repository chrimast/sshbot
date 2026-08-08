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

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.sync.SyncBaseline
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebDavSyncCoordinatorTest {
    private val config = WebDavBackupConfig("https://dav.example/", "u", "p", "sync.json", "secret")

    @Test
    fun conflictDoesNotApplyUploadOrAdvanceBaseline() = runTest {
        val base = record("hosts", "host-1", "old")
        val local = record("hosts", "host-1", "local")
        val remote = record("hosts", "host-1", "remote")
        val events = mutableListOf<String>()
        val store = FakeSnapshotStore(listOf(local), events)
        val baseline = FakeBaselineStore(
            SyncBaseline(
                deviceId = "device-a",
                generation = 3L,
                recordsJson = SyncProtocol.encode(listOf(base), "device-a", 3L),
                updatedAt = 10L,
            ),
            events,
        )
        val transport = FakeTransport(SyncProtocol.encode(listOf(remote), "other", 4), events)
        val coordinator = WebDavSyncCoordinator(store, baseline, transport, PlainCrypto)

        val result = coordinator.sync(config, "device-a", now = 1000L)

        assertThat(result).isInstanceOf(WebDavSyncResult.Conflict::class.java)
        assertThat(store.applied).isEmpty()
        assertThat(transport.uploads).isEmpty()
        assertThat(baseline.saved).isFalse()
    }

    @Test
    fun successfulSyncAppliesUploadsAndAdvancesBaselineInOrder() = runTest {
        val local = record("hosts", "host-1", "local")
        val events = mutableListOf<String>()
        val store = FakeSnapshotStore(listOf(local), events)
        val baseline = FakeBaselineStore(null, events)
        val transport = FakeTransport(SyncProtocol.encode(emptyList(), "other", 2), events)
        val coordinator = WebDavSyncCoordinator(store, baseline, transport, PlainCrypto)

        val result = coordinator.sync(config, "device-a", now = 1000L)

        assertThat(result).isEqualTo(WebDavSyncResult.Success)
        assertThat(store.applied).containsExactly(local)
        assertThat(transport.uploads).hasSize(1)
        assertThat(baseline.saved).isTrue()
        assertThat(baseline.value?.recordsJson).contains("host-1")
        assertThat(events).containsExactly("upload", "apply", "baseline")
    }

    @Test
    fun concurrentRemoteWriteDoesNotAdvanceBaseline() = runTest {
        val events = mutableListOf<String>()
        val store = FakeSnapshotStore(listOf(record("hosts", "host-1", "local")), events)
        val baseline = FakeBaselineStore(null, events)
        val transport = FakeTransport(
            SyncProtocol.encode(emptyList(), "other", 2),
            events,
            uploadFailure = WebDavException("precondition failed", 412),
        )
        val coordinator = WebDavSyncCoordinator(store, baseline, transport, PlainCrypto)

        val result = coordinator.sync(config, "device-a", now = 1000L)

        assertThat(result).isEqualTo(WebDavSyncResult.Failed)
        assertThat(store.applied).isEmpty()
        assertThat(baseline.saved).isFalse()
        assertThat(transport.ifMatch).isEqualTo("etag-1")
    }

    @Test
    fun missingRemoteCreatesFileWithoutOverwritingConcurrentCreator() = runTest {
        val events = mutableListOf<String>()
        val local = record("hosts", "host-1", "local")
        val store = FakeSnapshotStore(listOf(local), events)
        val baseline = FakeBaselineStore(null, events)
        val transport = FakeTransport("", events, downloadFailure = WebDavException("not found", 404))
        val coordinator = WebDavSyncCoordinator(store, baseline, transport, PlainCrypto)

        val result = coordinator.sync(config, "device-a", now = 1000L)

        assertThat(result).isEqualTo(WebDavSyncResult.Success)
        assertThat(transport.ifMatch).isNull()
        assertThat(transport.createOnly).isTrue()
        assertThat(store.applied).containsExactly(local)
        assertThat(baseline.saved).isTrue()
    }

    @Test
    fun remoteGenerationControlsNextGeneration() = runTest {
        val events = mutableListOf<String>()
        val store = FakeSnapshotStore(emptyList(), events)
        val baseline = FakeBaselineStore(
            SyncBaseline(
                deviceId = "device-a",
                generation = 2,
                recordsJson = SyncProtocol.encode(emptyList(), "device-a", 2),
                updatedAt = 10,
            ),
            events,
        )
        val transport = FakeTransport(SyncProtocol.encode(emptyList(), "other", 9), events)
        val coordinator = WebDavSyncCoordinator(store, baseline, transport, PlainCrypto)

        val result = coordinator.sync(config, "device-a", now = 1000L)

        assertThat(result).isEqualTo(WebDavSyncResult.Success)
        assertThat(baseline.value?.generation).isEqualTo(10L)
        assertThat(SyncProtocol.decodeDocument(transport.uploads.single().toString(Charsets.UTF_8)).generation).isEqualTo(10L)
    }

    @Test
    fun existingRemoteWithoutEtagFailsWithoutWrites() = runTest {
        val events = mutableListOf<String>()
        val store = FakeSnapshotStore(listOf(record("hosts", "host-1", "local")), events)
        val baseline = FakeBaselineStore(null, events)
        val transport = FakeTransport(
            SyncProtocol.encode(emptyList(), "other", 1),
            events,
            etag = null,
        )
        val coordinator = WebDavSyncCoordinator(store, baseline, transport, PlainCrypto)

        val result = coordinator.sync(config, "device-a", now = 1000L)

        assertThat(result).isEqualTo(WebDavSyncResult.Failed)
        assertThat(transport.uploads).isEmpty()
        assertThat(store.applied).isEmpty()
        assertThat(baseline.saved).isFalse()
    }

    private fun record(table: String, key: String, payload: String) = SyncRecord(table, key, 1, 1, false, payload)
}

private object PlainCrypto : BackupCryptoEngine {
    override fun encrypt(plaintext: ByteArray, password: String): String = plaintext.toString(Charsets.UTF_8)
    override fun decrypt(envelope: String, password: String): ByteArray = envelope.toByteArray()
}

private class FakeSnapshotStore(
    private val local: List<SyncRecord>,
    private val events: MutableList<String>,
) : SyncSnapshotStore {
    val applied = mutableListOf<SyncRecord>()
    override suspend fun readLocal(): List<SyncRecord> = local
    override suspend fun applyMerged(records: List<SyncRecord>) {
        events += "apply"
        applied += records
    }
}

private class FakeBaselineStore(
    initial: SyncBaseline?,
    private val events: MutableList<String>,
) : SyncBaselineStore {
    var value: SyncBaseline? = initial
    var saved = false
    override suspend fun read(): SyncBaseline? = value
    override suspend fun save(baseline: SyncBaseline) {
        events += "baseline"
        saved = true
        value = baseline
    }
}

private class FakeTransport(
    private val remote: String,
    private val events: MutableList<String>,
    private val uploadFailure: Exception? = null,
    private val downloadFailure: Exception? = null,
    private val etag: String? = "etag-1",
) : WebDavBackupTransport {
    val uploads = mutableListOf<ByteArray>()
    var ifMatch: String? = null
    var createOnly = false
    override suspend fun upload(config: WebDavBackupConfig, path: String, bytes: ByteArray) {
        events += "upload"
        uploads += bytes
    }
    override suspend fun download(config: WebDavBackupConfig, path: String): ByteArray = remote.toByteArray()

    override suspend fun downloadVersioned(config: WebDavBackupConfig, path: String): WebDavRemoteFile {
        downloadFailure?.let { throw it }
        return WebDavRemoteFile(remote.toByteArray(), etag)
    }

    override suspend fun uploadConditional(
        config: WebDavBackupConfig,
        path: String,
        bytes: ByteArray,
        ifMatch: String?,
        createOnly: Boolean,
    ) {
        this.ifMatch = ifMatch
        this.createOnly = createOnly
        uploadFailure?.let { throw it }
        upload(config, path, bytes)
    }
}
