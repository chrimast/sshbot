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

package org.connectbot.di

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.connectbot.backup.BackupCrypto
import org.connectbot.backup.BackupCryptoEngine
import org.connectbot.backup.ConnectBotBackupExporter
import org.connectbot.backup.OkHttpWebDavBackupTransport
import org.connectbot.backup.RoomSyncBaselineStore
import org.connectbot.backup.RoomSyncSnapshotStore
import org.connectbot.backup.SchemaBackupExporter
import org.connectbot.backup.WebDavBackupRepository
import org.connectbot.backup.WebDavBackupTransport
import org.connectbot.backup.WebDavSyncCoordinator
import org.connectbot.backup.WebDavSyncOperation
import org.connectbot.backup.WebDavSyncPreferences
import org.connectbot.backup.WebDavSyncRunner
import org.connectbot.backup.WebDavSyncScheduler
import org.connectbot.backup.WorkManagerWebDavSyncScheduler
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.DatabaseSchema
import org.connectbot.data.SchemaBasedExporter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    fun provideBackupCryptoEngine(): BackupCryptoEngine = BackupCrypto()

    @Provides
    fun provideWebDavBackupTransport(
        httpClient: OkHttpClient,
        dispatchers: CoroutineDispatchers,
    ): WebDavBackupTransport = OkHttpWebDavBackupTransport(httpClient, dispatchers.io)

    @Provides
    fun provideConnectBotBackupExporter(
        @ApplicationContext context: Context,
        database: ConnectBotDatabase,
    ): ConnectBotBackupExporter = SchemaBackupExporter(
        SchemaBasedExporter(database, DatabaseSchema.load(context)),
    )

    @Provides
    fun provideWebDavBackupRepository(
        exporter: ConnectBotBackupExporter,
        crypto: BackupCryptoEngine,
        transport: WebDavBackupTransport,
    ): WebDavBackupRepository = WebDavBackupRepository(exporter, crypto, transport)

    @Provides
    @Singleton
    fun provideWebDavSyncPreferences(preferences: SharedPreferences): WebDavSyncPreferences =
        WebDavSyncPreferences(preferences)

    @Provides
    @Singleton
    fun provideWebDavSyncOperation(
        @ApplicationContext context: Context,
        database: ConnectBotDatabase,
        transport: WebDavBackupTransport,
        crypto: BackupCryptoEngine,
    ): WebDavSyncOperation = WebDavSyncCoordinator(
        RoomSyncSnapshotStore(database, DatabaseSchema.load(context)),
        RoomSyncBaselineStore(database.syncBaselineDao()),
        transport,
        crypto,
    )

    @Provides
    @Singleton
    fun provideWebDavSyncRunner(
        operation: WebDavSyncOperation,
        preferences: WebDavSyncPreferences,
    ): WebDavSyncRunner = WebDavSyncRunner(operation, preferences)

    @Provides
    @Singleton
    fun provideWebDavSyncScheduler(@ApplicationContext context: Context): WebDavSyncScheduler =
        WorkManagerWebDavSyncScheduler { WorkManager.getInstance(context) }
}
