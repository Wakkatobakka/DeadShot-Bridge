package com.wakka.bridge;

import android.app.Activity;
import android.net.Uri;

/** Optional bridge capability for user-supplied local game-data imports. */
public interface BridgeImportBackend {
    String importButtonLabel();
    String importDialogTitle();
    String importDialogMessage();
    void importPayload(Activity activity, Uri uri) throws Exception;
}
