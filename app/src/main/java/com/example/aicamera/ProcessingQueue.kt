package com.example.aicamera

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Collections

/** Очередь обработки: фото обрабатываются по одному, в фоне */
object ProcessingQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val queued = Collections.synchronizedSet(HashSet<String>())

    /** Сигнал: что-то изменилось (появилось обработанное фото) */
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Текст ошибки обработки */
    val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun enqueue(ctx: Context, raw: File) {
        if (!queued.add(raw.absolutePath)) return
        val app = ctx.applicationContext
        scope.launch {
            mutex.withLock {
                try {
                    val result = PolzaClient.enhance(app, raw.readBytes())
                    val out = PhotoStore.aiFileFor(raw)
                    out.writeBytes(result)
                    PhotoStore.saveToGallery(app, out)
                } catch (e: Exception) {
                    errors.tryEmit(e.message ?: e.javaClass.simpleName)
                } finally {
                    queued.remove(raw.absolutePath)
                    changes.tryEmit(Unit)
                }
            }
        }
    }
}
