package com.zhuhai.medicine.ui.common

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream

/**
 * 把相册选中的 `content://` Uri 包成 multipart 的一个 part。
 *
 * 两个关键点：
 * 1. **part 名必须是 `files`**：后端是 `@RequestParam("files") MultipartFile[]`，
 *    名字对不上就是 400「Required request part 'files' is not present」。
 * 2. **流式发送，不 readBytes**：手机照片动辄 3–10MB，连选 9 张全读进堆里
 *    很容易 OOM。OkHttp 4.12 的 `asRequestBody` 只支持 File/ByteArray/String，
 *    没有 InputStream 重载，所以这里自己实现一个 RequestBody。
 */
fun Context.toPrescriptionPart(uri: Uri, index: Int): MultipartBody.Part? {
    val mime = contentResolver.getType(uri) ?: "image/jpeg"
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"

    // openInputStream 对失效的 Uri 会抛 FileNotFoundException / SecurityException，
    // 这里不崩，返回 null 让调用方过滤掉这一张。
    val stream = runCatching { contentResolver.openInputStream(uri) }.getOrNull() ?: return null

    // 文件名只是给服务端看原名的；后端实际会用「时间戳+随机串」重命名，不怕重名
    return MultipartBody.Part.createFormData(
        name = "files",
        filename = "prescription_${index + 1}.$extension",
        body = StreamRequestBody(stream, mime.toMediaTypeOrNull())
    )
}

/**
 * 把 InputStream 直接泵进 OkHttp 的 sink。
 *
 * `contentLength()` 返回 -1 表示「长度未知」→ OkHttp 会用 chunked 传输。
 * 多花几字节的编码开销，换来的是**内存里始终只有 8KB 的缓冲区**。
 */
private class StreamRequestBody(
    private val stream: InputStream,
    private val mediaType: MediaType?
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = -1L

    override fun writeTo(sink: BufferedSink) {
        // use 保证写完（或抛异常时）关闭文件描述符，避免 Android 端句柄泄漏
        stream.source().use { source -> sink.writeAll(source) }
    }
}
