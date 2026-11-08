// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Gerard Braad (Regroove | RGRV)
package nl.rgrv.r1;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;

import org.libsdl.app.SDLActivity;

/** SDL host: audio focus, the background audio service and MIDI. */
public class R1Activity extends SDLActivity {

    protected R1MidiHandler midiHandler;

    @Override
    protected String[] getLibraries() {
        return new String[] { "main" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                                 AudioManager.AUDIOFOCUS_GAIN);
        }

        // Keep audio alive in the background.
        Intent svc = new Intent(this, R1AudioService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svc);
        else
            startService(svc);

        midiHandler = new R1MidiHandler(this);
        midiHandler.initialize();

        hideSystemUi();
    }

    /* SDL resets the system UI when it regains focus; hide it again. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.statusBars()
                       | android.view.WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (midiHandler != null) midiHandler.destroy();
        stopService(new Intent(this, R1AudioService.class));
    }
}
