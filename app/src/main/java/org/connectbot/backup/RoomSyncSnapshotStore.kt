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

import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import androidx.room.withTransaction
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.DatabaseSchema
import org.connectbot.data.EntitySchema
import org.connectbot.data.sync.SyncIdentityMapping
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

class RoomSyncSnapshotStore(
    private val database: ConnectBotDatabase,
    private val schema: DatabaseSchema,
    private val now: () -> Long = System::currentTimeMillis,
) : SyncSnapshotStore {
    private val mappingDao = database.syncIdentityMappingDao()

    override suspend fun readLocal(): List<SyncRecord> = database.withTransaction {
        val records = mutableListOf<SyncRecord>()
        for (table in TABLES) {
            val entity = schema.getEntity(table) ?: continue
            val rows = readRows(entity)
            val seen = mutableSetOf<Long>()
            for (row in rows) {
                val localId = (row["id"] as Number).toLong()
                seen += localId
                var mapping = mappingDao.findByLocalId(table, localId)
                if (mapping == null) {
                    val bootstrapPayload = portablePayload(table, row)
                    mapping = SyncIdentityMapping(
                        tableName = table,
                        localId = localId,
                        stableKey = SyncIdentity.forRow(table, jsonMap(bootstrapPayload)),
                        revision = 0L,
                        modifiedAt = now(),
                        deleted = false,
                    )
                    mappingDao.upsert(mapping)
                }
                val payload = portablePayload(table, row)
                val payloadHash = sha256(payload)
                val changed = mapping.deleted || mapping.payloadHash != payloadHash
                val updated = if (changed) {
                    mapping.copy(
                        revision = mapping.revision + 1L,
                        modifiedAt = now(),
                        deleted = false,
                        payloadHash = payloadHash,
                    ).also { mappingDao.upsert(it) }
                } else {
                    mapping
                }
                records += updated.toRecord(payload)
            }
            for (mapping in mappingDao.findAll(table).filter { it.localId !in seen && !it.deleted }) {
                val deleted = mapping.copy(
                    revision = mapping.revision + 1L,
                    modifiedAt = now(),
                    deleted = true,
                    payloadHash = "",
                )
                mappingDao.upsert(deleted)
                records += deleted.toRecord("")
            }
            records += mappingDao.findAll(table)
                .filter { it.localId !in seen && it.deleted }
                .map { it.toRecord("") }
        }
        records
    }

    override suspend fun applyMerged(records: List<SyncRecord>) = database.withTransaction {
        val recordsByTable = records.groupBy { it.table }
        for (table in TABLES.asReversed()) {
            for (record in recordsByTable[table].orEmpty().filter { it.deleted }) {
                val mapping = mappingDao.findByStableKey(table, record.key)
                    ?: SyncIdentityMapping(
                        tableName = table,
                        localId = nextTombstoneId(table),
                        stableKey = record.key,
                        revision = record.revision,
                        modifiedAt = record.modifiedAt,
                        deleted = true,
                    )
                if (mapping.localId > 0) {
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM $table WHERE id = ?",
                        arrayOf(mapping.localId),
                    )
                }
                mappingDao.upsert(mapping.fromRecord(record, ""))
            }
        }
        for (table in TABLES) {
            val entity = schema.getEntity(table) ?: continue
            for (record in recordsByTable[table].orEmpty().filterNot { it.deleted }) {
                upsertRecord(entity, record, deferSelfReferences = table == "hosts")
            }
        }
        for (record in recordsByTable["hosts"].orEmpty().filterNot { it.deleted }) {
            updateHostJumpReference(record)
        }
        database.invalidationTracker.refreshVersionsAsync()
    }

    private suspend fun portablePayload(table: String, row: Map<String, Any?>): String {
        val stableKeys = TABLES.flatMap { parentTable ->
            mappingDao.findAll(parentTable).map { mapping ->
                (parentTable to mapping.localId) to mapping.stableKey
            }
        }.toMap()
        return SyncRowMapper.toPortablePayload(table, row) { parentTable, localId ->
            stableKeys[parentTable to localId]
        }
    }

    private fun readRows(entity: EntitySchema): List<Map<String, Any?>> {
        val fields = entity.fields.filter { !it.excluded }
        val columns = fields.joinToString(", ") { it.columnName }
        return database.openHelper.readableDatabase.query("SELECT $columns FROM ${entity.tableName}").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(fields.associate { field -> field.fieldPath to cursor.value(field.columnName, field.affinity) })
                }
            }
        }
    }

    private suspend fun upsertRecord(entity: EntitySchema, record: SyncRecord, deferSelfReferences: Boolean) {
        val existing = mappingDao.findByStableKey(entity.tableName, record.key)
        val values = payloadValues(
            entity,
            JSONObject(record.payload),
            deferSelfReferences,
            initializeExcluded = existing == null,
        )
        val db = database.openHelper.writableDatabase
        val localId = if (existing == null) {
            db.insert(entity.tableName, 0, values)
        } else {
            db.update(entity.tableName, 0, values, "id = ?", arrayOf(existing.localId.toString()))
            existing.localId
        }
        require(localId > 0) { "Failed to apply ${entity.tableName}:${record.key}" }
        mappingDao.upsert(
            (existing ?: SyncIdentityMapping(entity.tableName, localId, record.key, 0L, 0L, false))
                .fromRecord(record, sha256(record.payload)),
        )
    }

    private suspend fun payloadValues(
        entity: EntitySchema,
        payload: JSONObject,
        deferSelfReferences: Boolean,
        initializeExcluded: Boolean,
    ): ContentValues {
        val values = ContentValues()
        val relationshipFields = RELATIONSHIPS[entity.tableName].orEmpty()
        for (field in entity.fields.filter { it.fieldPath != "id" }) {
            if (field.excluded) {
                if (initializeExcluded && field.notNull) {
                    when (field.affinity) {
                        "INTEGER" -> values.put(field.columnName, 0L)
                        "REAL" -> values.put(field.columnName, 0.0)
                        "BLOB" -> values.put(field.columnName, ByteArray(0))
                        else -> values.put(field.columnName, "")
                    }
                }
                continue
            }
            if (initializeExcluded && !payload.has(field.fieldPath)) {
                val defaultValue: Any? = INSERT_DEFAULTS[entity.tableName to field.fieldPath]
                if (defaultValue != null) {
                    when (defaultValue) {
                        is Long -> values.put(field.columnName, defaultValue)
                        is String -> values.put(field.columnName, defaultValue)
                    }
                    continue
                }
            }
            val relationship = relationshipFields.firstOrNull { it.localField == field.fieldPath }
            if (relationship != null) {
                if (deferSelfReferences && relationship.parentTable == entity.tableName) continue
                val stableKey = payload.optString(relationship.remoteField).takeIf { it.isNotBlank() }
                val localId = stableKey?.let { mappingDao.findByStableKey(relationship.parentTable, it)?.localId }
                if (localId != null) values.put(field.columnName, localId) else values.putNull(field.columnName)
                continue
            }
            if (!payload.has(field.fieldPath)) continue
            when (field.affinity) {
                "INTEGER" -> values.put(field.columnName, payload.getLong(field.fieldPath))
                "REAL" -> values.put(field.columnName, payload.getDouble(field.fieldPath))
                "BLOB" -> values.put(field.columnName, Base64.decode(payload.getString(field.fieldPath), Base64.NO_WRAP))
                else -> values.put(field.columnName, payload.getString(field.fieldPath))
            }
        }
        return values
    }

    private suspend fun updateHostJumpReference(record: SyncRecord) {
        val payload = JSONObject(record.payload)
        val jumpKey = payload.optString("jumpHostKey").takeIf { it.isNotBlank() } ?: return
        val host = mappingDao.findByStableKey("hosts", record.key) ?: return
        val jumpHost = mappingDao.findByStableKey("hosts", jumpKey) ?: return
        database.openHelper.writableDatabase.execSQL(
            "UPDATE hosts SET jump_host_id = ? WHERE id = ?",
            arrayOf(jumpHost.localId, host.localId),
        )
    }

    private suspend fun nextTombstoneId(table: String): Long =
        (mappingDao.findAll(table).minOfOrNull { it.localId.coerceAtMost(0L) } ?: 0L) - 1L

    private fun SyncIdentityMapping.toRecord(payload: String) = SyncRecord(
        table = tableName,
        key = stableKey,
        revision = revision,
        modifiedAt = modifiedAt,
        deleted = deleted,
        payload = payload,
    )

    private fun SyncIdentityMapping.fromRecord(record: SyncRecord, hash: String) = copy(
        stableKey = record.key,
        revision = record.revision,
        modifiedAt = record.modifiedAt,
        deleted = record.deleted,
        payloadHash = hash,
    )

    private fun Cursor.value(column: String, affinity: String): Any? {
        val index = getColumnIndexOrThrow(column)
        if (isNull(index)) return null
        return when (affinity) {
            "INTEGER" -> getLong(index)
            "REAL" -> getDouble(index)
            "BLOB" -> Base64.encodeToString(getBlob(index), Base64.NO_WRAP)
            else -> getString(index)
        }
    }

    private fun jsonMap(json: String): Map<String, Any?> {
        val value = JSONObject(json)
        return value.keys().asSequence().associateWith { value.opt(it) }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(Locale.ROOT, it) }

    private data class Relationship(val localField: String, val remoteField: String, val parentTable: String)

    companion object {
        private val TABLES = listOf("profiles", "hosts", "port_forwards")
        private val RELATIONSHIPS = mapOf(
            "hosts" to listOf(
                Relationship("profileId", "profileKey", "profiles"),
                Relationship("jumpHostId", "jumpHostKey", "hosts"),
            ),
            "port_forwards" to listOf(Relationship("hostId", "hostKey", "hosts")),
        )
        private val INSERT_DEFAULTS = mapOf(
            ("hosts" to "pubkeyId") to -1L,
        )
    }
}
