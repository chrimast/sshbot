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

package org.connectbot.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncIdentityMappingDaoRobolectricTest {
    private lateinit var database: ConnectBotDatabase
    private lateinit var dao: SyncIdentityMappingDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.syncIdentityMappingDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndFindByStableKey() = runTest {
        dao.upsert(SyncIdentityMapping("hosts", 7L, "v1-host-7", 3L, 123L, false))

        assertThat(dao.findByStableKey("hosts", "v1-host-7")?.localId).isEqualTo(7L)
    }

    @Test
    fun upsertReplacesRevisionAndTombstone() = runTest {
        dao.upsert(SyncIdentityMapping("hosts", 7L, "v1-host-7", 3L, 123L, false))
        dao.upsert(SyncIdentityMapping("hosts", 7L, "v1-host-7", 4L, 456L, true))

        val mapping = dao.findByLocalId("hosts", 7L)
        assertThat(mapping?.revision).isEqualTo(4L)
        assertThat(mapping?.modifiedAt).isEqualTo(456L)
        assertThat(mapping?.deleted).isTrue()
    }
}
