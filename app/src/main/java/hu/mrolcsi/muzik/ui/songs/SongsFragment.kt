package hu.mrolcsi.muzik.ui.songs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import hu.mrolcsi.muzik.R
import hu.mrolcsi.muzik.ui.common.MediaItemListAdapter
import hu.mrolcsi.muzik.ui.common.observeAndRunNavCommands
import hu.mrolcsi.muzik.ui.common.observeAndRunUiCommands
import hu.mrolcsi.muzik.databinding.FragmentSongsBinding
import hu.mrolcsi.muzik.databinding.ListItemSongBinding
import hu.mrolcsi.muzik.ui.library.SortingMode
import kotlinx.android.synthetic.main.fragment_songs.*
import org.koin.androidx.viewmodel.ext.android.viewModel

class SongsFragment : Fragment() {

  private val viewModel: SongsViewModel by viewModel<SongsViewModelImpl>()

  private val songsAdapter by lazy {
    MediaItemListAdapter(requireContext()) { parent, _ ->
      SongHolder(
        itemView = ListItemSongBinding.inflate(
          LayoutInflater.from(parent.context),
          parent,
          false
        ).apply {
          theme = viewModel.currentTheme
          lifecycleOwner = viewLifecycleOwner
        }.root,
        showTrackNumber = false
      ).apply {
        itemView.setOnClickListener {
          model?.let {
            viewModel.onSongClick(it, adapterPosition)
          }
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setHasOptionsMenu(true)
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    viewModel.apply {

      requireContext().observeAndRunUiCommands(viewLifecycleOwner, this)
      findNavController().observeAndRunNavCommands(viewLifecycleOwner, this)

      items.observe(viewLifecycleOwner, songsAdapter)
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
    FragmentSongsBinding.inflate(inflater, container, false).apply {
      theme = viewModel.currentTheme
      lifecycleOwner = viewLifecycleOwner
    }.root

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    rvSongs.adapter = songsAdapter
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.menu_songs, menu)

    // Apply currentTheme to items
    val color = viewModel.currentTheme.value?.primaryForegroundColor ?: Color.WHITE
    menu.forEach {
      it.icon.setTint(color)
    }
  }

  override fun onPrepareOptionsMenu(menu: Menu) {
    super.onPrepareOptionsMenu(menu)

    when (viewModel.sortingMode) {
      SortingMode.SORT_BY_ARTIST -> menu.findItem(R.id.menuSortByArtist).isChecked = true
      SortingMode.SORT_BY_TITLE -> menu.findItem(R.id.menuSortByTitle).isChecked = true
      SortingMode.SORT_BY_DATE -> menu.findItem(R.id.menuSortByDate).isChecked = true
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    item.isChecked = true
    return when (item.itemId) {
      R.id.menuSortByArtist -> {
        viewModel.sortingMode = SortingMode.SORT_BY_ARTIST
        songsAdapter.sorting = SortingMode.SORT_BY_ARTIST
        true
      }
      R.id.menuSortByTitle -> {
        viewModel.sortingMode = SortingMode.SORT_BY_TITLE
        songsAdapter.sorting = SortingMode.SORT_BY_TITLE
        true
      }
      R.id.menuSortByDate -> {
        viewModel.sortingMode = SortingMode.SORT_BY_DATE
        songsAdapter.sorting = SortingMode.SORT_BY_DATE
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }
}