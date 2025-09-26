package com.jhjdekker98.fisheyegallery.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.activity.FullImageActivity;
import com.jhjdekker98.fisheyegallery.model.GalleryItem;
import com.jhjdekker98.fisheyegallery.util.CollectionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_IMAGE = 1;
    private static final Set<String> ONLINE_SCHEMES = CollectionUtil.setOf(
            "http",
            "https",
            "smb",
            "cifs"); //TODO: Find reliable source and expand

    private final List<GalleryItem> items = new ArrayList<>();

    private boolean isLocal(Uri uri) {
        // Only used for local filesystem
        if (uri.getScheme().equals("file")) {
            return true;
        }
        // Used for web resources
        if (ONLINE_SCHEMES.contains(uri.getScheme())) {
            return false;
        }
        // MediaStore, SAF, or through an app/intent
        if (uri.getScheme().equals("content")) {
            final String authority = uri.getAuthority();
            if (authority != null && authority.startsWith("media")) {
                return true;
            }
            // Unknown provider - assume cloud
            Log.d("MediaAdapter", "isLocal - unknown provider authority: `" + authority + "`");
            return false;
        }
        return false;
    }

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
            final ImageViewHolder imageHolder = (ImageViewHolder) holder;
            final GalleryItem.Image imageItem = (GalleryItem.Image) item;
            final boolean isLocal = isLocal(imageItem.uri);

            imageHolder.bind(imageItem.uri, isLocal);

            imageHolder.imageView.setOnClickListener(v -> {
                final Intent intent = new Intent(v.getContext(), FullImageActivity.class);
                intent.putExtra(FullImageActivity.EXTRA_IMAGE_URI, imageItem.uri);
                intent.putExtra(FullImageActivity.EXTRA_IMAGE_LOCAL, isLocal);

                final Drawable thumbDrawable = imageHolder.imageView.getDrawable();
                if (thumbDrawable instanceof BitmapDrawable) {
                    final Bitmap bitmap = ((BitmapDrawable) thumbDrawable).getBitmap();
                    intent.putExtra(FullImageActivity.EXTRA_THUMBNAIL, bitmap);
                }

                final ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        (Activity) v.getContext(),
                        imageHolder.imageView,
                        v.getContext().getString(R.string.image_transition));
                v.getContext().startActivity(intent, options.toBundle());
            });
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
        private final ImageView cloudIcon;

        ImageViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageButton);
            cloudIcon = itemView.findViewById(R.id.cloudIcon);
        }

        void bind(Uri uri, boolean isLocal) {
            Glide.with(imageView.getContext())
                    .load(uri)
                    .centerCrop()
                    .into(imageView);

            cloudIcon.setVisibility(isLocal ? View.GONE : View.VISIBLE);
        }
    }
}
