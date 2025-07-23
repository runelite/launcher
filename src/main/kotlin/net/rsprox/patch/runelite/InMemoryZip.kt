package net.rsprox.patch.runelite

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

public class InMemoryZip(
    private val internalMap: MutableMap<String, ByteArray>,
) : MutableMap<String, ByteArray> by internalMap

public fun readZipFileIntoMemory(
    path: Path,
    expectedFileCount: Int = 1000,
    executor: ExecutorService = ForkJoinPool.commonPool(),
): InMemoryZip {
    val inMemoryFiles = ConcurrentHashMap<String, ByteArray>()
    ZipFile(path.toFile()).use { zipFile ->
        val tasks =
            buildList(expectedFileCount) {
                for (entry in zipFile.entries()) {
                    if (entry.isDirectory) continue
                    add(
                        Callable {
                            zipFile.getInputStream(entry).use { input ->
                                val buffer = ByteArrayOutputStream()
                                input.copyTo(buffer)
                                inMemoryFiles.put(entry.name, buffer.toByteArray())
                            }
                        },
                    )
                }
            }

        executor.invokeAll(tasks)
    }
    return InMemoryZip(inMemoryFiles)
}

public fun writeInMemoryZipToPath(
    zip: InMemoryZip,
    path: Path,
    compressionLevel: Int = Deflater.NO_COMPRESSION,
) {
    ZipOutputStream(Files.newOutputStream(path)).use { zos ->
        zos.setLevel(compressionLevel)

        for ((pathString, bytes) in zip) {
            val entry = ZipEntry(pathString)
            zos.putNextEntry(entry)
            zos.write(bytes)
            zos.closeEntry()
        }
    }
}
