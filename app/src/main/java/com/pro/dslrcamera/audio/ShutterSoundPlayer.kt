package com.pro.dslrcamera.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.pro.dslrcamera.R

class ShutterSoundPlayer(context: Context) {
    private val soundPool: SoundPool
    private var shutterSoundId: Int = 0
    private var isLoaded = false

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) isLoaded = true
        }
        shutterSoundId = soundPool.load(context, R.raw.dslr_shutter, 1)
    }

    fun playShutterClick() {
        if (isLoaded && shutterSoundId != 0) {
            soundPool.play(shutterSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
