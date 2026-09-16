package com.example.smartrssai;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static class Article {
        String title, link, description;
        boolean isRead = false;
        boolean isSelected = false;

        Article(String title, String link, String description) {
            this.title = title;
            this.link = link;
            this.description = description;
        }
    }

    private View viewFeeds, viewArticles, viewSettings;
    private Button navFeeds, navArticles, navSettings;
    private Button btnTabNew, btnTabRead, btnAddFeed, btnSaveApiKey, btnSummarizeSelected, btnSpeakSummary;
    private EditText inputFeedUrl, inputApiKey;
    private Switch switchAi;
    private Spinner spinnerLanguage, spinnerTtsVoice;
    private TextView statusText;
    private ListView listFeeds;
    private RecyclerView recyclerArticles;
    private LinearLayout layoutAiBar;

    private SharedPreferences prefs;
    private TextToSpeech tts;
    private ArticleAdapter articleAdapter;

    private final List<String> feedUrls = new ArrayList<>();
    private final List<Article> masterArticles = new ArrayList<>();
    private final List<Article> displayedArticles = new ArrayList<>();
    private final List<Voice> availableVoices = new ArrayList<>();

    private final String[] languages = {"English", "Spanish", "Dutch", "French", "German"};
    private boolean showingNewTab = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("SmartRSSPrefs", Context.MODE_PRIVATE);

        // UI Views
        viewFeeds = findViewById(R.id.viewFeeds);
        viewArticles = findViewById(R.id.viewArticles);
        viewSettings = findViewById(R.id.viewSettings);

        // Nav Buttons
        navFeeds = findViewById(R.id.navFeeds);
        navArticles = findViewById(R.id.navArticles);
        navSettings = findViewById(R.id.navSettings);

        // Sub Controls
        btnTabNew = findViewById(R.id.btnTabNew);
        btnTabRead = findViewById(R.id.btnTabRead);
        btnAddFeed = findViewById(R.id.btnAddFeed);
        btnSaveApiKey = findViewById(R.id.btnSaveApiKey);
        btnSummarizeSelected = findViewById(R.id.btnSummarizeSelected);
        btnSpeakSummary = findViewById(R.id.btnSpeakSummary);

        inputFeedUrl = findViewById(R.id.inputFeedUrl);
        inputApiKey = findViewById(R.id.inputApiKey);
        switchAi = findViewById(R.id.switchAi);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        spinnerTtsVoice = findViewById(R.id.spinnerTtsVoice);
        statusText = findViewById(R.id.statusText);
        listFeeds = findViewById(R.id.listFeeds);
        recyclerArticles = findViewById(R.id.recyclerArticles);
        layoutAiBar = findViewById(R.id.layoutAiBar);

        // Setup RecyclerView
        recyclerArticles.setLayoutManager(new LinearLayoutManager(this));
        articleAdapter = new ArticleAdapter();
        recyclerArticles.setAdapter(articleAdapter);

        // Setup Target Language Spinner
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        spinnerLanguage.setAdapter(langAdapter);

        // Bottom Nav Listeners
        navFeeds.setOnClickListener(v -> switchView(viewFeeds));
        navArticles.setOnClickListener(v -> switchView(viewArticles));
        navSettings.setOnClickListener(v -> switchView(viewSettings));

        // Article Tabs
        btnTabNew.setOnClickListener(v -> { showingNewTab = true; filterArticles(); });
        btnTabRead.setOnClickListener(v -> { showingNewTab = false; filterArticles(); });

        loadSettings();

        btnAddFeed.setOnClickListener(v -> addFeed());
        btnSaveApiKey.setOnClickListener(v -> saveApiKey());
        switchAi.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("ai_enabled", isChecked).apply();
            updateAiState();
        });

        btnSummarizeSelected.setOnClickListener(v -> runAiSummary());
        btnSpeakSummary.setOnClickListener(v -> speakSummary());

        // Initialize Native TextToSpeech Engine (Runs completely independent of AI state)
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                populateTtsVoices();
            }
        });

        switchView(viewArticles);
        loadSavedFeeds();
    }

    private void switchView(View target) {
        viewFeeds.setVisibility(View.GONE);
        viewArticles.setVisibility(View.GONE);
        viewSettings.setVisibility(View.GONE);
        target.setVisibility(View.VISIBLE);
    }

    private void loadSettings() {
        inputApiKey.setText(prefs.getString("api_key", ""));
        switchAi.setChecked(prefs.getBoolean("ai_enabled", false));
        updateAiState();
    }

    private void saveApiKey() {
        prefs.edit().putString("api_key", inputApiKey.getText().toString().trim()).apply();
        statusText.setText("API Key saved successfully.");
    }

    private void updateAiState() {
        boolean enabled = switchAi.isChecked();
        layoutAiBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        articleAdapter.notifyDataSetChanged();
    }

    private void populateTtsVoices() {
        Set<Voice> voices = tts.getVoices();
        List<String> voiceNames = new ArrayList<>();
        availableVoices.clear();

        if (voices != null) {
            for (Voice voice : voices) {
                if (!voice.isNetworkConnectionRequired()) { // Filter for offline-compatible voices
                    availableVoices.add(voice);
                    voiceNames.add(voice.getLocale().getDisplayLanguage() + " (" + voice.getName() + ")");
                }
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceNames);
        spinnerTtsVoice.setAdapter(adapter);

        spinnerTtsVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!availableVoices.isEmpty()) {
                    tts.setVoice(availableVoices.get(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadSavedFeeds() {
        Set<String> saved = prefs.getStringSet("feed_list", new HashSet<>());
        feedUrls.clear();
        feedUrls.addAll(saved);
        if (feedUrls.isEmpty()) {
            feedUrls.add("https://www.srnieuws.com/rss/latest-posts");
        }
        renderFeedList();
        fetchAllFeeds();
    }

    private void addFeed() {
        String url = inputFeedUrl.getText().toString().trim();
        if (!url.isEmpty() && !feedUrls.contains(url)) {
            feedUrls.add(url);
            prefs.edit().putStringSet("feed_list", new HashSet<>(feedUrls)).apply();
            inputFeedUrl.setText("");
            renderFeedList();
            fetchAllFeeds();
        }
    }

    private void renderFeedList() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, feedUrls);
        listFeeds.setAdapter(adapter);
    }

    private void fetchAllFeeds() {
        statusText.setText("Updating feeds...");
        masterArticles.clear();

        new Thread(() -> {
            for (String url : feedUrls) {
                try {
                    Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(8000).get();
                    Elements items = doc.select("item");
                    if (items.isEmpty()) items = doc.select("entry");

                    for (Element item : items) {
                        String title = item.select("title").text();
                        String link = item.select("link").text();
                        if (link.isEmpty()) link = item.select("link").attr("href");
                        String desc = item.select("description").text();

                        if (!title.isEmpty()) {
                            masterArticles.add(new Article(title, link, desc));
                        }
                    }
                } catch (Exception ignored) {}
            }

            runOnUiThread(() -> {
                statusText.setText("Loaded " + masterArticles.size() + " items.");
                filterArticles();
            });
        }).start();
    }

    private void filterArticles() {
        displayedArticles.clear();
        for (Article a : masterArticles) {
            if (a.isRead != showingNewTab) {
                displayedArticles.add(a);
            }
        }
        articleAdapter.notifyDataSetChanged();
        updateSelectionCounter();
    }

    private void updateSelectionCounter() {
        int count = 0;
        for (Article a : displayedArticles) if (a.isSelected) count++;
        btnSummarizeSelected.setText("Summarize Selected (" + count + ")");
    }

    private void runAiSummary() {
        String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            statusText.setText("OpenRouter API Key is missing in Settings.");
            return;
        }

        String targetLang = languages[spinnerLanguage.getSelectedItemPosition()];
        statusText.setText("Scraping content & requesting summary...");

        new Thread(() -> {
            try {
                StringBuilder payload = new StringBuilder();
                for (Article a : displayedArticles) {
                    if (a.isSelected) {
                        Document doc = Jsoup.connect(a.link).userAgent("Mozilla/5.0").get();
                        String text = doc.body().text();
                        payload.append("Title: ").append(a.title).append("\n")
                               .append("Content: ").append(text.length() > 1200 ? text.substring(0, 1200) : text)
                               .append("\n\n---\n\n");
                    }
                }

                OkHttpClient client = new OkHttpClient();
                JSONObject json = new JSONObject();
                json.put("model", "anthropic/claude-3.5-haiku");

                JSONArray msgs = new JSONArray();
                msgs.put(new JSONObject().put("role", "system").put("content", "Summarize these articles in " + targetLang + ". Use clear bullet points."));
                msgs.put(new JSONObject().put("role", "user").put("content", payload.toString()));
                json.put("messages", msgs);

                Request req = new Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + key)
                        .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                        .build();

                try (Response res = client.newCall(req).execute()) {
                    if (res.isSuccessful() && res.body() != null) {
                        String summary = new JSONObject(res.body().string())
                                .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("AI Summary").setMessage(summary).setPositiveButton("OK", null).show());
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("AI Error: " + e.getMessage()));
            }
        }).start();
    }

    private void speakSummary() {
        if (displayedArticles.isEmpty()) return;

        StringBuilder speechContent = new StringBuilder();
        for (Article article : displayedArticles) {
            // Reads selected articles if AI is on, or reads all visible articles if AI is turned off
            if (article.isSelected || !switchAi.isChecked()) {
                speechContent.append(article.title).append(". ");
            }
        }

        if (tts != null && speechContent.length() > 0) {
            tts.speak(speechContent.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    // RecyclerView Adapter
    class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.ArticleHolder> {

        @NonNull
        @Override
        public ArticleHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rss_article, parent, false);
            return new ArticleHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ArticleHolder holder, int pos) {
            Article a = displayedArticles.get(pos);
            holder.title.setText(a.title);
            holder.snippet.setText(Jsoup.parse(a.description).text());

            boolean aiEnabled = switchAi.isChecked();
            holder.checkBox.setVisibility(aiEnabled ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(a.isSelected);

            holder.checkBox.setOnClickListener(v -> {
                a.isSelected = holder.checkBox.isChecked();
                updateSelectionCounter();
            });

            // Opens full article inside an In-App Chrome Custom Tab
            holder.itemView.setOnClickListener(v -> {
                a.isRead = true;
                filterArticles();

                CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                customTabsIntent.launchUrl(MainActivity.this, Uri.parse(a.link));
            });
        }

        @Override
        public int getItemCount() {
            return displayedArticles.size();
        }

        class ArticleHolder extends RecyclerView.ViewHolder {
            TextView title, snippet;
            CheckBox checkBox;

            ArticleHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.articleTitle);
                snippet = itemView.findViewById(R.id.articleSnippet);
                checkBox = itemView.findViewById(R.id.articleCheckBox);
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
