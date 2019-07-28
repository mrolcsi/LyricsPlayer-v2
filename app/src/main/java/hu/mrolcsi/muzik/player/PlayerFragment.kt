package hu.mrolcsi.muzik.player

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.postDelayed
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import androidx.transition.TransitionListenerAdapter
import dagger.android.support.DaggerFragment
import hu.mrolcsi.muzik.R
import hu.mrolcsi.muzik.common.OnRepeatTouchListener
import hu.mrolcsi.muzik.common.glide.GlideApp
import hu.mrolcsi.muzik.common.glide.MuzikGlideModule
import hu.mrolcsi.muzik.common.pager.PagerSnapHelperVerbose
import hu.mrolcsi.muzik.common.pager.RVPagerSnapHelperListenable
import hu.mrolcsi.muzik.common.pager.RVPagerStateListener
import hu.mrolcsi.muzik.common.pager.VisiblePageState
import hu.mrolcsi.muzik.extensions.applyForegroundColor
import hu.mrolcsi.muzik.extensions.applyNavigationBarColor
import hu.mrolcsi.muzik.extensions.applyStatusBarColor
import hu.mrolcsi.muzik.extensions.millisecondsToTimeStamp
import hu.mrolcsi.muzik.extensions.secondsToTimeStamp
import hu.mrolcsi.muzik.service.extensions.media.albumArtUri
import hu.mrolcsi.muzik.service.extensions.media.duration
import hu.mrolcsi.muzik.service.extensions.media.id
import hu.mrolcsi.muzik.service.extensions.media.isPaused
import hu.mrolcsi.muzik.service.extensions.media.isPlaying
import hu.mrolcsi.muzik.service.extensions.media.isSkipToNextEnabled
import hu.mrolcsi.muzik.service.extensions.media.isSkipToPreviousEnabled
import hu.mrolcsi.muzik.service.extensions.media.startProgressUpdater
import hu.mrolcsi.muzik.service.extensions.media.stopProgressUpdater
import hu.mrolcsi.muzik.service.theme.Theme
import hu.mrolcsi.muzik.service.theme.ThemeManager
import kotlinx.android.synthetic.main.content_player.*
import kotlinx.android.synthetic.main.fragment_player.*
import javax.inject.Inject
import kotlin.math.abs

class PlayerFragment : DaggerFragment() {

  @Inject lateinit var viewModel: PlayerViewModel

  private var mUserIsSeeking = false

  // Prepare drawables (separate for each button)
  private val mPreviousBackground by lazy { context?.getDrawable(R.drawable.media_button_background) }
  private val mPlayPauseBackground by lazy { context?.getDrawable(R.drawable.media_button_background) }
  private val mNextBackground by lazy { context?.getDrawable(R.drawable.media_button_background) }

  private val mRepeatNone by lazy {
    context?.getDrawable(R.drawable.ic_repeat_all)
      ?.constantState
      ?.newDrawable(resources)
      ?.mutate()
      ?.apply { alpha = Theme.DISABLED_OPACITY }
  }
  private val mRepeatOne by lazy {
    context?.getDrawable(R.drawable.ic_repeat_one)
  }
  private val mRepeatAll by lazy {
    context?.getDrawable(R.drawable.ic_repeat_all)
  }

  private val mQueueAdapter = QueueAdapter().apply {
    setHasStableIds(true)
  }
  private lateinit var mSnapHelper: PagerSnapHelperVerbose
  private var mScrollState = RecyclerView.SCROLL_STATE_IDLE

  private val mGlideListener = object : MuzikGlideModule.SimpleRequestListener<Drawable> {
    override fun onLoadFailed() {
      startPostponedEnterTransition()
    }

    override fun onResourceReady(resource: Drawable?) {
      startPostponedEnterTransition()
    }
  }

