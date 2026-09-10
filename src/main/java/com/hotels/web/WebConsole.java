/*
 * HotelsX - 酒店房间管理插件
 * MIT License
 *
 * Copyright (c) 2024-2026 HotelsX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hotels.web;

import com.hotels.HotelsPlugin;
import org.bukkit.Bukkit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Web 控制台日志捕获：注册 java.util.logging Handler 将插件日志实时写入内存缓冲区，
 * 供 Web 面板的 /api/console/logs 与 SSE 控制台推送读取
 */
public final class WebConsole {

    private final HotelsPlugin plugin;
    private final Deque<String> consoleBuffer = new ConcurrentLinkedDeque<>();
    private static final int CONSOLE_BUFFER_MAX = 500;
    private WebConsoleHandler consoleHandler;

    public WebConsole(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册日志 Handler 捕获插件日志，并加载初始日志到内存缓冲区
     */
    public void installConsoleCapture() {
        // 注册 java.util.logging Handler 捕获插件日志
        try {
            consoleHandler = new WebConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            Logger.getLogger("").addHandler(consoleHandler);
            Bukkit.getLogger().addHandler(consoleHandler);
        } catch (Exception ignored) {}

        // 添加初始日志到内存缓冲区
        logConsole("[HotelsX] Web 控制台已启动");
        logConsole("[HotelsX] 服务器版本: " + Bukkit.getVersion() + " | MC: " + Bukkit.getBukkitVersion());
        logConsole("[HotelsX] 在线玩家: " + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());

        // 确保至少有一条日志通过插件日志系统输出（会写入 logs/latest.log）
        plugin.getLogger().info("Web 控制台日志系统已初始化");

        // 尝试读取现有日志（从日志文件）
        tryReadLogFile(true);
    }

    /**
     * 尝试从多个可能的路径读取日志文件
     */
    private void tryReadLogFile(boolean readAll) {
        String[] candidates = {"logs/latest.log", "./logs/latest.log", "../logs/latest.log"};
        for (String path : candidates) {
            java.io.File f = new java.io.File(path);
            if (f.exists() && f.canRead()) {
                try {
                    java.io.FileInputStream fis = new java.io.FileInputStream(f);
                    byte[] data = fis.readAllBytes();
                    fis.close();
                    String content = new String(data, StandardCharsets.UTF_8);
                    String[] lines = content.split("\n");
                    int start = readAll ? Math.max(0, lines.length - 200) : 0;
                    for (int i = start; i < lines.length; i++) {
                        String line = lines[i].trim();
                        if (!line.isEmpty()) {
                            // 跳过重复的初始日志
                            if (!line.contains("[HotelsX] Web 控制台")) {
                                logConsole(line);
                            }
                        }
                    }
                    return;
                } catch (Exception ignored) {}
            }
        }
        logConsole("[HotelsX] 未找到日志文件 (logs/latest.log)，等待新日志...");
    }

    /**
     * 获取最新的控制台日志（API 入口）
     * 日志已由 WebConsoleHandler 实时捕获到内存缓冲区，直接返回即可，
     * 避免重新读取日志文件导致内容被重复追加、覆盖实时日志
     */
    public List<String> getConsoleLogs() {
        return new ArrayList<>(consoleBuffer);
    }

    public void logConsoleLine(String line) {
        if (line == null || line.isEmpty()) return;
        logConsole(line);
    }

    public void logConsole(String line) {
        if (line == null) return;
        consoleBuffer.offerLast(line);
        while (consoleBuffer.size() > CONSOLE_BUFFER_MAX) {
            consoleBuffer.pollFirst();
        }
    }

    private class WebConsoleHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            try {
                String msg = record.getMessage();
                if (msg == null) msg = "";
                Object[] params = record.getParameters();
                if (params != null && params.length > 0) {
                    try { msg = String.format(msg, params); } catch (Exception ignored) {}
                }
                Throwable thrown = record.getThrown();
                if (thrown != null) {
                    StringWriter sw = new StringWriter();
                    thrown.printStackTrace(new PrintWriter(sw));
                    msg = msg + "\n" + sw.toString();
                }
                WebConsole.this.logConsoleLine(msg);
            } catch (Exception ignored) {}
        }
        @Override
        public void flush() {}
        @Override
        public void close() throws SecurityException {}
    }
}