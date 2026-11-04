package ai.ebbflow.baseline.app.widget

import android.content.Context
import java.io.File

/**
 * The last acquisition state the widgets saw, kept so they can say "last known, as of …"
 * after the process ends. Lives under [Context.getNoBackupFilesDir], so it never reaches a
 * cloud backup, and holds no device name and no signal values (see [AcquisitionSnapshot]).
 */
object WidgetSnapshotStore {
    private const val DIR = "widgets"
    private const val FILE = "acquisition.snapshot"
    private val lock = Any()

    private fun file(context: Context): File = File(File(context.noBackupFilesDir, DIR), FILE)

    fun read(context: Context): AcquisitionSnapshot? {
        synchronized(lock) {
            val f = file(context)
            if (!f.isFile) return null
            return runCatching { SnapshotCodec.decode(f.readText()) }.getOrNull()
        }
    }

    fun write(context: Context, snapshot: AcquisitionSnapshot) {
        synchronized(lock) {
            val target = file(context)
            val dir = target.parentFile ?: return
            if (!dir.isDirectory && !dir.mkdirs()) return
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeText(SnapshotCodec.encode(snapshot))
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }
}
