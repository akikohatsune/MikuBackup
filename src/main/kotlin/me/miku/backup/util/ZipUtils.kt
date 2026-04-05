package me.miku.backup.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    fun zipFolders(sources: List<File>, destination: File) {
        FileOutputStream(destination).use { fos ->
            ZipOutputStream(fos).use { zos ->
                for (source in sources) {
                    if (source.exists()) {
                        zipFolder(source, source.name, zos)
                    }
                }
            }
        }
    }

    private fun zipFolder(fileToZip: File, fileName: String, zos: ZipOutputStream) {
        if (fileToZip.isHidden) return
        
        // Exclude session.lock and other transient files if necessary
        if (fileToZip.name == "session.lock") return

        if (fileToZip.isDirectory) {
            val children = fileToZip.listFiles() ?: return
            for (childFile in children) {
                zipFolder(childFile, "$fileName/${childFile.name}", zos)
            }
            return
        }

        FileInputStream(fileToZip).use { fis ->
            val zipEntry = ZipEntry(fileName)
            zos.putNextEntry(zipEntry)
            val bytes = ByteArray(1024)
            var length: Int
            while (fis.read(bytes).also { length = it } >= 0) {
                zos.write(bytes, 0, length)
            }
        }
    }
    
    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
