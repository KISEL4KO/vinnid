package com.example.project;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;
    private static final int PICK_IMAGE_REQUEST = 102;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private Interpreter tflite;
    private DatabaseHelper dbHelper;
    private ProcessCameraProvider cameraProvider;
    private int currentCameraSelector = CameraSelector.LENS_FACING_BACK;

    private static final float CONFIDENCE_THRESHOLD = 0.3f;

    private static final String STAGE_CLEAR = "Clear";
    private static final String STAGE_1 = "1-th stage";
    private static final String STAGE_2 = "2-th stage";
    private static final String STAGE_3 = "3-th stage";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Objects.requireNonNull(getSupportActionBar()).hide();
        getLifecycle().addObserver(new CameraXHelper());

        dbHelper = new DatabaseHelper(this);
        previewView = findViewById(R.id.previewView);
        cameraExecutor = Executors.newSingleThreadExecutor();

        checkPermissionsAndInitializeCamera();
        loadTFLiteModel();
    }

    private void loadTFLiteModel() {
        try {
            tflite = new Interpreter(loadModelFile("best2_float32.tflite"));
        } catch (IOException e) {
            Log.e("MainActivity", "Failed to load model", e);
            Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkPermissionsAndInitializeCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            startCameraX();
        }
    }

    private void startCameraX() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e("MainActivity", "Failed to start camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(currentCameraSelector)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
    }
    public void switchCamera(View view) {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            currentCameraSelector = (currentCameraSelector == CameraSelector.LENS_FACING_BACK) ?
                    CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            startCameraX();
        }
    }

    public void takePhoto(View view) {
        if (imageCapture == null) return;

        File photoFile = new File(getExternalFilesDir(null), "photo_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputFileOptions,
                cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        runOnUiThread(() -> processCapturedImage(photoFile));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraX", "Ошибка при сохранении фото", exception);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Ошибка при сохранении фото", Toast.LENGTH_SHORT).show());
                    }
                }
        );
    }

    private void processCapturedImage(File photoFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
        if (bitmap == null) {
            Log.e("MainActivity", "Bitmap is null");
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            bitmap = rotateBitmap(bitmap, photoFile.getAbsolutePath());
            processAndDisplayImage(bitmap);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, String imagePath) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap;
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (IOException e) {
            Log.e("MainActivity", "Failed to read Exif data", e);
            return bitmap;
        }
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

            Toast.makeText(this, results, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("MainActivity", "Error processing image", e);
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }

    private float[][][] runInference(TensorImage tensorImage) {
        if (tflite == null) {
            Log.e("MainActivity", "TensorFlow Lite interpreter is not initialized");
            Toast.makeText(this, "Model not loaded", Toast.LENGTH_SHORT).show();
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

    private MappedByteBuffer loadModelFile(String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void loadFromGallery(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE);
        } else {
            openGallery();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                processAndDisplayImage(bitmap);
            } catch (IOException e) {
                Log.e("MainActivity", "Failed to load image", e);
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void checkInfo(View view) {
        CustomDialogFragment dialog = new CustomDialogFragment();
        dialog.show(getSupportFragmentManager(), "custom");
    }

    public void goProfile(View view) {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll(); // Освобождаем камеру
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraProvider != null) {
            startCameraX(); // Перезапускаем камеру
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown(); // Освобождаем Executor
        }
        if (tflite != null) {
            tflite.close(); // Освобождаем модель TensorFlow Lite
        }
    }
}