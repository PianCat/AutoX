package com.stardust.autojs.core.image.capture

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjection
import com.stardust.app.OnActivityResultDelegate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.CancellationException

class ScreenCaptureManager : ScreenCaptureRequester {
    @Volatile
    override var screenCapture: ScreenCapturer? = null
    private var mediaProjection: MediaProjection? = null

    override suspend fun requestScreenCapture(context: Context, orientation: Int) {
        if (screenCapture?.available == true) {
            return
        }
        val result = if (context is OnActivityResultDelegate.DelegateHost && context is Activity) {
            ScreenCaptureRequester.ActivityScreenCaptureRequester(
                context.onActivityResultDelegateMediator, context
            ).request()
        } else {
            coroutineScope {
                val result = CompletableDeferred<MediaProjection>()
                ScreenCaptureRequestActivity.request(context,
                    object : ScreenCaptureRequestActivity.Callback {
                        override fun onResult(data: MediaProjection?) {
                            if (data != null) {
                                result.complete(data)
                            } else result.cancel(CancellationException("data is null"))
                        }
                    })
                result.await()
            }
        }
        mediaProjection = result
        screenCapture = ScreenCapturer(result)
    }

    override fun recycle() {
        screenCapture?.release()
        screenCapture = null
        mediaProjection?.stop()
    }
}