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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncIdentityMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: SyncIdentityMapping)

    @Query("SELECT * FROM sync_identity_mappings WHERE tableName = :tableName AND localId = :localId")
    suspend fun findByLocalId(tableName: String, localId: Long): SyncIdentityMapping?

    @Query("SELECT * FROM sync_identity_mappings WHERE tableName = :tableName AND stableKey = :stableKey")
    suspend fun findByStableKey(tableName: String, stableKey: String): SyncIdentityMapping?

    @Query("SELECT * FROM sync_identity_mappings WHERE tableName = :tableName")
    suspend fun findAll(tableName: String): List<SyncIdentityMapping>
}
