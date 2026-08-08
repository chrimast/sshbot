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
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerWebDavSyncSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerWebDavSyncScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerWebDavSyncScheduler(workManager)
    }

    @Test
    fun enabledSchedulesOnePeriodicSync() {
        scheduler.setEnabled(true)
        scheduler.setEnabled(true)

        val work = workManager.getWorkInfosForUniqueWork(WorkManagerWebDavSyncScheduler.PERIODIC_WORK_NAME).get()
        assertThat(work).hasSize(1)
        assertThat(work.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun disabledCancelsPeriodicSync() {
        scheduler.setEnabled(true)
        scheduler.setEnabled(false)

        val work = workManager.getWorkInfosForUniqueWork(WorkManagerWebDavSyncScheduler.PERIODIC_WORK_NAME).get()
        assertThat(work.single().state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    @Test
    fun syncNowEnqueuesUniqueOneTimeWork() {
        scheduler.syncNow()
        scheduler.syncNow()

        val work = workManager.getWorkInfosForUniqueWork(WorkManagerWebDavSyncScheduler.IMMEDIATE_WORK_NAME).get()
        assertThat(work).hasSize(1)
        assertThat(work.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }
}
