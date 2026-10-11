package com.rustdesk.standalone

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rustdesk.standalone.databinding.ActivityMainBinding
import com.rustdesk.standalone.protocol.NetworkUtils
import com.rustdesk.standalone.server.ScreenCaptureService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val localPort = 8443
    private val localPin = "123456"
    private var currentIp = "127.0.0.1"

    private val serviceStateListener: (Boolean) -> Unit = { running ->
        runOnUiThread {
            updateServerUi(running)
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            ScreenCaptureService.startService(
                this,
                result.resultCode,
                result.data!!,
                localPort,
                localPin
            )
            Toast.makeText(this, "بدأ بث RustDesk بنجاح (Port: $localPort)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "تم رفض إذن مشاركة الشاشة", Toast.LENGTH_SHORT).show()
            updateServerUi(false)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDeviceInfo()
        setupListeners()
        requestRequiredPermissions()

        ScreenCaptureService.addRunningStateListener(serviceStateListener)
    }

    override fun onResume() {
        super.onResume()
        updateServerUi(ScreenCaptureService.isRunning)
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenCaptureService.removeRunningStateListener(serviceStateListener)
    }

    private fun setupDeviceInfo() {
        currentIp = NetworkUtils.getLocalIpAddress(this)
        val formattedAddress = "$currentIp:$localPort"
        binding.tvLocalIp.text = formattedAddress
        binding.tvLocalPin.text = "رمز المرور (PIN): $localPin"
        binding.tvLocalPort.text = "المنفذ: $localPort"
    }

    private fun setupListeners() {
        // Copy Address Listener with tactile feedback and animation
        val copyAction = {
            val address = "$currentIp:$localPort"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("RustDesk Address", address)
            clipboard.setPrimaryClip(clip)
            binding.btnCopyAddress.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            binding.btnCopyAddress.animate()
                .scaleX(0.85f).scaleY(0.85f).setDuration(100)
                .withEndAction {
                    binding.btnCopyAddress.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }.start()
            Toast.makeText(this, "تم نسخ العنوان: $address", Toast.LENGTH_SHORT).show()
        }

        binding.btnCopyAddress.setOnClickListener { copyAction() }
        binding.cardAddressBox.setOnClickListener { copyAction() }

        // Host Button with immediate state toggle & tactile feedback
        binding.btnToggleServer.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            if (ScreenCaptureService.isRunning) {
                ScreenCaptureService.stopService(this)
                Toast.makeText(this, "تم إيقاف مشاركة الشاشة", Toast.LENGTH_SHORT).show()
            } else {
                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
            }
        }

        // Viewer Button (Connect) with debounce protection
        var lastConnectClickTime = 0L
        binding.btnConnectDirect.setOnClickListener {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastConnectClickTime < 1000L) return@setOnClickListener
            lastConnectClickTime = now
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)

            val targetIpInput = binding.etTargetIp.text.toString().trim()
            val targetPortStr = binding.etTargetPort.text.toString().trim()
            val targetPin = binding.etTargetPin.text.toString().trim()

            if (targetIpInput.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال عنوان الجهاز الهدف (IP Address)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val targetHost: String
            val targetPort: Int

            if (targetIpInput.contains(":")) {
                val parts = targetIpInput.split(":")
                targetHost = parts[0]
                targetPort = parts.getOrNull(1)?.toIntOrNull() ?: 8443
            } else {
                targetHost = targetIpInput
                targetPort = targetPortStr.toIntOrNull() ?: 8443
            }

            RemoteViewActivity.start(this, targetHost, targetPort, targetPin)
        }

        // Recent Chips with tactile feedback
        binding.chipLocalhost.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            binding.etTargetIp.setText("127.0.0.1")
            binding.etTargetPort.setText("8443")
        }

        binding.chipLan.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            binding.etTargetIp.setText("192.168.1.100")
            binding.etTargetPort.setText("8443")
        }
    }

    private fun updateServerUi(running: Boolean) {
        if (running) {
            binding.tvServerStatus.text = "مشارك (Live)"
            binding.tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.tvHeaderStatusText.text = "مشارك"

            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_green)
            )

            binding.btnToggleServer.text = "إيقاف مشاركة هذا الجهاز"
            binding.btnToggleServer.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_red)
            )
        } else {
            binding.tvServerStatus.text = "متوقف"
            binding.tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            binding.tvHeaderStatusText.text = "جاهز"

            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_amber)
            )

            binding.btnToggleServer.text = "بدء مشاركة هذا الجهاز"
            binding.btnToggleServer.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.rustdesk_blue)
            )
        }
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
