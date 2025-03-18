package com.example.project;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Objects;

public class ProfileActivity extends AppCompatActivity {
    private TextView acneStageTextView;
    private TextView textViewRecommendations;
    private TextView textViewHistory;
    private TextView dateTextView;
    private DatabaseHelper dbHelper;
    private ViewFlipper viewFlipper;
    private Button btnRecommendations;
    private Button btnHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Скрыть ActionBar
        Objects.requireNonNull(getSupportActionBar()).hide();

        // Инициализация DatabaseHelper
        dbHelper = new DatabaseHelper(this);

        // Инициализация TextView и других элементов
        acneStageTextView = findViewById(R.id.textAcne);
        textViewRecommendations = findViewById(R.id.text_recommendations);
        textViewHistory = findViewById(R.id.text_history);
        viewFlipper = findViewById(R.id.viewFlipper);
        btnRecommendations = findViewById(R.id.btnRecommendations);
        btnHistory = findViewById(R.id.btnHistory);

        // Обновляем текст "acne stage", рекомендации и дату
        updateAcneStageText();
        updateRecommendations();

        // По умолчанию показываем рекомендации
        showRecommendations(null);
    }

    // Метод для отображения рекомендаций
    public void showRecommendations(View view) {
        viewFlipper.setInAnimation(this, android.R.anim.slide_in_left);
        viewFlipper.setOutAnimation(this, android.R.anim.slide_out_right);
        viewFlipper.setDisplayedChild(0); // Показать первый элемент (рекомендации)
        updateRecommendations();

        // Изменение цвета кнопок
        btnRecommendations.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0EE15B"))); // Зеленый цвет
        btnHistory.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D1CBC1"))); // Серый цвет
    }

    // Метод для отображения истории
    public void showHistory(View view) {
        viewFlipper.setInAnimation(this, R.anim.slide_in_right);
        viewFlipper.setOutAnimation(this, R.anim.slide_out_left);
        viewFlipper.setDisplayedChild(1); // Показать второй элемент (история)
        updateHistory();

        // Изменение цвета кнопок
        btnRecommendations.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D1CBC1"))); // Серый цвет
        btnHistory.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0EE15B"))); // Зеленый цвет
    }

    // Метод для обновления текста "acne stage"
    private void updateAcneStageText() {
        String latestStage = dbHelper.getLatestAcneStage();
        acneStageTextView.setText("Стадия акне: " + latestStage);
    }

    // Метод для обновления рекомендаций
    private void updateRecommendations() {
        String latestStage = dbHelper.getLatestAcneStage();
        String recommendation = dbHelper.getRecommendationByStage(latestStage);

        if (recommendation != null) {
            textViewRecommendations.setText(recommendation);
        } else {
            textViewRecommendations.setText("Рекомендации не найдены");
        }
    }

    private void updateHistory() {
        StringBuilder historyBuilder = new StringBuilder();
        Cursor cursor = dbHelper.getAllAcneStages();

        if (cursor != null && cursor.moveToFirst()) {
            int stageIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_STAGE);
            int timestampIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_TIMESTAMP);

            if (stageIndex >= 0 && timestampIndex >= 0) {
                do {
                    String stage = cursor.getString(stageIndex);
                    String gmtTime = cursor.getString(timestampIndex);

                    // Преобразуем время в локальный формат
                    String localTime = TimeUtils.convertToLocalTime(gmtTime);

                    // Добавляем запись в историю
                    historyBuilder.append("Стадия: ").append(stage).append("\nДата: ").append(localTime).append("\n\n");
                } while (cursor.moveToNext());
            } else {
                // Если столбцы не найдены, выводим сообщение об ошибке
                historyBuilder.append("Ошибка: данные не найдены");
            }
        } else {
            historyBuilder.append("История не найдена");
        }

        if (cursor != null) {
            cursor.close();
        }

        // Устанавливаем текст истории
        textViewHistory.setText(historyBuilder.toString());
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем текст при возобновлении активности
        updateAcneStageText();
        updateRecommendations();
    }

    // Переход на MainActivity
    public void onHome(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}