package com.storyteller_f.ping.cubism

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.io.IOException

class WavFileHandler(private val filePath: String) :
    Thread() {
    override fun run() {
        loadWavFile()
    }

    private fun loadWavFile() {
        // 対応していないAPI(API24未満)の場合は音声再生を行わない。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        val mediaExtractor = MediaExtractor()
        try {
            mediaExtractor.setDataSource(filePath)
        } catch (e: IOException) {
            // 例外が発生したらエラーだけだして再生せずreturnする。
            e.printStackTrace()
            return
        }
        val mf = mediaExtractor.getTrackFormat(0)
        val samplingRate = mf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val bufferSize = AudioTrack.getMinBufferSize(
            samplingRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(samplingRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        audioTrack.play()

        // ぶつぶつ音を回避
        val offset = 100
        val voiceBuffer = readFile(filePath)
        audioTrack.write(voiceBuffer, offset, voiceBuffer.size - offset)
    }
}
