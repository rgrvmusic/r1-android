// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Gerard Braad (Regroove | RGRV)
package nl.rgrv.r1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * The ROM screen, opened from the Synth panel's ROMs button: list, import,
 * copy from the inbox, delete. It builds its views in code and mirrors the
 * panel's banner geometry (48dp bar, 48x32dp control at 8dp, title at 68dp).
 */
public class UserFsActivity extends Activity {
    private static final String TAG = "R1-UserFS";

    /* Banner geometry in dp, matching the synth panel's banner. */
    private static final float BANNER_H  = 48.0f;
    private static final float SHADOW_H  =  2.0f;
    private static final float MARK_X    =  8.0f;
    private static final float MARK_W    = 48.0f;
    private static final float MARK_H    = 32.0f;
    private static final float TITLE_GAP = 12.0f;

    private FilePicker picker;

    /** The whole screen; held so it can scroll back to the top. */
    private ScrollView scroller;
    /** The file rows, rebuilt by {@link #refresh}. */
    private LinearLayout filesBox;
    /** The last operation's message, from UserFsStore. */
    private TextView status;
    /** Shown when the ROM directory has no files. */
    private TextView empty;
    /** What is still missing, or that everything is present. */
    private TextView missing;

    /* The palette, read once. See colors.xml. */
    private int accent, accentPressed, background, surface, grey, dim, onBackground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        accent        = getColor(R.color.colorPrimary);
        accentPressed = getColor(R.color.colorPrimaryPressed);
        background    = getColor(R.color.colorBackground);
        surface       = getColor(R.color.colorSurface);
        grey          = getColor(R.color.colorButton);
        dim           = getColor(R.color.colorSecondaryText);
        onBackground  = getColor(R.color.colorOnBackground);

        picker = new FilePicker(this, this::importAll);
        setContentView(build());
        refresh();

        /* Posted: the first layout pass may scroll to a focusable button. */
        scroller.post(() -> scroller.scrollTo(0, 0));

