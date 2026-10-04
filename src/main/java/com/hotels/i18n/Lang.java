/*
 * HotelsX - 酒店房间管理插件
 * MIT License
 *
 * Copyright (c) 2024-2026 HotelsX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the Software), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hotels.i18n;

import com.hotels.HotelsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * 语言包管理器：把玩家可见文案从语言文件读取，供全插件统一调用。
 *
 * <p>语言由 config.yml 的 language 项全服统一指定，取值为 {@link #SUPPORTED} 之一。
 * 当前语言缺少某个键时，依次回退到 zh_CN、再回退到键名本身，保证任何情况下都有输出；
 * 语言文件读写一律显式使用 UTF-8，避免中文在 Windows 中文系统下乱码。
 */
public class Lang {

    /** 兜底语言：任何缺失都回退到它 */
    public static final String DEFAULT_CODE = "zh_CN";

    /** 内置支持的语言 */
    public static final List<String> SUPPORTED =
            Collections.unmodifiableList(Arrays.asList("zh_CN", "en_US"));

    private final HotelsPlugin plugin;

    /** 已告警过的缺失键，避免周期任务每 5 秒刷一次日志 */
    private final Set<String> warned = new HashSet<>();

    private String code = DEFAULT_CODE;
    private YamlConfiguration messages;
    private YamlConfiguration fallback;

    public Lang(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 释放语言文件到 dataFolder 并加载。
     * 任何异常都不会向外抛出——语言包不可用时插件仍必须能正常启动。
     */
    public void load() {
        warned.clear();

        // 释放内置语言文件（false = 不覆盖服主已有的修改）
        for (String supported : SUPPORTED) {
            String path = "lang/" + supported + ".yml";
            try {
                plugin.saveResource(path, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("jar 内缺少语言资源: " + path);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "释放语言文件失败: " + path, e);
            }
        }

        // 兜底语言恒为 zh_CN
        fallback = read(DEFAULT_CODE);

        // 读取配置指定的语言
        String configured = plugin.getConfig().getString("language", DEFAULT_CODE);
        if (configured == null || !SUPPORTED.contains(configured)) {
            plugin.getLogger().warning("config.yml 的 language 取值无效: " + configured
                    + "，已回退到 " + DEFAULT_CODE);
            configured = DEFAULT_CODE;
        }

        code = configured;
        messages = read(code);

        if (messages == null) {
            plugin.getLogger().warning("无法加载语言文件 lang/" + code + ".yml，已回退到 " + DEFAULT_CODE);
            code = DEFAULT_CODE;
            messages = fallback;
        } else if (DEFAULT_CODE.equals(code)) {
            // 当前语言就是兜底语言，共用同一份对象，省掉一次重复查找
            messages = fallback != null ? fallback : messages;
        }

        plugin.getLogger().info("语言已加载: " + code);
    }

    /**
     * 重新读取语言文件（供配置重载使用）
     */
    public void reload() {
        load();
    }

    /**
     * 当前语言代码，例如 zh_CN
     */
    public String code() {
        return code;
    }

    /**
     * 取一条文案（自动把 & 转成颜色符）
     */
    public String get(String key) {
        String raw = raw(key);
        return raw == null ? key : ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * 取一条文案并替换占位符。
     *
     * @param kv 交替传入「占位符名, 值」，例如 {@code get(key, "room", name, "id", id)}
     */
    public String get(String key, Object... kv) {
        return fill(get(key), kv);
    }

    /**
     * 取一组文案（供 GUI lore 使用）
     */
    public List<String> getList(String key) {
        List<String> raw = rawList(key);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return out;
    }

    /**
     * 取一组文案并替换占位符
     */
    public List<String> getList(String key, Object... kv) {
        List<String> raw = rawList(key);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(fill(ChatColor.translateAlternateColorCodes('&', line), kv));
        }
        return out;
    }

    /**
     * 给指定接收者发送一条文案
     */
    public void send(CommandSender to, String key, Object... kv) {
        if (to == null) return;
        to.sendMessage(get(key, kv));
    }

    /**
     * 取原始文本：当前语言 -> zh_CN -> null（并只告警一次）
     */
    private String raw(String key) {
        if (messages != null) {
            String value = messages.getString(key);
            if (value != null) return value;
        }
        if (fallback != null && fallback != messages) {
            String value = fallback.getString(key);
            if (value != null) return value;
        }
        if (warned.add(key)) {
            plugin.getLogger().warning("语言文件缺少键: " + key);
        }
        return null;
    }

    /**
     * 取原始列表：当前语言 -> zh_CN -> 空列表（并只告警一次）
     */
    private List<String> rawList(String key) {
        if (messages != null) {
            List<String> value = messages.getStringList(key);
            if (!value.isEmpty()) return value;
        }
        if (fallback != null && fallback != messages) {
            List<String> value = fallback.getStringList(key);
            if (!value.isEmpty()) return value;
        }
        if (warned.add(key)) {
            plugin.getLogger().warning("语言文件缺少列表键: " + key);
        }
        return Collections.emptyList();
    }

    /**
     * 把 {name} 形式的占位符替换为实际值
     */
    private String fill(String text, Object... kv) {
        if (text == null || kv == null || kv.length < 2) return text;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            text = text.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return text;
    }

    /**
     * 以显式 UTF-8 读取语言文件；读不到或解析失败时返回 null
     */
    private YamlConfiguration read(String languageCode) {
        File file = new File(plugin.getDataFolder(), "lang/" + languageCode + ".yml");
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "读取语言文件失败: " + file.getAbsolutePath(), e);
            return null;
        }
        return yaml;
    }
}
