package hu.mrolcsi.muzik.library.songs

import android.support.v4.media.MediaBrowserCompat
import hu.mrolcsi.muzik.common.viewmodel.ListViewModel
import hu.mrolcsi.muzik.common.viewmodel.NavCommandSource
import hu.mrolcsi.muzik.common.viewmodel.UiCommandSource
import hu.mrolcsi.muzik.library.SortingMode

interface SongsViewModel : ListViewModel<MediaBrowserCompat.MediaItem>, UiCommandSource, NavCommandSource {

  var sortingMode: SortingMode

  fun onSongClicked(songItem: MediaBrowserCompat.MediaItem, position: Int)

}