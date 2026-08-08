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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncProtocolTest {
    private val base = record("hosts", "host-1", payload = "old")

    @Test
    fun unchangedRemoteAndLocalProduceOneRecord() {
        val result = SyncProtocol.merge(listOf(base), listOf(base), listOf(base))

        assertThat(result.conflicts).isEmpty()
        assertThat(result.records).containsExactly(base)
    }

    @Test
    fun localOnlyChangeWinsWhenRemoteStillMatchesBase() {
        val local = base.copy(revision = 2, payload = "local")
        val result = SyncProtocol.merge(listOf(base), listOf(local), listOf(base))

        assertThat(result.conflicts).isEmpty()
        assertThat(result.records).containsExactly(local)
    }

    @Test
    fun remoteOnlyChangeWinsWhenLocalStillMatchesBase() {
        val remote = base.copy(revision = 2, payload = "remote")
        val result = SyncProtocol.merge(listOf(base), listOf(base), listOf(remote))

        assertThat(result.conflicts).isEmpty()
        assertThat(result.records).containsExactly(remote)
    }

    @Test
    fun divergentChangesAreNotSilentlyOverwritten() {
        val local = base.copy(revision = 2, payload = "local")
        val remote = base.copy(revision = 2, payload = "remote")
        val result = SyncProtocol.merge(listOf(base), listOf(local), listOf(remote))

        assertThat(result.records).isEmpty()
        assertThat(result.conflicts).hasSize(1)
        val conflict = result.conflicts.single()
        assertThat(conflict.local?.payload).isEqualTo("local")
        assertThat(conflict.remote?.payload).isEqualTo("remote")
    }

    @Test
    fun deletionIsARecordAndCanBeMerged() {
        val deleted = base.copy(revision = 2, deleted = true, payload = "")
        val result = SyncProtocol.merge(listOf(base), listOf(deleted), listOf(base))

        assertThat(result.conflicts).isEmpty()
        assertThat(result.records).containsExactly(deleted)
    }

    @Test
    fun encodedRecordsRoundTrip() {
        val records = listOf(base, base.copy(table = "profiles", key = "profile-1"))

        val decoded = SyncProtocol.decode(SyncProtocol.encode(records, "device-a", 3))

        assertThat(decoded).containsExactlyElementsOf(records.sortedWith(compareBy({ it.table }, { it.key })))
    }

    private fun record(table: String, key: String, payload: String): SyncRecord = SyncRecord(
        table = table,
        key = key,
        revision = 1,
        modifiedAt = 1_000,
        deleted = false,
        payload = payload,
    )
}
