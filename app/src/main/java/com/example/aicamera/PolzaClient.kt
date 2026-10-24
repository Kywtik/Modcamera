package com.example.aicamera

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object PolzaClient {

    /** Отправляет фото в polza.ai и возвращает байты обработанного JPEG/PNG */
    fun enhance(ctx: Context, original: ByteArray): ByteArray {
        val key = Prefs.apiKey(ctx)
        require(key.isNotBlank()) { "Не задан API-ключ (⚙ → API-ключ)" }

        val image = prepareForUpload(original, Prefs.maxSide(ctx))
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(image, Base64.NO_WRAP)

        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", Prefs.prompt(ctx)))
            .put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", dataUrl))
            )

        val body = JSONObject()
            .put("model", Prefs.model(ctx))
            .put("modalities", JSONArray().put("image").put("text"))
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", content))
            )
            .toString()

        val url = URL(Prefs.baseUrl(ctx).trimEnd('/') + "/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 240_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $key")
        }

        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${text.take(200)}")
            }
            return extractImage(text) ?: throw IllegalStateException("В ответе нет изображения")
        } finally {
            conn.disconnect()
        }
    }

    /** Ищет картинку в ответе: data-URL, b64_json или прямую ссылку */
    private fun extractImage(text: String): ByteArray? {
        Regex("data:image/[a-zA-Z]+;base64,([A-Za-z0-9+/=]+)").find(text)?.let {
            return Base64.decode(it.groupValues[1], Base64.DEFAULT)
        }
        Regex("\"b64_json\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"").find(text)?.let {
            return Base64.decode(it.groupValues[1], Base64.DEFAULT)
        }
        Regex("https://[^\"\\s]+\\.(?:png|jpe?g|webp)").find(text)?.let {
            val c = URL(it.value).openConnection() as HttpURLConnection
            return c.inputStream.use { s -> s.readBytes() }
        }
        return null
    }

    /** Уменьшаем фото перед отправкой: быстрее загрузка и обработка */
    private fun prepareForUpload(jpeg: ByteArray, maxSide: Int): ByteArray {
        val bmp = PhotoStore.decodeUpright(jpeg, maxSide) ?: return jpeg
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }
}
