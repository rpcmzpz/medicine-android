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
import java.io.IOException

/**
 * 把相册选中的 `content://` Uri 包成 multipart 的一个 part。
 *
 * 三个关键点：
 * 1. **part 名必须是 `files`**：后端是 `@RequestParam("files") MultipartFile[]`，
 *    名字对不上就是 400「Required request part 'files' is not present」。
 * 2. **流式发送，不 readBytes**：手机照片动辄 3–10MB，连选 9 张全读进堆里
 *    很容易 OOM。OkHttp 4.12 的 `asRequestBody` 只支持 File/ByteArray/String，
 *    没有 InputStream 重载，所以这里自己实现一个 RequestBody。
 * 3. **body 必须可重放**：见 [UriRequestBody] 的注释。
 */
fun Context.toPrescriptionPart(uri: Uri, index: Int): MultipartBody.Part? {
    val mime = contentResolver.getType(uri) ?: "image/jpeg"
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"

    // 先探一次能不能读：Uri 可能已失效（用户删了图、授权被回收），
    // 这种要当场跳过，不能等到发请求时才炸。open 后立刻 close，不读内容。
    val readable = runCatching {
        contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)
    if (!readable) return null

    // 文件名只是给服务端看原名的；后端实际会用「时间戳+随机串」重命名，不怕重名
    return MultipartBody.Part.createFormData(
        name = "files",
        filename = "prescription_${index + 1}.$extension",
        body = UriRequestBody(this, uri, mime.toMediaTypeOrNull())
    )
}

/**
 * 把图片流式泵进 OkHttp 的 sink，但**每次写都重新开一次流**。
 *
 * ## 为什么 body 里存 Uri 而不是已打开的 InputStream
 *
 * 早期版本持有的是打开好的 InputStream，只能读一次（`contentLength()` 返回 -1，
 * 走 chunked 传输，内存里始终只有 8KB 缓冲区，这点没变）。
 * 但这种 body 是**一次性**的：OkHttp 在「401 -> 换 token -> 重放请求」时，
 * 会把同一个 body 再写一遍，而此时流早就被读空 → `IOException: Stream Closed`。
 *
 * 改成存 Uri 之后，`writeTo()` 每次都重新 `openInputStream`，
 * body 就变成**可重放**的，重放时能拿到一份全新的流。
 *
 * 顺带解决另一个隐患：OkHttp 在连接失败时也可能重试请求，
 * 一次性 body 在那种场景下同样会失败，现在一并免疫了。
 *
 * 代价是每次重放都要重新打开一次文件描述符——相比重新上传整张图，这点开销可以忽略。
 */
private class UriRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mediaType: MediaType?
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = -1L

    override fun writeTo(sink: BufferedSink) {
        // use 保证写完（或抛异常时）关闭文件描述符，避免 Android 端句柄泄漏
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取图片：$uri")
        stream.source().use { source -> sink.writeAll(source) }
    }
}
