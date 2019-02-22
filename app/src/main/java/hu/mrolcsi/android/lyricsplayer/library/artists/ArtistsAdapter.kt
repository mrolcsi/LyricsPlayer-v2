package hu.mrolcsi.android.lyricsplayer.library.artists

import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import hu.mrolcsi.android.lyricsplayer.R

class ArtistsAdapter : ListAdapter<MediaBrowserCompat.MediaItem, ArtistsAdapter.ArtistHolder>(
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

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_artist, parent, false)
    return ArtistHolder(view)
  }

  override fun onBindViewHolder(holder: ArtistHolder, position: Int) {
    val item = getItem(position)

    with(holder) {
      tvArtist?.text = item.description.title
      val numberOfAlbums = item.description.extras?.getInt(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS) ?: 0
      val numberOfSongs = item.description.extras?.getInt(MediaStore.Audio.Artists.NUMBER_OF_TRACKS) ?: 0
      val numberOfAlbumsString =
        itemView.context.resources.getQuantityString(R.plurals.artists_numberOfAlbums, numberOfAlbums, numberOfAlbums)
      val numberOfSongsString =
        itemView.context.resources.getQuantityString(R.plurals.artists_numberOfSongs, numberOfSongs, numberOfSongs)
      tvNumOfSongs?.text =
        itemView.context.getString(R.string.artists_item_subtitle, numberOfAlbumsString, numberOfSongsString)

      itemView.setOnClickListener {
        with(it.findNavController()) {
          Log.d(LOG_TAG, "Current Destination = $currentDestination")
//          when (currentDestination?.id) {
//            R.id.navigation_artists -> {
          try {
            val direction = ArtistsFragmentDirections.actionArtistsToAlbums(
              item.mediaId,
              item.description.title.toString(),
              numberOfSongs
            )
            navigate(direction)
          } catch (e: IllegalArgumentException) {
            Toast.makeText(it.context, "Lost navigation.", Toast.LENGTH_SHORT).show()
          }
//            }
//          }
        }
      }
    }
  }

  class ArtistHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val tvArtist: TextView? = itemView.findViewById(R.id.tvTitle)
    val tvNumOfSongs: TextView? = itemView.findViewById(R.id.tvSubtitle)
  }

  companion object {
    private const val LOG_TAG = "ArtistsAdapter"
  }
}