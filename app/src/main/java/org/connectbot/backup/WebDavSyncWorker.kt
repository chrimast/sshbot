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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class WebDavSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val runner = EntryPointAccessors.fromApplication(
            applicationContext,
            WebDavSyncWorkerEntryPoint::class.java,
        ).runner()
        return when (runner.run()) {
            WebDavSyncRunOutcome.SUCCESS -> Result.success()
            WebDavSyncRunOutcome.FAILURE -> Result.failure()
            WebDavSyncRunOutcome.RETRY -> Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WebDavSyncWorkerEntryPoint {
    fun runner(): WebDavSyncRunner
}
