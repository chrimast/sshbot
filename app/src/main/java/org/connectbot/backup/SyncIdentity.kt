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

import java.security.MessageDigest
import java.util.Locale

/**
 * Creates a deterministic first-generation identity for legacy Room rows.
 *
 * These identities are only used to bootstrap the explicit identity map. Once
 * a row has an assigned sync key, that key must be retained even if the user
 * changes a mutable field such as a nickname.
 */
object SyncIdentity {
    fun forRow(table: String, fields: Map<String, Any?>): String {
        val identityFields = identityFields(table)
        val values = identityFields.map { field ->
            val value = fields[field]
                ?: throw IllegalArgumentException("Missing identity field $field for $table")
            "$field=${normalize(value)}"
        }
        val canonical = "v1|$table|${values.joinToString("|")}"
        return "v1-${sha256(canonical)}"
    }

    private fun identityFields(table: String): List<String> = when (table) {
        "profiles" -> listOf("name")
        "hosts" -> listOf("nickname", "hostname", "port")
        "pubkeys" -> listOf("publicKey")
        "port_forwards" -> listOf("hostKey", "nickname", "type", "sourcePort")
        "known_hosts" -> listOf("hostname", "port", "hostKeyAlgo", "hostKey")
        "color_schemes" -> listOf("name")
        "color_palette" -> listOf("schemeKey", "colorIndex")
        else -> throw IllegalArgumentException("Unsupported sync table $table")
    }

    private fun normalize(value: Any): String = when (value) {
        is ByteArray -> value.joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
        is String -> value.trim().replace("\\", "\\\\").replace("|", "\\|")
        else -> value.toString()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
}
