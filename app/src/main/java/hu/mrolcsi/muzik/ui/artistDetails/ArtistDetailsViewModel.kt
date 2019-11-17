package hu.mrolcsi.muzik.ui.artistDetails

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.view.View
import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.lifecycle.LiveData
import hu.mrolcsi.muzik.ui.common.NavCommandSource
import hu.mrolcsi.muzik.ui.common.UiCommandSource
import hu.mrolcsi.muzik.ui.base.ThemedViewModel

interface ArtistDetailsViewModel : ThemedViewModel, Observable, UiCommandSource,
  NavCommandSource {

  fun setArgument(artistId: Long)

  val artistSongs: LiveData<List<MediaBrowserCompat.MediaItem>>

  val artistAlbums: LiveData<List<MediaBrowserCompat.MediaItem>>

  @get:Bindable
  val artistName: String?

  val artistPicture: LiveData<Uri>

  @get:Bindable
  val isAlbumsVisible: Boolean

  fun onAlbumClick(albumItem: MediaBrowserCompat.MediaItem, transitionedView: View)

  fun onSongClick(songItem: MediaBrowserCompat.MediaItem, position: Int)

  fun onShuffleAllClick()
}