package com.jhjdekker98.fisheyegallery.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.config.smb.SmbTransferHelper;
import com.jhjdekker98.fisheyegallery.config.smb.TransferCallback;

public class FullImageActivity extends AppCompatActivity {
    public static final String EXTRA_IMAGE_URI = "extra_image_uri";
    public static final String EXTRA_IMAGE_LOCAL = "extra_image_local";
    public static final String EXTRA_THUMBNAIL = "thumbnail_drawable";
    private ImageView fullImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up shared element zoom transition
        getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
        final Transition zoom = TransitionInflater.from(this)
                .inflateTransition(R.transition.zoom_shared_element);
        getWindow().setSharedElementEnterTransition(zoom);
        getWindow().setSharedElementReturnTransition(zoom);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_image);

        final SmbTransferHelper smbTransferHelper = new SmbTransferHelper(this, new TransferCallback() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String message, Exception e) {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        fullImageView = findViewById(R.id.fullImageView);

        ImageButton btnShare = findViewById(R.id.btnShare);
        ImageButton btnEdit = findViewById(R.id.btnEdit);
        ImageButton btnDownload = findViewById(R.id.btnDownload);
        ImageButton btnMoveToCloud = findViewById(R.id.btnMoveToCloud);

        final Uri imageUri;
        final boolean isLocal;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
            isLocal = getIntent().getBooleanExtra(EXTRA_IMAGE_LOCAL, true);
        } else {
            imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI, Uri.class);
            isLocal = getIntent().getBooleanExtra(EXTRA_IMAGE_LOCAL, true);
        }

        // Load image
        if (imageUri != null) {
            // Make SAF image files removable
            if (imageUri.toString().startsWith("content://com.android.")) {
                final int takeFlags = getIntent().getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(imageUri, takeFlags);
            }

            final Bitmap thumbnailBitmap = getIntent().getParcelableExtra(EXTRA_THUMBNAIL);
            final Drawable thumbnail = thumbnailBitmap == null ?
                    ContextCompat.getDrawable(this, R.drawable.noimg) :
                    new BitmapDrawable(getResources(), thumbnailBitmap);

            fullImageView.setImageDrawable(thumbnail);

            supportPostponeEnterTransition();
            getWindow().getSharedElementEnterTransition().addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition) {
                }

                @Override
                public void onTransitionEnd(@NonNull Transition transition) {
                    Glide.with(FullImageActivity.this)
                            .load(imageUri)
                            .placeholder(thumbnail)
                            .error(R.drawable.md_close_24px)
                            .into(fullImageView);
                    transition.removeListener(this);
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                }

                @Override
                public void onTransitionPause(Transition transition) {
                }

                @Override
                public void onTransitionResume(Transition transition) {
                }
            });

            supportStartPostponedEnterTransition();
        }

        if (!isLocal) {
            btnDownload.setVisibility(View.VISIBLE);
            btnDownload.setOnClickListener(v -> smbTransferHelper.downloadToLocal(this, imageUri));
        } else {
            btnMoveToCloud.setVisibility(View.VISIBLE);
            btnMoveToCloud.setOnClickListener(v -> smbTransferHelper.uploadToSmb(this, imageUri));
        }

        btnShare.setOnClickListener(v -> {
            if (imageUri == null) return;
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, imageUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(Intent.createChooser(intent, "Share image via"));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No app available to share image", Toast.LENGTH_SHORT).show();
            }
        });

        btnEdit.setOnClickListener(v -> {
            if (imageUri == null) return;
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(imageUri, getContentResolver().getType(imageUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(Intent.createChooser(intent, "Edit image"));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No app available to edit image", Toast.LENGTH_SHORT).show();
            }
        });
    }
}

