package com.example.project;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;

public class BitmapUtil {
    public static Bitmap loadBitmapFromFile(File file) {
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }
}