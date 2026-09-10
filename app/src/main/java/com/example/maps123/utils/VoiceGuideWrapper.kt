package com.example.maps123.utils

import android.speech.tts.TextToSpeech

class VoiceGuideWrapper(private val tts: TextToSpeech?) {
    // Stub implementation
    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun shutdown() {
        tts?.shutdown()
    }
}