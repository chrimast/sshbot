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
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncIdentityTest {
    @Test
    fun hostIdentityUsesStableBusinessFields() {
        val first = SyncIdentity.forRow(
            "hosts",
            mapOf("nickname" to "prod", "hostname" to "10.0.0.1", "port" to 22),
        )
        val second = SyncIdentity.forRow(
            "hosts",
            mapOf("nickname" to "prod", "hostname" to "10.0.0.1", "port" to 22),
        )

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun changingNonIdentityFieldsDoesNotChangeIdentity() {
        val first = SyncIdentity.forRow(
            "profiles",
            mapOf("name" to "Ops", "fontSize" to 10, "encoding" to "UTF-8"),
        )
        val second = SyncIdentity.forRow(
            "profiles",
            mapOf("name" to "Ops", "fontSize" to 18, "encoding" to "ISO-8859-1"),
        )

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun childIdentityIncludesStableParentIdentity() {
        val first = SyncIdentity.forRow(
            "port_forwards",
            mapOf("hostKey" to "hosts:host-1", "nickname" to "web", "type" to "local", "sourcePort" to 8080),
        )
        val second = SyncIdentity.forRow(
            "port_forwards",
            mapOf("hostKey" to "hosts:host-1", "nickname" to "web", "type" to "local", "sourcePort" to 8080),
        )

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun identityRejectsRowsWithoutRequiredFields() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncIdentity.forRow("hosts", mapOf("nickname" to "prod"))
        }
    }
}
