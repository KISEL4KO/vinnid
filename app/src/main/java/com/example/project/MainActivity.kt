package com.example.project

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.project.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.CountDownLatch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var backend: BackendHelper
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var sensorEventListener: SensorEventListener? = null
    private var coroutineScope = CoroutineScope(Dispatchers.Main)
    private var takePhotoJob: Job? = null

    companion object {
        private const val MIN_LUX_THRESHOLD = 20f // Порог освещения
    }

    // Регистрация для выбора изображения из галереи
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { imageUri ->
                    processImageFromUri(imageUri)
                } ?: Toast.makeText(this, "Не удалось выбрать изображение", Toast.LENGTH_SHORT).show()
            }
        }

    // Регистрация для запроса разрешения на камеру
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Разрешение на камеру не предоставлено", Toast.LENGTH_SHORT).show()
            }
        }
    private fun applyRoundedCorners(previewView: PreviewView) {
        previewView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 50f) // 50f - радиус закругления
            }
        }
        previewView.clipToOutline = true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        backend = BackendHelper(this)
        supportActionBar?.hide()

        // Инициализация сенсора освещения
        initLightSensor()
        applyRoundedCorners(binding.previewView)
        // Проверка и запрос разрешений
        checkCameraPermission()
    }

    private fun initLightSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    // Проверка разрешения на камеру
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Запуск камеры
    // Запуск камеры
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Настройка Preview
            val preview = Preview.Builder().build()
            val previewView = binding.previewView
            preview.setSurfaceProvider(previewView.surfaceProvider)

            // Настройка UseCase (например, ImageCapture или ImageAnalysis)
            val imageCapture = ImageCapture.Builder().build()

            // Выбор камеры (задняя камера по умолчанию)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Привязка UseCase к жизненному циклу
            val camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture
            )

            // Обработка касаний для Tap-to-Focus
            previewView.setOnTouchListener { _, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> return@setOnTouchListener true
                    MotionEvent.ACTION_UP -> {
                        // Получаем MeteringPointFactory из PreviewView
                        val factory = previewView.meteringPointFactory

                        // Создаем MeteringPoint из координат касания
                        val point = factory.createPoint(motionEvent.x, motionEvent.y)

                        // Создаем FocusMeteringAction
                        val action = FocusMeteringAction.Builder(point).build()

                        // Запускаем фокусировку
                        camera.cameraControl.startFocusAndMetering(action)

                        return@setOnTouchListener true
                    }
                    else -> return@setOnTouchListener false
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun takePhoto(view: View) {
        takePhotoJob = coroutineScope.launch {
            try {
                val isGoodLighting = checkLightingCondition()
                if (!isGoodLighting) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Мало освещения, невозможно сделать фото", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // Запускаем фоновую задачу для съемки фото
                backend.takePhoto { photoFile ->
                    if (photoFile == null) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Ошибка при создании фото", Toast.LENGTH_LONG).show()
                        }
                        return@takePhoto
                    }

                    // Проверяем, существует ли файл
                    if (!photoFile.exists()) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Файл изображения не найден", Toast.LENGTH_LONG).show()
                        }
                        return@takePhoto
                    }

                    // Обработка изображения в фоновом потоке
                    backend.processImage(photoFile) { result ->
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Ошибка при съемке фото: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private suspend fun checkLightingCondition(): Boolean {
        if (lightSensor == null) {
            return true // Если датчик отсутствует, считаем, что освещение нормальное
        }

        return withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
            val isGoodLighting = booleanArrayOf(true)

            sensorEventListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values[0]
                    if (lux < MIN_LUX_THRESHOLD) {
                        isGoodLighting[0] = false
                    }
                    sensorManager.unregisterListener(this)
                    latch.countDown()
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    // Можно обработать изменение точности датчика, если нужно
                }
            }

            sensorManager.registerListener(sensorEventListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)

            try {
                latch.await()
                isGoodLighting[0]
            } catch (e: InterruptedException) {
                e.printStackTrace()
                false
            }
        }
    }

    // Переключение камеры
    fun switchCamera(view: View) {
        backend.switchCamera(binding.previewView)
    }

    // Загрузка изображения из галереи
    fun loadFromGallery(view: View) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // Обработка изображения из галереи
    private fun processImageFromUri(imageUri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            backend.processImage(bitmap) { result ->
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    // Переход на экран профиля
    fun goProfile(view: View) {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
    }

    // Показать диалог с информацией
    fun checkInfo(view: View) {
        val dialog = CustomDialogFragment()
        dialog.show(supportFragmentManager, "custom")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Отменяем корутины
        takePhotoJob?.cancel()
        // Отключаем слушатель сенсора
        sensorEventListener?.let {
            sensorManager.unregisterListener(it)
        }
        backend.releaseResources()
    }
}