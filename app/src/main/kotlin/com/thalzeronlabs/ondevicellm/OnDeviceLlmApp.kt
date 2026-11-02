package com.thalzeronlabs.ondevicellm

import android.app.Application
import android.content.Context
import com.thalzeronlabs.ondevicellm.battery.batteryStatusFlow
import com.thalzeronlabs.ondevicellm.data.FileModelStore
import com.thalzeronlabs.ondevicellm.data.LlamaChatEngine
import com.thalzeronlabs.ondevicellm.data.ModelManager
import com.thalzeronlabs.ondevicellm.data.RoomConversationRepository
import com.thalzeronlabs.ondevicellm.data.db.ChatDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OnDeviceLlmApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Manual dependency injection: one of each, for the whole app. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val engine = LlamaChatEngine()
    val conversations = RoomConversationRepository(ChatDatabase.create(context).chatDao())
    val models = ModelManager(FileModelStore(context.filesDir), engine, appScope)
    val battery = batteryStatusFlow(context)
}
