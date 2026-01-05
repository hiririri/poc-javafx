package com.sun.java.swing.plaf.windows;

import javax.swing.LookAndFeel;

/**
 * Minimal stub to satisfy JIDE's optional Windows LAF references on non-Windows
 * runtimes that do not ship the internal WindowsLookAndFeel class.
 */
public class WindowsLookAndFeel extends LookAndFeel {
    @Override
    public String getName() {
        return "Stub Windows LookAndFeel";
    }

    @Override
    public String getID() {
        return "StubWindows";
    }

    @Override
    public String getDescription() {
        return "Stub Windows LookAndFeel for non-Windows runtime.";
    }

    @Override
    public boolean isNativeLookAndFeel() {
        return false;
    }

    @Override
    public boolean isSupportedLookAndFeel() {
        return false;
    }
}