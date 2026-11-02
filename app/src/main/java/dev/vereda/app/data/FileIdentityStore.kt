package dev.vereda.app.data

import dev.vereda.core.Identity
import java.io.File

class FileIdentityStore(private val file: File) {
    fun loadOrCreate(): Identity {
        if (!file.exists()) return fresh()
        return try {
            Identity.decode(file.readBytes())
        } catch (_: RuntimeException) {
            val broken = File(file.parentFile, "identity.bin.corrupt")
            file.renameTo(broken)
            fresh()
        }
    }

    private fun fresh(): Identity {
        val created = Identity.generate()
        file.parentFile?.mkdirs()
        file.writeBytes(created.encode())
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        return created
    }
}
