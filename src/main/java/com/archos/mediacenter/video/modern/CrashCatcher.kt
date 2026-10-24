// Nova Modern: keeps the details of a crash (or a failed library load) so the app can show and
// share them on the next launch instead of just closing. Nothing leaves the phone unless shared.
package com.archos.mediacenter.video.modern

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashCatcher {
    private const val CRASH_FILE = "nova_modern_crash.txt"
    private const val PROBLEMS_FILE = "nova_modern_problems.txt"
    private const val MAX_PROBLEMS_BYTES = 48 * 1024

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(app.filesDir, CRASH_FILE).writeText(report(app, "Crash in thread \"${thread.name}\"", error))
            } catch (_: Throwable) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun recordProblem(context: Context, where: String, error: Throwable) {
        try {
            val f = File(context.filesDir, PROBLEMS_FILE)
            val entry = report(context, "Problem while: $where", error) + "\n\n"
            if (f.exists() && f.length() > MAX_PROBLEMS_BYTES) f.writeText(entry) else f.appendText(entry)
        } catch (_: Throwable) {
        }
    }

    fun pendingCrash(context: Context): String? = read(File(context.filesDir, CRASH_FILE))

    fun clearCrash(context: Context) {
        File(context.filesDir, CRASH_FILE).delete()
    }

    fun problems(context: Context): String? = read(File(context.filesDir, PROBLEMS_FILE))

    fun clearProblems(context: Context) {
        File(context.filesDir, PROBLEMS_FILE).delete()
    }

    private fun read(f: File): String? = try {
        if (f.exists()) f.readText().takeIf { it.isNotBlank() } else null
    } catch (_: Throwable) {
        null
    }

    private fun report(context: Context, title: String, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${context.packageName})"
        } catch (_: Throwable) {
            context.packageName
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("Nova Modern report — $time")
            appendLine(title)
            appendLine("App: $version")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            append(trace)
        }
    }
}

/** Installs [CrashCatcher] before the Application starts, so even start-up crashes are kept. */
class CrashCatcherProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { CrashCatcher.install(it) }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
