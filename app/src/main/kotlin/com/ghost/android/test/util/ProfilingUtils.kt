package com.ghost.android.test.util

import android.annotation.SuppressLint

/**
 * Utility to force Garbage Collection if the platform supports it.
 * Essential for fair memory benchmarking.
 */
fun forceGC() {
    System.gc()
    Runtime.getRuntime().gc()
    // Small delay to let GC work
    try { Thread.sleep(20) } catch (e: Exception) {}
}

/**
 * Gets the allocated bytes on the current thread using VMDebug for byte-precision.
 */
@SuppressLint("SoonBlockedPrivateApi")
fun getCurrentThreadAllocatedBytes(): Long {
    return try {
        // VMDebug is the internal truth of Dalvik/ART.
        // threadAllocSize() provides byte-precision that android.os.Debug might aggregate in 32KB chunks.
        val vmDebugClass = Class.forName("dalvik.system.VMDebug")
        val method = vmDebugClass.getMethod("threadAllocSize")
        method.invoke(null) as Long
    } catch (e: Exception) {
        // Last-ditch effort if VMDebug is missing
        val runtime = Runtime.getRuntime()
        runtime.totalMemory() - runtime.freeMemory()
    }
}

/**
 * Human readable memory formatting.
 */
fun formatMem(bytes: Long): String {
    val b = if (bytes < 0) 0L else bytes
    return when {
        b >= 1024 * 1024 -> "${(b / (1024 * 1024.0) * 100).toInt() / 100.0} MB"
        b >= 1024 -> "${(b / 1024.0 * 100).toInt() / 100.0} KB"
        else -> "$b B"
    }
}
