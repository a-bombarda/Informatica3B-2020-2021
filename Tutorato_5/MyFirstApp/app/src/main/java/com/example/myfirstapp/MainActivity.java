package com.example.myfirstapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    // Definiamo un identificativo per il messaggio
    public static final String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void sendMessage(View view) {
        // Definiamo un intent per la comunicazione delle due activities
        Intent intent = new Intent(this, DisplayMessageActivity.class);
        // Recupero il componente utilizzando la notazione R.id.[NomeComponente]
        EditText textField = (EditText) findViewById(R.id.editText);
        // Dal componente prendo il testo inserito
        String insertedMessage = textField.getText().toString();
        // Aggiungiamo all'intent il messaggio
        intent.putExtra(EXTRA_MESSAGE, insertedMessage);
        // Avvio l'activity relativa all'intent
        startActivity(intent);
    }
}
