package com.example.multiverse_hackathon.analysis

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream

object FrameExtractor {

    fun extractFrames(context: Context, videoUri: Uri, count: Int = 8): List<ByteArray> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return emptyList()

            val interval = durationMs / (count + 1)
            (1..count).mapNotNull { i ->
                val timeUs = interval * i * 1000 // ms to us
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.toJpegBytes()
            }
        } finally {
            retriever.release()
        }
    }

    private fun Bitmap.toJpegBytes(quality: Int = 80): ByteArray {
        return ByteArrayOutputStream().also {
            compress(Bitmap.CompressFormat.JPEG, quality, it)
        }.toByteArray()
    }
}
