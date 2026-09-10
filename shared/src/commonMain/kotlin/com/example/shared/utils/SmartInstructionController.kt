package com.example.shared.utils

class SmartInstructionController {
    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0
    private val MIN_SPEECH_INTERVAL_MS = 5000 // Minimum 5 seconds between updates
    
    fun shouldSpeak(text: String, currentTimeMs: Long): Boolean {
        val now = currentTimeMs
        
        // If exact same text, don't repeat
        if (text == lastSpokenText) {
            return false
        }
        
        // If text is different, check if we should debounce
        // For navigation updates ("In 50 meters..."), we want to avoid spamming every meter
        // But "Turn left now" should be immediate
        
        val isUrgent = text.contains("now", ignoreCase = true) || 
                       text.contains("arrived", ignoreCase = true) ||
                       text.startsWith("Get ready", ignoreCase = true)
        
        if (isUrgent) {
            // Always speak urgent messages if they are new
            lastSpokenText = text
            lastSpokenTime = now
            return true
        }
        
        // For distance updates, only speak if enough time passed
        if (now - lastSpokenTime < MIN_SPEECH_INTERVAL_MS) {
            return false
        }
        
        lastSpokenText = text
        lastSpokenTime = now
        return true
    }
}
