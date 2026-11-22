package com.pixelkeys

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

/** Передача распознанного текста от прозрачного экрана к клавиатуре (оба живут в одном процессе). */
object VoiceBridge {
    var lang = "ru-RU"
    private var text: String? = null
    private var at = 0L

    fun put(t: String) {
        text = t
        at = System.currentTimeMillis()
    }

    /** Забирает текст один раз; если прошло больше 30 секунд, текст считается устаревшим. */
    fun take(): String? {
        val t = text
        text = null
        if (t == null || System.currentTimeMillis() - at > 30000L) return null
        return t
    }
}

/**
 * Прозрачный экран: открывает системный голосовой ввод (Google), получает текст и закрывается.
 * Разрешение на микрофон не нужно: запись ведёт приложение распознавания.
 */
class VoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceBridge.lang)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            startActivityForResult(i, 1)
        } catch (e: Exception) {
            Toast.makeText(this, "Нет приложения для распознавания речи", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val list = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val t = list?.firstOrNull()
            if (!t.isNullOrBlank()) VoiceBridge.put(t.trim())
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
