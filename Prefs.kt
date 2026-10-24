package com.example.aicamera

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val DEFAULT_BASE_URL = "https://polza.ai/api/v1"
    const val DEFAULT_MODEL = "google/gemini-nano-banana-2.1"
    const val DEFAULT_PROMPT =
        "Enhance this photo like Samsung Galaxy AI: keep it realistic and natural, " +
        "correct exposure and white balance, recover shadows and highlights, " +
        "sharpen details, reduce noise, make colors vivid but natural. " +
        "Do not change the people, faces, objects or composition."

    fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun apiKey(ctx: Context): String = sp(ctx).getString("api_key", "").orEmpty()
    fun baseUrl(ctx: Context): String = sp(ctx).getString("base_url", DEFAULT_BASE_URL).orEmpty()
    fun model(ctx: Context): String = sp(ctx).getString("model", DEFAULT_MODEL).orEmpty()
    fun prompt(ctx: Context): String = sp(ctx).getString("prompt", DEFAULT_PROMPT).orEmpty()
    fun maxSide(ctx: Context): Int = sp(ctx).getInt("max_side", 1536)
    fun autoProcess(ctx: Context): Boolean = sp(ctx).getBoolean("auto", true)

    fun setAutoProcess(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean("auto", value).apply()
    }
}
