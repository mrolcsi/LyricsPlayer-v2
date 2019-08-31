package hu.mrolcsi.muzik.library.albums.details

import android.content.Context
import android.graphics.BitmapFactory
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.core.os.bundleOf
import androidx.databinding.library.baseAdapters.BR
import androidx.lifecycle.MutableLiveData
import hu.mrolcsi.muzik.R
import hu.mrolcsi.muzik.common.glide.GlideApp
import hu.mrolcsi.muzik.common.glide.onResourceReady
import hu.mrolcsi.muzik.common.viewmodel.DataBindingViewModel
import hu.mrolcsi.muzik.common.viewmodel.ExecuteOnceNavCommandSource
import hu.mrolcsi.muzik.common.viewmodel.ExecuteOnceUiCommandSource
import hu.mrolcsi.muzik.common.viewmodel.ObservableImpl
import hu.mrolcsi.muzik.media.MediaRepository
import hu.mrolcsi.muzik.media.MediaService
import hu.mrolcsi.muzik.service.extensions.media.MediaType
import hu.mrolcsi.muzik.service.extensions.media.album
import hu.mrolcsi.muzik.service.extensions.media.albumArtUri
import hu.mrolcsi.muzik.service.extensions.media.albumYear
import hu.mrolcsi.muzik.service.extensions.media.artist
import hu.mrolcsi.muzik.service.extensions.media.id
import hu.mrolcsi.muzik.service.extensions.media.numberOfSongs
import hu.mrolcsi.muzik.service.extensions.media.titleKey
import hu.mrolcsi.muzik.service.extensions.media.trackNumber
import hu.mrolcsi.muzik.service.extensions.media.type
import hu.mrolcsi.muzik.theme.Theme
import hu.mrolcsi.muzik.theme.ThemedViewModel
import hu.mrolcsi.muzik.theme.ThemedViewModelImpl
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import javax.inject.Inject
import kotlin.properties.Delegates

class AlbumDetailsViewModelImpl @Inject constructor(
  observable: ObservableImpl,
  uiCommandSource: ExecuteOnceUiCommandSource,
  navCommandSource: ExecuteOnceNavCommandSource,
  private val themedViewModel: ThemedViewModelImpl,
  private val context: Context,
  private val mediaService: MediaService,
  private val mediaRepo: MediaRepository
) : DataBindingViewModel(observable, uiCommandSource, navCommandSource),
  ThemedViewModel by themedViewModel,
  AlbumDetailsViewModel {

  override val progressVisible: Boolean = false
  override val listViewVisible: Boolean = true
  override val emptyViewVisible: Boolean = false

  override val items = MutableLiveData<List<MediaItem>>()

  override var albumTitleText: String? by boundStringOrNull(BR.albumTitleText)
  override var artistText: String? by boundStringOrNull(BR.artistText)
  override var yearText: String? by boundStringOrNull(BR.yearText)
  override var numberOfSongsText: String? by boundStringOrNull(BR.numberOfSongsText)

  override var albumItem: MediaItem? by Delegates.observable(null) { _, old: MediaItem?, new: MediaItem? ->
    if (old != new) new?.let {
      albumSubject.onNext(it)
    }
  }

  private var albumSubject = PublishSubject.create<MediaItem>()

  override val albumTheme = MutableLiveData<Theme>()

  private val shuffleAllItem = MediaItem(
    MediaDescriptionCompat.Builder()
      .setMediaId("shuffle/all")
      .setTitle(context.getString(R.string.mediaControl_shuffleAll))
      .setIconBitmap(BitmapFactory.decodeResource(context.resources, R.drawable.ic_shuffle_all))
      .setExtras(bundleOf(MediaType.MEDIA_TYPE_KEY to MediaType.MEDIA_OTHER))
      .build(),
    0
  )

  override fun onSongClick(songItem: MediaItem, position: Int) {
    albumItem?.description?.artist?.let { mediaService.setQueueTitle(it) }

    if (songItem.description.type == MediaType.MEDIA_OTHER) {
      songDescriptions?.let { mediaService.playAllShuffled(it) }
    } else {
      songDescriptions?.let { mediaService.playAll(it, position - 2) }
    }
  }

  private var songDescriptions: List<MediaDescriptionCompat>? = null

  init {
    albumSubject
      .subscribeOn(Schedulers.io())
      .filter { it.mediaId != null }
      .doOnNext { updateHeaderText(it) }
      .switchMap { mediaRepo.getSongsFromAlbum(it.description.id) }
      .map { songs -> songs.sortedBy { it.description.titleKey }.sortedBy { it.description.trackNumber } }
      .doOnNext { songs -> songDescriptions = songs.filter { it.isPlayable }.map { it.description } }
      .map { it.addDiscIndicator().addShuffleAll() }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onNext = { items.value = it },
        onError = { showError(this, it) }
      ).disposeOnCleared()
  }

  private fun updateHeaderText(albumItem: MediaItem) {
    albumTitleText = albumItem.description.album
    artistText = albumItem.description.artist
    yearText = albumItem.description.albumYear

    val numberOfSong = albumItem.description.numberOfSongs
    numberOfSongsText = context.resources.getQuantityString(R.plurals.artists_numberOfSongs, numberOfSong, numberOfSong)

    GlideApp.with(context)
      .asBitmap()
      .load(albumItem.description.albumArtUri)
      .onResourceReady { albumArt ->
        themedViewModel.themeService.createTheme(albumArt).subscribeBy { albumTheme.value = it }
      }
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

  private fun List<MediaItem>.addShuffleAll(): List<MediaItem> = toMutableList().apply {
    add(0, shuffleAllItem)
  }.toList()
}