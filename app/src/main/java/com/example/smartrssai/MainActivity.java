package com.example.smartrssai;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    public static class Article {
        String title, link, description;
        boolean isRead = false;
        boolean isSelected = false;
        long readTimestamp = 0;

        Article(String title, String link, String description) {
            this.title = title;
            this.link = link;
            this.description = description;
        }
    }

    public static class SummaryItem {
        String title;
        String timestamp;
        String content;

        SummaryItem(String title, String timestamp, String content) {
            this.title = title;
            this.timestamp = timestamp;
            this.content = content;
        }
    }

    private View viewFeeds, viewArticles, viewSettings, viewReader, viewSummaries;
    private Button navFeeds, navArticles, navSettings, navSummaries;
    private Button btnTabNew, btnTabRead, btnAddFeed, btnSaveApiKey, btnSummarizeSelected;
    private Button btnBackToArticles, btnReadFullArticleAloud, btnSpeakSummaryTab, btnBackToSummariesList;
    private EditText inputFeedUrl, inputApiKey;
    private Switch switchAi, switchAutoMarkRead;
    private Spinner spinnerLanguage, spinnerTtsVoice, spinnerRetention;
    private TextView statusText, readerTitle, readerContent, textSummaryOutput, summaryHeaderTitle;
    private ProgressBar summaryProgressBar;
    private ListView listFeeds, listSummaries;
    private ScrollView scrollSummaryDetail;
    private RecyclerView recyclerArticles;
    private LinearLayout layoutAiBar;

    private SharedPreferences prefs;
    private TextToSpeech tts;
    private ArticleAdapter articleAdapter;

    private final List<String> feedUrls = new ArrayList<>();
    private final List<Article> masterArticles = new ArrayList<>();
    private final List<Article> displayedArticles = new ArrayList<>();
    private final List<SummaryItem> savedSummaries = new ArrayList<>();
    private final List<Voice> availableVoices = new ArrayList<>();

    private final String[] languages = {"English", "Spanish", "Dutch", "French", "German"};
    private final String[] retentionOptions = {"1 Day", "3 Days", "7 Days", "Keep Forever"};

    private boolean showingNewTab = true;
    private String currentFullArticleText = "";
    private String activeSummaryText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("SmartRSSPrefs", Context.MODE_PRIVATE);

        // Views & Containers
        viewFeeds = findViewById(R.id.viewFeeds);
        viewArticles = findViewById(R.id.viewArticles);
        viewSettings = findViewById(R.id.viewSettings);
        viewReader = findViewById(R.id.viewReader);
        viewSummaries = findViewById(R.id.viewSummaries);

        // Navigation
        navFeeds = findViewById(R.id.navFeeds);
        navArticles = findViewById(R.id.navArticles);
        navSettings = findViewById(R.id.navSettings);
        navSummaries = findViewById(R.id.navSummaries);

        // Article & Reader Controls
        btnBackToArticles = findViewById(R.id.btnBackToArticles);
        btnReadFullArticleAloud = findViewById(R.id.btnReadFullArticleAloud);
        readerTitle = findViewById(R.id.readerTitle);
        readerContent = findViewById(R.id.readerContent);

        // Summary View Controls
        btnBackToSummariesList = findViewById(R.id.btnBackToSummariesList);
        btnSpeakSummaryTab = findViewById(R.id.btnSpeakSummaryTab);
        summaryHeaderTitle = findViewById(R.id.summaryHeaderTitle);
        summaryProgressBar = findViewById(R.id.summaryProgressBar);
        listSummaries = findViewById(R.id.listSummaries);
        scrollSummaryDetail = findViewById(R.id.scrollSummaryDetail);
        textSummaryOutput = findViewById(R.id.textSummaryOutput);

        // Settings Controls
        inputApiKey = findViewById(R.id.inputApiKey);
        switchAi = findViewById(R.id.switchAi);
        switchAutoMarkRead = findViewById(R.id.switchAutoMarkRead);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        spinnerTtsVoice = findViewById(R.id.spinnerTtsVoice);
        spinnerRetention = findViewById(R.id.spinnerRetention);
        btnSaveApiKey = findViewById(R.id.btnSaveApiKey);

        // Sub Controls
        btnTabNew = findViewById(R.id.btnTabNew);
        btnTabRead = findViewById(R.id.btnTabRead);
        btnAddFeed = findViewById(R.id.btnAddFeed);
        btnSummarizeSelected = findViewById(R.id.btnSummarizeSelected);
        inputFeedUrl = findViewById(R.id.inputFeedUrl);
        statusText = findViewById(R.id.statusText);
        listFeeds = findViewById(R.id.listFeeds);
        recyclerArticles = findViewById(R.id.recyclerArticles);
        layoutAiBar = findViewById(R.id.layoutAiBar);

        // RecyclerView Setup
        recyclerArticles.setLayoutManager(new LinearLayoutManager(this));
        articleAdapter = new ArticleAdapter();
        recyclerArticles.setAdapter(articleAdapter);

        // Spinners Setup
        spinnerLanguage.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages));
        spinnerRetention.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, retentionOptions));

        // Navigation Click Listeners
        navFeeds.setOnClickListener(v -> switchView(viewFeeds));
        navArticles.setOnClickListener(v -> switchView(viewArticles));
        navSettings.setOnClickListener(v -> switchView(viewSettings));
        navSummaries.setOnClickListener(v -> {
            showSummariesList();
            switchView(viewSummaries);
        });

        // Tabs
        btnTabNew.setOnClickListener(v -> { showingNewTab = true; filterArticles(); });
        btnTabRead.setOnClickListener(v -> { showingNewTab = false; filterArticles(); });

        loadSettings();

        btnAddFeed.setOnClickListener(v -> addFeed());
        btnSaveApiKey.setOnClickListener(v -> saveApiKey());
        
        switchAi.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("ai_enabled", isChecked).apply();
            updateAiState();
        });

        switchAutoMarkRead.setOnCheckedChangeListener((btn, isChecked) -> 
            prefs.edit().putBoolean("auto_mark_read", isChecked).apply()
        );

        spinnerRetention.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                prefs.edit().putInt("retention_index", pos).apply();
                applyRetentionPolicy();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSummarizeSelected.setOnClickListener(v -> runAiSummary());

        btnBackToArticles.setOnClickListener(v -> switchView(viewArticles));
        btnReadFullArticleAloud.setOnClickListener(v -> {
            if (tts != null && !currentFullArticleText.isEmpty()) {
                tts.speak(currentFullArticleText, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        btnBackToSummariesList.setOnClickListener(v -> showSummariesList());
        btnSpeakSummaryTab.setOnClickListener(v -> {
            if (tts != null && !activeSummaryText.isEmpty()) {
                tts.speak(activeSummaryText, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        // TTS Engine Initialization
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                populateTtsVoices();
            }
        });

        loadSavedSummaries();
        switchView(viewArticles);
        loadSavedFeeds();
    }

    private void switchView(View target) {
        viewFeeds.setVisibility(View.GONE);
        viewArticles.setVisibility(View.GONE);
        viewSettings.setVisibility(View.GONE);
        viewReader.setVisibility(View.GONE);
        viewSummaries.setVisibility(View.GONE);
        target.setVisibility(View.VISIBLE);
    }

    private void loadSettings() {
        inputApiKey.setText(prefs.getString("api_key", ""));
        switchAi.setChecked(prefs.getBoolean("ai_enabled", false));
        switchAutoMarkRead.setChecked(prefs.getBoolean("auto_mark_read", true));
        spinnerRetention.setSelection(prefs.getInt("retention_index", 2)); // Default: 7 days
        updateAiState();
    }

    private void saveApiKey() {
        prefs.edit().putString("api_key", inputApiKey.getText().toString().trim()).apply();
        Toast.makeText(this, "API Key saved successfully!", Toast.LENGTH_SHORT).show();
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
                if (!voice.isNetworkConnectionRequired()) {
                    availableVoices.add(voice);
                    voiceNames.add(voice.getLocale().getDisplayLanguage() + " (" + voice.getName() + ")");
                }
            }
        }

        spinnerTtsVoice.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceNames));
        spinnerTtsVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!availableVoices.isEmpty()) {
                    tts.setVoice(availableVoices.get(position));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
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
        listFeeds.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, feedUrls));
    }

    private void fetchAllFeeds() {
        statusText.setAlpha(1.0f);
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
                applyRetentionPolicy();
                filterArticles();
                statusText.setText("Loaded " + masterArticles.size() + " items.");
                statusText.animate().alpha(0.0f).setDuration(3000).start();
            });
        }).start();
    }

    private void applyRetentionPolicy() {
        int index = prefs.getInt("retention_index", 2);
        if (index == 3) return; // Keep Forever

        long daysInMillis = (index == 0 ? 1L : index == 1 ? 3L : 7L) * 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();

        masterArticles.removeIf(a -> a.isRead && (now - a.readTimestamp > daysInMillis));
    }

    private void filterArticles() {
        displayedArticles.clear();
        int newCount = 0, readCount = 0;

        for (Article a : masterArticles) {
            if (a.isRead) readCount++; else newCount++;
            if (a.isRead != showingNewTab) {
                displayedArticles.add(a);
            }
        }

        btnTabNew.setText("New Articles (" + newCount + ")");
        btnTabRead.setText("Read Articles (" + readCount + ")");
        articleAdapter.notifyDataSetChanged();
        updateSelectionCounter();
    }

    private void updateSelectionCounter() {
        int count = 0;
        for (Article a : masterArticles) if (a.isSelected) count++;
        btnSummarizeSelected.setText("Summarize Selected (" + count + ")");
    }

    private void openCleanArticle(Article a) {
        switchView(viewReader);
        readerTitle.setText(a.title);
        readerContent.setText("Extracting clean article text...");

        new Thread(() -> {
            try {
                Document doc = Jsoup.connect(a.link).userAgent("Mozilla/5.0").timeout(8000).get();
                doc.select("script, style, nav, header, footer, iframe, .ads, .comments, .sidebar, .related, aside, .trending").remove();

                Elements container = doc.select("article, .entry-content, .post-content, .article-body, #content");
                Elements paragraphs = !container.isEmpty() ? container.select("p") : doc.select("p");

                StringBuilder cleanText = new StringBuilder();
                for (Element p : paragraphs) {
                    String text = p.text().trim();
                    if (text.length() > 40) {
                        cleanText.append(text).append("\n\n");
                    }
                }

                currentFullArticleText = cleanText.length() > 0 ? cleanText.toString() : Jsoup.parse(a.description).text();
                runOnUiThread(() -> readerContent.setText(currentFullArticleText));
            } catch (Exception e) {
                runOnUiThread(() -> readerContent.setText("Failed to extract full article text.\nLink: " + a.link));
            }
        }).start();
    }

    private void runAiSummary() {
        String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            Toast.makeText(this, "Set API Key in Settings first", Toast.LENGTH_SHORT).show();
            return;
        }

        switchView(viewSummaries);
        showSummaryDetail("Generating Summary...", "Extracting selected articles and contacting AI...");
        summaryProgressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                StringBuilder payload = new StringBuilder();
                for (Article a : masterArticles) {
                    if (a.isSelected) {
                        try {
                            Document doc = Jsoup.connect(a.link).userAgent("Mozilla/5.0").timeout(5000).get();
                            doc.select("script, style, nav, header, footer, iframe, .ads, .comments, .sidebar").remove();
                            Elements container = doc.select("article, .entry-content, .post-content, .article-body");
                            String text = !container.isEmpty() ? container.text() : doc.body().text();
                            
                            payload.append("Title: ").append(a.title).append("\n")
                                   .append("Content: ").append(text.length() > 1500 ? text.substring(0, 1500) : text)
                                   .append("\n\n---\n\n");
                        } catch (Exception e) {
                            payload.append("Title: ").append(a.title).append("\nContent: ").append(a.description).append("\n\n---\n\n");
                        }
                    }
                }

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                JSONObject json = new JSONObject();
                json.put("model", "anthropic/claude-3-haiku");

                JSONArray msgs = new JSONArray();
                String targetLang = languages[spinnerLanguage.getSelectedItemPosition()];
                
                String prompt = "Summarize these articles in " + targetLang + " using bullet points.\n" +
                                "IMPORTANT: Format your response as valid JSON with two fields: 'title' (a short, concise 3-6 word summary title) and 'summary' (the full bulleted summary text).\n\n" +
                                "Articles:\n" + payload.toString();

                msgs.put(new JSONObject().put("role", "user").put("content", prompt));
                json.put("messages", msgs);

                Request req = new Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + key)
                        .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                        .build();

                try (Response res = client.newCall(req).execute()) {
                    if (res.isSuccessful() && res.body() != null) {
                        String rawContent = new JSONObject(res.body().string())
                                .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                        
                        String parsedTitle = "News Summary";
                        String parsedSummary = rawContent;

                        try {
                            JSONObject parsedJson = new JSONObject(rawContent.substring(rawContent.indexOf("{"), rawContent.lastIndexOf("}") + 1));
                            parsedTitle = parsedJson.optString("title", "News Summary");
                            parsedSummary = parsedJson.optString("summary", rawContent);
                        } catch (Exception ignored) {}

                        String timestamp = new SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(new Date());
                        SummaryItem newSummary = new SummaryItem(parsedTitle, timestamp, parsedSummary);
                        
                        savedSummaries.add(0, newSummary);
                        saveSummariesToPrefs();

                        // Reset selection & optionally mark as read
                        boolean autoMarkRead = prefs.getBoolean("auto_mark_read", true);
                        for (Article a : masterArticles) {
                            if (a.isSelected) {
                                a.isSelected = false;
                                if (autoMarkRead) {
                                    a.isRead = true;
                                    a.readTimestamp = System.currentTimeMillis();
                                }
                            }
                        }

                        String finalTitle = parsedTitle;
                        String finalSummary = parsedSummary;
                        runOnUiThread(() -> {
                            summaryProgressBar.setVisibility(View.GONE);
                            filterArticles();
                            showSummaryDetail(finalTitle, finalSummary);
                        });
                    } else {
                        String err = res.body() != null ? res.body().string() : "Unknown response error";
                        runOnUiThread(() -> {
                            summaryProgressBar.setVisibility(View.GONE);
                            showSummaryDetail("API Error", "Error (" + res.code() + "): " + err);
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    summaryProgressBar.setVisibility(View.GONE);
                    showSummaryDetail("Failed", "Error generating summary: " + e.getLocalizedMessage());
                });
            }
        }).start();
    }

    // Summary List Management
    private void showSummariesList() {
        summaryHeaderTitle.setText("Saved Summaries");
        btnBackToSummariesList.setVisibility(View.GONE);
        btnSpeakSummaryTab.setVisibility(View.GONE);
        scrollSummaryDetail.setVisibility(View.GONE);
        listSummaries.setVisibility(View.VISIBLE);

        List<String> listLabels = new ArrayList<>();
        for (SummaryItem s : savedSummaries) {
            listLabels.add(s.title + "\n" + s.timestamp);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listLabels);
        listSummaries.setAdapter(adapter);

        listSummaries.setOnItemClickListener((parent, view, position, id) -> {
            SummaryItem item = savedSummaries.get(position);
            showSummaryDetail(item.title, item.content);
        });

        listSummaries.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Summary")
                    .setMessage("Do you want to delete this summary?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        savedSummaries.remove(position);
                        saveSummariesToPrefs();
                        showSummariesList();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
    }

    private void showSummaryDetail(String title, String content) {
        summaryHeaderTitle.setText(title);
        activeSummaryText = content;
        textSummaryOutput.setText(content);

        btnBackToSummariesList.setVisibility(View.VISIBLE);
        btnSpeakSummaryTab.setVisibility(View.VISIBLE);
        listSummaries.setVisibility(View.GONE);
        scrollSummaryDetail.setVisibility(View.VISIBLE);
    }

    private void saveSummariesToPrefs() {
        try {
            JSONArray arr = new JSONArray();
            for (SummaryItem item : savedSummaries) {
                JSONObject obj = new JSONObject();
                obj.put("title", item.title);
                obj.put("timestamp", item.timestamp);
                obj.put("content", item.content);
                arr.put(obj);
            }
            prefs.edit().putString("saved_summaries_json", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadSavedSummaries() {
        savedSummaries.clear();
        String jsonStr = prefs.getString("saved_summaries_json", "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                savedSummaries.add(new SummaryItem(
                        obj.getString("title"),
                        obj.getString("timestamp"),
                        obj.getString("content")
                ));
            }
        } catch (Exception ignored) {}
    }

    // Article Adapter
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

            holder.checkBox.setVisibility(a.isSelected ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(a.isSelected);

            // Tap to open full in-app article reader
            holder.itemView.setOnClickListener(v -> {
                if (!a.isRead) {
                    a.isRead = true;
                    a.readTimestamp = System.currentTimeMillis();
                }
                filterArticles();
                openCleanArticle(a);
            });

            // Long-press to toggle selection for AI summary
            holder.itemView.setOnLongClickListener(v -> {
                a.isSelected = !a.isSelected;
                notifyItemChanged(pos);
                updateSelectionCounter();
                return true;
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
