package app.zhanzhuang.timer.wear.health

import kotlinx.coroutines.flow.filter

/** Prevents every Health Services entry point when runtime heart-rate permission is absent. */
class PermissionAwareWearHealthClient(
    private val delegate: WearHealthClient,
    private val permissionGranted: () -> Boolean,
) : WearHealthClient {
    override val updates = delegate.updates.filter { permissionGranted() }

    override suspend fun start(): WearHealthStartResult =
        if (permissionGranted()) delegate.start() else denied()

    override suspend fun reattach(): WearHealthStartResult =
        if (permissionGranted()) delegate.reattach() else denied()

    override suspend fun pause() {
        if (permissionGranted()) delegate.pause()
    }

    override suspend fun resume() {
        if (permissionGranted()) delegate.resume()
    }

    override suspend fun end() {
        if (permissionGranted()) delegate.end()
    }

    private fun denied(): WearHealthStartResult =
        WearHealthStartResult.DurationOnly(DurationOnlyReason.PERMISSION_DENIED)
}
