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
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebDavSyncPreferencesTest {
    private lateinit var preferences: WebDavSyncPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("sync-test", Context.MODE_PRIVATE).edit().clear().commit()
        preferences = WebDavSyncPreferences(context.getSharedPreferences("sync-test", Context.MODE_PRIVATE))
    }

    @Test
    fun readsExistingBackupConfigurationAndStableDeviceId() {
        preferences.updateConfiguration(
            WebDavBackupConfig("https://dav.example", "alice", "secret", "ConnectBot/sync.cbbackup", "encrypt"),
        )

        val firstDeviceId = preferences.deviceId()
        val secondDeviceId = preferences.deviceId()

        assertThat(preferences.configuration()).isEqualTo(
            WebDavBackupConfig("https://dav.example", "alice", "secret", "ConnectBot/sync.cbbackup", "encrypt"),
        )
        assertThat(firstDeviceId).isNotBlank()
        assertThat(secondDeviceId).isEqualTo(firstDeviceId)
    }

    @Test
    fun storesAutomaticSyncAndLatestStatus() {
        preferences.setAutomaticSyncEnabled(true)
        preferences.saveStatus(WebDavSyncStatus(WebDavSyncStatusCode.CONFLICT, 123L, 2))

        assertThat(preferences.isAutomaticSyncEnabled()).isTrue()
        assertThat(preferences.status()).isEqualTo(WebDavSyncStatus(WebDavSyncStatusCode.CONFLICT, 123L, 2))
    }
}
