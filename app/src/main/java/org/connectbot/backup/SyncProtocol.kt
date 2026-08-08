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

import org.json.JSONArray
import org.json.JSONObject

/** A portable identity and payload for one synchronizable database row. */
data class SyncRecord(
    val table: String,
    val key: String,
    val revision: Long,
    val modifiedAt: Long,
    val deleted: Boolean,
    val payload: String,
)

data class SyncConflict(
    val table: String,
    val key: String,
    val base: SyncRecord?,
    val local: SyncRecord?,
    val remote: SyncRecord?,
)

data class SyncMergeResult(
    val records: List<SyncRecord>,
    val conflicts: List<SyncConflict>,
)

data class SyncDocument(
    val deviceId: String,
    val generation: Long,
    val records: List<SyncRecord>,
)

/**
 * Deterministic three-way merge for one encrypted backup's record set.
 *
 * A record is safe to take from one side when the other side still equals the
 * last successful sync baseline. Divergent edits are returned as conflicts and
 * never silently overwritten.
 */
object SyncProtocol {
    const val CURRENT_VERSION = 1

    fun merge(
        base: List<SyncRecord>,
        local: List<SyncRecord>,
        remote: List<SyncRecord>,
    ): SyncMergeResult {
        val baseByKey = base.associateBy { it.identity() }
        val localByKey = local.associateBy { it.identity() }
        val remoteByKey = remote.associateBy { it.identity() }
        val keys = (baseByKey.keys + localByKey.keys + remoteByKey.keys).sorted()
        val merged = mutableListOf<SyncRecord>()
        val conflicts = mutableListOf<SyncConflict>()

        for (identity in keys) {
            val baseRecord = baseByKey[identity]
            val localRecord = localByKey[identity]
            val remoteRecord = remoteByKey[identity]
            val selected = when {
                equivalent(localRecord, remoteRecord) -> localRecord ?: remoteRecord
                equivalent(localRecord, baseRecord) -> remoteRecord
                equivalent(remoteRecord, baseRecord) -> localRecord
                else -> null
            }
            if (selected != null) {
                merged += selected
            } else {
                conflicts += SyncConflict(
                    table = identity.substringBefore('\u0000'),
                    key = identity.substringAfter('\u0000'),
                    base = baseRecord,
                    local = localRecord,
                    remote = remoteRecord,
                )
            }
        }
        return SyncMergeResult(merged, conflicts)
    }

    fun encode(records: List<SyncRecord>, deviceId: String, generation: Long): String {
        val root = JSONObject()
            .put("syncVersion", CURRENT_VERSION)
            .put("deviceId", deviceId)
            .put("generation", generation)
        val array = JSONArray()
        records.sortedWith(compareBy({ it.table }, { it.key })).forEach { record ->
            array.put(
                JSONObject()
                    .put("table", record.table)
                    .put("key", record.key)
                    .put("revision", record.revision)
                    .put("modifiedAt", record.modifiedAt)
                    .put("deleted", record.deleted)
                    .put("payload", record.payload),
            )
        }
        return root.put("records", array).toString()
    }

    fun decode(json: String): List<SyncRecord> = decodeDocument(json).records

    fun decodeDocument(json: String): SyncDocument {
        val root = JSONObject(json)
        require(root.getInt("syncVersion") == CURRENT_VERSION) { "Unsupported sync version" }
        val array = root.getJSONArray("records")
        val records = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SyncRecord(
                table = item.getString("table"),
                key = item.getString("key"),
                revision = item.getLong("revision"),
                modifiedAt = item.getLong("modifiedAt"),
                deleted = item.getBoolean("deleted"),
                payload = item.getString("payload"),
            )
        }
        return SyncDocument(
            deviceId = root.getString("deviceId"),
            generation = root.getLong("generation"),
            records = records,
        )
    }

    private fun SyncRecord.identity(): String = "$table\u0000$key"

    private fun equivalent(left: SyncRecord?, right: SyncRecord?): Boolean = when {
        left == null && right == null -> true
        left == null || right == null -> false
        else -> left.table == right.table && left.key == right.key && left.deleted == right.deleted && left.payload == right.payload
    }
}
