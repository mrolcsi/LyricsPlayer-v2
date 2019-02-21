package hu.mrolcsi.android.lyricsplayer.extensions

private const val TIME_FORMAT_SHORT = "%02d:%02d"
private const val TIME_FORMAT_LONG = "%02d:%02d:%02d"

fun Int.secondsToTimeStamp(): String {
  var remaining = this

  val hours = remaining / 60 / 60
  remaining -= hours * 60 * 60

  val minutes = remaining / 60
  remaining -= minutes * 60

  val seconds = remaining

  return if (hours > 0) {
    // Use long format
    String.format(TIME_FORMAT_LONG, hours, minutes, seconds)
  } else {
    // Use short format
    String.format(TIME_FORMAT_SHORT, minutes, seconds)
  }
}

fun Long.millisecondsToTimeStamp(): String {
  var remaining = this

  val hours = remaining / 1000 / 60 / 60
  remaining -= hours * 60 * 60 * 1000

  val minutes = remaining / 1000 / 60
  remaining -= minutes * 60 * 1000

  val seconds = remaining / 1000
  remaining -= seconds * 1000

  if (remaining > Math.abs(0)) {
    throw ArithmeticException("remaining should be 0!")
  }

  return if (hours > 0) {
    // Use long format
    String.format(TIME_FORMAT_LONG, hours, minutes, seconds)
  } else {
    // Use short format
    String.format(TIME_FORMAT_SHORT, minutes, seconds)
  }
}
