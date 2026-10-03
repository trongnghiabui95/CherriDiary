package com.cherri.diary.service

import com.cherri.diary.api.invalid
import com.cherri.diary.api.missing
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

@Service
class ProofStorageService(@Value("\${app.upload-dir}") directory: String) {
    private val root = Path.of(directory).toAbsolutePath().normalize()

    fun store(file: MultipartFile?): String? {
        if (file == null || file.isEmpty) return null
        if (file.size > 5 * 1024 * 1024) invalid("Ảnh tối đa 5 MB")
        val image = file.inputStream.use { input ->
            ImageIO.createImageInputStream(input).use { stream ->
                val readers = ImageIO.getImageReaders(stream)
                if (!readers.hasNext()) invalid("Chỉ nhận ảnh PNG hoặc JPEG")
                val reader = readers.next()
                try {
                    if (reader.formatName.lowercase() !in setOf("png", "jpeg", "jpg")) invalid("Chỉ nhận ảnh PNG hoặc JPEG")
                    reader.input = stream
                    if (reader.getWidth(0).toLong() * reader.getHeight(0) > 20_000_000) invalid("Ảnh vượt quá 20 megapixel")
                    try { reader.read(0) }
                    catch (_: javax.imageio.IIOException) { invalid("Ảnh bị hỏng hoặc không đọc được") }
                } finally { reader.dispose() }
            }
        } ?: invalid("Không đọc được ảnh")
        Files.createDirectories(root)
        val name = "${UUID.randomUUID()}.png"
        val path = root.resolve(name)
        try {
            ImageIO.write(image, "png", path.toFile()) // Re-encode, strip metadata; never use client filenames.
            if (Files.size(path) > 5 * 1024 * 1024) invalid("Ảnh sau xử lý vượt quá 5 MB")
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCompletion(status: Int) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) Files.deleteIfExists(path)
                    }
                })
            }
            return "/api/v1/proofs/$name"
        } catch (e: Exception) {
            Files.deleteIfExists(path)
            throw e
        } finally { image.flush() }
    }

    fun load(name: String): FileSystemResource {
        if (!name.matches(Regex("[a-f0-9-]{36}\\.png"))) missing("Không tìm thấy ảnh")
        val path = root.resolve(name).normalize()
        if (path.parent != root || !Files.isRegularFile(path)) missing("Không tìm thấy ảnh")
        return FileSystemResource(path)
    }
}
