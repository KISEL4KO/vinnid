package com.example.project;

import androidx.camera.core.ImageCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import java.util.concurrent.ExecutorService;

public class CameraXHelper implements LifecycleObserver {
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void startCamera() {
        // Инициализация и запуск камеры
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void releaseResources() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
