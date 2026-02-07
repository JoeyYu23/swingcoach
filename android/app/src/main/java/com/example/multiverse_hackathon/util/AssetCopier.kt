package com.example.multiverse_hackathon.util

import android.content.Context
import android.net.Uri
import java.io.File

object AssetCopier {
    fun copyAssetToCache(context: Context, assetName: String): Uri {
        val file = File(context.cacheDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return Uri.fromFile(file)
    }
}
