package com.example.betracking;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class driver12 extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver12);

        Button btnDriver1 = findViewById(R.id.btnpengemudi1);
        Button btnDriver2 = findViewById(R.id.btnpengemudi2);

        // DRIVER 1
        btnDriver1.setOnClickListener(v -> {

            showToast("Menuju Halaman Driver 1");

            Intent intent = new Intent(driver12.this, MapsActivity.class);
            intent.putExtra("bus_id", "bus_1");
            startActivity(intent);
        });

        // DRIVER 2
        btnDriver2.setOnClickListener(v -> {

            showToast("Menuju Halaman Driver 2");

            Intent intent = new Intent(driver12.this, MapsActivity3.class);
            intent.putExtra("bus_id", "bus_2");
            startActivity(intent);
        });
    }
    private void showToast(String message){
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast, null);

        TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }
}
