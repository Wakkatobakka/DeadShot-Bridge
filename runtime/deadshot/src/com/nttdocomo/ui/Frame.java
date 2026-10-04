package com.nttdocomo.ui;

public class Frame {
    private final String[] labels = new String[2];

    public void setSoftLabel(int index, String label) {
        if (index >= 0 && index < labels.length) labels[index] = label;
    }

    // Host-side display helper; not part of the game-facing DoJa API surface.
    public String __softLabel(int index) {
        return (index >= 0 && index < labels.length) ? labels[index] : null;
    }
}
