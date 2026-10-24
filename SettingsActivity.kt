package com.example.aicamera

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiKey: EditText
    private lateinit var baseUrl: EditText
    private lateinit var model: EditText
    private lateinit var prompt: EditText
    private lateinit var maxSide: EditText
    private lateinit var autoSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(40))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(col)
        }

        col.addView(TextView(this).apply {
            text = "← Настройки"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(0, 0, 0, dp(8))
            setOnClickListener { finish() }
        })

        apiKey = row(col, "API-ключ polza.ai", Prefs.apiKey(this))
        baseUrl = row(col, "Адрес API", Prefs.baseUrl(this))
        model = row(col, "Модель", Prefs.model(this))
        prompt = row(col, "Промпт обработки", Prefs.prompt(this), multi = true)
        maxSide = row(col, "Макс. сторона для отправки (512–4096)", Prefs.maxSide(this).toString(), numeric = true)

        autoSwitch = SwitchCompat(this).apply {
            text = "Обрабатывать автоматически"
            setTextColor(Color.WHITE)
            isChecked = Prefs.autoProcess(this@SettingsActivity)
            setPadding(0, dp(20), 0, dp(10))
        }
        col.addView(autoSwitch)

        col.addView(Button(this).apply {
            text = "Сохранить"
            setOnClickListener { save() }
        })

        setContentView(scroll)
    }

    private fun row(
        col: LinearLayout,
        label: String,
        value: String,
        multi: Boolean = false,
        numeric: Boolean = false
    ): EditText {
        col.addView(TextView(this).apply {
            text = label
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 14f
            setPadding(0, dp(14), 0, dp(4))
        })
        val et = EditText(this).apply {
            setText(value)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF1E1E1E.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = when {
                numeric -> InputType.TYPE_CLASS_NUMBER
                multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (multi) minLines = 4
        }
        col.addView(et)
        return et
    }

    private fun save() {
        Prefs.sp(this).edit()
            .putString("api_key", apiKey.text.toString().trim())
            .putString("base_url", baseUrl.text.toString().trim().ifBlank { Prefs.DEFAULT_BASE_URL })
            .putString("model", model.text.toString().trim().ifBlank { Prefs.DEFAULT_MODEL })
            .putString("prompt", prompt.text.toString().trim().ifBlank { Prefs.DEFAULT_PROMPT })
            .putInt("max_side", maxSide.text.toString().toIntOrNull()?.coerceIn(512, 4096) ?: 1536)
            .putBoolean("auto", autoSwitch.isChecked)
            .apply()
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