        /* Files pushed into the inbox are taken in when the screen opens. */
        copyFromInbox();
    }

    /* ------------------------------------------------------------- the shape -- */

    private View build() {
        final LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(background);

        outer.addView(banner());

        final View shadow = new View(this);
        shadow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(SHADOW_H)));
        final GradientDrawable fade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x80000000, 0x00000000 });
        shadow.setBackground(fade);
        outer.addView(shadow);

        scroller = new ScrollView(this);
        scroller.setBackgroundColor(background);
        scroller.setFillViewport(true);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        outer.addView(scroller);

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(18));
        scroller.addView(root);

        root.addView(hint("The synth reads its ROM files from this app's own storage. "
                + "Copy the M1 ROM files listed here before playing."));

        root.addView(section("ON THE DEVICE"));
        filesBox = new LinearLayout(this);
        filesBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(filesBox);

        empty = hint("No files yet. Use Import below, or copy files into the inbox "
                + "and take them from there.");
        empty.setPadding(0, dp(4), 0, dp(12));
        root.addView(empty);

        missing = hint("");
        missing.setPadding(0, dp(4), 0, dp(12));
        root.addView(missing);

        /* The two ways in, side by side. */
        final LinearLayout imports = new LinearLayout(this);
        imports.setOrientation(LinearLayout.HORIZONTAL);
        imports.setPadding(0, dp(6), 0, dp(22));

        final Button pick = button("Import…", true);
        pick.setOnClickListener(v -> picker.open());
        imports.addView(pick);

        final Button fromInbox = button("Copy from inbox", false);
        fromInbox.setOnClickListener(v -> copyFromInbox());
        final LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.leftMargin = dp(12);
        imports.addView(fromInbox, fp);

        root.addView(imports);

        /* The inbox path is selectable, for adb push from a terminal. */
        root.addView(section("INBOX"));
        root.addView(hint("Push files here from a computer and take them onto the "
                + "device with Copy from inbox. This is the developer's route; "
                + "Import is everyone else's."));

        final File inbox = UserFsStore.inbox(this);
        final TextView path = new TextView(this);
        path.setText(inbox != null
                ? inbox.getAbsolutePath()
                : "no shared storage on this device, use Import");
        path.setTextColor(inbox != null ? onBackground : dim);
        path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        path.setTypeface(Typeface.MONOSPACE);
        path.setTextIsSelectable(true);
        path.setPadding(0, dp(6), 0, dp(18));
        root.addView(path);

        /* Status, last, under the control that produced it. */
        status = new TextView(this);
        status.setTextColor(dim);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextIsSelectable(true);
        root.addView(status);

        return outer;
    }

    /* ------------------------------------------------------------ the banner -- */

    private View banner() {
        final LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(accent);
        bar.setPadding(dp(MARK_X), 0, dp(MARK_X), 0);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(BANNER_H)));

        bar.addView(backMark());

        final TextView name = new TextView(this);
        name.setText(getString(R.string.app_name).toUpperCase(Locale.ROOT));
        name.setTextColor(onBackground);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        name.setLetterSpacing(0.12f);
        final LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.leftMargin = dp(TITLE_GAP);
        bar.addView(name, np);

        final TextView where = new TextView(this);
        where.setText("ROMS");
        where.setTextColor(0xC0FFFFFF);
        where.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        where.setTypeface(Typeface.MONOSPACE);
        where.setLetterSpacing(0.12f);
        final LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.leftMargin = dp(10);
        bar.addView(where, wp);

        return bar;
    }

    /** The banner's left control: a back arrow in the panel's hit area. */
    private View backMark() {
        final float w = dp(MARK_W), h = dp(MARK_H);
        final float cy = h * 0.5f;
        final float cx = w * 0.5f;
        final float tipX = cx - dp(13), tailX = cx + dp(13);
        final float armX = tipX + dp(10), armY = dp(10);

        final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        pen.setColor(onBackground);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(dp(3));
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);

        final Path head = new Path();
        head.moveTo(armX, cy - armY);
        head.lineTo(tipX, cy);
        head.lineTo(armX, cy + armY);

        final View mark = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawLine(tipX, cy, tailX, cy, pen);
                canvas.drawPath(head, pen);
            }
        };
        mark.setBackground(held());
        mark.setClickable(true);
        mark.setContentDescription("Back");
        mark.setOnClickListener(v -> finish());
        mark.setLayoutParams(new LinearLayout.LayoutParams((int) w, (int) h));
        return mark;
    }

    /** The banner control's background: a white wash when pressed. */
    private Drawable held() {
        final StateListDrawable s = new StateListDrawable();
        final GradientDrawable on = new GradientDrawable();
        on.setColor(0x1EFFFFFF);
        on.setCornerRadius(dp(4));
        s.addState(new int[] { android.R.attr.state_pressed }, on);
        s.addState(new int[] {}, new GradientDrawable());
        return s;
    }

    /* ------------------------------------------------------------- the files -- */

    /** Rebuilds the list and the missing-files line from the ROM directory. */
    private void refresh() {
        final List<UserFsStore.Entry> files = UserFsStore.list(this);

        filesBox.removeAllViews();
        empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);

        for (UserFsStore.Entry file : files) {
            filesBox.addView(row(file));
        }

        final List<String> needed = UserFsStore.missing(this);
        if (needed.isEmpty()) {
            missing.setText("All required files are present.");
            missing.setTextColor(dim);
        } else {
            final StringBuilder text = new StringBuilder("Missing: ");
            for (int i = 0; i < needed.size(); ++i) {
                if (i > 0) text.append(", ");
                text.append(needed.get(i));
            }
            missing.setText(text);
            missing.setTextColor(accent);
        }
    }

    /** One file on the device: its name, its size, and the button that removes it. */
    private View row(final UserFsStore.Entry file) {
        final LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setBackgroundColor(surface);
        line.setPadding(dp(16), dp(10), dp(10), dp(10));

        final TextView name = new TextView(this);
        name.setText(file.name);
        name.setTextColor(onBackground);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        line.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        final TextView size = new TextView(this);
        size.setText(size(file.size));
        size.setTextColor(dim);
        size.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        size.setPadding(0, 0, dp(14), 0);
        line.addView(size);

        final Button delete = button("Delete", false);
        delete.setTextColor(accent);
        delete.setOnClickListener(v -> confirmDelete(file));
        line.addView(delete);

        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        line.setLayoutParams(lp);
        return line;
    }

    /** Ask before removing a file. */
    private void confirmDelete(final UserFsStore.Entry file) {
        new AlertDialog.Builder(this, R.style.UserFsDialog)
                .setTitle(file.name)
                .setMessage("Remove this file from the device?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    final boolean gone = UserFsStore.delete(this, file.name);
                    say(gone ? "deleted    : " + file.name
                             : "could not delete " + file.name);
                    refresh();
                })
                .show();
    }

    /* -------------------------------------------------------- the two ways in -- */

    private void importAll(List<Uri> uris) {
        offThread(() -> {
            for (Uri uri : uris) {
                final String line = UserFsStore.importFrom(this, uri);
                Log.i(TAG, line);
                say(line);
            }
            done();
        });
    }

    private void copyFromInbox() {
        offThread(() -> {
            for (String line : UserFsStore.sweep(this)) {
                Log.i(TAG, line);
                say(line);
            }
            done();
        });
    }

    /* ------------------------------------------------------------------ plumbing -- */

    /** After any change: re-read the list. */
    private void done() {
        runOnUiThread(this::refresh);
    }

    private void say(final String line) {
        runOnUiThread(() -> status.append(line + "\n"));
    }

    private void offThread(final Runnable work) {
        new Thread(work, "r1-userfs").start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        picker.handleResult(requestCode, resultCode, data);
    }

    /* ------------------------------------------------------------------ widgets -- */

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** A flat button in the panel's style; {@code primary} is the banner red. */
    private Button button(String text, boolean primary) {
        final Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTextColor(onBackground);
        b.setBackground(primary
                ? flat(accent, accentPressed)
                : flat(grey, surface));
        b.setPadding(dp(22), dp(9), dp(22), dp(9));
        b.setMinimumWidth(0);
        b.setStateListAnimator(null);
        return b;
    }

    /** A rounded rectangle that changes shade when pressed. */
    private Drawable flat(int fill, int pressed) {
        final StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_pressed }, shape(pressed));
        s.addState(new int[] {}, shape(fill));
        return s;
    }

    private Drawable shape(int fill) {
        final GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(2));
        return g;
    }

    /** A form label, in the accent. */
    private TextView section(String text) {
        final TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(accent);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLetterSpacing(0.12f);
        v.setPadding(0, dp(4), 0, dp(10));
        return v;
    }

    private TextView hint(String text) {
        final TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(dim);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setLineSpacing(0, 1.25f);
        v.setPadding(0, 0, 0, dp(16));
        return v;
    }

    /** A readable byte count: binary units and two decimals. */
    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.2f KiB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MiB", bytes / (1024.0 * 1024.0));
    }
}
