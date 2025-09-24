package com.jhjdekker98.fisheyegallery.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.jhjdekker98.fisheyegallery.model.FileEntry;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private final List<FileEntry> fileEntries;

    public FileAdapter(List<FileEntry> fileEntries) {
        this.fileEntries = fileEntries;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileEntry entry = fileEntries.get(position);

        // Indent based on depth
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < entry.getDepth(); i++) {
            prefix.append("  "); // two spaces per depth
        }

        // Add "/" for directories
        String suffix = entry.getFile().isDirectory() ? "/" : "";

        holder.textView.setText(prefix + entry.getFile().getName() + suffix);
    }

    @Override
    public int getItemCount() {
        return fileEntries.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
