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

package org.connectbot.ui.screens.settings

import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.connectbot.backup.BackupCryptoEngine
import org.connectbot.backup.ConnectBotBackupExporter
import org.connectbot.backup.WebDavBackupConfig
import org.connectbot.backup.WebDavBackupRepository
import org.connectbot.backup.WebDavBackupTransport
import org.connectbot.backup.WebDavSyncScheduler
import org.connectbot.data.ProfileRepository
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.di.FakeLanguagePackManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelWebDavBackupTest {
    private val testDispatcher = StandardTestDispatcher()
    private val transport = FakeTransport()
    private val scheduler = FakeSyncScheduler()
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() = runTest {
        prefs = mock()
        prefsEditor = mock()
        val profileRepository = mock<ProfileRepository>()
        whenever(profileRepository.getAll()).thenReturn(emptyList())
        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putString(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putFloat(any(), any())).thenReturn(prefsEditor)
        whenever(prefs.getBoolean(any(), any())).thenAnswer { it.arguments[1] as Boolean }
        whenever(prefs.getString(any(), any())).thenAnswer { it.arguments[1] as String? }
        whenever(prefs.getFloat(any(), any())).thenAnswer { it.arguments[1] as Float }
        whenever(prefs.getLong(any(), any())).thenAnswer { it.arguments[1] as Long }

        viewModel = SettingsViewModel(
            prefs = prefs,
            profileRepository = profileRepository,
            context = RuntimeEnvironment.getApplication(),
            dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher),
            languagePackManager = FakeLanguagePackManager(),
            webDavBackupRepository = WebDavBackupRepository(FakeExporter(), FakeCrypto(), transport),
            webDavSyncScheduler = scheduler,
        )
        advanceUntilIdle()
    }

    @Test
    fun runWebDavBackupUsesCurrentSettings() = runTest(testDispatcher) {
        viewModel.updateWebDavUrl("https://dav.example.com/files/alice/")
        viewModel.updateWebDavUsername("alice")
        viewModel.updateWebDavPassword("dav-password")
        viewModel.updateWebDavRemotePath("ConnectBot/latest.cbbackup")
        viewModel.updateWebDavEncryptionPassword("backup-password")
        advanceUntilIdle()

        viewModel.runWebDavBackup()
        advanceUntilIdle()

        assertEquals("ConnectBot/latest.cbbackup", transport.uploadPath)
        assertEquals("encrypted", transport.uploadBytes?.toString(Charsets.UTF_8))
        assertEquals("WebDAV backup operation completed", viewModel.uiState.value.webDavStatusMessage)
    }

    @Test
    fun automaticSyncTogglePersistsAndSchedules() = runTest(testDispatcher) {
        viewModel.updateWebDavAutomaticSync(true)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.webDavAutomaticSync)
        assertEquals(true, scheduler.enabled)
    }

    @Test
    fun syncNowEnqueuesImmediateSync() = runTest(testDispatcher) {
        viewModel.runWebDavSync()

        assertEquals(1, scheduler.immediateRuns)
        assertEquals("WebDAV sync queued", viewModel.uiState.value.webDavStatusMessage)
    }

    private class FakeExporter : ConnectBotBackupExporter {
        override fun exportJson(): String = "{}"

        override fun importJson(json: String) = Unit
    }

    private class FakeCrypto : BackupCryptoEngine {
        override fun encrypt(plaintext: ByteArray, password: String): String = "encrypted"

        override fun decrypt(envelope: String, password: String): ByteArray = ByteArray(0)
    }

    private class FakeTransport : WebDavBackupTransport {
        var uploadPath: String? = null
        var uploadBytes: ByteArray? = null

        override suspend fun upload(config: WebDavBackupConfig, path: String, bytes: ByteArray) {
            uploadPath = path
            uploadBytes = bytes
        }

        override suspend fun download(config: WebDavBackupConfig, path: String): ByteArray = ByteArray(0)
    }

    private class FakeSyncScheduler : WebDavSyncScheduler {
        var enabled: Boolean? = null
        var immediateRuns = 0

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        override fun syncNow() {
            immediateRuns++
        }
    }
}
