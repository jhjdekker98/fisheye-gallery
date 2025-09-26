package com.jhjdekker98.fisheyegallery.config.smb;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.module.AppGlideModule;
import java.io.InputStream;

@GlideModule
public final class SmbGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.append(Uri.class, InputStream.class, new ModelLoaderFactory<Uri, InputStream>() {
            @NonNull
            @Override
            public ModelLoader<Uri, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
                return new SmbUriLoader(context);
            }

            @Override
            public void teardown() {
            }
        });
    }
}
