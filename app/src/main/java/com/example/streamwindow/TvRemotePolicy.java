package com.example.streamwindow;

/** Fullscreen arrows navigate controls; they must not reach player seek shortcuts. */
final class TvRemotePolicy {
    // Android DPAD key codes are stable: up=19, down=20, left=21, right=22.
    static int focusDirection(int keyCode) {
        switch(keyCode) {
            case 19: case 21: return -1;
            case 20: case 22: return 1;
            default: return 0;
        }
    }
}
