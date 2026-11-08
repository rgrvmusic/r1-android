// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Gerard Braad (Regroove | RGRV)
package nl.rgrv.r1;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ROM directory, filesDir/Programs. Files arrive from the system picker
 * or from the external {@code inbox} directory, which adb can fill.
 */
final class UserFsStore {

    private static final String INBOX = "inbox";

    /** The names the engine reads. */
    private static final String[] NEEDED = {
            "m1-ic23.bin", "m1-ic32.bin", "m1-ic30.bin",
            "m1-ic36.bin", "m1-ic37.bin", "m1-ic38.bin", "m1-ic39.bin",
            "m1-ic40.bin", "m1-ic41.bin", "m1-ic42.bin", "m1-ic43.bin",
    };

    private UserFsStore() {}

    static File home(Context ctx) {
        final File f = new File(ctx.getFilesDir(), "Programs");
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        return f;
    }

    static File inbox(Context ctx) {
        final File base = ctx.getExternalFilesDir(null);
        if (base == null) return null;
        final File f = new File(base, INBOX);
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        return f;
    }

    static final class Entry {
        final String name;
        final long size;

        Entry(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }

    static List<Entry> list(Context ctx) {
        final List<Entry> out = new ArrayList<>();
        final File[] files = home(ctx).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !f.getName().startsWith("."))
                    out.add(new Entry(f.getName(), f.length()));
            }
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    static List<String> missing(Context ctx) {
        final List<String> out = new ArrayList<>();
        final File home = home(ctx);
        for (String name : NEEDED) {
            if (!new File(home, name).isFile()) out.add(name);
        }
        return out;
    }

    static boolean delete(Context ctx, String name) {
        final File file = new File(home(ctx), name);
        try {
            final String root = home(ctx).getCanonicalPath();
            if (!file.getCanonicalPath().startsWith(root + File.separator)) return false;
        } catch (IOException e) {
            return false;
        }
        return file.delete();
    }

    /** Copies a picked file in under the name the engine reads. */
    static String importFrom(Context ctx, Uri uri) {
        String name = displayName(ctx, uri);
        if (name == null) return "that file has no usable name";
        name = targetName(name);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return "cannot read " + name;
            copy(in, new File(home(ctx), name));
        } catch (Exception e) {
            return "could not copy " + name + ": " + e;
        }
        return "imported: " + name;
    }

    /** Copies the inbox onto the ROM directory, newer or larger files only. */
    static List<String> sweep(Context ctx) {
        final List<String> lines = new ArrayList<>();
        final File inbox = inbox(ctx);
        if (inbox == null) {
            lines.add("no external storage; use Import");
            return lines;
        }
        final File home = home(ctx);
        final File[] files = inbox.listFiles();
        int copied = 0;
        if (files != null) {
            for (File src : files) {
                if (!src.isFile() || src.getName().startsWith(".")) continue;
                final File dst = new File(home, targetName(src.getName()));
                if (dst.isFile() && dst.length() == src.length()
                        && dst.lastModified() >= src.lastModified()) continue;
                try (InputStream in = new FileInputStream(src)) {
                    copy(in, dst);
                    //noinspection ResultOfMethodCallIgnored
                    dst.setLastModified(src.lastModified());
                    ++copied;
                } catch (IOException e) {
                    lines.add("could not copy " + src.getName() + ": " + e);
                }
            }
        }
        if (copied > 0) lines.add("inbox: copied " + copied + " file(s)");
        return lines;
    }

    /** Chip names to the names the engine reads. */
    static String targetName(String name) {
        final String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".syx")) return "m1-preload.syx";
        if (lower.contains("ic20") || lower.contains("ic26") || lower.contains("ic30"))
            return "m1-ic30.bin";
        for (int n = 36; n <= 43; ++n) {
            if (lower.contains("ic" + n)) return "m1-ic" + n + ".bin";
        }
        if (lower.contains("ic23")) return "m1-ic23.bin";
        if (lower.contains("ic32")) return "m1-ic32.bin";
        return name;
    }

    private static String displayName(Context ctx, Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor c = ctx.getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                final int col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) name = c.getString(col);
            }
        } catch (Exception e) {
            // Fall back to the path segment.
        }
        return safeName(name);
    }

    private static String safeName(String name) {
        if (name == null) return null;
        final int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = slash >= 0 ? name.substring(slash + 1) : name;
        if (name.isEmpty() || name.contains("..")) return null;
        return name;
    }

    /* ".part" keeps a copy that was killed from being read as a file. */
    private static void copy(InputStream in, File dst) throws IOException {
        final File parent = dst.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        final File tmp = new File(parent, dst.getName() + ".part");
        try (OutputStream out = new FileOutputStream(tmp)) {
            final byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(dst);
    }
}
