package com.github.yournamehere

import android.content.Context
import android.media.AudioRecord
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import de.robv.android.xposed.XC_MethodHook
import java.nio.ByteBuffer

@AliucordPlugin
class MicGain : Plugin() {

    override fun start(context: Context) {
        // 50dB gain multiplier (316x volume!)
        val multiplier = 316.22f

        // Hack #1: Short Array
        patcher.patch(AudioRecord::class.java.getDeclaredMethod("read", ShortArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val readSize = param.result as? Int ?: return
                if (readSize > 0) {
                    val audioData = param.args[0] as ShortArray
                    val offset = param.args[1] as Int
                    for (i in offset until offset + readSize) {
                        var amplified = (audioData[i] * multiplier).toInt()
                        if (amplified > 32767) amplified = 32767
                        else if (amplified < -32768) amplified = -32768
                        audioData[i] = amplified.toShort()
                    }
                }
            }
        })

        // Hack #2: Byte Array
        patcher.patch(AudioRecord::class.java.getDeclaredMethod("read", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val readSize = param.result as? Int ?: return
                if (readSize > 0) {
                    val audioData = param.args[0] as ByteArray
                    val offset = param.args[1] as Int
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
            }
        })

        // Hack #3: ByteBuffer
        patcher.patch(AudioRecord::class.java.getDeclaredMethod("read", ByteBuffer::class.java, Int::class.javaPrimitiveType), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val readSize = param.result as? Int ?: return
                if (readSize > 0) {
                    val buffer = param.args[0] as ByteBuffer
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
            }
        })
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}
