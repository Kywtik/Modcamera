package com.example.aicamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.aicamera.databinding.ActivityCameraBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var b: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var zoomIndex = 0
    private val zoomLevels = floatArrayOf(1f, 2f)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                toast("Нужен доступ к камере")
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(b.root)

        setupControls()
        updateAiLabel()
        loadThumbnail()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ProcessingQueue.errors.collect { msg ->
                        toast("Ошибка обработки: $msg")
                    }
                }
                launch {
                    ProcessingQueue.changes.collect { loadThumbnail() }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }

    private fun setupControls() {
        b.btnShutter.setOnClickListener { takePhoto() }

        b.btnFlip.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            zoomIndex = 0
            b.btnZoom.text = "1×"
            startCamera()
        }

        b.btnFlash.setOnClickListener {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
            imageCapture?.flashMode = flashMode
            b.btnFlash.text = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> "Вкл"
                ImageCapture.FLASH_MODE_AUTO -> "Авто"
                else -> "Выкл"
            }
        }

        b.btnZoom.setOnClickListener {
            zoomIndex = (zoomIndex + 1) % zoomLevels.size
            camera?.cameraControl?.setZoomRatio(zoomLevels[zoomIndex])
            b.btnZoom.text = if (zoomIndex == 0) "1×" else "2×"
        }

        b.btnAi.setOnClickListener {
            Prefs.setAutoProcess(this, !Prefs.autoProcess(this))
            updateAiLabel()
        }

        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        b.btnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        b.modeVideo.setOnClickListener { toast("Видео — скоро") }
        b.modePortrait.setOnClickListener { toast("Портрет — скоро") }
    }

    private fun updateAiLabel() {
        b.btnAi.text = if (Prefs.autoProcess(this)) "AI ✨ Вкл" else "AI выкл"
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(b.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .build()

            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, imageCapture)
            camera?.cameraControl?.setZoomRatio(zoomLevels[zoomIndex])
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val file = PhotoStore.newRawFile(this)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        flashPreview()

        capture.takePicture(options, ioExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                val app = applicationContext
                val auto = Prefs.autoProcess(app)
                if (auto) ProcessingQueue.enqueue(app, file)

                runOnUiThread {
                    showStatus(if (auto) "Снимок сохранён · AI обрабатывает…" else "Снимок сохранён")
                    loadThumbnail()
                }
            }

            override fun onError(exc: ImageCaptureException) {
                runOnUiThread { toast("Ошибка съёмки: ${exc.message}") }
            }
        })
    }

    /** Эффект затвора: короткая вспышка превью */
    private fun flashPreview() {
        b.previewView.animate().alpha(0.35f).setDuration(70).withEndAction {
            b.previewView.animate().alpha(1f).setDuration(180).start()
        }.start()
    }

    private fun showStatus(msg: String) {
        b.statusText.text = msg
        b.statusText.visibility = View.VISIBLE
        b.statusText.postDelayed({ b.statusText.visibility = View.GONE }, 2500)
    }

    private fun loadThumbnail() {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                PhotoStore.listRaw(this@CameraActivity).firstOrNull()?.let { raw ->
                    PhotoStore.decodeUpright(PhotoStore.displayFile(raw).readBytes(), 200)
                }
            }
            if (bmp != null) b.btnGallery.setImageBitmap(bmp)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
