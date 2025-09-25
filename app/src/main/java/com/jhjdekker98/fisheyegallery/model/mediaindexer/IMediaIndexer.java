package com.jhjdekker98.fisheyegallery.model.mediaindexer;

import android.net.Uri;
import java.util.List;

public interface IMediaIndexer {
    void startIndexing(Callback callback);

    void stop();

    interface Callback {
        void onMediaFound(List<Uri> newUris);

        void onComplete();
    }
}
