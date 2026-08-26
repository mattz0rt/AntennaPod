# :playback:service

The main service doing media playback.

`Media3PlaybackService` is the active implementation, built on AndroidX Media3/ExoPlayer.
`PlaybackService` is legacy and will throw an exception if started — it exists only during the transition period.

External callers should interact with the service through `PlaybackController`, which provides a
`bindToMedia3Service()` helper that connects a `MediaController` and runs a callback on it.
The `MediaController` exposes the standard Media3 `Player` interface: `seekTo(positionMs)`,
`play()`, `pause()`, `getCurrentPosition()`, `getPlaybackParameters()`, etc.
Each call to `bindToMedia3Service()` creates a short-lived connection that is released after the
callback returns.

Media-library actions on browse items must be declared with `setCommandButtonsForMediaItems()`,
granted to controllers in `onConnect()`, and advertised through `MediaMetadata.supportedCommands`.
The selected item ID is delivered to `onCustomCommand()` in `MediaConstants.EXTRA_KEY_MEDIA_ID`.

Narrated episode summaries are inserted before queued episodes only when the summary audio file
exists and the podcast's summary preference, resolved against the global default, is enabled.
