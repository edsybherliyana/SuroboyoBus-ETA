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

public class MainActivity extends AppCompatActivity {

    EditText username, password;
    Button loginButton;
    TextView signupText;

    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        loginButton = findViewById(R.id.loginButton);
        signupText = findViewById(R.id.signupText);

        mAuth = FirebaseAuth.getInstance();

        // LOGIN
        loginButton.setOnClickListener(v -> {

            String user = username.getText().toString().trim();
            String pass = password.getText().toString().trim();

            if (user.isEmpty() || pass.isEmpty()) {
                showToast("Email dan Password wajib diisi");
                return;
            }

            mAuth.signInWithEmailAndPassword(user, pass)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            showToast("Login Berhasil");

                            Intent intent = new Intent(MainActivity.this, halte.class);
                            startActivity(intent);
                            finish();

                        } else {

                            showToast(task.getException().getMessage());
                        }

                    });

        });

        signupText.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, Registrasi.class));
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