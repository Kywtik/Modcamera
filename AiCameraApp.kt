package com.example.aicamera

import android.app.Application

class AiCameraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // При запуске дообрабатываем фото, которые не успели обработаться
        if (Prefs.autoProcess(this)) {
            PhotoStore.listRaw(this)
                .filter { PhotoStore.isPending(it) }
                .forEach { ProcessingQueue.enqueue(this, it) }
        }
    }
}