  // Gesture Detection
  private val mGestureDetector by lazy {
    GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {

      private val SWIPE_THRESHOLD = 100
      private val SWIPE_VELOCITY_THRESHOLD = 100

      override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x
        if (abs(diffX) < abs(diffY)) {
          if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
            if (diffY > 0) {
              // onSwipeDown
              activity?.onBackPressed()
              return true
            }
          }
        }
        return false
      }
    })
  }

  //region LIFECYCLE

  override fun onAttach(context: Context) {
    super.onAttach(context)
    activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        // If drawer is open, just close it
        if (drawer_layout.isDrawerOpen(GravityCompat.END)) {
          drawer_layout.closeDrawer(GravityCompat.END)
        } else {
          findNavController().navigateUp()
        }
      }
    })
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    postponeEnterTransition()

    val animationDuration = context?.resources?.getInteger(R.integer.preferredAnimationDuration)?.toLong() ?: 300L

    enterTransition = TransitionInflater
      .from(requireContext())
      .inflateTransition(R.transition.slide_bottom)
      .setDuration(animationDuration)
      .excludeTarget(imgCoverArt, true)
      .addListener(object : TransitionListenerAdapter() {
        override fun onTransitionEnd(transition: Transition) {
          Log.v(LOG_TAG, "enterTransition.onTransitionEnd()")

          context?.let {
            ThemeManager.getInstance(it).currentTheme.value?.let { theme ->
              activity?.applyStatusBarColor(theme.statusBarColor)
            }
          }
        }
      })

    returnTransition = TransitionInflater
      .from(requireContext())
      .inflateTransition(R.transition.slide_bottom)
      .setDuration(animationDuration)
      .excludeTarget(imgCoverArt, true)
      .addListener(object : TransitionListenerAdapter() {
        override fun onTransitionStart(transition: Transition) {
          Log.v(LOG_TAG, "returnTransition.onTransitionStart()")

          context?.let {
            ThemeManager.getInstance(requireContext()).currentTheme.value?.let { theme ->
              activity?.applyStatusBarColor(theme.primaryBackgroundColor)
            }
          }
        }
      })

    sharedElementEnterTransition = TransitionInflater
      .from(requireContext())
      .inflateTransition(android.R.transition.move)
      .setDuration(animationDuration)

    sharedElementReturnTransition = TransitionInflater
      .from(requireContext())
      .inflateTransition(android.R.transition.move)
      .setDuration(animationDuration)

    if (savedInstanceState != null) {
      // Re-apply status bar color after rotation
      activity?.run {

        ViewCompat.animate(rvQueue)
          .alpha(1f)
          .setDuration(context?.resources?.getInteger(R.integer.preferredAnimationDuration)?.toLong() ?: 300L)
          .withEndAction { imgCoverArt?.visibility = View.INVISIBLE }
          .start()

        ThemeManager.getInstance(this).currentTheme.value?.let { theme ->
          applyStatusBarColor(theme.statusBarColor)
        }
      }
    }
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    activity?.run {
      viewModel.apply {
        Log.d(LOG_TAG, "Got PlayerViewModel: $this")

        mediaController.observe(viewLifecycleOwner, Observer { controller ->
          controller?.let { setupTransportControls(it) }
        })
        currentMediaMetadata.observe(viewLifecycleOwner, Observer { metadata ->
          metadata?.let {
            updateSongData(it)
            updatePager()
          }
        })
        currentPlaybackState.observe(viewLifecycleOwner, Observer { state ->
          state?.let { updateControls(it) }
        })
        currentQueue.observe(viewLifecycleOwner, Observer {
          // Update Queue
          mQueueAdapter.submitList(it)
        })
      }

      ThemeManager.getInstance(requireContext()).currentTheme.observe(viewLifecycleOwner, object : Observer<Theme> {

        private var initialLoad = true

        override fun onChanged(it: Theme) {
          if (initialLoad) {
            applyThemeStatic(it)
            initialLoad = false
          } else {
            val backgroundColor = (content_player.background as ColorDrawable).color
            Log.v(
              LOG_TAG,
              "onThemeChanged(" +
                  "backgroundColor=${String.format("#%X", backgroundColor)}, " +
                  "themeColor=${String.format("#%X", it.primaryBackgroundColor)}" +
                  ")"
            )
            if (backgroundColor != it.primaryBackgroundColor) {
//            val visiblePosition = if (mQueueAdapter.realItemCount == 0) -1
//            else mSnapHelper.findSnapPosition(rvQueue.layoutManager) //% mQueueAdapter.realItemCount
              val visiblePosition = mSnapHelper.findSnapPosition(rvQueue.layoutManager)

              val activeId = MediaControllerCompat.getMediaController(requireActivity())
                ?.playbackState?.activeQueueItemId ?: -1
              val activePosition = mQueueAdapter.getItemPositionById(activeId/*, visiblePosition*/)

              if (abs(visiblePosition - activePosition) > 1) {
                applyThemeAnimated(it)
              } else {
                applyThemeStatic(it)
              }
            }
          }
        }
      })
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
    inflater.inflate(R.layout.fragment_player, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    setupToolbar()
    setupPager()

    rvQueue.setOnTouchListener { v, event ->
      mGestureDetector.onTouchEvent(event) || v.onTouchEvent(event)
    }
  }

  override fun onResume() {
    super.onResume()
    activity?.volumeControlStream = AudioManager.STREAM_MUSIC
  }

  override fun onStop() {
    super.onStop()

    MediaControllerCompat.getMediaController(requireActivity())?.transportControls?.stopProgressUpdater()
  }

  //endregion

  private fun setupToolbar() {
    playerToolbar.run {
      // Apply Theme
      val theme = ThemeManager.getInstance(requireContext()).currentTheme.value
      val color = theme?.primaryForegroundColor ?: Color.WHITE
      playerToolbar.applyForegroundColor(color)

      setNavigationOnClickListener {
        activity?.onBackPressed()
      }

      setOnMenuItemClickListener { item ->
        when (item?.itemId) {
          R.id.menuPlaylist -> {
            drawer_layout.openDrawer(GravityCompat.END)
            true
          }
          else -> super.onOptionsItemSelected(item)
        }
      }
    }
  }

  private fun setupPager() {
    rvQueue.run {
      layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
      adapter = mQueueAdapter
      setHasFixedSize(true)
      doOnNextLayout {
        // This could be more sophisticated
        it.postDelayed(500) { updatePager() }
      }
    }

    mSnapHelper = RVPagerSnapHelperListenable().attachToRecyclerView(rvQueue, object : RVPagerStateListener {

      override fun onPageScroll(pagesState: List<VisiblePageState>) {
        // Blend colors while scrolling
        when (pagesState.size) {
          2 -> {  // between 2 pages -> blend colors
            val leftHolder = rvQueue.findContainingViewHolder(pagesState.first().view) as QueueAdapter.QueueItemHolder
            val rightHolder = rvQueue.findContainingViewHolder(pagesState.last().view) as QueueAdapter.QueueItemHolder

            val leftTheme = leftHolder.usedTheme
            val rightTheme = rightHolder.usedTheme

            if (leftTheme != null && rightTheme != null) {

              val ratio = 1f - pagesState.first().distanceToSettled

              val backgroundColor = ColorUtils.blendARGB(
                leftTheme.primaryBackgroundColor,
                rightTheme.primaryBackgroundColor,
                ratio
              )
              val foregroundColor = ColorUtils.blendARGB(
                leftTheme.primaryForegroundColor,
                rightTheme.primaryForegroundColor,
                ratio
              )

              applyBackgroundColor(backgroundColor)
              applyForegroundColor(foregroundColor)

              val statusBarColor = ColorUtils.blendARGB(
                leftTheme.statusBarColor,
                rightTheme.statusBarColor,
                ratio
              )

              activity?.applyStatusBarColor(statusBarColor)
            }
          }
        }
      }

      override fun onScrollStateChanged(state: Int) {
        mScrollState = state

        if (state == RecyclerView.SCROLL_STATE_IDLE) {
          activity?.let {
            val controller = MediaControllerCompat.getMediaController(it)
            // check if item position is different from the now playing position
            val queueId = controller.playbackState.activeQueueItemId
            val pagerPosition = mSnapHelper.findSnapPosition(rvQueue.layoutManager)
            val itemId = mQueueAdapter.getItemId(pagerPosition)

            if (queueId != itemId) {
              controller.transportControls.skipToQueueItem(itemId)
            }
          }
        }
      }
    })
  }

  private fun setupTransportControls(controller: MediaControllerCompat) {
    // Enable controls
    sbSongProgress.isEnabled = true
    btnPrevious.isEnabled = true
    btnPlayPause.isEnabled = true
    btnNext.isEnabled = true

    // Update song metadata
    controller.metadata?.let {
      updateSongData(it)
    }

    // Update music controls
    controller.playbackState?.let {
      updateControls(it)
    }

    // Setup listeners

    btnPrevious.setOnClickListener {
      if (sbSongProgress.progress > 5) {
        // restart the song
        controller.transportControls?.seekTo(0)
      } else {
        //mediaControllerCompat.transportControls?.skipToPrevious()
        val currentPosition = mSnapHelper.findSnapPosition(rvQueue.layoutManager)
        rvQueue.smoothScrollToPosition(currentPosition - 1)
      }
    }
    btnPrevious.setOnTouchListener(
      OnRepeatTouchListener(FAST_FORWARD_INTERVAL, FAST_FORWARD_INTERVAL,
        View.OnClickListener {
          MediaControllerCompat.getMediaController(requireActivity())?.run {
            transportControls.rewind()
            tvSeekProgress.text = playbackState.position.millisecondsToTimeStamp()
          }
        },
        View.OnClickListener {
          tvSeekProgress.visibility = View.VISIBLE
        },
        View.OnClickListener {
          tvSeekProgress.visibility = View.GONE
        }
      )
    )

    btnNext.setOnClickListener {
      //mediaControllerCompat.transportControls?.skipToNext()
      val currentPosition = mSnapHelper.findSnapPosition(rvQueue.layoutManager)
      rvQueue.smoothScrollToPosition(currentPosition + 1)
    }
    btnNext.setOnTouchListener(
      OnRepeatTouchListener(FAST_FORWARD_INTERVAL, FAST_FORWARD_INTERVAL,
        View.OnClickListener {
          MediaControllerCompat.getMediaController(requireActivity())?.run {
            transportControls.fastForward()
            tvSeekProgress.text = playbackState.position.millisecondsToTimeStamp()
          }
        },
        View.OnClickListener {
          tvSeekProgress.visibility = View.VISIBLE
        },
        View.OnClickListener {
          tvSeekProgress.visibility = View.GONE
        }
      )
    )

    btnPlayPause.setOnClickListener {
      when (controller.playbackState.state) {
        PlaybackStateCompat.STATE_PLAYING -> {
          // Pause playback, stop updater
          controller.transportControls.pause()
          controller.transportControls.startProgressUpdater()
        }
        PlaybackStateCompat.STATE_PAUSED,
        PlaybackStateCompat.STATE_STOPPED -> {
          // Start playback, start updater
          controller.transportControls.play()
          controller.transportControls.stopProgressUpdater()
        }
      }
    }

    btnShuffle.setOnClickListener {
      when (controller.shuffleMode) {
        PlaybackStateCompat.SHUFFLE_MODE_NONE -> {
          controller.transportControls.setShuffleMode(
            PlaybackStateCompat.SHUFFLE_MODE_ALL
          )
          Toast.makeText(context, R.string.player_shuffleEnabled, Toast.LENGTH_SHORT).show()
        }
        else -> {
          controller.transportControls.setShuffleMode(
            PlaybackStateCompat.SHUFFLE_MODE_NONE
          )
          Toast.makeText(context, R.string.player_shuffleDisabled, Toast.LENGTH_SHORT).show()
        }
      }
    }

    btnRepeat.setOnClickListener {
      when (controller.repeatMode) {
        PlaybackStateCompat.REPEAT_MODE_NONE -> {
          controller.transportControls.setRepeatMode(
            PlaybackStateCompat.REPEAT_MODE_ONE
          )
          Toast.makeText(context, R.string.player_repeatOne, Toast.LENGTH_SHORT).show()
        }
        PlaybackStateCompat.REPEAT_MODE_ONE -> {
          controller.transportControls.setRepeatMode(
            PlaybackStateCompat.REPEAT_MODE_ALL
          )
          Toast.makeText(context, R.string.player_repeatAll, Toast.LENGTH_SHORT).show()
        }
        else -> {
          controller.transportControls.setRepeatMode(
            PlaybackStateCompat.REPEAT_MODE_NONE
          )
          Toast.makeText(context, R.string.player_repeatDisabled, Toast.LENGTH_SHORT).show()
        }
      }
    }

    sbSongProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

      private var mProgress = 0

      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
          mProgress = progress
          tvSeekProgress.text = progress.secondsToTimeStamp()
          tvSeekProgress.visibility = View.VISIBLE
        }
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) {
        mUserIsSeeking = true
      }

      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        tvSeekProgress.visibility = View.GONE
        controller.transportControls.seekTo((mProgress * 1000).toLong())
        mUserIsSeeking = false
      }
    })
  }

  @SuppressLint("SetTextI18n")
  private fun updateControls(playbackState: PlaybackStateCompat) {
    // Update progress
    val elapsedTime = playbackState.position / 1000
    val remainingTime = sbSongProgress.max - elapsedTime

    tvElapsedTime.text = elapsedTime.toInt().secondsToTimeStamp()
    tvRemainingTime.text = "-${remainingTime.toInt().secondsToTimeStamp()}"

    if (!mUserIsSeeking) {
      sbSongProgress.progress = elapsedTime.toInt()
    }

    btnPrevious.isEnabled = playbackState.isSkipToPreviousEnabled
    btnPrevious.alpha = if (playbackState.isSkipToPreviousEnabled) 1f else Theme.DISABLED_ALPHA

    btnNext.isEnabled = playbackState.isSkipToNextEnabled
    btnNext.alpha = if (playbackState.isSkipToNextEnabled) 1f else Theme.DISABLED_ALPHA

    MediaControllerCompat.getMediaController(requireActivity())?.let { controller ->
      when {
        playbackState.isPaused -> {
          controller.transportControls.stopProgressUpdater()
          btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
        playbackState.isPlaying -> {
          controller.transportControls.startProgressUpdater()
          btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }
      }

      when (controller.shuffleMode) {
        PlaybackStateCompat.SHUFFLE_MODE_NONE -> btnShuffle.alpha = Theme.DISABLED_ALPHA
        PlaybackStateCompat.SHUFFLE_MODE_ALL -> btnShuffle.alpha = 1f
      }

      when (controller.repeatMode) {
        PlaybackStateCompat.REPEAT_MODE_NONE -> btnRepeat.setImageDrawable(mRepeatNone)
        PlaybackStateCompat.REPEAT_MODE_ONE -> btnRepeat.setImageDrawable(mRepeatOne)
        PlaybackStateCompat.REPEAT_MODE_ALL -> btnRepeat.setImageDrawable(mRepeatAll)
      }
    }
  }

  private fun updateSongData(metadata: MediaMetadataCompat) {
    ViewCompat.setTransitionName(imgCoverArt, "coverArt" + metadata.id)

    GlideApp.with(this)
      .load(metadata.albumArtUri)
      .listener(mGlideListener)
      .into(imgCoverArt)

    sbSongProgress.max = (metadata.duration / 1000).toInt()
  }

  private fun updatePager() {
    // Scroll pager to current item
//    val visiblePosition = if (mQueueAdapter.realItemCount == 0) -1
//    else mSnapHelper.findSnapPosition(rvQueue.layoutManager) % mQueueAdapter.realItemCount
    val visiblePosition = mSnapHelper.findSnapPosition(rvQueue.layoutManager)

    // Skip if Pager is not ready yet
    if (visiblePosition < 0) {
      Log.d(LOG_TAG, "updatePager(visiblePosition=$visiblePosition)")
      return
    }

    val visibleId = mQueueAdapter.getItemId(visiblePosition)

    // If Metadata has changed, then PlaybackState should have changed as well.
    val queueId = MediaControllerCompat.getMediaController(requireActivity()).playbackState.activeQueueItemId
    val queuePosition = mQueueAdapter.getItemPositionById(queueId/*, visiblePosition*/)

    Log.d(
      LOG_TAG,
      "updatePager(" +
          "visiblePosition=$visiblePosition, " +
          "visibleId=$visibleId, " +
          "queuePosition=$queuePosition, " +
          "queueId=$queueId) " +
          "ScrollState=$mScrollState"
    )

    if (mScrollState == RecyclerView.SCROLL_STATE_IDLE) {
      if (queuePosition > RecyclerView.NO_POSITION && visibleId != queueId) {
        if (abs(queuePosition - visiblePosition) > 1) {
          rvQueue.scrollToPosition(queuePosition)
        } else {
          rvQueue.smoothScrollToPosition(queuePosition)
        }
        // Make view visible
        ViewCompat.animate(rvQueue)
          .alpha(1f)
          .setDuration(context?.resources?.getInteger(R.integer.preferredAnimationDuration)?.toLong() ?: 300L)
          .withEndAction { imgCoverArt?.visibility = View.INVISIBLE }
          .start()
      } else if (queuePosition == RecyclerView.NO_POSITION) {
        // Try again after the adapter settles?
        Log.v(LOG_TAG, "updatePager() DELAY CHANGE")
        rvQueue.postDelayed(300) { updatePager() }
      } else {
        // Make view visible
        ViewCompat.animate(rvQueue)
          .alpha(1f)
          .setDuration(context?.resources?.getInteger(R.integer.preferredAnimationDuration)?.toLong() ?: 300L)
          .withEndAction { imgCoverArt?.visibility = View.INVISIBLE }
          .start()
      }
    }
  }

  private fun applyThemeStatic(theme: Theme) {
    Log.i(LOG_TAG, "applyThemeStatic($theme)")

    applyBackgroundColor(theme.primaryBackgroundColor)
    applyForegroundColor(theme.primaryForegroundColor)
  }

  private fun applyThemeAnimated(theme: Theme) {
    Log.i(LOG_TAG, "applyingThemeAnimated($theme)")

    val previousTheme = ThemeManager.getInstance(requireContext()).previousTheme
    val animationDuration = context?.resources?.getInteger(R.integer.preferredAnimationDuration)?.toLong() ?: 300L

    // StatusBar Color
    ValueAnimator.ofArgb(
      previousTheme?.statusBarColor ?: ContextCompat.getColor(requireContext(), R.color.backgroundColor),
      theme.statusBarColor
    ).run {
      duration = animationDuration
      addUpdateListener {
        val color = it.animatedValue as Int

        activity?.applyStatusBarColor(color)
      }
      start()
    }

    // Background Color
    ValueAnimator.ofArgb(
      previousTheme?.primaryBackgroundColor ?: ContextCompat.getColor(requireContext(), R.color.backgroundColor),
      theme.primaryBackgroundColor
    ).run {
      duration = animationDuration
      addUpdateListener {
        val color = it.animatedValue as Int
        applyBackgroundColor(color)
      }
      start()
    }

    // Foreground Color
    ValueAnimator.ofArgb(
      previousTheme?.primaryForegroundColor ?: Color.WHITE,
      theme.primaryForegroundColor
    ).run {
      duration = animationDuration
      addUpdateListener {
        val color = it.animatedValue as Int

        applyForegroundColor(color)
      }
      start()
    }
  }

  private fun applyForegroundColor(color: Int) {
    // Toolbar
    playerToolbar.applyForegroundColor(color)

    // Texts
    tvElapsedTime.setTextColor(color)
    tvRemainingTime.setTextColor(color)
    tvSeekProgress.setTextColor(color)

    // SeekBar
    sbSongProgress.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    sbSongProgress.thumb.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)

    // Media Buttons Background
    mPreviousBackground?.setTint(color)
    mPlayPauseBackground?.setTint(color)
    mNextBackground?.setTint(color)

    // Additional Buttons
    btnShuffle.drawable.setTint(color)

    mRepeatNone?.setTint(color)
    mRepeatOne?.setTint(color)
    mRepeatAll?.setTint(color)
  }

  private fun applyBackgroundColor(color: Int) {
    // Window background
    content_player.setBackgroundColor(color)

    // Navigation Bar
    activity?.window?.navigationBarColor = color
    activity?.applyNavigationBarColor(color)

    // Seek Progress background
    tvSeekProgress.setBackgroundColor(ColorUtils.setAlphaComponent(color, Theme.DISABLED_OPACITY))

    // Media Buttons Icon
    btnPrevious.setColorFilter(color)
    btnPlayPause.setColorFilter(color)
    btnNext.setColorFilter(color)

    // Media Buttons Ripple (need to use separate drawables)
    val rippleColor = ColorUtils.setAlphaComponent(color, Theme.DISABLED_OPACITY)
    btnPrevious.background = Theme.getRippleDrawable(rippleColor, mPreviousBackground)
    btnPlayPause.background = Theme.getRippleDrawable(rippleColor, mPlayPauseBackground)
    btnNext.background = Theme.getRippleDrawable(rippleColor, mNextBackground)
  }

  companion object {
    private const val LOG_TAG = "PlayerFragment"

    private const val FAST_FORWARD_INTERVAL = 500
  }
}
