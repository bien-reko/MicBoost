package com.aliucord.plugins

import android.content.Context
import android.media.AudioRecord
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.PostHook
import java.nio.ByteBuffer

@AliucordPlugin
class MicGain : Plugin() {

    override fun start(context: Context) {
        // 50dB gain multiplier
        val multiplier = 316.22f

        // Hack #1: Intercept Short Array audio
        patcher.patch(AudioRecord::class.java.getDeclaredMethod("read", ShortArray::class.java, java.lang.Integer.TYPE, java.lang.Integer.TYPE), PostHook { callFrame ->
            val readSize = callFrame.result as Int
            if (readSize > 0) {
                val audioData = callFrame.args[0] as ShortArray
                val offset = callFrame.args[1] as Int
                for (i in offset until offset + readSize) {
                    var amplified = (audioData[i] * multiplier).toInt()
                    if (amplified > 32767) amplified = 32767
                    else if (amplified < -32768) amplified = -32768
                    audioData[i] = amplified.toShort()
                }
            }
        })

        // Hack #2: Intercept Byte Array audio
        patcher.patch(AudioRecord::class.java.getDeclaredMethod("read", ByteArray::class.java, java.lang.Integer.TYPE, java.lang.Integer.TYPE), PostHook { callFrame ->
            val readSize = callFrame.result as Int
            if (readSize > 0) {
                val audioData = callFrame.args[0] as ByteArray
                val offset = callFrame.args[1] as Int
                var i = offset
                while (i < offset + readSize - 1) {
                    var sample = ((audioData[i].toInt() and 0xFF) or (audioData[i + 1].toInt() shl 8)).toShort().toInt()
                    sample = (sample * multiplier).toInt()
                    if (sample > 32767) sample = 32767
                    else if (sample < -32768) sample = -32768
                    audioData[i] = (sample and 0xFF).toByte()
                    audioData[i + 1] = ((sample shr 8) and 0xFF).toByte()
                    i += 2
                }
            }
        })

        // Hack #3: Intercept Discord WebRTC audio streams
        patcher.patch(AudioRecord::class.java.getDeclaredMethod("read", ByteBuffer::class.java, java.lang.Integer.TYPE), PostHook { callFrame ->
            val readSize = callFrame.result as Int
            if (readSize > 0) {
                val buffer = callFrame.args[0] as ByteBuffer
                var i = 0
                while (i < readSize - 1) {
                    var sample = ((buffer.get(i).toInt() and 0xFF) or (buffer.get(i + 1).toInt() shl 8)).toShort().toInt()
                    sample = (sample * multiplier).toInt()
                    if (sample > 32767) sample = 32767
                    else if (sample < -32768) sample = -32768
                    buffer.put(i, (sample and 0xFF).toByte())
                    buffer.put(i + 1, ((sample shr 8) and 0xFF).toByte())
                    i += 2
                }
            }
        })
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}
