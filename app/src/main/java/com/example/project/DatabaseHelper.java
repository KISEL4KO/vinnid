package com.example.project;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "acne_results.db";
    public static final int DATABASE_VERSION = 1;

    // Название таблиц и столбцов
    public static final String TABLE_RESULTS = "results";
    public static final String TABLE_ACNE_STAGES = "acne_stages";
    public static final String TABLE_DETECTED_OBJECTS = "detected_objects";
    public static final String TABLE_ACNE_RECOMMENDATIONS = "acne_recommendations";

    public static final String COLUMN_ID = "id";
    public static final String COLUMN_LABEL = "label";
    public static final String COLUMN_COUNT = "count";
    public static final String COLUMN_STAGE = "stage";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_RECOMMENDATION = "recommendation";

    // SQL-запросы для создания таблиц
    public static final String CREATE_TABLE_RESULTS =
            "CREATE TABLE " + TABLE_RESULTS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_LABEL + " TEXT);";

    public static final String CREATE_TABLE_ACNE_STAGES =
            "CREATE TABLE " + TABLE_ACNE_STAGES + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_STAGE + " TEXT, " +
                    COLUMN_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP);";

    public static final String CREATE_TABLE_DETECTED_OBJECTS =
            "CREATE TABLE " + TABLE_DETECTED_OBJECTS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_LABEL + " TEXT, " +
                    COLUMN_COUNT + " INTEGER, " +
                    COLUMN_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP);";

    public static final String CREATE_TABLE_ACNE_RECOMMENDATIONS =
            "CREATE TABLE " + TABLE_ACNE_RECOMMENDATIONS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_STAGE + " TEXT, " +
                    COLUMN_RECOMMENDATION + " TEXT);";

    private Context context;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Создаем таблицы
        db.execSQL(CREATE_TABLE_RESULTS);
        db.execSQL(CREATE_TABLE_ACNE_STAGES);
        db.execSQL(CREATE_TABLE_DETECTED_OBJECTS);
        db.execSQL(CREATE_TABLE_ACNE_RECOMMENDATIONS);

        // Заполняем таблицу рекомендаций данными из файлов
        loadRecommendationsFromFiles(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Удаляем старые таблицы и создаем новые
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RESULTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACNE_STAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DETECTED_OBJECTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACNE_RECOMMENDATIONS);
        onCreate(db);
    }

    // Метод для загрузки рекомендаций из файлов
    private void loadRecommendationsFromFiles(SQLiteDatabase db) {
        String[] stages = {"1-th stage", "2-th stage", "3-th stage"};
        String[] fileNames = {"stage_1.txt", "stage_2.txt", "stage_3.txt"};

        for (int i = 0; i < stages.length; i++) {
            String stage = stages[i];
            String fileName = fileNames[i];
            String recommendation = readRecommendationFromFile(fileName);

            if (recommendation != null) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_STAGE, stage);
                values.put(COLUMN_RECOMMENDATION, recommendation);
                db.insert(TABLE_ACNE_RECOMMENDATIONS, null, values);
            } else {
                Log.e("DatabaseHelper", "Не удалось загрузить рекомендации для стадии: " + stage);
            }
        }
    }

    // Метод для чтения рекомендаций из файла
    private String readRecommendationFromFile(String fileName) {
        StringBuilder recommendation = new StringBuilder();

        try (InputStream inputStream = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                recommendation.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e("DatabaseHelper", "Ошибка при чтении файла: " + fileName, e);
            return null;
        }

        return recommendation.toString().trim();
    }

    // Метод для вставки результата (только label)
    public void insertResult(String label) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LABEL, label); // Вставляем только название класса
        db.insert(TABLE_RESULTS, null, values);
        db.close();
    }

    // Метод для вставки количества обнаруженных объектов
    public void insertDetectedObject(String label, int count) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LABEL, label); // Название класса
        values.put(COLUMN_COUNT, count); // Количество объектов
        db.insert(TABLE_DETECTED_OBJECTS, null, values);
        db.close();
    }

    // Метод для вставки стадии акне
    public void insertAcneStage(String stage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STAGE, stage); // Стадия акне
        db.insert(TABLE_ACNE_STAGES, null, values);
        db.close();
    }

    // Метод для получения всех результатов из таблицы results
    public Cursor getAllResults() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_RESULTS, // Название таблицы
                null,         // Столбцы (null означает все столбцы)
                null,         // Условие WHERE (null означает без условия)
                null,         // Аргументы для условия WHERE
                null,         // GROUP BY
                null,         // HAVING
                COLUMN_ID + " DESC" // Сортировка по ID в обратном порядке (новые записи сверху)
        );
    }

    // Метод для получения всех обнаруженных объектов из таблицы detected_objects
    public Cursor getAllDetectedObjects() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_DETECTED_OBJECTS, // Название таблицы
                null,                  // Столбцы (null означает все столбцы)
                null,                  // Условие WHERE (null означает без условия)
                null,                  // Аргументы для условия WHERE
                null,                  // GROUP BY
                null,                  // HAVING
                COLUMN_ID + " DESC"    // Сортировка по ID в обратном порядке (новые записи сверху)
        );
    }

    // Метод для получения последней стадии акне
    public String getLatestAcneStage() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + COLUMN_STAGE + " FROM " + TABLE_ACNE_STAGES +
                " ORDER BY " + COLUMN_ID + " DESC LIMIT 1";
        Cursor cursor = db.rawQuery(query, null);

        String latestStage = "clear"; // Значение по умолчанию, если записей нет
        if (cursor.moveToFirst()) {
            latestStage = cursor.getString(cursor.getColumnIndex(COLUMN_STAGE));
        }

        cursor.close();
        db.close();
        return latestStage;
    }

    // Метод для получения последней даты стадии акне
    public String getLatestAcneDate() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + COLUMN_TIMESTAMP + " FROM " + TABLE_ACNE_STAGES +
                " ORDER BY " + COLUMN_ID + " DESC LIMIT 1";
        Cursor cursor = db.rawQuery(query, null);

        String latestDate = "Дата не найдена"; // Значение по умолчанию
        if (cursor.moveToFirst()) {
            latestDate = cursor.getString(cursor.getColumnIndex(COLUMN_TIMESTAMP));
        }

        cursor.close();
        db.close();
        return latestDate;
    }

    // Метод для получения всех стадий акне из таблицы acne_stages
    public Cursor getAllAcneStages() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_ACNE_STAGES, // Название таблицы
                null,             // Столбцы (null означает все столбцы)
                null,             // Условие WHERE (null означает без условия)
                null,             // Аргументы для условия WHERE
                null,             // GROUP BY
                null,             // HAVING
                COLUMN_TIMESTAMP + " DESC" // Сортировка по дате (новые записи сверху)
        );
    }

    // Метод для получения истории стадий акне за определенный период
    public Cursor getAcneHistoryByDateRange(String startDate, String endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_ACNE_STAGES +
                " WHERE " + COLUMN_TIMESTAMP + " BETWEEN ? AND ?" +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
        return db.rawQuery(query, new String[]{startDate, endDate});
    }

    // Метод для удаления старых записей истории
    public void deleteOldAcneHistory(String cutoffDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_ACNE_STAGES, COLUMN_TIMESTAMP + " < ?", new String[]{cutoffDate});
        db.close();
    }

    // Метод для получения последних N записей истории
    public Cursor getLatestAcneHistory(int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_ACNE_STAGES, // Название таблицы
                null,              // Все столбцы
                null,              // Без условия WHERE
                null,              // Без аргументов для WHERE
                null,              // Без GROUP BY
                null,              // Без HAVING
                COLUMN_TIMESTAMP + " DESC", // Сортировка по дате (новые записи сверху)
                String.valueOf(limit) // Лимит записей
        );
    }

    // Метод для получения рекомендации по стадии акне
    public String getRecommendationByStage(String stage) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + COLUMN_RECOMMENDATION + " FROM " + TABLE_ACNE_RECOMMENDATIONS +
                " WHERE " + COLUMN_STAGE + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{stage});

        String recommendation = "Рекомендация не найдена"; // Значение по умолчанию
        if (cursor.moveToFirst()) {
            recommendation = cursor.getString(cursor.getColumnIndex(COLUMN_RECOMMENDATION));
        }

        cursor.close();
        db.close();
        return recommendation;
    }
}