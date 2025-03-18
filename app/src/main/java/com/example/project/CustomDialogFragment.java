package com.example.project;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class CustomDialogFragment extends DialogFragment {
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState){
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        String dialogText = String.format(
                "1. Поместите участок кожи с акне на лице в рамку.\n" +
                        "2. Нажмите на кнопку фотозатвора, чтобы сделать фото.\n" +
                        "3. После подождите, пока не высветится плашка с результатами.\n" +
                        "4. Перейдите во вкладку \"Профиль\".\n" +
                        "5. В этой вкладке представлены последний результат,рекомендации и история.\n\n" +
                        "Также вы можете:\n" +
                        "- Менять камеру с помощью кнопки смены камеры.\n" +
                        "- Использовать фото из галереи с помощью кнопки \"Галерея\".\n\n"

        );
        return builder.setTitle("Инструкция применения приложения Vinnid").setMessage(dialogText).create();

    }

}
