package com.sekwah.advancedportals.paper;

import com.sekwah.advancedportals.core.util.InfoLogger;
import com.sekwah.advancedportals.paper.PortalsProMaxPlugin;

import java.util.logging.Level;

public class PaperInfoLogger extends InfoLogger {
    private final PortalsProMaxPlugin plugin;

    public PaperInfoLogger(PortalsProMaxPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void warning(String s) {
        plugin.getLogger().warning(s);
    }

    @Override
    public void info(String s) {
        plugin.getLogger().info(s);
    }

    @Override
    public void error(Exception e) {
        plugin.getLogger().log(Level.SEVERE, e.getMessage(), e);
    }
}
