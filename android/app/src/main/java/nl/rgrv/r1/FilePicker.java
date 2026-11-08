// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Gerard Braad (Regroove | RGRV)
package nl.rgrv.r1;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * ACTION_OPEN_DOCUMENT for the ROM screen. It grants access to the chosen
 * files only, so no runtime permission is needed.
 */
public final class FilePicker {

    public static final int PICK_FILES_REQUEST = 11;

    public interface Listener {
        void onFilesSelected(List<Uri> uris);
    }

    private final Activity activity;
    private final Listener listener;

    public FilePicker(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void open() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivityForResult(intent, PICK_FILES_REQUEST);
    }

    /* One file is in getData(), several in getClipData(). */
    public void handleResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FILES_REQUEST || resultCode != Activity.RESULT_OK)
            return;

        final List<Uri> uris = new ArrayList<>();
        if (data != null) {
            final ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); ++i) {
                    final Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
        }
        if (!uris.isEmpty() && listener != null) listener.onFilesSelected(uris);
    }
}
