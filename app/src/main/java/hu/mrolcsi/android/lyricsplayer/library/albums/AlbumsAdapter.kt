package hu.mrolcsi.android.lyricsplayer.library.albums

import android.content.res.ColorStateList
import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import hu.mrolcsi.android.lyricsplayer.BuildConfig
import hu.mrolcsi.android.lyricsplayer.R
import hu.mrolcsi.android.lyricsplayer.extensions.artistKey
import hu.mrolcsi.android.lyricsplayer.theme.Theme
import hu.mrolcsi.android.lyricsplayer.theme.ThemeManager

class AlbumsAdapter : ListAdapter<MediaBrowserCompat.MediaItem, AlbumsAdapter.AlbumHolder>(
  object : DiffUtil.ItemCallback<MediaBrowserCompat.MediaItem>() {
    override fun areItemsTheSame(
      oldItem: MediaBrowserCompat.MediaItem,
      newItem: MediaBrowserCompat.MediaItem
    ): Boolean {
      return oldItem == newItem
    }

    override fun areContentsTheSame(
      oldItem: MediaBrowserCompat.MediaItem,
      newItem: MediaBrowserCompat.MediaItem
    ): Boolean {
      return oldItem.description.mediaId == newItem.description.mediaId
    }
  }
) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumHolder {
    val itemView = LayoutInflater.from(parent.context).inflate(R.layout.list_item_artist, parent, false)
    return AlbumHolder(itemView)
  }

  override fun onBindViewHolder(holder: AlbumHolder, position: Int) {
    val item = getItem(position)

    with(holder) {
      // Apply theme
      ThemeManager.currentTheme.value?.let { theme ->
        itemView.background = Theme.getRippleDrawable(theme.darkForegroundColor, theme.darkerBackgroundColor)

        tvAlbum?.setTextColor(theme.darkerForegroundColor)
        tvArtist?.setTextColor(ColorUtils.setAlphaComponent(theme.darkerForegroundColor, Theme.INACTIVE_OPACITY))

        imgChevronRight?.imageTintList = ColorStateList.valueOf(theme.darkerForegroundColor)
      }

      // Set texts
      tvAlbum?.text = item.description.title
      tvArtist?.text = item.description.subtitle

      // Set onClickListener
      if (item.mediaId == MEDIA_ID_ALL_SONGS) {
        itemView.setOnClickListener {
          val direction = AlbumsFragmentDirections.actionAlbumsToSongs(
            item.description.extras?.artistKey,
            item.description.extras?.artist,
            null,
            null
          )
          it.findNavController().navigate(direction)
        }
      } else {
        itemView.setOnClickListener {
          with(it.findNavController()) {
            try {
              val direction = AlbumsFragmentDirections.actionAlbumsToSongs(
                null,
                null,
                item.mediaId,
                item.description.title.toString()
              )
              navigate(direction)
            } catch (e: IllegalArgumentException) {
              Toast.makeText(it.context, "Lost navigation.", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
    }


  }

  class AlbumHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val tvAlbum: TextView? = itemView.findViewById(R.id.tvTitle)
    val tvArtist: TextView? = itemView.findViewById(R.id.tvSubtitle)
    val imgChevronRight: ImageView? = itemView.findViewById(R.id.imgChevronRight)
  }

  companion object {
    private const val LOG_TAG = "AlbumsAdapter"

    const val MEDIA_ID_ALL_SONGS = BuildConfig.APPLICATION_ID + ".ALL_SONGS"
  }
}