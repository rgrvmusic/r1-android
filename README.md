R1 for Android
==============


The Korg M1 engine as a standalone synth app, built on the PicoFaceM1 engine
from
[PicoVintageSynthCollection](https://github.com/Michi71/PicoVintageSynthCollection)


## ROMs

R1 does not include ROMs. Use the files from your own M1; they cannot be
redistributed. Put these in the app's `Programs` directory. On Android the
ROMs screen imports them:

```
m1-ic23.bin          the two halves of the original M1 OS
m1-ic32.bin
m1-ic30.bin          coefficient ROM (IC20 or IC26 is the same chip)
m1-ic36.bin .. m1-ic43.bin   PCM, 512 KB each
m1-preload.syx       optional: an M1 ALL DATA dump (factory programs)
```

Without `m1-preload.syx` program 0 is built from the ROM multisounds; with it
program 0 of the dump is the default sound. The M1 effect section is bypassed
in both cases.

On Android the ROMs screen opens from the Settings view's ROMs button. Files
can be imported with the system picker, or copied into the app's external
`inbox` directory and taken with Copy from inbox. Chip names such as `ic30` or
`ic36` are mapped to the names above. The Main view steps through the programs
with the - and + buttons around the program name.


## How to build

Android:

```sh
gradle -p android assembleDebug
```

Desktop, with SDL3 and OpenGL development files installed:

```sh
cmake -B build
cmake --build build
```

The engine is the submodule `third_party/pvsc`:

```sh
git submodule update --init --depth 1 third_party/pvsc
```


## License
MIT for this project's own code. The files that bind the engine are
GPL-3.0-or-later because the engine is GPL-3.0: `src/main.cpp`,
`engine/r1_engine.cc`, `engine/r1_engine.h`, `engine/m1_rom.h` and
`engine/m1_resampler.h`. See `LICENSE`, `LICENSE-GPL-3.0` and the engine's
own licence.

