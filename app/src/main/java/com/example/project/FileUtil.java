// FileUtil.java
package com.example.project;

import android.content.Context;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FileUtil {
    public static MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(context.getAssets().openFd(modelName).getFileDescriptor())) {
            FileChannel fileChannel = fileInputStream.getChannel();
            long startOffset = context.getAssets().openFd(modelName).getStartOffset();
            long declaredLength = context.getAssets().openFd(modelName).getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }
}