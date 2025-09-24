package com.jhjdekker98.fisheyegallery.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.model.FileEntry;
import com.jhjdekker98.fisheyegallery.util.ThumbnailManager;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
    private List<FileEntry> entries;
    private Context context;
    private ThumbnailManager thumbnailManager;

    public ImageAdapter(Context context, List<FileEntry> entries, ThumbnailManager thumbnailManager) {
        this.context = context;
        this.entries = entries;
        this.thumbnailManager = thumbnailManager;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int ViewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final FileEntry entry = entries.get(position);
        holder.imageButton.setImageResource(R.drawable.noimg);
        final Bitmap thumb = thumbnailManager.getThumbnail(context.getApplicationContext(), entry.getFile().getUri());
        if (thumb != null) {
            holder.imageButton.setImageBitmap(thumb);
        }
        holder.imageButton.setOnClickListener(v -> {
            Toast.makeText(context, "Clicked: " + entry.getFile().getName(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageButton imageButton;

        public ViewHolder(View view) {
            super(view);
            imageButton = view.findViewById(R.id.imageButton);
        }
    }
}
