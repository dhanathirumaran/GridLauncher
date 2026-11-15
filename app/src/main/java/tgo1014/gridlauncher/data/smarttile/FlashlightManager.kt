package tgo1014.gridlauncher.data.smarttile

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlashlightManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn = _isFlashlightOn.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable = _isAvailable.asStateFlow()

    private var cameraId: String? = null

    init {
        runCatching {
            cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            _isAvailable.value = cameraId != null

            cameraManager?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(camId: String, enabled: Boolean) {
                    if (camId == cameraId) {
                        _isFlashlightOn.value = enabled
                        _isAvailable.value = true
                    }
                }

                override fun onTorchModeUnavailable(camId: String) {
                    if (camId == cameraId) {
                        _isFlashlightOn.value = false
                        _isAvailable.value = false
                    }
                }
            }, null)
        }
    }

    fun toggle() {
        val id = cameraId ?: return
        val targetState = !_isFlashlightOn.value
        try {
            cameraManager?.setTorchMode(id, targetState)
            // State is updated strictly by the hardware TorchCallback upon confirmation
        } catch (e: CameraAccessException) {
            _isFlashlightOn.value = false
            _isAvailable.value = false
        } catch (e: Exception) {
            _isFlashlightOn.value = false
        }
    }
}
