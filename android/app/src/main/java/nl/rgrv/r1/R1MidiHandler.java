// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Gerard Braad (Regroove | RGRV)
package nl.rgrv.r1;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Receives MIDI bytes and passes them to the native side. */
public class R1MidiHandler {

    private static final String TAG = "R1Midi";

    public static class DeviceInfo {
        public int id;
        public String name;

        DeviceInfo(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static R1MidiHandler instance;

    private final Context context;
    private MidiManager midiManager;
    private MidiDevice currentDevice;
    private MidiOutputPort currentPort;

    public List<DeviceInfo> devices = new ArrayList<>();

    public R1MidiHandler(Context context) {
        this.context = context;
        instance = this;
    }

    @SuppressLint("WrongConstant")
    public void initialize() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w(TAG, "MIDI requires Android 6.0+");
            return;
        }
        midiManager = (MidiManager) context.getSystemService(Context.MIDI_SERVICE);
        if (midiManager == null) {
            Log.w(TAG, "MIDI service unavailable");
            return;
        }
        refreshDevices();
    }

    public void refreshDevices() {
        if (midiManager == null) return;
        devices.clear();
        MidiDeviceInfo[] infos = midiManager.getDevices();
        for (MidiDeviceInfo info : infos) {
            if (info.getOutputPortCount() > 0) {
                Bundle props = info.getProperties();
                String name = props.getString(MidiDeviceInfo.PROPERTY_NAME, "Unknown");
                devices.add(new DeviceInfo(info.getId(), name));
            }
        }
        Log.i(TAG, "Found " + devices.size() + " MIDI device(s)");
    }

    public void connect(int deviceId) {
        disconnect();
        if (midiManager == null) return;

        MidiDeviceInfo[] infos = midiManager.getDevices();
        for (MidiDeviceInfo info : infos) {
            if (info.getId() == deviceId && info.getOutputPortCount() > 0) {
                midiManager.openDevice(info, device -> {
                    if (device == null) {
                        Log.e(TAG, "Failed to open device");
                        return;
                    }
                    currentDevice = device;
                    currentPort = device.openOutputPort(0);
                    if (currentPort != null) {
                        currentPort.connect(new MidiReceiver() {
                            @Override
                            public void onSend(byte[] msg, int off, int count, long timestamp)
                                    throws IOException {
                                nativeMidiData(msg, off, count);
                            }
                        });
                        Log.i(TAG, "Connected to MIDI device " + deviceId);
                    }
                }, null);
                return;
            }
        }
        Log.w(TAG, "MIDI device " + deviceId + " not found");
    }

    public void disconnect() {
        try {
            if (currentPort != null) {
                currentPort.close();
                currentPort = null;
            }
            if (currentDevice != null) {
                currentDevice.close();
                currentDevice = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error disconnecting MIDI", e);
        }
    }

    public void destroy() {
        disconnect();
    }

    /** Device list as "id:name" lines. */
    public static String getDeviceList() {
        if (instance == null || instance.devices.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DeviceInfo d : instance.devices) {
            sb.append(d.id).append(':').append(d.name).append('\n');
        }
        return sb.toString();
    }

    public static void connectDevice(int deviceId) {
        if (instance != null) instance.connect(deviceId);
    }

    public static void refreshDeviceList() {
        if (instance != null) instance.refreshDevices();
    }

    private native void nativeMidiData(byte[] data, int offset, int length);
}
