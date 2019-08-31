package hu.mrolcsi.muzik.extensions

import android.widget.TextView
import androidx.core.view.postDelayed

@Deprecated("use app:enableMarquee!", ReplaceWith(""))
fun TextView.startMarquee(delay: Long = 0) {
  if (delay > 0) {
    postDelayed(delay) {
      isSelected = true
    }
  } else {
    isSelected = true
  }
}