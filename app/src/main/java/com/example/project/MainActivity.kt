package com.example.project

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.project.databinding.ActivityMainBinding
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var backend: BackendHelper

    // Регистрация для выбора изображения из галереи
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                processImageFromUri(imageUri)
            } else {
                Toast.makeText(this, "Не удалось выбрать изображение", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация бэкенда
        backend = BackendHelper(this)
        if (supportActionBar != null) {
            supportActionBar!!.hide()
        }

        // Запуск камеры
        backend.startCamera(binding.previewView, CameraSelector.LENS_FACING_BACK)

        // Обработка тапов для фокуса
        binding.previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                backend.tapToFocus(event.x, event.y)
            }
            true
        }
    }

    // Сделать фото
    fun takePhoto(view: View) {
        backend.takePhoto { photoFile ->
            runOnUiThread {
                backend.processImage(photoFile) { result ->
                    runOnUiThread {
                        Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        backend.checkLightingCondition()
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
                runOnUiThread {
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
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
        backend.releaseResources() // Освобождение ресурсов бэкенда
    }
}