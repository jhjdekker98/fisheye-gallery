package com.jhjdekker98.fisheyegallery.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.model.GalleryItem;
import java.util.ArrayList;
import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_IMAGE = 1;

    private final List<GalleryItem> items = new ArrayList<>();

    public void submitList(List<GalleryItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return (items.get(position) instanceof GalleryItem.Header)
                ? VIEW_TYPE_HEADER
                : VIEW_TYPE_IMAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_image, parent, false);
            return new ImageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GalleryItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(((GalleryItem.Header) item).dateLabel);
        } else if (holder instanceof ImageViewHolder) {
            ((ImageViewHolder) holder).bind(((GalleryItem.Image) item).uri);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        HeaderViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.textHeader);
        }

        void bind(String date) {
            textView.setText(date);
        }
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;

        ImageViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageButton);
        }

        void bind(Uri uri) {
            Glide.with(imageView.getContext())
                    .load(uri)
                    .centerCrop()
                    .into(imageView);
        }
    }
}


