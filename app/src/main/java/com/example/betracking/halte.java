package com.example.betracking;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class halte extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_halte);

        Button btnPassenger = findViewById(R.id.btnPassenger);
        Button btnDriver = findViewById(R.id.btnDriver);

// PASSENGER
        btnPassenger.setOnClickListener(v -> {

            showToast("Menuju Halaman Passenger");

            Intent intent = new Intent(halte.this, MapsActivity2.class);
            intent.putExtra("role", "Passenger");
            startActivity(intent);
        });

// DRIVER
        btnDriver.setOnClickListener(v -> {

            showToast("Menuju Halaman Driver");

            Intent intent = new Intent(halte.this, driver12.class);
            intent.putExtra("role", "Driver");
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
