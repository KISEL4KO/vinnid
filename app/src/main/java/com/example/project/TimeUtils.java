package com.example.project;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TimeUtils {
    // Метод для преобразования времени из GMT+0 в локальное время
    public static String convertToLocalTime(String gmtTime) {
        try {
            // Формат времени, который используется в базе данных
            SimpleDateFormat gmtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            gmtFormat.setTimeZone(TimeZone.getTimeZone("GMT")); // Указываем, что время в GMT+0

            // Парсим время из строки
            Date date = gmtFormat.parse(gmtTime);

            // Формат для локального времени
            SimpleDateFormat localFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            localFormat.setTimeZone(TimeZone.getDefault()); // Устанавливаем часовой пояс устройства

            // Преобразуем время в локальный формат
            return localFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
            return gmtTime; // В случае ошибки возвращаем исходное время
        }
    }
}

