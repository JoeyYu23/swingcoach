package com.example.multiverse_hackathon

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.multiverse_hackathon.util.AssetCopier
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Smoke tests to verify the device/emulator is properly configured
 * for running SwingCoach. These tests should pass before attempting
 * real workflow tests.
 */
class DeviceReadinessTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    // --- Permission & Feature Tests ---

    @Test
    fun device_hasCameraFeature() {
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        assertTrue("Device should have a camera (or emulated camera)", hasCamera)
    }

    @Test
    fun device_hasInternetConnectivity() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        assertNotNull("Device should have an active network connection", network)

        val capabilities = cm.getNetworkCapabilities(network)
        assertNotNull("Network capabilities should not be null", capabilities)
        assertTrue(
            "Device should have internet capability",
            capabilities!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        )
    }

    @Test
    fun device_hasTtsEngine() {
        val latch = CountDownLatch(1)
        var ttsStatus = -1

        val tts = TextToSpeech(context) { status ->
            ttsStatus = status
            latch.countDown()
        }

        val initialized = latch.await(5, TimeUnit.SECONDS)
        tts.shutdown()

        assertTrue("TTS should initialize within 5 seconds", initialized)
        assertEquals(
            "TTS should initialize successfully (status=SUCCESS)",
            TextToSpeech.SUCCESS,
            ttsStatus
        )
    }

    // --- Camera Provider Tests ---

    @Test
    fun cameraProvider_isAvailable() {
        val latch = CountDownLatch(1)
        var provider: ProcessCameraProvider? = null
        var error: Exception? = null

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                provider = future.get()
                latch.countDown()
            } catch (e: Exception) {
                error = e
                latch.countDown()
            }
        }, { it.run() })

        val completed = latch.await(10, TimeUnit.SECONDS)
        assertTrue("CameraProvider should be available within 10s", completed)
        assertNull("CameraProvider should not throw error: ${error?.message}", error)
        assertNotNull("CameraProvider should not be null", provider)
    }

    @Test
    fun cameraProvider_hasBackCamera() {
        val latch = CountDownLatch(1)
        var hasBackCamera = false

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = future.get()
            hasBackCamera = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            latch.countDown()
        }, { it.run() })

        latch.await(10, TimeUnit.SECONDS)
        assertTrue("Device should have a back camera (or emulated)", hasBackCamera)
    }

    // --- Asset Tests ---

    @Test
    fun sampleVideos_existInAssets() {
        val assets = context.assets.list("") ?: emptyArray()
        assertTrue("sample1.mp4 should exist in assets", "sample1.mp4" in assets)
        assertTrue("sample2.mp4 should exist in assets", "sample2.mp4" in assets)
    }

    @Test
    fun sampleVideo_canBeCopiedToCache() {
        val uri = AssetCopier.copyAssetToCache(context, "sample1.mp4")
        assertNotNull("Copied URI should not be null", uri)

        val path = uri.path
        assertNotNull("URI path should not be null", path)
        val file = File(path!!)
        assertTrue("Copied file should exist", file.exists())
        assertTrue("Copied file should have content (size > 0)", file.length() > 0)
    }

    @Test
    fun sampleVideo_bytesAreReadable() {
        val bytes = context.assets.open("sample1.mp4").use { it.readBytes() }
        assertTrue("Sample video should have content", bytes.isNotEmpty())
        assertTrue("Sample video should be at least 1KB", bytes.size >= 1024)
    }

    // --- Build Config ---

    @Test
    fun buildConfig_geminiApiKey_isConfigured() {
        val key = BuildConfig.GEMINI_API_KEY
        assertTrue("GEMINI_API_KEY should be set in BuildConfig", key.isNotBlank())
    }

    @Test
    fun appPackageName_isCorrect() {
        assertEquals(
            "com.example.multiverse_hackathon",
            context.packageName
        )
    }
}
