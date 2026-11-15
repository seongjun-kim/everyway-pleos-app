package app.carwell.drive.data

import android.content.Context
import android.content.Intent
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener

/** 주차 후 한 줄을 읽어 주는 음성 출력. 주행 중에는 호출하지 않는다(호출 쪽에서 P 확인). */
interface Speaker {
    val ready: Boolean
    fun speak(text: String)
    fun release()
}

/** Pleos TextToSpeech SDK (온디바이스 모드: 네트워크·앱 등록 불필요). */
class PleosSpeaker(context: Context) : Speaker {
    private var tts: TextToSpeech? = null
    @Volatile override var ready = false; private set

    init {
        runCatching {
            val t = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() { ready = true }
                override fun onFailure(errorType: ErrorType) { Log.w("PleosSpeaker", "init failed: $errorType") }
            })
        }.onFailure { Log.w("PleosSpeaker", "TTS unavailable", it) }
    }

    override fun speak(text: String) {
        if (ready) runCatching { tts?.speak(text, true) }
    }

    override fun release() { runCatching { tts?.release() }; tts = null; ready = false }

    companion object {
        fun isAvailable(context: Context): Boolean = runCatching {
            context.packageManager.queryIntentServices(Intent("ai.pleos.playground.caas.intent.action.SPEECH_START"), 0).isNotEmpty()
        }.getOrDefault(false)
    }
}
