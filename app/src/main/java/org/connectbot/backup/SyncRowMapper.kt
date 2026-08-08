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

import org.json.JSONObject

object SyncRowMapper {
    private data class Relationship(val localField: String, val remoteField: String, val parentTable: String)

    private val relationships = mapOf(
        "hosts" to listOf(
            Relationship("profileId", "profileKey", "profiles"),
            Relationship("pubkeyId", "pubkeyKey", "pubkeys"),
            Relationship("jumpHostId", "jumpHostKey", "hosts"),
        ),
        "port_forwards" to listOf(Relationship("hostId", "hostKey", "hosts")),
        "known_hosts" to listOf(Relationship("hostId", "hostKey", "hosts")),
        "color_palette" to listOf(Relationship("schemeId", "schemeKey", "color_schemes")),
    )

    fun toPortablePayload(
        table: String,
        row: Map<String, Any?>,
        stableKeyFor: (String, Long) -> String?,
    ): String {
        val portable = row.toMutableMap()
        portable.remove("id")
        for (relationship in relationships[table].orEmpty()) {
            val localId = (portable.remove(relationship.localField) as? Number)?.toLong()
            if (localId != null && localId > 0) {
                val stableKey = requireNotNull(stableKeyFor(relationship.parentTable, localId)) {
                    "Missing stable identity for ${relationship.parentTable}:$localId"
                }
                portable[relationship.remoteField] = stableKey
            }
        }
        return deterministicJson(portable)
    }

    private fun deterministicJson(values: Map<String, Any?>): String {
        val json = JSONObject()
        values.toSortedMap().forEach { (key, value) ->
            if (value != null) json.put(key, value)
        }
        return json.toString()
    }
}
