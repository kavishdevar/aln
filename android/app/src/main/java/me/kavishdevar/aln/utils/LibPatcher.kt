package me.kavishdevar.aln.utils

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

object LibPatcher {
    fun deployModule(context: Context): Boolean {
        if (!RootChecker.hasRootAccess()) {
            Log.e("LibPatcher", "Root access not granted.")
            return false
        }
        try {
            val moduleName = "btl2capfix"
            val destinationPath = "/data/adb/modules/$moduleName"
            val tempPath = File(context.cacheDir, moduleName)
            // Delete existing module (if any)
            executeRootCommand("rm -rf $destinationPath")
            executeRootCommand("mkdir -p $destinationPath")
            // Extract entire folder from assets to a temporary location
            copyAssetFolder(context, moduleName, tempPath)
// Move the extracted folder to the destination with root
            executeRootCommand("cp -r ${tempPath.absolutePath}/* $destinationPath/")
// Set proper permissions
            executeRootCommand("chmod -R 755 $destinationPath")
// Cleanup temp files
            tempPath.deleteRecursively()
            Log.d("LibPatcher", "Module deployed successfully.")
            return true
        } catch (e: IOException) {
            Log.e("LibPatcher", "Error deploying module: ${e.message}")
            return false
        }
    }
    private fun copyAssetFolder(context: Context, assetFolder: String, destination: File): Boolean {
        val assetManager = context.assets
        try {
            val files = assetManager.list(assetFolder) ?: return false
            if (!destination.exists()) destination.mkdirs()
            for (file in files) {
                val assetPath = "$assetFolder/$file"
                val outputFile = File(destination, file)
                if (assetManager.list(assetPath)?.isNotEmpty() == true) {
                    // If it's a directory, recursively copy
                    copyAssetFolder(context, assetPath, outputFile)
                } else {
                    // Copy files
                    assetManager.open(assetPath).use { inputStream ->
                        FileOutputStream(outputFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
            return true
        } catch (e: IOException) {
            Log.e("LibPatcher", "Error copying asset folder: ${e.message}")
            return false
        }
    }
    private fun executeRootCommand(command: String): String? {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            Log.e("LibPatcher", "Error executing command: ${e.message}")
            null
        }
    }
}
