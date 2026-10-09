package ir.comicreader.fa.util

import android.content.Context
import java.io.File

/** Persists the last uncaught exception so it can be shown/copied on the next launch. */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(app.filesDir, FILE_NAME).writeText(
                    throwable.stackTraceToString() + "\n\nthread: " + thread.name
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE_NAME).takeIf { it.isFile }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
