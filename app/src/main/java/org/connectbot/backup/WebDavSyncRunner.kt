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

class WebDavSyncRunner(
    private val operation: WebDavSyncOperation,
    private val preferences: WebDavSyncPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(): WebDavSyncRunOutcome {
        val timestamp = now()
        return when (val result = operation.sync(preferences.configuration(), preferences.deviceId(), timestamp)) {
            WebDavSyncResult.Success -> {
                preferences.saveStatus(WebDavSyncStatus(WebDavSyncStatusCode.SUCCESS, timestamp))
                WebDavSyncRunOutcome.SUCCESS
            }
            is WebDavSyncResult.Conflict -> {
                preferences.saveStatus(
                    WebDavSyncStatus(WebDavSyncStatusCode.CONFLICT, timestamp, result.conflicts.size),
                )
                WebDavSyncRunOutcome.FAILURE
            }
            WebDavSyncResult.MissingConfiguration -> {
                preferences.saveStatus(WebDavSyncStatus(WebDavSyncStatusCode.MISSING_CONFIGURATION, timestamp))
                WebDavSyncRunOutcome.FAILURE
            }
            WebDavSyncResult.Failed -> {
                preferences.saveStatus(WebDavSyncStatus(WebDavSyncStatusCode.FAILED, timestamp))
                WebDavSyncRunOutcome.RETRY
            }
        }
    }
}

enum class WebDavSyncRunOutcome {
    SUCCESS,
    FAILURE,
    RETRY,
}
