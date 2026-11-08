// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Gerard Braad (Regroove | RGRV)
package nl.rgrv.r1;

import android.content.Intent;

/** The synth, plus the ROM screen the native panel opens. */
public class MainActivity extends R1Activity {

    private static final int ROMPICK = 1;

    /** Called from the native panel's ROMs button. */
    public void openUserFs() {
        startActivityForResult(new Intent(this, UserFsActivity.class), ROMPICK);
    }

    /** The ROM files may have changed, so reread them. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ROMPICK) nativeReload();
    }

    /** Rereads the ROM directory; the native side's reload(). */
    private native void nativeReload();
}
