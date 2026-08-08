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

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

class WebDavSyncPreferences(private val preferences: SharedPreferences) {
    fun configuration(): WebDavBackupConfig = WebDavBackupConfig(
        baseUrl = preferences.getString(WEB_DAV_URL, "").orEmpty(),
        username = preferences.getString(WEB_DAV_USERNAME, "").orEmpty(),
        password = preferences.getString(WEB_DAV_PASSWORD, "").orEmpty(),
        remotePath = preferences.getString(WEB_DAV_REMOTE_PATH, DEFAULT_REMOTE_PATH) ?: DEFAULT_REMOTE_PATH,
        encryptionPassword = preferences.getString(WEB_DAV_ENCRYPTION_PASSWORD, "").orEmpty(),
    )

    fun updateConfiguration(config: WebDavBackupConfig) {
        preferences.edit {
            putString(WEB_DAV_URL, config.baseUrl)
            putString(WEB_DAV_USERNAME, config.username)
            putString(WEB_DAV_PASSWORD, config.password)
            putString(WEB_DAV_REMOTE_PATH, config.remotePath)
            putString(WEB_DAV_ENCRYPTION_PASSWORD, config.encryptionPassword)
        }
    }

    fun deviceId(): String {
        preferences.getString(SYNC_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        return UUID.randomUUID().toString().also { id -> preferences.edit { putString(SYNC_DEVICE_ID, id) } }
    }

    fun isAutomaticSyncEnabled(): Boolean = preferences.getBoolean(AUTOMATIC_SYNC_ENABLED, false)

    fun setAutomaticSyncEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(AUTOMATIC_SYNC_ENABLED, enabled) }
    }

    fun status(): WebDavSyncStatus {
        val code = runCatching {
            WebDavSyncStatusCode.valueOf(preferences.getString(SYNC_STATUS, WebDavSyncStatusCode.NEVER.name).orEmpty())
        }.getOrDefault(WebDavSyncStatusCode.NEVER)
        return WebDavSyncStatus(
            code = code,
            updatedAt = preferences.getLong(SYNC_STATUS_TIME, 0L),
            conflictCount = preferences.getInt(SYNC_CONFLICT_COUNT, 0),
        )
    }

    fun saveStatus(status: WebDavSyncStatus) {
        preferences.edit {
            putString(SYNC_STATUS, status.code.name)
            putLong(SYNC_STATUS_TIME, status.updatedAt)
            putInt(SYNC_CONFLICT_COUNT, status.conflictCount)
        }
    }

    companion object {
        const val WEB_DAV_URL = "webdav_backup_url"
        const val WEB_DAV_USERNAME = "webdav_backup_username"
        const val WEB_DAV_PASSWORD = "webdav_backup_password"
        const val WEB_DAV_REMOTE_PATH = "webdav_backup_remote_path"
        const val WEB_DAV_ENCRYPTION_PASSWORD = "webdav_backup_encryption_password"
        const val AUTOMATIC_SYNC_ENABLED = "webdav_sync_enabled"
        const val DEFAULT_REMOTE_PATH = "ConnectBot/latest.cbbackup"

        private const val SYNC_DEVICE_ID = "webdav_sync_device_id"
        private const val SYNC_STATUS = "webdav_sync_status"
        private const val SYNC_STATUS_TIME = "webdav_sync_status_time"
        private const val SYNC_CONFLICT_COUNT = "webdav_sync_conflict_count"
    }
}

data class WebDavSyncStatus(
    val code: WebDavSyncStatusCode = WebDavSyncStatusCode.NEVER,
    val updatedAt: Long = 0L,
    val conflictCount: Int = 0,
)

enum class WebDavSyncStatusCode {
    NEVER,
    SUCCESS,
    CONFLICT,
    MISSING_CONFIGURATION,
    FAILED,
}
