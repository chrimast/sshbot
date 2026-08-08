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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.DatabaseSchema
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Profile
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomSyncSnapshotStoreTest {
    private lateinit var database: ConnectBotDatabase
    private lateinit var store: RoomSyncSnapshotStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomSyncSnapshotStore(database, DatabaseSchema.load(context), now = { 100L })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun readsProfilesAndHostsWithStableRelationshipKeys() = runTest {
        val profileId = database.profileDao().insert(Profile(name = "Ops"))
        database.hostDao().insert(Host(nickname = "prod", hostname = "example.com", profileId = profileId))

        val records = store.readLocal()
        val profile = records.single { it.table == "profiles" }
        val host = records.single { it.table == "hosts" }

        assertThat(profile.revision).isEqualTo(1L)
        assertThat(JSONObject(host.payload).getString("profileKey")).isEqualTo(profile.key)
        assertThat(JSONObject(host.payload).has("profileId")).isFalse()
    }

    @Test
    fun changedPayloadIncrementsRevision() = runTest {
        val profileId = database.profileDao().insert(Profile(name = "Default"))
        val hostId = database.hostDao().insert(
            Host(nickname = "prod", hostname = "example.com", profileId = profileId),
        )
        val first = store.readLocal().single { it.table == "hosts" }
        database.hostDao().update(database.hostDao().getById(hostId)!!.copy(hostname = "new.example.com"))

        val second = store.readLocal().single { it.table == "hosts" }

        assertThat(second.key).isEqualTo(first.key)
        assertThat(second.revision).isEqualTo(2L)
        assertThat(second.modifiedAt).isEqualTo(100L)
    }

    @Test
    fun appliesPortableRecordsAndRebuildsLocalRelationships() = runTest {
        val profileId = database.profileDao().insert(Profile(name = "Ops"))
        database.hostDao().insert(
            Host(nickname = "prod", hostname = "example.com", profileId = profileId),
        )
        val records = store.readLocal()

        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomSyncSnapshotStore(database, DatabaseSchema.load(context), now = { 200L })

        store.applyMerged(records)

        val importedProfile = database.profileDao().getAll().single()
        val importedHost = database.hostDao().getAll().single()
        assertThat(importedHost.profileId).isEqualTo(importedProfile.id)
        assertThat(importedHost.hostname).isEqualTo("example.com")
    }

    @Test
    fun appliesUpdateAndTombstoneByStableKey() = runTest {
        val profileId = database.profileDao().insert(Profile(name = "Default"))
        database.hostDao().insert(
            Host(nickname = "prod", hostname = "old.example.com", profileId = profileId),
        )
        val records = store.readLocal()
        val host = records.single { it.table == "hosts" }
        val changedPayload = JSONObject(host.payload).put("hostname", "new.example.com").toString()

        store.applyMerged(records.map { if (it == host) host.copy(revision = 2, payload = changedPayload) else it })
        assertThat(database.hostDao().getAll().single().hostname).isEqualTo("new.example.com")

        store.applyMerged(listOf(host.copy(revision = 3, deleted = true, payload = "")))
        assertThat(database.hostDao().getAll()).isEmpty()
    }

    @Test
    fun preservesTombstoneForRecordNeverPresentOnThisDevice() = runTest {
        val tombstone = SyncRecord("hosts", "remote-deleted-host", 3, 100, true, "")

        store.applyMerged(listOf(tombstone))

        assertThat(store.readLocal()).contains(tombstone)
    }
}
