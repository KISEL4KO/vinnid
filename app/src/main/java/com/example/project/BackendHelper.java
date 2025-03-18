package com.example.project;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.TorchState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackendHelper {
    private final Context context;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private Interpreter tflite;
    private int currentCameraSelector = CameraSelector.LENS_FACING_BACK;
    private DatabaseHelper dbHelper;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;
    private PreviewView previewView;
    private SensorManager sensorManager;
    private Sensor lightSensor;

    // Константы
    private static final float CONFIDENCE_THRESHOLD = 0.3f; // Порог уверенности
    private static final String STAGE_CLEAR = "clear";
    private static final String STAGE_1 = "1-th stage";
    private static final String STAGE_2 = "2-th stage";
    private static final String STAGE_3 = "3-th stage";

    public BackendHelper(Context context) {
        this.context = context;
        cameraExecutor = Executors.newSingleThreadExecutor();
        dbHelper = new DatabaseHelper(context);
        loadTFLiteModel();
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
    }

    // Загрузка модели TensorFlow Lite
    private void loadTFLiteModel() {
        try {
            tflite = new Interpreter(FileUtil.loadModelFile(context, "best2_float32.tflite"));
        } catch (Exception e) {
            Log.e("BackendHelper", "Failed to load model", e);
        }
    }

    // Запуск камеры
    public void startCamera(PreviewView previewView, int lensFacing) {
        this.previewView = previewView;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(previewView, lensFacing);
            } catch (Exception e) {
                Log.e("BackendHelper", "Failed to start camera", e);
                Toast.makeText(context, "Failed to start camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases(PreviewView previewView, int lensFacing) {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // Привязка UseCases к жизненному циклу
        Camera camera = cameraProvider.bindToLifecycle(
                (LifecycleOwner) context,
                cameraSelector,
                preview,
                imageCapture
        );

        // Получаем CameraControl и CameraInfo
        cameraControl = camera.getCameraControl();
        cameraInfo = camera.getCameraInfo();
    }

    // Фокус по нажатию
    public void tapToFocus(float x, float y) {
        if (cameraControl == null || previewView == null) return;

        MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(
                previewView.getWidth(), previewView.getHeight()
        );

        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .build();

        cameraControl.startFocusAndMetering(action);
    }

    // Проверка освещенности
    public void checkLightingCondition() {
        if (lightSensor == null) {
            Toast.makeText(context, "Датчик освещенности не доступен", Toast.LENGTH_SHORT).show();
            return;
        }

        // Регистрация слушателя датчика освещенности
        sensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float lux = event.values[0];
                if (lux < 30) { // Порог для плохого освещения (можно настроить)
                    Toast.makeText(context, "Освещенность плохая", Toast.LENGTH_SHORT).show();
                }
                sensorManager.unregisterListener(this); // Отписываемся после получения данных
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // Можно обработать изменение точности датчика, если нужно
            }
        }, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    // Переключение камеры
    public void switchCamera(PreviewView previewView) {
        currentCameraSelector = (currentCameraSelector == CameraSelector.LENS_FACING_BACK) ?
                CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        startCamera(previewView, currentCameraSelector);
    }

    // Сделать фото
    public void takePhoto(OnPhotoCapturedCallback callback) {
        if (imageCapture == null) return;

        File photoFile = new File(context.getExternalFilesDir(null), "photo_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputFileOptions,
                cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        callback.onCaptured(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("BackendHelper", "Photo capture failed", exception);
                    }
                }
        );
    }

    // Обработка изображения
    public void processImage(File photoFile, OnResultCallback callback) {
        Bitmap bitmap = BitmapUtil.loadBitmapFromFile(photoFile);
        if (bitmap != null) {
            processAndDisplayImage(bitmap);
        }
    }

    public void processImage(Bitmap bitmap, OnResultCallback callback) {
        processAndDisplayImage(bitmap);
    }

    private void processAndDisplayImage(Bitmap bitmap) {
        try {
            ImageProcessor imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(0f, 255f))
                    .build();

            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(bitmap);
            tensorImage = imageProcessor.process(tensorImage);

            float[][][] outputBuffer = runInference(tensorImage);
            String results = processResults(outputBuffer, dbHelper);

            Toast.makeText(context, results, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("MainActivity", "Error processing image", e);
            Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }

    private float[][][] runInference(TensorImage tensorImage) {
        if (tflite == null) {
            Log.e("MainActivity", "TensorFlow Lite interpreter is not initialized");
            Toast.makeText(context, "Model not loaded", Toast.LENGTH_SHORT).show();
            return new float[0][0][0];
        }

        Tensor outputTensor = tflite.getOutputTensor(0);
        int[] outputShape = outputTensor.shape();
        float[][][] outputBuffer = new float[outputShape[0]][outputShape[1]][outputShape[2]];

        tflite.run(tensorImage.getBuffer(), outputBuffer);
        return outputBuffer;
    }


    private String processResults(float[][][] outputBuffer, DatabaseHelper dbHelper) {
        StringBuilder results = new StringBuilder();
        String[] labelNames = {"blackheads", "dark spot", "nodules", "papules", "pustules", "whiteheads"};
        Set<String> detectedClasses = new HashSet<>();

        if (outputBuffer == null || outputBuffer.length == 0 || outputBuffer[0].length == 0) {
            return "No results to process.";
        }

        int[] classCounts = new int[labelNames.length];

        for (int i = 0; i < outputBuffer[0][0].length; i++) {
            float confidence = 0f;
            for (int j = 4; j < 10; j++) confidence = Math.max(confidence, outputBuffer[0][j][i]);
            if (confidence > CONFIDENCE_THRESHOLD) {
                int classId = -1;
                float maxClassProb = 0f;

                for (int j = 5; j < outputBuffer[0].length; j++) {
                    if (outputBuffer[0][j][i] > maxClassProb) {
                        maxClassProb = outputBuffer[0][j][i];
                        classId = j - 5;
                    }
                }

                if (classId >= 0 && classId < labelNames.length) {
                    classCounts[classId]++;
                    detectedClasses.add(labelNames[classId]);
                }
            }
        }

        for (int i = 0; i < classCounts.length; i++) {
            if (classCounts[i] > 0) {
                results.append(classCounts[i]).append(" ").append(labelNames[i]).append(", ");
            }
        }

        if (results.length() > 0) {
            results.setLength(results.length() - 2);
        }

        String acneStage = determineAcneStage(
                classCounts[0], classCounts[1], classCounts[2],
                classCounts[3], classCounts[4], classCounts[5]
        );

        dbHelper.insertAcneStage(acneStage);
        results.append("\nСтадия акне: ").append(acneStage);

        if (detectedClasses.isEmpty()) {
            results.append("\nКожа чистая, акне не обнаружено.");
        }

        for (int i = 0; i < classCounts.length; i++) {
            if (classCounts[i] > 0) {
                dbHelper.insertDetectedObject(labelNames[i], classCounts[i]);
            }
        }

        return results.toString();
    }

    private String determineAcneStage(int blackheads, int darkSpots, int nodules, int papules, int pustules, int whiteheads) {
        int inflamedElements = papules + pustules;

        if (nodules > 5) {
            return STAGE_3;
        }
        if (inflamedElements > 10 || nodules > 0) {
            return STAGE_2;
        }
        if (blackheads > 0 || whiteheads > 0 || inflamedElements > 0) {
            return STAGE_1;
        }
        return STAGE_CLEAR;
    }


    // Освобождение ресурсов
    public void releaseResources() {
        if (tflite != null) {
            tflite.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    // Интерфейсы обратного вызова
    public interface OnPhotoCapturedCallback {
        void onCaptured(File photoFile);
    }

    public interface OnResultCallback {
        void onResult(String result);
    }
}