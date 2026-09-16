package com.example.smartrssai;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    public static class Article {
        String title, link, description, feedUrl;
        boolean isRead = false;
        boolean isSelected = false;
        long readTimestamp = 0;

        Article(String title, String link, String description, String feedUrl) {
            this.title = title;
            this.link = link;
            this.description = description;
            this.feedUrl = feedUrl;
        }
    }

    public static class SummaryItem {
        String title, timestamp, content;
        SummaryItem(String title, String timestamp, String content) {
            this.title = title;
            this.timestamp = timestamp;
            this.content = content;
        }
    }

    public static class FeedInfo {
        String title, url, category;
        FeedInfo(String title, String url, String category) {
            this.title = title;
            this.url = url;
            this.category = category;
        }
    }

    private View viewFeeds, viewArticles, viewSettings, viewReader, viewSummaries;
    private Button navFeeds, navArticles, navSettings, navSummaries;
    private Button btnTabNew, btnTabRead, btnAddFeed, btnDiscoverFeeds, btnSaveApiKey, btnSummarizeSelected, btnDebateSelected;
    private Button btnBackToArticles, btnBackToSummariesList;
    
    // Media Player Controls
    private LinearLayout readerMediaControls, summaryMediaControls;
    private Button btnReaderRewind, btnReaderPlayPause, btnReaderFastForward, btnReaderStop;
    private Button btnSummaryRewind, btnSummaryPlayPause, btnSummaryFastForward, btnSummaryStop;

    private EditText inputFeedUrl, inputApiKey;
    private Switch switchAi, switchAutoMarkRead;
    private Spinner spinnerLanguage, spinnerTtsVoice, spinnerRetention, spinnerDepth, spinnerDebateTone;
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
    private final String[] depthOptions = {"Short (Quick Bullet Points)", "Medium (Detailed Highlights)", "Long (Comprehensive Deep Dive)"};
    private final String[] debateTones = {"Main Facts & Balanced Analysis", "Satirical & Witty", "Economic & Market Focus", "Financial & Investor Angle", "Philosophical & Societal Impact"};

    private boolean showingNewTab = true;
    private String activeTextToRead = "";
    private String[] activeParagraphChunks;
    private int currentSpeechChunkIndex = 0;
    private boolean isTtsPaused = false;
    private boolean isTtsPlaying = false;

    private final Map<String, List<FeedInfo>> countryFeedDirectory = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("SmartRSSPrefs", Context.MODE_PRIVATE);

        initCountryDirectory();

        // Bind Views
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

        // Controls
        btnBackToArticles = findViewById(R.id.btnBackToArticles);
        readerTitle = findViewById(R.id.readerTitle);
        readerContent = findViewById(R.id.readerContent);

        // Reader Media Player Bar
        readerMediaControls = findViewById(R.id.readerMediaControls);
        btnReaderRewind = findViewById(R.id.btnReaderRewind);
        btnReaderPlayPause = findViewById(R.id.btnReaderPlayPause);
        btnReaderFastForward = findViewById(R.id.btnReaderFastForward);
        btnReaderStop = findViewById(R.id.btnReaderStop);

        // Summary Media Player Bar
        summaryMediaControls = findViewById(R.id.summaryMediaControls);
        btnSummaryRewind = findViewById(R.id.btnSummaryRewind);
        btnSummaryPlayPause = findViewById(R.id.btnSummaryPlayPause);
        btnSummaryFastForward = findViewById(R.id.btnSummaryFastForward);
        btnSummaryStop = findViewById(R.id.btnSummaryStop);

        // Summary Controls
        btnBackToSummariesList = findViewById(R.id.btnBackToSummariesList);
        summaryHeaderTitle = findViewById(R.id.summaryHeaderTitle);
        summaryProgressBar = findViewById(R.id.summaryProgressBar);
        listSummaries = findViewById(R.id.listSummaries);
        scrollSummaryDetail = findViewById(R.id.scrollSummaryDetail);
        textSummaryOutput = findViewById(R.id.textSummaryOutput);

        // Settings
        inputApiKey = findViewById(R.id.inputApiKey);
        switchAi = findViewById(R.id.switchAi);
        switchAutoMarkRead = findViewById(R.id.switchAutoMarkRead);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        spinnerTtsVoice = findViewById(R.id.spinnerTtsVoice);
        spinnerRetention = findViewById(R.id.spinnerRetention);
        spinnerDepth = findViewById(R.id.spinnerDepth);
        spinnerDebateTone = findViewById(R.id.spinnerDebateTone);
        btnSaveApiKey = findViewById(R.id.btnSaveApiKey);

        // Sub Controls
        btnTabNew = findViewById(R.id.btnTabNew);
        btnTabRead = findViewById(R.id.btnTabRead);
        btnAddFeed = findViewById(R.id.btnAddFeed);
        btnDiscoverFeeds = findViewById(R.id.btnDiscoverFeeds);
        btnSummarizeSelected = findViewById(R.id.btnSummarizeSelected);
        btnDebateSelected = findViewById(R.id.btnDebateSelected);
        inputFeedUrl = findViewById(R.id.inputFeedUrl);
        statusText = findViewById(R.id.statusText);
        listFeeds = findViewById(R.id.listFeeds);
        recyclerArticles = findViewById(R.id.recyclerArticles);
        layoutAiBar = findViewById(R.id.layoutAiBar);

        // Setup RecyclerView
        recyclerArticles.setLayoutManager(new LinearLayoutManager(this));
        articleAdapter = new ArticleAdapter();
        recyclerArticles.setAdapter(articleAdapter);

        // Spinners Configuration
        spinnerLanguage.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages));
        spinnerRetention.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, retentionOptions));
        spinnerDepth.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, depthOptions));
        spinnerDebateTone.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, debateTones));

        // Navigation
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
        btnDiscoverFeeds.setOnClickListener(v -> showDiscoverFeedsDialog());
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

        spinnerDepth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                prefs.edit().putInt("depth_index", pos).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerDebateTone.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                prefs.edit().putInt("debate_tone_index", pos).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSummarizeSelected.setOnClickListener(v -> runAiAction(false));
        btnDebateSelected.setOnClickListener(v -> runAiAction(true));

        btnBackToArticles.setOnClickListener(v -> {
            stopTts();
            switchView(viewArticles);
        });

        btnBackToSummariesList.setOnClickListener(v -> {
            stopTts();
            showSummariesList();
        });

        setupMediaPlayerClickListeners();

        // Setup Standalone TTS Engine
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                populateTtsVoices();
            }
        });

        loadSavedSummaries();
        switchView(viewArticles);
        loadSavedFeeds();
    }

    private void initCountryDirectory() {
        List<FeedInfo> surinameFeeds = new ArrayList<>();
        surinameFeeds.add(new FeedInfo("Waterkant", "https://www.waterkant.net/feed/", "News"));
        surinameFeeds.add(new FeedInfo("De Ware Tijd", "https://dwtonline.com/feed/", "News"));
        surinameFeeds.add(new FeedInfo("Starnieuws", "https://www.starnieuws.com/index.php/rss/index", "News"));
        countryFeedDirectory.put("Suriname", surinameFeeds);

        List<FeedInfo> usaFeeds = new ArrayList<>();
        usaFeeds.add(new FeedInfo("BBC Tech News", "http://feeds.bbci.co.uk/news/technology/rss.xml", "Tech"));
        usaFeeds.add(new FeedInfo("The Verge", "https://www.theverge.com/rss/index.xml", "Tech"));
        usaFeeds.add(new FeedInfo("Reuters Top News", "https://www.reutersagency.com/feed/", "News"));
        countryFeedDirectory.put("United States / Global", usaFeeds);

        List<FeedInfo> nlFeeds = new ArrayList<>();
        nlFeeds.add(new FeedInfo("NOS Nieuws Algemeen", "https://feeds.nos.nl/nosnieuwsalgemeen", "News"));
        nlFeeds.add(new FeedInfo("NOS Tech Nieuws", "https://feeds.nos.nl/nosnieuwstech", "Tech"));
        nlFeeds.add(new FeedInfo("Tweakers", "https://feeds.feedburner.com/tweakers/mixed", "Tech"));
        countryFeedDirectory.put("Netherlands", nlFeeds);
    }

    // DISCOVER FEEDS: Features Web Search for Any Country
    private void showDiscoverFeedsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_discover_feeds, null);
        builder.setView(dialogView);

        Spinner spinnerCountry = dialogView.findViewById(R.id.spinnerCountry);
        EditText inputCustomCountry = dialogView.findViewById(R.id.inputCustomCountry); // Optional input box for custom queries
        Button btnSearchWebFeeds = dialogView.findViewById(R.id.btnSearchWebFeeds);     // Optional search button
        LinearLayout layoutContainer = dialogView.findViewById(R.id.layoutDiscoveredContainer);

        List<String> countries = new ArrayList<>(countryFeedDirectory.keySet());
        countries.add("Search Other Country...");
        spinnerCountry.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, countries));

        final List<CheckBox> selectedBoxes = new ArrayList<>();

        spinnerCountry.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutContainer.removeAllViews();
                selectedBoxes.clear();

                String selectedCountry = countries.get(position);
                if (selectedCountry.equals("Search Other Country...")) {
                    if (inputCustomCountry != null) inputCustomCountry.setVisibility(View.VISIBLE);
                    if (btnSearchWebFeeds != null) btnSearchWebFeeds.setVisibility(View.VISIBLE);
                    return;
                }

                if (inputCustomCountry != null) inputCustomCountry.setVisibility(View.GONE);
                if (btnSearchWebFeeds != null) btnSearchWebFeeds.setVisibility(View.GONE);

                List<FeedInfo> feeds = countryFeedDirectory.get(selectedCountry);
                if (feeds != null) {
                    renderDiscoveredFeedBoxes(feeds, layoutContainer, selectedBoxes);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (btnSearchWebFeeds != null && inputCustomCountry != null) {
            btnSearchWebFeeds.setOnClickListener(v -> {
                String targetCountry = inputCustomCountry.getText().toString().trim();
                if (!targetCountry.isEmpty()) {
                    performWebRssDiscovery(targetCountry, layoutContainer, selectedBoxes);
                }
            });
        }

        builder.setPositiveButton("Add Selected Feeds", (dialog, which) -> {
            boolean addedAny = false;
            for (CheckBox cb : selectedBoxes) {
                if (cb.isChecked() && cb.isEnabled()) {
                    String url = (String) cb.getTag();
                    if (!feedUrls.contains(url)) {
                        feedUrls.add(url);
                        addedAny = true;
                    }
                }
            }
            if (addedAny) {
                prefs.edit().putStringSet("feed_list", new HashSet<>(feedUrls)).apply();
                renderFeedList();
                fetchAllFeeds();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void renderDiscoveredFeedBoxes(List<FeedInfo> feeds, LinearLayout container, List<CheckBox> boxList) {
        String currentCategory = "";
        for (FeedInfo feed : feeds) {
            if (!feed.category.equalsIgnoreCase(currentCategory)) {
                currentCategory = feed.category;
                TextView catHeader = new TextView(MainActivity.this);
                catHeader.setText("--- " + currentCategory.toUpperCase() + " ---");
                catHeader.setPadding(0, 16, 0, 8);
                catHeader.setTypeface(null, Typeface.BOLD);
                container.addView(catHeader);
            }

            CheckBox cb = new CheckBox(MainActivity.this);
            cb.setText(feed.title + "\n(" + feed.url + ")");
            cb.setTag(feed.url);
            if (feedUrls.contains(feed.url)) {
                cb.setChecked(true);
                cb.setEnabled(false);
            }
            boxList.add(cb);
            container.addView(cb);
        }
    }

    // Dynamic Search Engine Scraping for Regional Feeds
    private void performWebRssDiscovery(String queryCountry, LinearLayout container, List<CheckBox> boxList) {
        container.removeAllViews();
        boxList.clear();

        TextView loading = new TextView(this);
        loading.setText("Searching web for RSS feeds in " + queryCountry + "...");
        container.addView(loading);

        new Thread(() -> {
            List<FeedInfo> discovered = new ArrayList<>();
            try {
                String searchUrl = "https://html.duckduckgo.com/html/?q=" + queryCountry + "+news+rss+feed";
                Document doc = Jsoup.connect(searchUrl).userAgent("Mozilla/5.0").timeout(8000).get();
                Elements links = doc.select("a.result__url");

                for (Element l : links) {
                    String href = l.attr("href");
                    if (href.contains("rss") || href.contains("feed") || href.endsWith(".xml")) {
                        String title = l.text().replaceAll("https?://", "").replaceAll("/.*", "");
                        discovered.add(new FeedInfo(title, href, "Web Discovery"));
                    }
                }
            } catch (Exception ignored) {}

            runOnUiThread(() -> {
                container.removeAllViews();
                if (discovered.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("No RSS feeds automatically found for " + queryCountry + ". Try adding direct URLs manually.");
                    container.addView(empty);
                } else {
                    renderDiscoveredFeedBoxes(discovered, container, boxList);
                }
            });
        }).start();
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
        spinnerLanguage.setSelection(prefs.getInt("language_index", 0));
        spinnerRetention.setSelection(prefs.getInt("retention_index", 2));
        spinnerDepth.setSelection(prefs.getInt("depth_index", 1));
        spinnerDebateTone.setSelection(prefs.getInt("debate_tone_index", 0));
        updateAiState();
    }

    private void saveApiKey() {
        prefs.edit().putString("api_key", inputApiKey.getText().toString().trim()).apply();
        prefs.edit().putInt("language_index", spinnerLanguage.getSelectedItemPosition()).apply();
        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show();
    }

    private void updateAiState() {
        boolean enabled = switchAi.isChecked();
        layoutAiBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        articleAdapter.notifyDataSetChanged();
    }

    // CLEANED UP & ALPHABETICALLY SORTED TTS VOICES
    private void populateTtsVoices() {
        Set<Voice> voices = tts.getVoices();
        availableVoices.clear();

        List<VoiceItem> voiceItems = new ArrayList<>();

        if (voices != null) {
            for (Voice voice : voices) {
                if (voice.getLocale() == null) continue;
                
                String langName = voice.getLocale().getDisplayLanguage(Locale.ENGLISH);
                String country = voice.getLocale().getDisplayCountry(Locale.ENGLISH);
                
                String label = langName + (country.isEmpty() ? "" : " (" + country + ")") + " - " + voice.getName();
                if (voice.isNetworkConnectionRequired()) {
                    label += " [HD]";
                }

                voiceItems.add(new VoiceItem(voice, label, langName));
            }
        }

        // Sort voices alphabetically by Language Name
        Collections.sort(voiceItems, Comparator.comparing(a -> a.displayLabel));

        List<String> voiceLabels = new ArrayList<>();
        for (VoiceItem item : voiceItems) {
            availableVoices.add(item.voice);
            voiceLabels.add(item.displayLabel);
        }

        spinnerTtsVoice.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceLabels));
        spinnerTtsVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!availableVoices.isEmpty() && position < availableVoices.size()) {
                    tts.setVoice(availableVoices.get(position));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private static class VoiceItem {
        Voice voice;
        String displayLabel;
        String langName;

        VoiceItem(Voice voice, String displayLabel, String langName) {
            this.voice = voice;
            this.displayLabel = displayLabel;
            this.langName = langName;
        }
    }

    private void loadSavedFeeds() {
        Set<String> saved = prefs.getStringSet("feed_list", null);
        feedUrls.clear();
        if (saved != null) {
            feedUrls.addAll(saved);
        }
        renderFeedList();
        if (!feedUrls.isEmpty()) {
            fetchAllFeeds();
        } else {
            statusText.setText("No RSS feeds subscribed yet. Use Discover or Add Feed.");
        }
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

        listFeeds.setOnItemLongClickListener((parent, view, position, id) -> {
            String feedToRemove = feedUrls.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Remove Feed")
                    .setMessage("Remove " + feedToRemove + "?\nThis will remove all associated articles from memory.")
                    .setPositiveButton("Remove", (dialog, which) -> {
                        feedUrls.remove(position);
                        prefs.edit().putStringSet("feed_list", new HashSet<>(feedUrls)).apply();
                        masterArticles.removeIf(a -> a.feedUrl.equals(feedToRemove));
                        renderFeedList();
                        filterArticles();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
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
                            masterArticles.add(new Article(title, link, desc, url));
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
        if (index == 3) return;

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
        btnSummarizeSelected.setText("Summarize (" + count + ")");
        btnDebateSelected.setText("AI Debate (" + count + ")");
    }

    private boolean isAnyArticleSelected() {
        for (Article a : masterArticles) if (a.isSelected) return true;
        return false;
    }

    private void openCleanArticle(Article a) {
        stopTts();
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

                String fullText = cleanText.length() > 0 ? cleanText.toString() : Jsoup.parse(a.description).text();
                runOnUiThread(() -> {
                    readerContent.setText(fullText);
                    prepareTtsChunks(fullText);
                });
            } catch (Exception e) {
                runOnUiThread(() -> readerContent.setText("Failed to extract full article text.\nLink: " + a.link));
            }
        }).start();
    }

    // AI DEBATE LANGUAGE ENFORCEMENT FIX
    private void runAiAction(boolean isDebateMode) {
        String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            Toast.makeText(this, "Set API Key in Settings first", Toast.LENGTH_SHORT).show();
            return;
        }

        stopTts();
        switchView(viewSummaries);
        showSummaryDetail(isDebateMode ? "Generating AI Debate..." : "Generating Summary...", "Fetching content and contacting AI...", false);
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
                
                // Explicit target language handling
                String targetLang = languages[spinnerLanguage.getSelectedItemPosition()];
                int depthIdx = prefs.getInt("depth_index", 1);
                String depthConstraint = depthIdx == 0 ? "Keep it short and concise using tight bullet points." :
                                         depthIdx == 1 ? "Provide a medium-length structured summary with detailed bullet points and key takeaways." :
                                         "Provide a highly thorough, detailed deep dive with comprehensive analysis for every single article covered.";

                String tone = debateTones[prefs.getInt("debate_tone_index", 0)];

                String prompt;
                if (isDebateMode) {
                    // Force the language in the instruction regardless of article count
                    prompt = "CRITICAL INSTRUCTION: You MUST speak, debate, and write your ENTIRE response exclusively in " + targetLang + " language.\n\n" +
                             "Generate a lively AI Debate between Speaker A and Speaker B comparing these articles.\n" +
                             "Tone/Perspective: " + tone + ".\n" +
                             "Return ONLY a valid JSON object with keys 'title' (a short title in " + targetLang + ") and 'content' (the complete transcript in " + targetLang + " formatted cleanly with line breaks).\n\n" +
                             "Articles:\n" + payload.toString();
                } else {
                    prompt = "CRITICAL INSTRUCTION: You MUST write your ENTIRE summary exclusively in " + targetLang + " language.\n\n" +
                             "Summarize these articles in " + targetLang + ".\n" +
                             "Detail depth: " + depthConstraint + "\n" +
                             "Return ONLY a valid JSON object with keys 'title' (a short title in " + targetLang + ") and 'content' (the summary points in " + targetLang + " formatted cleanly with line breaks).\n\n" +
                             "Articles:\n" + payload.toString();
                }

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
                        
                        String parsedTitle = isDebateMode ? "AI Debate" : "News Summary";
                        String parsedContent = rawContent;

                        try {
                            int firstBrace = rawContent.indexOf("{");
                            int lastBrace = rawContent.lastIndexOf("}");
                            if (firstBrace != -1 && lastBrace != -1) {
                                JSONObject parsedJson = new JSONObject(rawContent.substring(firstBrace, lastBrace + 1));
                                parsedTitle = parsedJson.optString("title", parsedTitle);
                                parsedContent = parsedJson.optString("content", parsedJson.optString("summary", rawContent));
                            }
                        } catch (Exception ignored) {}

                        parsedContent = parsedContent.replaceAll("^\\{\\s*\"content\":\\s*\"", "")
                                                     .replaceAll("\"\\s*\\}$", "")
                                                     .replace("\\n", "\n")
                                                     .replace("\\\"", "\"");

                        String timestamp = new SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(new Date());
                        SummaryItem newSummary = new SummaryItem((isDebateMode ? "[Debate] " : "") + parsedTitle, timestamp, parsedContent);
                        
                        savedSummaries.add(0, newSummary);
                        saveSummariesToPrefs();

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
                        String finalContent = parsedContent;
                        runOnUiThread(() -> {
                            summaryProgressBar.setVisibility(View.GONE);
                            filterArticles();
                            showSummaryDetail(finalTitle, finalContent, true);
                        });
                    } else {
                        String err = res.body() != null ? res.body().string() : "Unknown response error";
                        runOnUiThread(() -> {
                            summaryProgressBar.setVisibility(View.GONE);
                            showSummaryDetail("API Error", "Error (" + res.code() + "): " + err, false);
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    summaryProgressBar.setVisibility(View.GONE);
                    showSummaryDetail("Failed", "Error running AI action: " + e.getLocalizedMessage(), false);
                });
            }
        }).start();
    }

    private void showSummariesList() {
        summaryHeaderTitle.setText("Saved Summaries");
        btnBackToSummariesList.setVisibility(View.GONE);
        summaryMediaControls.setVisibility(View.GONE);
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
            showSummaryDetail(item.title, item.content, true);
        });

        listSummaries.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Item")
                    .setMessage("Do you want to delete this saved entry?")
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

    private void showSummaryDetail(String title, String content, boolean enableControls) {
        summaryHeaderTitle.setText(title);
        textSummaryOutput.setText(content);

        btnBackToSummariesList.setVisibility(View.VISIBLE);
        summaryMediaControls.setVisibility(enableControls ? View.VISIBLE : View.GONE);
        listSummaries.setVisibility(View.GONE);
        scrollSummaryDetail.setVisibility(View.VISIBLE);

        if (enableControls) {
            prepareTtsChunks(cleanSpeakerPrefixesForTts(content));
        }
    }

    private String cleanSpeakerPrefixesForTts(String rawText) {
        return rawText.replaceAll("(?m)^(Speaker\\s+[A-Z]|Person\\s+[A-Z]|Host|Narrator|User):\\s*", "");
    }

    private void setupMediaPlayerClickListeners() {
        btnReaderPlayPause.setOnClickListener(v -> toggleTtsPlayPause(btnReaderPlayPause));
        btnReaderStop.setOnClickListener(v -> stopTts());
        btnReaderRewind.setOnClickListener(v -> rewindTts());
        btnReaderFastForward.setOnClickListener(v -> fastForwardTts());

        btnSummaryPlayPause.setOnClickListener(v -> toggleTtsPlayPause(btnSummaryPlayPause));
        btnSummaryStop.setOnClickListener(v -> stopTts());
        btnSummaryRewind.setOnClickListener(v -> rewindTts());
        btnSummaryFastForward.setOnClickListener(v -> fastForwardTts());
    }

    private void prepareTtsChunks(String text) {
        stopTts();
        activeTextToRead = text;
        activeParagraphChunks = text.split("\n+");
        currentSpeechChunkIndex = 0;
    }

    private void toggleTtsPlayPause(Button targetButton) {
        if (tts == null || activeParagraphChunks == null || activeParagraphChunks.length == 0) return;

        if (isTtsPlaying) {
            tts.stop();
            isTtsPlaying = false;
            isTtsPaused = true;
            targetButton.setText("▶ Play");
        } else {
            isTtsPlaying = true;
            isTtsPaused = false;
            targetButton.setText("⏸ Pause");
            speakCurrentChunk(targetButton);
        }
    }

    private void speakCurrentChunk(Button targetButton) {
        if (currentSpeechChunkIndex >= activeParagraphChunks.length) {
            stopTts();
            return;
        }

        String toSpeak = activeParagraphChunks[currentSpeechChunkIndex].trim();
        if (toSpeak.isEmpty()) {
            currentSpeechChunkIndex++;
            speakCurrentChunk(targetButton);
            return;
        }

        tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, "TTS_CHUNK_ID");
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                if (isTtsPlaying && !isTtsPaused) {
                    currentSpeechChunkIndex++;
                    runOnUiThread(() -> speakCurrentChunk(targetButton));
                }
            }

            @Override
            public void onError(String utteranceId) {}
        });
    }

    private void rewindTts() {
        if (currentSpeechChunkIndex > 0) {
            currentSpeechChunkIndex = Math.max(0, currentSpeechChunkIndex - 1);
            if (isTtsPlaying) {
                tts.stop();
                speakCurrentChunk(viewReader.getVisibility() == View.VISIBLE ? btnReaderPlayPause : btnSummaryPlayPause);
            }
        }
    }

    private void fastForwardTts() {
        if (activeParagraphChunks != null && currentSpeechChunkIndex < activeParagraphChunks.length - 1) {
            currentSpeechChunkIndex++;
            if (isTtsPlaying) {
                tts.stop();
                speakCurrentChunk(viewReader.getVisibility() == View.VISIBLE ? btnReaderPlayPause : btnSummaryPlayPause);
            }
        }
    }

    private void stopTts() {
        if (tts != null) {
            tts.stop();
        }
        isTtsPlaying = false;
        isTtsPaused = false;
        currentSpeechChunkIndex = 0;

        btnReaderPlayPause.setText("▶ Play");
        btnSummaryPlayPause.setText("▶ Play");
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

            holder.itemView.setOnClickListener(v -> {
                if (isAnyArticleSelected()) {
                    a.isSelected = !a.isSelected;
                    notifyItemChanged(pos);
                    updateSelectionCounter();
                } else {
                    if (!a.isRead) {
                        a.isRead = true;
                        a.readTimestamp = System.currentTimeMillis();
                    }
                    filterArticles();
                    openCleanArticle(a);
                }
            });

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
