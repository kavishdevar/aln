package me.kavishdevar.aln.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.kavishdevar.aln.services.AirPodsService

class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> try { context?.startForegroundService(
                Intent(
                    context,
                    AirPodsService::class.java
                )
            ) } catch (e: Exception) { e.printStackTrace() }
            Intent.ACTION_BOOT_COMPLETED -> try { context?.startForegroundService(
                Intent(
                    context,
                    AirPodsService::class.java
                )
            ) } catch (e: Exception) { e.printStackTrace() }
        }
    }
}