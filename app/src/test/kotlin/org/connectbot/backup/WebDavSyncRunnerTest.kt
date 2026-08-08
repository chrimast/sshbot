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
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebDavSyncRunnerTest {
    private lateinit var preferences: WebDavSyncPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = context.getSharedPreferences("runner-test", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        preferences = WebDavSyncPreferences(sharedPreferences)
        preferences.updateConfiguration(
            WebDavBackupConfig("https://dav.example", "alice", "secret", "sync.cbbackup", "encrypt"),
        )
    }

    @Test
    fun successStoresStatusAndCompletes() = runTest {
        val runner = WebDavSyncRunner(FakeSyncOperation(WebDavSyncResult.Success), preferences, now = { 123L })

        assertThat(runner.run()).isEqualTo(WebDavSyncRunOutcome.SUCCESS)
        assertThat(preferences.status()).isEqualTo(WebDavSyncStatus(WebDavSyncStatusCode.SUCCESS, 123L, 0))
    }

    @Test
    fun conflictStoresCountWithoutRetrying() = runTest {
        val conflict = SyncConflict("hosts", "host-1", null, null, null)
        val runner = WebDavSyncRunner(
            FakeSyncOperation(WebDavSyncResult.Conflict(listOf(conflict))),
            preferences,
            now = { 456L },
        )

        assertThat(runner.run()).isEqualTo(WebDavSyncRunOutcome.FAILURE)
        assertThat(preferences.status()).isEqualTo(WebDavSyncStatus(WebDavSyncStatusCode.CONFLICT, 456L, 1))
    }

    @Test
    fun transientFailureRequestsRetry() = runTest {
        val runner = WebDavSyncRunner(FakeSyncOperation(WebDavSyncResult.Failed), preferences, now = { 789L })

        assertThat(runner.run()).isEqualTo(WebDavSyncRunOutcome.RETRY)
        assertThat(preferences.status().code).isEqualTo(WebDavSyncStatusCode.FAILED)
    }

    @Test
    fun missingConfigurationDoesNotRetry() = runTest {
        val runner = WebDavSyncRunner(
            FakeSyncOperation(WebDavSyncResult.MissingConfiguration),
            preferences,
            now = { 999L },
        )

        assertThat(runner.run()).isEqualTo(WebDavSyncRunOutcome.FAILURE)
        assertThat(preferences.status().code).isEqualTo(WebDavSyncStatusCode.MISSING_CONFIGURATION)
    }
}

private class FakeSyncOperation(private val result: WebDavSyncResult) : WebDavSyncOperation {
    override suspend fun sync(config: WebDavBackupConfig, deviceId: String, now: Long): WebDavSyncResult = result
}
