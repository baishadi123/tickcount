package io.github.zzpby.tickcount.domain

/**
 * 换机后的相册重连,写成纯规则:体检按指纹给出一组「旧条目 → 新条目」的
 * 映射,这里把它套到记录上。只有被映射命中的照片列表才算改过——一条记录
 * 没沾到,就原样归还,调用方因此知道哪些记录根本不用写回。
 */
object GalleryRelink {

    /**
     * Applies the mapping to one photo list, or null when nothing in it
     * changed — null meaning "leave the record alone".
     */
    fun remapPhotos(photos: List<String>, mapping: Map<String, String>): List<String>? {
        var changed = false
        val mapped = photos.map { entry -> mapping[entry]?.also { changed = true } ?: entry }
        return if (changed) mapped else null
    }

    /** The album cover follows its photo: same mapping, same rule. */
    fun remapCover(cover: String?, mapping: Map<String, String>): String? =
        cover?.let { mapping[it] ?: it }
}
