package com.jhjdekker98.fisheyegallery.adapter;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.jhjdekker98.fisheyegallery.R;
import java.util.ArrayList;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
    private final Context context;
    private final List<DocumentFile> items = new ArrayList<>();
    private final OnClickListener onClick;

    public ImageAdapter(Context context, OnClickListener onClick) {
        this.context = context;
        this.onClick = onClick;
    }

    public void setItems(List<DocumentFile> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int ViewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final DocumentFile file = items.get(position);
        final Uri uri = file.getUri();

        Glide.with(holder.imageButton.getContext())
                .load(uri)
                .placeholder(R.drawable.noimg)
                .centerCrop()
                .into(holder.imageButton);

        holder.itemView.setOnClickListener(v -> {
            if (onClick == null) {
                return;
            }
            onClick.onItemClick(file);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnClickListener {
        void onItemClick(DocumentFile file);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageButton imageButton;

        public ViewHolder(View view) {
            super(view);
            imageButton = view.findViewById(R.id.imageButton);
        }
    }
}
