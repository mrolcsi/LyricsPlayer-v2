package hu.mrolcsi.muzik.data.service.media.exoplayer

import android.content.Context
import android.os.Handler
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.metadata.MetadataOutput
import com.google.android.exoplayer2.text.TextOutput
import com.google.android.exoplayer2.video.VideoRendererEventListener

internal class AudioOnlyRenderersFactory(private val context: Context) : RenderersFactory {
  override fun createRenderers(
    eventHandler: Handler?,
    videoRendererEventListener: VideoRendererEventListener?,
    audioRendererEventListener: AudioRendererEventListener?,
    textRendererOutput: TextOutput?,
    metadataRendererOutput: MetadataOutput?,
    drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>?
  ): Array<Renderer> {
    return arrayOf(
      MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, eventHandler, audioRendererEventListener)
      //LibflacAudioRenderer(eventHandler, audioRendererEventListener),
      //FfmpegAudioRenderer(eventHandler, audioRendererEventListener)
    )
  }
}