package com.example.smartrssai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static class RssArticle {
        String title;
        String link;
        String description;
        boolean isSelected = false;

        RssArticle(String title, String link, String description) {
            this.title = title;
            this.link = link;
            this.description = description;
        }
    }

    private EditText rssUrlInput;
    private Switch switchAiToggle;
    private Spinner languageSpinner;
    private TextView statusText;
    private Button btnLoadFeed, btnSummarizeSelected, btnSpeakSummary;
    private LinearLayout bottomActionBar;
    private RecyclerView recyclerView;

    private ArticleAdapter adapter;
    private TextToSpeech tts;
    private SharedPreferences prefs;

    private final List<RssArticle> articleList = new ArrayList<>();
    private final String[] languages = {"English", "Spanish", "Dutch", "French", "German"};
    private final String[] langCodes = {"en", "es", "nl", "fr", "de"};
    private static final String PREF_KEY_AI = "ai_summary_enabled";
    private static final String PREF_KEY_API = "openrouter_api_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("SmartRSSSettings", Context.MODE_PRIVATE);

        rssUrlInput = findViewById(R.id.rssUrlInput);
        switchAiToggle = findViewById(R.id.switchAiToggle);
        languageSpinner = findViewById(R.id.languageSpinner);
        statusText = findViewById(R.id.statusText);
        btnLoadFeed = findViewById(R.id.btnLoadFeed);
        btnSummarizeSelected = findViewById(R.id.btnSummarizeSelected);
        btnSpeakSummary = findViewById(R.id.btnSpeakSummary);
        bottomActionBar = findViewById(R.id.bottomActionBar);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ArticleAdapter();
        recyclerView.setAdapter(adapter);

        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        languageSpinner.setAdapter(langAdapter);

        boolean isAiEnabled = prefs.getBoolean(PREF_KEY_AI, false);
        switchAiToggle.setChecked(isAiEnabled);

        switchAiToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_KEY_AI, isChecked).apply();
            updateUiSelectionState();
        });

        btnLoadFeed.setOnClickListener(v -> fetchRssFeed());
        btnSummarizeSelected.setOnClickListener(v -> summarizeSelectedArticles());
        btnSpeakSummary.setOnClickListener(v -> speakSummary());

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                tts.setLanguage(Locale.US);
            }
        });

        updateUiSelectionState();
    }

    private void updateUiSelectionState() {
        boolean isAiActive = switchAiToggle.isChecked();
        adapter.setSelectionModeEnabled(isAiActive);
        bottomActionBar.setVisibility(isAiActive ? View.VISIBLE : View.GONE);
        updateSelectionCount();
    }

    private void updateSelectionCount() {
        int count = 0;
        for (RssArticle article : articleList) {
            if (article.isSelected) count++;
        }
        btnSummarizeSelected.setText("Summarize Selected (" + count + ")");
        btnSummarizeSelected.setEnabled(count > 0);
    }

    private void fetchRssFeed() {
        String urlString = rssUrlInput.getText().toString().trim();
        if (urlString.isEmpty()) return;

        statusText.setText("Loading feed...");
        articleList.clear();

        new Thread(() -> {
            try {
                Document doc = Jsoup.connect(urlString).userAgent("Mozilla/5.0").timeout(10000).get();
                Elements items = doc.select("item");
                if (items.isEmpty()) items = doc.select("entry");

                for (Element item : items) {
                    String title = item.select("title").text();
                    String link = item.select("link").text();
                    if (link.isEmpty()) link = item.select("link").attr("href");
                    String desc = item.select("description").text();

                    if (!title.isEmpty()) {
                        articleList.add(new RssArticle(title, link, desc));
                    }
                }

                runOnUiThread(() -> {
                    statusText.setText("Loaded " + articleList.size() + " articles.");
                    adapter.notifyDataSetChanged();
                    updateSelectionCount();
                });
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Load Error: " + e.getMessage()));
            }
        }).start();
    }

    private void summarizeSelectedArticles() {
        String apiKey = prefs.getString(PREF_KEY_API, "").trim();
        if (apiKey.isEmpty()) {
            statusText.setText("OpenRouter API key missing in settings.");
            return;
        }

        int langIndex = languageSpinner.getSelectedItemPosition();
        String targetLang = languages[langIndex];

        statusText.setText("Extracting selected articles & invoking AI...");

        new Thread(() -> {
            try {
                StringBuilder payload = new StringBuilder();
                for (RssArticle article : articleList) {
                    if (article.isSelected) {
                        Document doc = Jsoup.connect(article.link).userAgent("Mozilla/5.0").get();
                        String bodyText = doc.body().text();
                        String snippet = bodyText.length() > 1500 ? bodyText.substring(0, 1500) : bodyText;

                        payload.append("Title: ").append(article.title).append("\n")
                               .append("Content: ").append(snippet).append("\n\n---\n\n");
                    }
                }

                OkHttpClient client = new OkHttpClient();
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "anthropic/claude-3.5-haiku");

                JSONArray messages = new JSONArray();
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", "Synthesize a structured summary of the selected articles in " + targetLang + ".");
                messages.put(sysMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", payload.toString());
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
                        String aiSummary = resJson.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        runOnUiThread(() -> {
                            statusText.setText("Summary Complete.");
                            showSummaryDialog(aiSummary);
                        });
                    } else {
                        int code = response.code();
                        runOnUiThread(() -> statusText.setText("API Error: " + code));
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("AI Error: " + e.getMessage()));
            }
        }).start();
    }

    private void showSummaryDialog(String summaryText) {
        new AlertDialog.Builder(this)
                .setTitle("AI Summary")
                .setMessage(summaryText)
                .setPositiveButton("Close", null)
                .show();
    }

    private void speakSummary() {
        int index = languageSpinner.getSelectedItemPosition();
        tts.setLanguage(new Locale(langCodes[index]));
    }

    // RecyclerView Adapter
    class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.ViewHolder> {

        private boolean selectionMode = false;

        public void setSelectionModeEnabled(boolean enabled) {
            this.selectionMode = enabled;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rss_article, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RssArticle article = articleList.get(position);
            holder.title.setText(article.title);
            holder.snippet.setText(Jsoup.parse(article.description).text());

            holder.checkBox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(article.isSelected);

            holder.checkBox.setOnClickListener(v -> {
                article.isSelected = holder.checkBox.isChecked();
                updateSelectionCount();
            });

            holder.itemView.setOnClickListener(v -> {
                if (selectionMode) {
                    article.isSelected = !article.isSelected;
                    holder.checkBox.setChecked(article.isSelected);
                    updateSelectionCount();
                }
            });
        }

        @Override
        public int getItemCount() {
            return articleList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, snippet;
            CheckBox checkBox;

            ViewHolder(View itemView) {
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
