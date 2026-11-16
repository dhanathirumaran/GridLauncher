package tgo1014.gridlauncher.data.smarttile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.services.AppNotificationListenerService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private var instance: MediaPlaybackManager? = null

        fun sync() {
            instance?.syncActiveSessions()
        }

        fun playPause() {
            instance?.togglePlayPause()
        }

        fun skipToNext() {
            instance?.next()
        }

        fun skipToPrevious() {
            instance?.previous()
        }

        fun openPlayerApp(ctx: Context) {
            instance?.launchPlayer(ctx)
        }
    }

    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val componentName = ComponentName(context, AppNotificationListenerService::class.java)

    private val _mediaState = MutableStateFlow(MediaPlaybackState())
    val mediaState = _mediaState.asStateFlow()

    private var activeController: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pauseTimeoutJob: Job? = null
    private var pausedAtElapsedMs: Long = 0L

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            mainHandler.post { updateFromController(activeController) }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            mainHandler.post { updateFromController(activeController) }
        }

        override fun onSessionDestroyed() {
            mainHandler.post { syncActiveSessions() }
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        mainHandler.post { onControllersUpdated(controllers) }
    }

    init {
        instance = this
        registerListener()
        syncActiveSessions()
    }

    private var isListenerRegistered = false

    fun registerListener() {
        if (isListenerRegistered) return
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            isListenerRegistered = true
        } catch (_: Exception) {
            // Guard against SecurityException if notification listener access is not yet granted
            isListenerRegistered = false
        }
    }

    fun syncActiveSessions() {
        if (!isListenerRegistered) {
            registerListener()
        }
        scope.launch(Dispatchers.Main) {
            try {
                val controllers = mediaSessionManager?.getActiveSessions(componentName)
                onControllersUpdated(controllers)
            } catch (_: Exception) {
                _mediaState.value = MediaPlaybackState(isActive = false)
            }
        }
    }

    private fun onControllersUpdated(controllers: List<MediaController>?) {
        val candidate = controllers?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers?.firstOrNull {
                val s = it.playbackState?.state
                s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING
            }

        if (activeController != candidate) {
            try {
                activeController?.unregisterCallback(controllerCallback)
            } catch (_: Exception) {}
            activeController = candidate
            try {
                activeController?.registerCallback(controllerCallback)
            } catch (_: Exception) {}
        }

        updateFromController(activeController)
    }

    private fun updateFromController(controller: MediaController?) {
        if (controller == null) {
            pauseTimeoutJob?.cancel()
            pausedAtElapsedMs = 0L
            _mediaState.value = MediaPlaybackState(isActive = false)
            return
        }

        val playbackState = controller.playbackState
        val metadata = controller.metadata

        val stateCode = playbackState?.state ?: PlaybackState.STATE_NONE
        val isPlaying = stateCode == PlaybackState.STATE_PLAYING

        // PAUSE TIMEOUT & CLUTTER MITIGATION:
        // Android media players (e.g. Spotify, YouTube Music, Podcasts) hold their MediaSession in
        // STATE_PAUSED indefinitely in the background. Treating STATE_PAUSED as active forever
        // causes an intrusive 8x2 media tile to permanently occupy the user's home screen.
        // We provide a 3-minute grace period when paused so users can quickly resume;
        // after 3 minutes of inactivity, the tile automatically collapses.
        if (isPlaying || stateCode == PlaybackState.STATE_BUFFERING) {
            pauseTimeoutJob?.cancel()
            pausedAtElapsedMs = 0L
        } else if (stateCode == PlaybackState.STATE_PAUSED) {
            if (pausedAtElapsedMs == 0L) {
                pausedAtElapsedMs = SystemClock.elapsedRealtime()
            }
            val elapsedSincePause = SystemClock.elapsedRealtime() - pausedAtElapsedMs
            if (elapsedSincePause > 3 * 60 * 1000L) {
                _mediaState.value = MediaPlaybackState(isActive = false)
                return
            }
            if (pauseTimeoutJob?.isActive != true) {
                val remainingGraceMs = (3 * 60 * 1000L - elapsedSincePause).coerceAtLeast(0L)
                pauseTimeoutJob = scope.launch(Dispatchers.Main) {
                    delay(remainingGraceMs)
                    if (activeController?.playbackState?.state != PlaybackState.STATE_PLAYING) {
                        _mediaState.value = MediaPlaybackState(isActive = false)
                    }
                }
            }
        } else {
            // STATE_STOPPED, STATE_NONE, STATE_ERROR, etc.
            pauseTimeoutJob?.cancel()
            pausedAtElapsedMs = 0L
            _mediaState.value = MediaPlaybackState(isActive = false)
            return
        }

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.description?.title?.toString()
            ?: "Unknown Title"

        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.description?.subtitle?.toString()
            ?: ""

        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""

        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.description?.iconBitmap

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L
        val updateTime = playbackState?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime()

        _mediaState.value = MediaPlaybackState(
            isActive = true,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            album = album,
            albumArt = albumArt,
            durationMs = duration,
            positionMs = position,
            lastPositionUpdateTimeMs = updateTime,
            packageName = controller.packageName.orEmpty()
        )
    }

    fun togglePlayPause() {
        val controller = activeController ?: return
        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        try {
            if (isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } catch (_: Exception) {}
    }

    fun next() {
        try {
            activeController?.transportControls?.skipToNext()
        } catch (_: Exception) {}
    }

    fun previous() {
        try {
            activeController?.transportControls?.skipToPrevious()
        } catch (_: Exception) {}
    }

    fun launchPlayer(ctx: Context) {
        val controller = activeController
        val activity = controller?.sessionActivity
        if (activity != null) {
            try {
                activity.send()
                return
            } catch (_: Exception) {}
        }

        val pkg = controller?.packageName
        if (!pkg.isNullOrBlank()) {
            try {
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
            } catch (_: Exception) {}
        }
    }
}
