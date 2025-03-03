package me.kavishdevar.aln.utils

import java.io.BufferedReader
import java.io.InputStreamReader

object RootChecker {

    fun hasRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = process.outputStream
            val inputStream = process.inputStream
            outputStream.write("id\n".toByteArray())
            outputStream.flush()
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            outputStream.close()
            val reader = BufferedReader(InputStreamReader(inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().contains("uid=0") // Root user has UID 0
        } catch (e: Exception) {
            false
        }
    }
}
