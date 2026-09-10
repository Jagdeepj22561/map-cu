package com.example.maps123.utils

import android.net.Uri

object PostLinks {
    private val postPathRegex = Regex("""(?:maps123://post/|https://maps123\.example\.com/post/)([A-Za-z0-9-]+)""")

    fun buildPostLink(postId: String): String = "maps123://post/$postId"

    fun buildShareMessage(title: String?, postId: String): String {
        val trimmedTitle = title?.trim().orEmpty()
        val link = buildPostLink(postId)
        return if (trimmedTitle.isNotEmpty()) {
            "Check out this post: $trimmedTitle\n$link"
        } else {
            link
        }
    }

    fun extractPostId(text: String): String? {
        postPathRegex.find(text)?.groupValues?.getOrNull(1)?.let { return it }

        return runCatching {
            val uri = Uri.parse(text.trim())
            if (uri.scheme == "maps123" && uri.host == "post") {
                uri.lastPathSegment
            } else {
                null
            }
        }.getOrNull()
    }
}
