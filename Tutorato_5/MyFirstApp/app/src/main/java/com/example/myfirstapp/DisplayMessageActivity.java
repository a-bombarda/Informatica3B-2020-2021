package com.example.myfirstapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class DisplayMessageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_message);

        // Recupero l'intent che avvia l'activity ed estraggo la stringa
        Intent intent = getIntent();
        String message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);

        // Modifico la visualizzazione, mettendo nella textView il testo ricevuto nell'intent
        TextView textView = findViewById(R.id.textView);
        textView.setText(message);
    }
}
