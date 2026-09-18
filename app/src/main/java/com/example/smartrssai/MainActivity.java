package com.example.smartrssai;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    // Views
    private LinearLayout viewFeeds, viewArticles, viewReader, viewSummaries, viewSettings;
    private Button navFeeds, navArticles, navSummaries, navSettings;
    private Button btnSelectAllArticles, btnSummarizeSelected, btnDebateSelected;
    private RecyclerView recyclerArticles;
    private EditText inputApiKey;
    private Button btnSaveApiKey;

    // Adapter & Data
    private ArticleAdapter articleAdapter;
    private List<Article> articleList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupNavigation();
        setupRecyclerView();
        setupAiBarActions();
        
        // Load mock data or RSS feeds
        loadSampleArticles();
    }

    private void initViews() {
        // Navigation buttons
        navFeeds = findViewById(R.id.navFeeds);
        navArticles = findViewById(R.id.navArticles);
        navSummaries = findViewById(R.id.navSummaries);
        navSettings = findViewById(R.id.navSettings);

        // Content views
        viewFeeds = findViewById(R.id.viewFeeds);
        viewArticles = findViewById(R.id.viewArticles);
        viewReader = findViewById(R.id.viewReader);
        viewSummaries = findViewById(R.id.viewSummaries);
        viewSettings = findViewById(R.id.viewSettings);

        // AI Bar & RecyclerView
        btnSelectAllArticles = findViewById(R.id.btnSelectAllArticles);
        btnSummarizeSelected = findViewById(R.id.btnSummarizeSelected);
        btnDebateSelected = findViewById(R.id.btnDebateSelected);
        recyclerArticles = findViewById(R.id.recyclerArticles);

        // Settings
        inputApiKey = findViewById(R.id.inputApiKey);
        btnSaveApiKey = findViewById(R.id.btnSaveApiKey);
    }

    private void setupNavigation() {
        navFeeds.setOnClickListener(v -> showView(viewFeeds));
        navArticles.setOnClickListener(v -> showView(viewArticles));
        navSummaries.setOnClickListener(v -> showView(viewSummaries));
        navSettings.setOnClickListener(v -> showView(viewSettings));

        if (btnSaveApiKey != null) {
            btnSaveApiKey.setOnClickListener(v -> {
                String key = inputApiKey.getText().toString().trim();
                Toast.makeText(this, "OpenRouter API Key saved", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showView(View targetView) {
        viewFeeds.setVisibility(View.GONE);
        viewArticles.setVisibility(View.GONE);
        viewReader.setVisibility(View.GONE);
        viewSummaries.setVisibility(View.GONE);
        viewSettings.setVisibility(View.GONE);

        targetView.setVisibility(View.VISIBLE);
    }

    private void setupRecyclerView() {
        recyclerArticles.setLayoutManager(new LinearLayoutManager(this));
        
        articleAdapter = new ArticleAdapter(new ArticleAdapter.OnArticleSelectionListener() {
            @Override
            public void onItemClick(Article article, int position) {
                // Single click opens reader view
                showView(viewReader);
                TextView readerTitle = findViewById(R.id.readerTitle);
                TextView readerContent = findViewById(R.id.readerContent);
                if (readerTitle != null) readerTitle.setText(article.getTitle());
                if (readerContent != null) readerContent.setText(article.getContent());
            }

            @Override
            public void onSelectionModeStarted() {
                // Reveal "Select All" button only on long-press
                if (btnSelectAllArticles != null) {
                    btnSelectAllArticles.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onSelectionCountChanged(int count) {
                btnSummarizeSelected.setText("Summarize (" + count + ")");
                btnDebateSelected.setText("AI Debate (" + count + ")");

                if (count == 0 && btnSelectAllArticles != null) {
                    btnSelectAllArticles.setVisibility(View.GONE);
                }
            }
        });

        recyclerArticles.setAdapter(articleAdapter);
    }

    private void setupAiBarActions() {
        if (btnSelectAllArticles != null) {
            btnSelectAllArticles.setOnClickListener(v -> articleAdapter.selectAll());
        }

        btnSummarizeSelected.setOnClickListener(v -> {
            List<Article> selected = articleAdapter.getSelectedArticles();
            if (selected.isEmpty()) {
                Toast.makeText(this, "Long-press an article to select first", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Summarizing " + selected.size() + " articles via OpenRouter...", Toast.LENGTH_SHORT).show();
            }
        });

        btnDebateSelected.setOnClickListener(v -> {
            List<Article> selected = articleAdapter.getSelectedArticles();
            if (selected.isEmpty()) {
                Toast.makeText(this, "Long-press an article to select first", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Starting AI Debate for " + selected.size() + " articles...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSampleArticles() {
        articleList.add(new Article("Sample Article 1", "Content for article 1", "https://example.com/1"));
        articleList.add(new Article("Sample Article 2", "Content for article 2", "https://example.com/2"));
        articleAdapter.setArticles(articleList);
    }

    // =========================================================================
    // Inner Data Model Class
    // =========================================================================
    public static class Article {
        private String title;
        private String content;
        private String url;

        public Article(String title, String content, String url) {
            this.title = title;
            this.content = content;
            this.url = url;
        }

        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getUrl() { return url; }
    }

    // =========================================================================
    // Inner Adapter Class
    // =========================================================================
    public static class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder> {

        private List<Article> articles = new ArrayList<>();
        private final Set<Integer> selectedPositions = new HashSet<>();
        private boolean isMultiSelectMode = false;
        private final OnArticleSelectionListener listener;

        public interface OnArticleSelectionListener {
            void onItemClick(Article article, int position);
            void onSelectionModeStarted();
            void onSelectionCountChanged(int count);
        }

        public ArticleAdapter(OnArticleSelectionListener listener) {
            this.listener = listener;
        }

        public void setArticles(List<Article> articles) {
            this.articles = articles;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_article, parent, false);
            return new ArticleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ArticleViewHolder holder, int position) {
            Article article = articles.get(position);
            holder.titleText.setText(article.getTitle());

            if (isMultiSelectMode) {
                holder.checkBox.setVisibility(View.VISIBLE);
                holder.checkBox.setChecked(selectedPositions.contains(position));
            } else {
                holder.checkBox.setVisibility(View.GONE);
                holder.checkBox.setChecked(false);
            }

            holder.itemView.setOnClickListener(v -> {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) return;

                if (isMultiSelectMode) {
                    toggleSelection(adapterPos);
                } else if (listener != null) {
                    listener.onItemClick(articles.get(adapterPos), adapterPos);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) return false;

                if (!isMultiSelectMode) {
                    isMultiSelectMode = true;
                    if (listener != null) {
                        listener.onSelectionModeStarted();
                    }
                }
                toggleSelection(adapterPos);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return articles.size();
        }

        public void toggleSelection(int position) {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position);
            } else {
                selectedPositions.add(position);
            }
            notifyItemChanged(position);

            if (listener != null) {
                listener.onSelectionCountChanged(selectedPositions.size());
            }
        }

        public void selectAll() {
            selectedPositions.clear();
            for (int i = 0; i < articles.size(); i++) {
                selectedPositions.add(i);
            }
            notifyDataSetChanged();

            if (listener != null) {
                listener.onSelectionCountChanged(selectedPositions.size());
            }
        }

        public List<Article> getSelectedArticles() {
            List<Article> selected = new ArrayList<>();
            for (int pos : selectedPositions) {
                if (pos >= 0 && pos < articles.size()) {
                    selected.add(articles.get(pos));
                }
            }
            return selected;
        }

        public static class ArticleViewHolder extends RecyclerView.ViewHolder {
            TextView titleText;
            CheckBox checkBox;

            public ArticleViewHolder(@NonNull View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.textArticleTitle);
                checkBox = itemView.findViewById(R.id.checkArticleSelect);
            }
        }
    }
}
