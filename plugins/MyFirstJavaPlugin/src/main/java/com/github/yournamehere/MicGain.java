package com.aliucord.plugins;

import android.content.Context;
import android.media.AudioRecord;

import com.aliucord.annotation.AliucordPlugin;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.PostHook;

import java.nio.ByteBuffer;

@AliucordPlugin
public class MicGain extends Plugin {
    
    @Override
    public void start(Context context) throws Throwable {
        // 50dB gain multiplier (Increases volume by 316 times!)
        final float multiplier = 316.22f;

        // Hack #1: Intercept standard audio waves
        patcher.patch(AudioRecord.class.getDeclaredMethod("read", short[].class, int.class, int.class), new PostHook(callFrame -> {
            int readSize = (int) callFrame.getResult();
            if (readSize > 0) {
                short[] audioData = (short[]) callFrame.args[0];
                int offset = (int) callFrame.args[1];
                for (int i = offset; i < offset + readSize; i++) {
                    int amplified = (int) (audioData[i] * multiplier);
                    if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE;
                    else if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE;
                    audioData[i] = (short) amplified;
                }
            }
        }));

        // Hack #2: Intercept byte-level audio waves
        patcher.patch(AudioRecord.class.getDeclaredMethod("read", byte[].class, int.class, int.class), new PostHook(callFrame -> {
            int readSize = (int) callFrame.getResult();
            if (readSize > 0) {
                byte[] audioData = (byte[]) callFrame.args[0];
                int offset = (int) callFrame.args[1];
                for (int i = offset; i < offset + readSize - 1; i += 2) {
                    int sample = (audioData[i] & 0xFF) | (audioData[i + 1] << 8);
                    sample = (int) (sample * multiplier);
                    if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
                    else if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
                    audioData[i] = (byte) (sample & 0xFF);
                    audioData[i + 1] = (byte) ((sample >> 8) & 0xFF);
                }
            }
        }));

        // Hack #3: Intercept Discord WebRTC audio streams
        patcher.patch(AudioRecord.class.getDeclaredMethod("read", ByteBuffer.class, int.class), new PostHook(callFrame -> {
            int readSize = (int) callFrame.getResult();
            if (readSize > 0) {
                ByteBuffer buffer = (ByteBuffer) callFrame.args[0];
                for (int i = 0; i < readSize - 1; i += 2) {
                    int sample = (buffer.get(i) & 0xFF) | (buffer.get(i + 1) << 8);
                    sample = (int) (sample * multiplier);
                    if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
                    else if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
                    buffer.put(i, (byte) (sample & 0xFF));
                    buffer.put(i + 1, (byte) ((sample >> 8) & 0xFF));
                }
            }
        }));
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
    }
}
