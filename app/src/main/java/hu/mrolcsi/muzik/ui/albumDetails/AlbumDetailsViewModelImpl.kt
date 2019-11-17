package hu.mrolcsi.muzik.ui.albumDetails

import android.content.Context
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.core.os.bundleOf
import androidx.databinding.library.baseAdapters.BR
import androidx.lifecycle.MutableLiveData
import hu.mrolcsi.muzik.R
import hu.mrolcsi.muzik.data.manager.media.MediaManager
import hu.mrolcsi.muzik.data.model.media.MediaType
import hu.mrolcsi.muzik.data.model.media.album
import hu.mrolcsi.muzik.data.model.media.albumArtUri
import hu.mrolcsi.muzik.data.model.media.albumYear
import hu.mrolcsi.muzik.data.model.media.artist
import hu.mrolcsi.muzik.data.model.media.id
import hu.mrolcsi.muzik.data.model.media.mediaId
import hu.mrolcsi.muzik.data.model.media.numberOfSongs
import hu.mrolcsi.muzik.data.model.media.titleKey
import hu.mrolcsi.muzik.data.model.media.trackNumber
import hu.mrolcsi.muzik.data.model.theme.Theme
import hu.mrolcsi.muzik.data.repository.media.MediaRepository
import hu.mrolcsi.muzik.ui.base.DataBindingViewModel
import hu.mrolcsi.muzik.ui.base.ThemedViewModel
import hu.mrolcsi.muzik.ui.base.ThemedViewModelImpl
import hu.mrolcsi.muzik.ui.common.ExecuteOnceNavCommandSource
import hu.mrolcsi.muzik.ui.common.ExecuteOnceUiCommandSource
import hu.mrolcsi.muzik.ui.common.ObservableImpl
import hu.mrolcsi.muzik.ui.common.glide.GlideApp
import hu.mrolcsi.muzik.ui.common.glide.onResourceReady
import hu.mrolcsi.muzik.ui.songs.applyNowPlaying
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.BehaviorSubject
import org.koin.core.KoinComponent
import org.koin.core.inject

class AlbumDetailsViewModelImpl constructor(
  observable: ObservableImpl,
  uiCommandSource: ExecuteOnceUiCommandSource,
  navCommandSource: ExecuteOnceNavCommandSource,
  themedViewModel: ThemedViewModelImpl
) : DataBindingViewModel(observable, uiCommandSource, navCommandSource),
  ThemedViewModel by themedViewModel,
  AlbumDetailsViewModel,
  KoinComponent {

  private val context: Context by inject()
  private val mediaManager: MediaManager by inject()
  private val mediaRepo: MediaRepository by inject()

  override val progressVisible: Boolean = false
  override val listViewVisible: Boolean = true
  override val emptyViewVisible: Boolean = false

  override val items = MutableLiveData<List<MediaItem>>()

  override var albumTitleText: String? by boundStringOrNull(BR.albumTitleText)
  override var artistText: String? by boundStringOrNull(BR.artistText)
  override var yearText: String? by boundStringOrNull(BR.yearText)
  override var numberOfSongsText: String? by boundStringOrNull(BR.numberOfSongsText)

  override fun setArgument(albumId: Long) {
    albumSubject.onNext(albumId)
  }

  private val albumSubject = BehaviorSubject.create<Long>()

  override var albumItem = MutableLiveData<MediaItem>()

  override val albumTheme = MutableLiveData<Theme>()

  override fun onSongClick(songItem: MediaItem, position: Int) {
    albumItem.value?.description?.artist?.let { mediaManager.setQueueTitle(it) }
    songDescriptions?.let { mediaManager.playAll(it, position) }
  }

  override fun onShuffleAllClick() {
    albumItem.value?.description?.artist?.let { mediaManager.setQueueTitle(it) }
    songDescriptions?.let { mediaManager.playAllShuffled(it) }
  }

  private var songDescriptions: List<MediaDescriptionCompat>? = null

  init {
    Observables.combineLatest(
      albumSubject
        .switchMap { mediaRepo.getAlbumById(it) }
        .doOnNext { updateHeaderText(it) }
        .switchMap { mediaRepo.getSongsFromAlbum(it.description.id) },
      mediaManager.mediaMetadata
        .distinctUntilChanged { t: MediaMetadataCompat -> t.mediaId }
        .filter { it.mediaId != null }
    )
      .map { (songs, metadata) ->
        songs
          .applyNowPlaying(metadata.mediaId)
          .sortedBy { it.description.titleKey }
          .sortedBy { it.description.trackNumber }
      }
      .doOnNext { songs -> songDescriptions = songs.filter { it.isPlayable }.map { it.description } }
      .map { it.addDiscIndicator() }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onNext = { items.value = it },
        onError = { showError(this, it) }
      ).disposeOnCleared()
  }

  private fun updateHeaderText(albumItem: MediaItem) {
    this.albumItem.value = albumItem

    albumTitleText = albumItem.description.album
    artistText = albumItem.description.artist
    yearText = albumItem.description.albumYear

    val numberOfSong = albumItem.description.numberOfSongs
    numberOfSongsText = context.resources.getQuantityString(R.plurals.artists_numberOfSongs, numberOfSong, numberOfSong)

    GlideApp.with(context)
      .asBitmap()
      .load(albumItem.description.albumArtUri)
      .onResourceReady { albumArt -> themeService.createTheme(albumArt).subscribeBy { albumTheme.value = it } }
      .preload()
  }

  private fun List<MediaItem>.addDiscIndicator(): List<MediaItem> = toMutableList().apply {
    if (last().description.trackNumber > 1000) {
      // Add disc number indicators
      val numDiscs = last().description.trackNumber / 1000
      if (numDiscs > 0) {
        for (i in 1..numDiscs) {
          val index = indexOfFirst { it.description.trackNumber > 1000 }
          val item = MediaItem(
            MediaDescriptionCompat.Builder()
              .setMediaId("disc/$i")
              .setTitle(i.toString())
              .setExtras(bundleOf(MediaType.MEDIA_TYPE_KEY to MediaType.MEDIA_OTHER))
              .build(),
            MediaItem.FLAG_BROWSABLE
          )
          add(index, item)
        }
      }
    }
  }.toList()
}