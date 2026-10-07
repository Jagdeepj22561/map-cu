@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitView
import com.example.shared.ui.PureAvatarPlaceholder
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.Foundation.NSData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.NSObject

internal object IosRemoteImageCache {
    private val images = mutableMapOf<String, UIImage>()

    fun get(url: String): UIImage? = images[url]

    fun put(url: String, image: UIImage) {
        images[url] = image
    }
}

internal class RemoteImageView : UIImageView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var currentUrl: String? = null
    var loadingUrl: String? = null
    var imageTask: platform.Foundation.NSURLSessionDataTask? = null
}

@Composable
internal fun IosRemoteImage(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (imageUrl.isNullOrBlank()) {
        PureAvatarPlaceholder(modifier = modifier)
        return
    }

    val latestUrl = rememberUpdatedState(imageUrl)
    Box(modifier = modifier.background(Color.Transparent)) {
        PureAvatarPlaceholder(modifier = Modifier.fillMaxSize())

        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                RemoteImageView().apply {
                    clipsToBounds = true
                    backgroundColor = null
                }
            },
            update = { imageView ->
                loadRemoteImage(
                    imageView = imageView,
                    urlString = latestUrl.value
                )
            }
        )
    }
}

internal fun loadRemoteImage(
    imageView: RemoteImageView,
    urlString: String
) {
    if (imageView.currentUrl == urlString && imageView.image != null) return

    imageView.currentUrl = urlString
    val cached = IosRemoteImageCache.get(urlString)
    if (cached != null) {
        imageView.image = cached
        return
    }

    imageView.image = null
    if (imageView.loadingUrl == urlString) return
    imageView.imageTask?.cancel()
    imageView.loadingUrl = urlString

    val url = NSURL(string = urlString) ?: run {
        imageView.loadingUrl = null
        return
    }
    val request = NSMutableURLRequest.requestWithURL(url) as NSMutableURLRequest
    imageView.imageTask = NSURLSession.sharedSession.dataTaskWithRequest(request) {
            data: NSData?, _, _ ->
        val decoded = data?.let { UIImage.imageWithData(it) }
        dispatch_async(dispatch_get_main_queue()) {
            // A recycled Compose cell may now represent another URL. Never
            // let a late response paint the wrong profile/post image.
            if (imageView.currentUrl != urlString) return@dispatch_async
            imageView.loadingUrl = null
            imageView.imageTask = null
            if (decoded != null) {
                IosRemoteImageCache.put(urlString, decoded)
                imageView.image = decoded
            }
        }
    }
    imageView.imageTask?.resume()
}
