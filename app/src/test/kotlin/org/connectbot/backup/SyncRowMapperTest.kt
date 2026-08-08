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
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncRowMapperTest {
    @Test
    fun hostPayloadReplacesLocalIdsWithStableKeys() {
        val payload = SyncRowMapper.toPortablePayload(
            table = "hosts",
            row = mapOf(
                "id" to 10L,
                "nickname" to "prod",
                "hostname" to "example.com",
                "port" to 22L,
                "profileId" to 3L,
                "pubkeyId" to 4L,
                "jumpHostId" to 5L,
            ),
            stableKeyFor = { table, id -> "$table-key-$id" },
        )
        val json = JSONObject(payload)

        assertThat(json.has("id")).isFalse()
        assertThat(json.has("profileId")).isFalse()
        assertThat(json.getString("profileKey")).isEqualTo("profiles-key-3")
        assertThat(json.getString("pubkeyKey")).isEqualTo("pubkeys-key-4")
        assertThat(json.getString("jumpHostKey")).isEqualTo("hosts-key-5")
    }

    @Test
    fun absentOptionalRelationshipStaysAbsent() {
        val payload = SyncRowMapper.toPortablePayload(
            "hosts",
            mapOf("id" to 10L, "nickname" to "local", "profileId" to null, "pubkeyId" to -1L),
        ) { _, _ -> error("must not resolve absent relationship") }
        val json = JSONObject(payload)

        assertThat(json.has("profileKey")).isFalse()
        assertThat(json.has("pubkeyKey")).isFalse()
    }

    @Test
    fun profilePayloadNeverContainsLocalId() {
        val payload = SyncRowMapper.toPortablePayload(
            "profiles",
            mapOf("id" to 3L, "name" to "Ops", "fontSize" to 14L),
        ) { _, _ -> null }

        val json = JSONObject(payload)
        assertThat(json.has("id")).isFalse()
        assertThat(json.getString("name")).isEqualTo("Ops")
        assertThat(json.getLong("fontSize")).isEqualTo(14L)
    }
}
