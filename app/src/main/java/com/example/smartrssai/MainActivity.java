package com.example.smartrssai;

import android.content.Context;
import android.content.SharedPreferences;
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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    static class RssArticle {
        String title;
        String link;
        String description;
        boolean isRead = false;

        RssArticle(String title, String link, String description) {
            this.title = title;
            this.link = link;
            this.description = description;
        }
    }

    private EditText apiKeyInput, rssUrlInput;
    private Spinner languageSpinner;
    private TextView statusText, contentDisplay;
    private Button btnSaveKey, btnLoadFeed, btnSummarize, btnSpeak, btnTabNew, btnTabRead;
    private TextToSpeech tts;

    private final List<RssArticle> articleList = new ArrayList<>();
    private final String[] languages = {"English", "Spanish", "Dutch", "French", "German"};
    private final String[] langCodes = {"en", "es", "nl", "fr", "de"};
    
    private SharedPreferences prefs;
    private static final String PREF_KEY = "openrouter_api_key";
    private boolean showingNewTab = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("SmartRSSSettings", Context.MODE_PRIVATE);

        apiKeyInput = findViewById(R.id.apiKeyInput);
        rssUrlInput = findViewById(R.id.rssUrlInput);
        languageSpinner = findViewById(R.id.languageSpinner);
        statusText = findViewById(R.id.statusText);
        contentDisplay = findViewById(R.id.contentDisplay);
        
        btnSaveKey = findViewById(R.id.btnSaveKey);
        btnLoadFeed = findViewById(R.id.btnLoadFeed);
        btnSummarize = findViewById(R.id.btnSummarize);
        btnSpeak = findViewById(R.id.btnSpeak);
        btnTabNew = findViewById(R.id.btnTabNew);
        btnTabRead = findViewById(R.id.btnTabRead);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        languageSpinner.setAdapter(adapter);

        // Load saved API Key if available
        String savedKey = prefs.getString(PREF_KEY, "");
        if (!savedKey.isEmpty()) {
            apiKeyInput.setText(savedKey);
            btnSummarize.setEnabled(true);
        }

        btnSaveKey.setOnClickListener(v -> saveApiKey());
        btnLoadFeed.setOnClickListener(v -> fetchRssFeed());
        btnSummarize.setOnClickListener(v -> processAiSummary());
        btnSpeak.setOnClickListener(v -> speakContent());

        btnTabNew.setOnClickListener(v -> {
            showingNewTab = true;
            renderArticles();
        });

        btnTabRead.setOnClickListener(v -> {
            showingNewTab = false;
            renderArticles();
        });

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                tts.setLanguage(Locale.US);
            }
        });
    }

    private void saveApiKey() {
        String key = apiKeyInput.getText().toString().trim();
        prefs.edit().putString(PREF_KEY, key).apply();
        btnSummarize.setEnabled(!key.isEmpty());
        statusText.setText(key.isEmpty() ? "API Key cleared. AI Summarize disabled." : "API Key saved in Settings.");
    }

    private void fetchRssFeed() {
        String urlString = rssUrlInput.getText().toString().trim();
        if (urlString.isEmpty()) return;

        statusText.setText("Fetching RSS feed...");
        articleList.clear();

        new Thread(() -> {
            try {
                // Jsoup handles both XML and HTML gracefully without XML syntax errors
                Document doc = Jsoup.connect(urlString)
                        .userAgent("Mozilla/5.0")
                        .timeout(10000)
                        .get();

                // Parse RSS <item> tags
                Elements items = doc.select("item");

                // Fallback for Atom feeds (<entry> tags)
                if (items.isEmpty()) {
                    items = doc.select("entry");
                }

                for (Element item : items) {
                    String title = item.select("title").text();
                    
                    // Get link (handles RSS <link> and Atom <link href="...">)
                    String link = item.select("link").text();
                    if (link.isEmpty()) {
                        link = item.select("link").attr("href");
                    }

                    // Get description/content
                    String description = item.select("description").text();
                    if (description.isEmpty()) {
                        description = item.select("summary, content").text();
                    }

                    if (!title.isEmpty()) {
                        articleList.add(new RssArticle(title, link, description));
                    }
                }

                runOnUiThread(() -> {
                    if (articleList.isEmpty()) {
                        statusText.setText("No RSS items found. Make sure the URL points to an RSS XML feed.");
                    } else {
                        statusText.setText("Loaded " + articleList.size() + " articles.");
                    }
                    renderArticles();
                });

            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("RSS Load Error: " + e.getMessage()));
            }
        }).start();
    }

    private void renderArticles() {
        StringBuilder sb = new StringBuilder();
        int count = 0;

        for (int i = 0; i < articleList.size(); i++) {
            RssArticle article = articleList.get(i);
            if (article.isRead != showingNewTab) {
                count++;
                sb.append(count).append(". ").append(article.title).append("\n");
                if (article.description != null && !article.description.isEmpty()) {
                    String cleanSnippet = Jsoup.parse(article.description).text();
                    sb.append(cleanSnippet).append("\n");
                }
                sb.append("Source: ").append(article.link).append("\n\n---\n\n");
            }
        }

        if (count == 0) {
            sb.append("No articles in ").append(showingNewTab ? "'New'" : "'Read'").append(" section.");
        }

        contentDisplay.setText(sb.toString());
    }

    private void processAiSummary() {
        String apiKey = prefs.getString(PREF_KEY, "").trim();
        if (apiKey.isEmpty()) {
            statusText.setText("Please save a valid OpenRouter API Key first.");
            return;
        }

        int selectedIndex = languageSpinner.getSelectedItemPosition();
        String targetLang = languages[selectedIndex];

        statusText.setText("Extracting article text & invoking AI...");

        new Thread(() -> {
            try {
                StringBuilder articlesContext = new StringBuilder();
                int processedCount = 0;

                for (RssArticle article : articleList) {
                    if (!article.isRead) {
                        Document doc = Jsoup.connect(article.link).userAgent("Mozilla/5.0").get();
                        String bodyText = doc.body().text();
                        String truncated = bodyText.length() > 1500 ? bodyText.substring(0, 1500) : bodyText;

                        articlesContext.append("Title: ").append(article.title).append("\n")
                                        .append("Content: ").append(truncated).append("\n\n");
                        
                        article.isRead = true; // Move to Read after parsing
                        processedCount++;
                        if (processedCount >= 5) break; // Limit payload context size
                    }
                }

                OkHttpClient client = new OkHttpClient();
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "anthropic/claude-3.5-haiku");

                JSONArray messages = new JSONArray();
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", "Summarize these news articles in " + targetLang + ". Provide key points and synthesized insights.");
                messages.put(sysMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", articlesContext.toString());
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
                        JSONObject resJson = new JSONObject(response.body().string());
                        String aiOutput = resJson.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        runOnUiThread(() -> {
                            statusText.setText("Summary Complete (" + targetLang + "). Articles moved to Read.");
                            contentDisplay.setText(aiOutput);
                        });
                    } else {
                        int code = response.code();
                        runOnUiThread(() -> statusText.setText("API Error: " + code + " (Check OpenRouter Key/Credits)"));
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Summary Error: " + e.getMessage()));
            }
        }).start();
    }

    private void speakContent() {
        int selectedIndex = languageSpinner.getSelectedItemPosition();
        Locale locale = new Locale(langCodes[selectedIndex]);
        if (tts != null) {
            tts.setLanguage(locale);
            String text = contentDisplay.getText().toString();
            if (!text.isEmpty()) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
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
