package com.example.smartrssai;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private EditText apiKeyInput, rssUrlInput;
    private Spinner languageSpinner;
    private TextView statusText, summaryText;
    private Button btnLoadFeed, btnSummarize, btnSpeak;
    private TextToSpeech tts;

    private final String[] languages = {"English", "Spanish", "Dutch", "French", "German"};
    private final String[] langCodes = {"en", "es", "nl", "fr", "de"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiKeyInput = findViewById(R.id.apiKeyInput);
        rssUrlInput = findViewById(R.id.rssUrlInput);
        languageSpinner = findViewById(R.id.languageSpinner);
        statusText = findViewById(R.id.statusText);
        summaryText = findViewById(R.id.summaryText);
        btnLoadFeed = findViewById(R.id.btnLoadFeed);
        btnSummarize = findViewById(R.id.btnSummarize);
        btnSpeak = findViewById(R.id.btnSpeak);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        languageSpinner.setAdapter(adapter);

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                updateTtsLanguage();
            }
        });

        btnLoadFeed.setOnClickListener(v -> loadRssFeed());
        btnSummarize.setOnClickListener(v -> processAiSummary());
        btnSpeak.setOnClickListener(v -> speakSummary());
    }

    private void loadRssFeed() {
        String url = rssUrlInput.getText().toString();
        statusText.setText("Loading feed header...");

        new Thread(() -> {
            try {
                Document doc = Jsoup.connect(url).get();
                String title = doc.select("title").first() != null ? doc.select("title").first().text() : "RSS Feed";
                runOnUiThread(() -> statusText.setText("Loaded: " + title + ". Tap 'AI Summarize' to pull full article content."));
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Error loading feed: " + e.getMessage()));
            }
        }).start();
    }

    private void processAiSummary() {
        String apiKey = apiKeyInput.getText().toString().trim();
        String url = rssUrlInput.getText().toString().trim();
        int selectedLangIndex = languageSpinner.getSelectedItemPosition();
        String targetLang = languages[selectedLangIndex];

        if (apiKey.isEmpty()) {
            statusText.setText("Please enter your OpenRouter API Key.");
            return;
        }

        statusText.setText("Fetching full article content & calling AI...");

        new Thread(() -> {
            try {
                Document doc = Jsoup.connect(url).get();
                String fullBodyText = doc.body().text();
                String sampleText = fullBodyText.length() > 3000 ? fullBodyText.substring(0, 3000) : fullBodyText;

                OkHttpClient client = new OkHttpClient();
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "anthropic/claude-3.5-haiku");

                JSONArray messages = new JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "You are an expert news editor. Summarize the provided articles in " + targetLang + ". Provide key insights, connection points, and bullet points.");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", sampleText);
                messages.put(userMsg);

                jsonBody.put("messages", messages);

                RequestBody body = RequestBody.create(
                        jsonBody.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String resStr = response.body().string();
                        JSONObject resJson = new JSONObject(resStr);
                        String aiOutput = resJson.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        runOnUiThread(() -> {
                            statusText.setText("Summary Complete (" + targetLang + ")");
                            summaryText.setText(aiOutput);
                        });
                    } else {
                        runOnUiThread(() -> statusText.setText("API Error: " + response.code()));
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Failed: " + e.getMessage()));
            }
        }).start();
    }

    private void updateTtsLanguage() {
        int selectedIndex = languageSpinner.getSelectedItemPosition();
        String code = langCodes[selectedIndex];
        Locale locale = new Locale(code);
        if (tts != null) {
            tts.setLanguage(locale);
        }
    }

    private void speakSummary() {
        updateTtsLanguage();
        String text = summaryText.getText().toString();
        if (!text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
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
