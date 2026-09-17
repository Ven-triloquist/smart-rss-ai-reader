package com.example.smartrssai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder> {

    private List<Article> articles = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private boolean isMultiSelectMode = false;
    private OnArticleSelectionListener listener;

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

        // Manage CheckBox visibility & checked state
        if (isMultiSelectMode) {
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(selectedPositions.contains(position));
        } else {
            holder.checkBox.setVisibility(View.GONE);
            holder.checkBox.setChecked(false);
        }

        // Regular click behavior
        holder.itemView.setOnClickListener(v -> {
            int adapterPos = holder.getAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) return;

            if (isMultiSelectMode) {
                toggleSelection(adapterPos);
            } else if (listener != null) {
                listener.onItemClick(articles.get(adapterPos), adapterPos);
            }
        });

        // Long click behavior triggers selection mode
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

    public void clearSelection() {
        selectedPositions.clear();
        isMultiSelectMode = false;
        notifyDataSetChanged();

        if (listener != null) {
            listener.onSelectionCountChanged(0);
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
