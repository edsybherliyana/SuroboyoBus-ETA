package com.example.betracking;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class Registrasi extends AppCompatActivity {

    EditText username, password, confirmPassword;
    Button registerButton;
    TextView loginText;

    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registrasi);

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        confirmPassword = findViewById(R.id.confirmPassword);
        registerButton = findViewById(R.id.registerButton);
        loginText = findViewById(R.id.loginText);

        mAuth = FirebaseAuth.getInstance();

        registerButton.setOnClickListener(v -> {

            String user = username.getText().toString().trim();
            String pass = password.getText().toString().trim();
            String confirm = confirmPassword.getText().toString().trim();

            if (user.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
                showToast("Semua field wajib diisi");
                return;
            }

            if (!pass.equals(confirm)) {
                showToast("Password tidak sama");
                return;
            }

            mAuth.createUserWithEmailAndPassword(user, pass)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            showToast("Registrasi berhasil");

                            startActivity(new Intent(Registrasi.this, MainActivity.class));
                            finish();

                        } else {

                            showToast(task.getException().getMessage());
                        }

                    });

        });

        loginText.setOnClickListener(v -> {
            startActivity(new Intent(Registrasi.this, MainActivity.class));
            finish();
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