package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceCaptureState(
    val isListening: Boolean = false,
    val amplitude: Float = 0f,
    val partialTranscript: String = "",
    val isAudioClipMode: Boolean = false,
    val errorMessage: String? = null
)

class VoiceInputController(
    private val context: Context,
    private val onTranscriptReady: (String) -> Unit,
    private val onAudioWavReady: (ByteArray) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(VoiceCaptureState())
    val state: StateFlow<VoiceCaptureState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile
    private var isRecordingWav = false
    private var audioRecordThread: Thread? = null
    private var pcmBufferStream: ByteArrayOutputStream? = null

    fun startListening() {
        stopSilently()
        _state.value = VoiceCaptureState(
            isListening = true,
            amplitude = 0.14f,
            partialTranscript = "",
            errorMessage = null
        )

        val canUseSpeechRecognizer = runCatching {
            SpeechRecognizer.isRecognitionAvailable(context)
        }.getOrDefault(false)

        if (canUseSpeechRecognizer) {
            startSpeechRecognizer()
        } else {
            startDirectPcmCapture()
        }
    }

    fun finishListeningAndSubmit() {
        if (isRecordingWav) {
            isRecordingWav = false
            return
        }
        mainHandler.post {
            val sr = speechRecognizer
            if (sr != null) {
                runCatching { sr.stopListening() }
            } else {
                _state.value = VoiceCaptureState()
            }
        }
    }

    fun cancelListening() {
        val wasWav = isRecordingWav
        isRecordingWav = false
        if (wasWav) {
            runCatching { audioRecordThread?.interrupt() }
            pcmBufferStream = null
        }
        mainHandler.post {
            runCatching { speechRecognizer?.cancel() }
            releaseSpeechRecognizer()
            _state.value = VoiceCaptureState()
        }
    }

    fun release() {
        stopSilently()
    }

    private fun stopSilently() {
        isRecordingWav = false
        runCatching { audioRecordThread?.interrupt() }
        audioRecordThread = null
        pcmBufferStream = null
        mainHandler.post {
            releaseSpeechRecognizer()
        }
    }

    private fun startSpeechRecognizer() {
        mainHandler.post {
            try {
                releaseSpeechRecognizer()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = _state.value.copy(
                            isListening = true,
                            isAudioClipMode = false,
                            errorMessage = null
                        )
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
                        _state.value = _state.value.copy(amplitude = norm)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _state.value = _state.value.copy(amplitude = 0.1f)
                    }

                    override fun onError(error: Int) {
                        releaseSpeechRecognizer()
                        if (error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                            error == SpeechRecognizer.ERROR_NETWORK ||
                            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_SERVER ||
                            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
                        ) {
                            // Fallback seamlessly to real PCM mic recording -> Gemini Multimodal Audio
                            startDirectPcmCapture()
                        } else {
                            val partial = _state.value.partialTranscript.trim()
                            _state.value = VoiceCaptureState()
                            if (partial.isNotBlank()) {
                                onTranscriptReady(partial)
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        val fallbackPartial = _state.value.partialTranscript.trim()
                        releaseSpeechRecognizer()
                        _state.value = VoiceCaptureState()
                        val finalText = text.ifBlank { fallbackPartial }
                        if (finalText.isNotBlank()) {
                            onTranscriptReady(finalText)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            _state.value = _state.value.copy(partialTranscript = text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                recognizer.startListening(intent)
            } catch (_: Exception) {
                startDirectPcmCapture()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDirectPcmCapture() {
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: SecurityException) {
            _state.value = VoiceCaptureState(errorMessage = "Mikrofon-Berechtigung fehlt.")
            return
        } catch (e: Exception) {
            _state.value = VoiceCaptureState(errorMessage = "Mikrofon nicht verfügbar.")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder.release() }
            _state.value = VoiceCaptureState(errorMessage = "Audio-Aufnahme konnte nicht gestartet werden.")
            return
        }

        val outStream = ByteArrayOutputStream()
        pcmBufferStream = outStream
        isRecordingWav = true
        _state.value = VoiceCaptureState(
            isListening = true,
            amplitude = 0.15f,
            partialTranscript = "",
            isAudioClipMode = true
        )

        audioRecordThread = thread(start = true, name = "DotEqVoiceCapture") {
            val shortBuf = ShortArray(1024)
            val byteBuf = ByteArray(shortBuf.size * 2)
            val maxBytes = sampleRate * 2 * 15 // Max 15 seconds

            try {
                recorder.startRecording()
                while (isRecordingWav && outStream.size() < maxBytes) {
                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = shortBuf[i]
                            sumSq += (s * s).toDouble()
                            byteBuf[i * 2] = (s.toInt() and 0xFF).toByte()
                            byteBuf[i * 2 + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
                        }
                        outStream.write(byteBuf, 0, read * 2)
                        val rms = sqrt(sumSq / read).toFloat() / 6500f
                        _state.value = _state.value.copy(
                            amplitude = rms.coerceIn(0.08f, 1f)
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                val rawPcm = outStream.toByteArray()
                val shouldSubmit = rawPcm.size > 1600 && pcmBufferStream === outStream
                pcmBufferStream = null
                mainHandler.post {
                    _state.value = VoiceCaptureState()
                    if (shouldSubmit) {
                        onAudioWavReady(wrapPcmAsWav(rawPcm, sampleRate))
                    }
                }
            }
        }
    }

    private fun releaseSpeechRecognizer() {
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
    }

    private fun wrapPcmAsWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val totalAudioLen = pcm.size
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(totalAudioLen)
        return header.array() + pcm
    }
}
