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
import java.util.Calendar;
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
        String title, link, description, feedUrl, feedTitle;
        boolean isRead = false;
        boolean isSelected = false;
        long readTimestamp = 0;
        long pubTimestamp = System.currentTimeMillis();

        Article(String title, String link, String description, String feedUrl, String feedTitle, long pubTimestamp) {
            this.title = title;
            this.link = link;
            this.description = description;
            this.feedUrl = feedUrl;
            this.feedTitle = feedTitle;
            this.pubTimestamp = pubTimestamp > 0 ? pubTimestamp : System.currentTimeMillis();
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
    private Button btnTabNew, btnTabRead, btnAddFeed, btnDiscoverFeeds, btnSaveApiKey;
    private Button btnSummarizeSelected, btnDebateSelected, btnSelectAllArticles;
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
        btnSelectAllArticles = findViewById(R.id.btnSelectAllArticles);
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
        
        btnSelectAllArticles.setOnClickListener(v -> toggleSelectAllArticles());

        switchAi.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("ai_enabled", isChecked).apply();
            updateAiState();
        });

        switchAutoMarkRead.setOnCheckedChangeListener((btn, isChecked) -> 
            prefs.edit().putBoolean("auto_mark_read", isChecked).apply()
        );

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                prefs.edit().putInt("language_index", pos).apply();
                populateTtsVoices();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

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

        // Setup TTS Engine
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
        surinameFeeds.add(new FeedInfo("Starnieuws", "https://www.starnieuws.com/rss/starnieuws.rss", "News"));
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

    private void showDiscoverFeedsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_discover_feeds, null);
        builder.setView(dialogView);

        Spinner spinnerCountry = dialogView.findViewById(R.id.spinnerCountry);
        EditText inputCustomCountry = dialogView.findViewById(R.id.inputCustomCountry);
        Button btnSearchWebFeeds = dialogView.findViewById(R.id.btnSearchWebFeeds);
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
                saveFeedUrls();
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
        prefs.edit()
                .putString("api_key", inputApiKey.getText().toString().trim())
                .putInt("language_index", spinnerLanguage.getSelectedItemPosition())
                .putInt("retention_index", spinnerRetention.getSelectedItemPosition())
                .putInt("depth_index", spinnerDepth.getSelectedItemPosition())
                .putInt("debate_tone_index", spinnerDebateTone.getSelectedItemPosition())
                .apply();
        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show();
    }

    private void updateAiState() {
        boolean enabled = switchAi.isChecked();
        layoutAiBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        articleAdapter.notifyDataSetChanged();
    }

    private void populateTtsVoices() {
        if (tts == null) return;
        
        Set<Voice> voices = tts.getVoices();
        availableVoices.clear();

        List<VoiceItem> voiceItems = new ArrayList<>();
        int selectedLangIdx = prefs.getInt("language_index", 0);
        String targetLangCode = selectedLangIdx == 1 ? "es" :
                                selectedLangIdx == 2 ? "nl" :
                                selectedLangIdx == 3 ? "fr" :
                                selectedLangIdx == 4 ? "de" : "en";

        Voice savedOrMatchingVoice = null;

        if (voices != null) {
            for (Voice voice : voices) {
                if (voice.getLocale() == null) continue;
                
                String langCode = voice.getLocale().getLanguage();
                String langName = voice.getLocale().getDisplayLanguage(Locale.ENGLISH);
                String country = voice.getLocale().getDisplayCountry(Locale.ENGLISH);
                
                if (langCode.equalsIgnoreCase(targetLangCode)) {
                    String label = langName + (country.isEmpty() ? "" : " (" + country + ")") + " - " + voice.getName();
                    if (voice.isNetworkConnectionRequired()) {
                        label += " [HD]";
                    }

                    VoiceItem item = new VoiceItem(voice, label, langName);
                    voiceItems.add(item);

                    if (savedOrMatchingVoice == null) {
                        savedOrMatchingVoice = voice;
                    }
                }
            }
        }

        Collections.sort(voiceItems, Comparator.comparing(a -> a.displayLabel));

        List<String> voiceLabels = new ArrayList<>();
        for (VoiceItem item : voiceItems) {
            availableVoices.add(item.voice);
            voiceLabels.add(item.displayLabel);
        }

        if (voiceLabels.isEmpty()) {
            voiceLabels.add("Default System Voice (" + targetLangCode.toUpperCase() + ")");
        }

        spinnerTtsVoice.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceLabels));
        
        if (savedOrMatchingVoice != null) {
            tts.setVoice(savedOrMatchingVoice);
        }

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
        if (saved != null && !saved.isEmpty()) {
            feedUrls.addAll(saved);
        }
        renderFeedList();
        if (!feedUrls.isEmpty()) {
            fetchAllFeeds();
        } else {
            statusText.setText("No RSS feeds subscribed yet. Use Discover or Add Feed.");
        }
    }

    private void saveFeedUrls() {
        prefs.edit().putStringSet("feed_list", new HashSet<>(feedUrls)).apply();
    }

    private void addFeed() {
        String url = inputFeedUrl.getText().toString().trim();
        if (!url.isEmpty() && !feedUrls.contains(url)) {
            feedUrls.add(url);
            saveFeedUrls();
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
                        saveFeedUrls();
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

        new Thread(() -> {
            List<Article> fetchedList = new ArrayList<>();
            for (String url : feedUrls) {
                try {
                    Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(8000).get();
                    String feedTitle = doc.select("channel > title, feed > title").text();
                    if (feedTitle.isEmpty()) feedTitle = url.replaceAll("https?://(www\\.)?", "").replaceAll("/.*", "");

                    Elements items = doc.select("item");
                    if (items.isEmpty()) items = doc.select("entry");

                    for (Element item : items) {
                        String title = item.select("title").text();
                        String link = item.select("link").text();
                        if (link.isEmpty()) link = item.select("link").attr("href");
                        String desc = item.select("description, summary").text();
                        String pubDateStr = item.select("pubDate, published, updated").text();

                        long timestamp = System.currentTimeMillis();
                        if (!pubDateStr.isEmpty()) {
                            try {
                                timestamp = new Date(pubDateStr).getTime();
                            } catch (Exception ignored) {}
                        }

                        if (!title.isEmpty()) {
                            fetchedList.add(new Article(title, link, desc, url, feedTitle, timestamp));
                        }
                    }
                } catch (Exception ignored) {}
            }

            runOnUiThread(() -> {
                for (Article newArt : fetchedList) {
                    boolean exists = false;
                    for (Article existing : masterArticles) {
                        if (existing.link.equals(newArt.link)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        masterArticles.add(newArt);
                    }
                }

                // Sort master list descending by published time
                Collections.sort(masterArticles, (a1, a2) -> Long.compare(a2.pubTimestamp, a1.pubTimestamp));

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

    private void toggleSelectAllArticles() {
        boolean allSelected = true;
        for (Article a : displayedArticles) {
            if (!a.isSelected) {
                allSelected = false;
                break;
            }
        }

        for (Article a : displayedArticles) {
            a.isSelected = !allSelected;
        }

        articleAdapter.notifyDataSetChanged();
        updateSelectionCounter();
    }

    private void updateSelectionCounter() {
        int count = 0;
        for (Article a : masterArticles) if (a.isSelected) count++;
        
        btnSummarizeSelected.setText("Summarize (" + count + ")");
        btnDebateSelected.setText("AI Debate (" + count + ")");

        if (btnSelectAllArticles != null) {
            btnSelectAllArticles.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            boolean allDisplayedSelected = !displayedArticles.isEmpty();
            for (Article a : displayedArticles) {
                if (!a.isSelected) {
                    allDisplayedSelected = false;
                    break;
                }
            }
            btnSelectAllArticles.setText(allDisplayedSelected ? "Deselect All" : "Select All");
        }
    }

    private boolean isAnyArticleSelected() {
        for (Article a : masterArticles) {
            if (a.isSelected) return true;
        }
        return false;
    }

    private void runAiAction(boolean isDebate) {
        if (!isAnyArticleSelected()) {
            Toast.makeText(this, "Please select at least one article.", Toast.LENGTH_SHORT).show();
            return;
        }

        String apiKey = prefs.getString("api_key", "").trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "API Key missing! Set it in Settings.", Toast.LENGTH_LONG).show();
            return;
        }

        switchView(viewSummaries);
        listSummaries.setVisibility(View.GONE);
        scrollSummaryDetail.setVisibility(View.VISIBLE);
        summaryProgressBar.setVisibility(View.VISIBLE);
        summaryHeaderTitle.setText(isDebate ? "Generating AI Debate..." : "Generating Summary...");
        textSummaryOutput.setText("");

        new Thread(() -> {
            StringBuilder combinedContent = new StringBuilder();
            for (Article a : masterArticles) {
                if (a.isSelected) {
                    combinedContent.append("Title: ").append(a.title).append("\n");
                    combinedContent.append("Source: ").append(a.feedTitle).append("\n");
                    combinedContent.append("Content: ").append(a.description).append("\n\n");
                }
            }

            String lang = languages[prefs.getInt("language_index", 0)];
            String depth = depthOptions[prefs.getInt("depth_index", 1)];
            String tone = debateTones[prefs.getInt("debate_tone_index", 0)];

            String prompt;
            if (isDebate) {
                prompt = "Perform a multi-perspective analysis/debate based on the following news articles. " +
                        "Language: " + lang + ". Tone/Angle: " + tone + ". Detail Level: " + depth + ".\n\nArticles:\n" + combinedContent;
            } else {
                prompt = "Summarize the following news articles into a cohesive overview. " +
                        "Language: " + lang + ". Detail Level: " + depth + ".\n\nArticles:\n" + combinedContent;
            }

            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build();

                JSONObject jsonBody = new JSONObject();
                JSONArray contentsArr = new JSONArray();
                JSONObject partsObj = new JSONObject();
                JSONArray partsArr = new JSONArray();
                JSONObject textObj = new JSONObject();

                textObj.put("text", prompt);
                partsArr.put(textObj);
                partsObj.put("parts", partsArr);
                contentsArr.put(partsObj);
                jsonBody.put("contents", contentsArr);

                RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String resStr = response.body().string();
                    JSONObject resJson = new JSONObject(resStr);
                    String output = resJson.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");

                    runOnUiThread(() -> {
                        summaryProgressBar.setVisibility(View.GONE);
                        summaryHeaderTitle.setText(isDebate ? "AI Debate Analysis" : "AI Summary");
                        textSummaryOutput.setText(output);
                        saveSummaryItem(isDebate ? "AI Debate" : "AI Summary", output);
                    });
                } else {
                    runOnUiThread(() -> {
                        summaryProgressBar.setVisibility(View.GONE);
                        textSummaryOutput.setText("Failed to generate response. Check API Key or connectivity.");
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    summaryProgressBar.setVisibility(View.GONE);
                    textSummaryOutput.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void saveSummaryItem(String titlePrefix, String content) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        String title = titlePrefix + " - " + timestamp;
        savedSummaries.add(0, new SummaryItem(title, timestamp, content));

        // Save to SharedPreferences
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
        String jsonStr = prefs.getString("saved_summaries_json", null);
        if (jsonStr != null) {
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
    }

    private void showSummariesList() {
        listSummaries.setVisibility(View.VISIBLE);
        scrollSummaryDetail.setVisibility(View.GONE);

        List<String> titles = new ArrayList<>();
        for (SummaryItem s : savedSummaries) {
            titles.add(s.title);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        listSummaries.setAdapter(adapter);

        listSummaries.setOnItemClickListener((parent, view, position, id) -> {
            SummaryItem selected = savedSummaries.get(position);
            summaryHeaderTitle.setText(selected.title);
            textSummaryOutput.setText(selected.content);
            listSummaries.setVisibility(View.GONE);
            scrollSummaryDetail.setVisibility(View.VISIBLE);
        });
    }

    private void setupMediaPlayerClickListeners() {
        // Reader Media
        btnReaderPlayPause.setOnClickListener(v -> toggleTtsPlayPause(readerContent.getText().toString()));
        btnReaderStop.setOnClickListener(v -> stopTts());
        btnReaderRewind.setOnClickListener(v -> skipTtsChunk(-1));
        btnReaderFastForward.setOnClickListener(v -> skipTtsChunk(1));

        // Summary Media
        btnSummaryPlayPause.setOnClickListener(v -> toggleTtsPlayPause(textSummaryOutput.getText().toString()));
        btnSummaryStop.setOnClickListener(v -> stopTts());
        btnSummaryRewind.setOnClickListener(v -> skipTtsChunk(-1));
        btnSummaryFastForward.setOnClickListener(v -> skipTtsChunk(1));
    }

    private void toggleTtsPlayPause(String fullText) {
        if (!isTtsPlaying) {
            activeTextToRead = fullText;
            activeParagraphChunks = fullText.split("\n+");
            currentSpeechChunkIndex = 0;
            speakNextChunk();
        } else if (isTtsPaused) {
            isTtsPaused = false;
            speakNextChunk();
        } else {
            isTtsPaused = true;
            tts.stop();
        }
    }

    private void speakNextChunk() {
        if (activeParagraphChunks == null || currentSpeechChunkIndex >= activeParagraphChunks.length) {
            stopTts();
            return;
        }

        isTtsPlaying = true;
        String chunk = activeParagraphChunks[currentSpeechChunkIndex];
        tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "chunk_" + currentSpeechChunkIndex);
    }

    private void skipTtsChunk(int direction) {
        if (activeParagraphChunks == null) return;
        currentSpeechChunkIndex += direction;
        if (currentSpeechChunkIndex < 0) currentSpeechChunkIndex = 0;
        if (currentSpeechChunkIndex >= activeParagraphChunks.length) {
            stopTts();
            return;
        }
        speakNextChunk();
    }

    private void stopTts() {
        if (tts != null) {
            tts.stop();
        }
        isTtsPlaying = false;
        isTtsPaused = false;
        currentSpeechChunkIndex = 0;
    }

    // RecyclerView Adapter
    private class ArticleAdapter extends RecyclerView.Adapter<ArticleViewHolder> {

        @NonNull
        @Override
        public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rss_article, parent, false);
            return new ArticleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ArticleViewHolder holder, int position) {
            Article article = displayedArticles.get(position);

            holder.title.setText(article.title);
            
            if (holder.feedLabel != null) {
                holder.feedLabel.setText(article.feedTitle);
            }

            if (holder.snippet != null) {
                holder.snippet.setText(Jsoup.parse(article.description).text());
            }

            if (holder.dateHeader != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault());
                holder.dateHeader.setText(sdf.format(new Date(article.pubTimestamp)));
            }

            boolean aiEnabled = switchAi.isChecked();
            holder.checkBox.setVisibility(aiEnabled ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(article.isSelected);

            holder.checkBox.setOnClickListener(v -> {
                article.isSelected = holder.checkBox.isChecked();
                updateSelectionCounter();
            });

            holder.itemView.setOnClickListener(v -> {
                if (prefs.getBoolean("auto_mark_read", true)) {
                    article.isRead = true;
                    article.readTimestamp = System.currentTimeMillis();
                }

                readerTitle.setText(article.title);
                readerContent.setText(Jsoup.parse(article.description).text());
                switchView(viewReader);
            });
        }

        @Override
        public int getItemCount() {
            return displayedArticles.size();
        }
    }

    public static class ArticleViewHolder extends RecyclerView.ViewHolder {
        public TextView title, snippet, feedLabel, dateHeader;
        public CheckBox checkBox;

        public ArticleViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.articleTitle);
            snippet = itemView.findViewById(R.id.articleSnippet);
            feedLabel = itemView.findViewById(R.id.articleFeedLabel);
            dateHeader = itemView.findViewById(R.id.articleDateHeader);
            checkBox = itemView.findViewById(R.id.articleCheckBox);
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
