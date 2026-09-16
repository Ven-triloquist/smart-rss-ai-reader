package com.example.smartrssai;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private EditText apiKeyInput, rssUrlInput;
    private TextView summaryText;
    private Button btnFetch, btnSpeak;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiKeyInput = findViewById(R.id.apiKeyInput);
        rssUrlInput = findViewById(R.id.rssUrlInput);
        summaryText = findViewById(R.id.summaryText);
        btnFetch = findViewById(R.id.btnFetch);
        btnSpeak = findViewById(R.id.btnSpeak);

        // Initialize Android Native Text-to-Speech
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                tts.setLanguage(Locale.US);
            }
        });

        btnFetch.setOnClickListener(v -> processRssAndSummarize());

        btnSpeak.setOnClickListener(v -> {
            String text = summaryText.getText().toString();
            if (!text.isEmpty()) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    private void processRssAndSummarize() {
        String url = rssUrlInput.getText().toString();
        summaryText.setText("Fetching full articles and processing AI summary...");

        new Thread(() -> {
            try {
                // Scrape page content using JSoup
                Document doc = Jsoup.connect(url).get();
                String fullBodyText = doc.body().text();

                // Truncate to avoid context window overflow
                String sampleText = fullBodyText.length() > 2000 ? fullBodyText.substring(0, 2000) : fullBodyText;

                runOnUiThread(() -> {
                    summaryText.setText("Extracted Article Content:\n\n" + sampleText + "\n\n(Hook API call here)");
                });
            } catch (Exception e) {
                runOnUiThread(() -> summaryText.setText("Error fetching URL: " + e.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
