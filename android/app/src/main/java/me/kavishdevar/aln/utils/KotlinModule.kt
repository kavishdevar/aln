package me.kavishdevar.aln.utils

import android.content.pm.ApplicationInfo
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam

private const val TAG = "AirPodsHook"
private lateinit var module: KotlinModule

class KotlinModule(base: XposedInterface, param: ModuleLoadedParam): XposedModule(base, param) {
    init {
        Log.i(TAG, "AirPodsHook module initialized at :: ${param.processName}")
        module = this
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        Log.i(TAG, "onPackageLoaded :: ${param.packageName}")

        if (param.packageName == "com.android.bluetooth") {
            Log.i(TAG, "Bluetooth app detected, hooking l2c_fcr_chk_chan_modes")

            try {
                if (param.isFirstPackage) {
                    Log.i(TAG, "Loading native library for Bluetooth hook")
                    System.loadLibrary("l2c_fcr_hook")
                    Log.i(TAG, "Native library loaded successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load native library: ${e.message}", e)
            }
        }
    }

    override fun getApplicationInfo(): ApplicationInfo {
        return super.applicationInfo
    }
}
