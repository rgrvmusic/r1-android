Install
=======

## App

| File | Where |
| ---- | ----- |
| `android/app/build/outputs/apk/debug/app-debug.apk` | install on the device, then launch `R1` |

```
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## ROMs

Required, in the app's `Programs` directory. No ROM file is shipped with the
app.

| File | Size | Contents |
| ---- | ---- | -------- |
| `m1-ic23.bin` | 64 KB | M1 OS, Rev 19, one half |
| `m1-ic32.bin` | 64 KB | M1 OS, Rev 19, other half |
| `m1-ic30.bin` | 64 KB | coefficient ROM; `ic20.bin` or `ic26.bin` is accepted in its place |
| `m1-ic36.bin` .. `m1-ic43.bin` | 512 KB each | PCM, IC36-39 low bytes, IC40-43 high bytes |

Optional:

| File | Size | Contents |
| ---- | ---- | -------- |
| `m1-preload.syx` | M1 ALL DATA dump | factory programs |

Without the preload program 0 is built from the ROM multisounds.

Open the ROMs screen from the Settings view's ROMs button. Files can be
imported with the system picker, or copied into the app's external `inbox`
directory and taken with Copy from inbox:

```
Android/data/nl.rgrv.r1/files/inbox
```

Chip names such as `ic30` or `ic36` are mapped to the names above. The screen
lists the files present and names the ones still missing; the app reloads the
ROMs when the screen closes.
