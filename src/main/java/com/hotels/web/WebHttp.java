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

import com.sun.net.httpserver.HttpExchange;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web 面板基础设施工具方法（纯静态工具类）
 * 统一处理 HTTP 响应、表单解析、字符串转义与 JSON 序列化等通用逻辑
 */
public final class WebHttp {

    private WebHttp() {}

    /**
     * 发送 JSON 响应
     */
    public static void sendJson(HttpExchange exchange, int status, String json) throws java.io.IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        setSecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    /**
     * 发送 HTML 响应
     */
    public static void sendHtml(HttpExchange exchange, int status, String html) throws java.io.IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        setSecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        // HTML 页面额外的 CSP：允许内联脚本/样式（本面板大量使用 onclick 和内联 <style>）
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    /**
     * 为所有 HTTP 响应统一添加安全响应头
     */
    public static void setSecurityHeaders(HttpExchange exchange) {
        var h = exchange.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "same-origin");
        h.set("X-Permitted-Cross-Domain-Policies", "none");
        h.set("X-XSS-Protection", "1; mode=block");
        // 禁止缓存动态内容，防止登出后通过后退按钮看到敏感数据
        h.set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        h.set("Pragma", "no-cache");
    }

    /**
     * 读取请求体（UTF-8）
     */
    public static String readBody(HttpExchange exchange) throws java.io.IOException {
        try (java.io.InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 解析表单体（application/x-www-form-urlencoded）
     */
    public static Map<String, String> parseForm(String body) {
        Map<String, String> result = new HashMap<>();
        if (body == null || body.isEmpty()) return result;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
            }
        }
        return result;
    }

    /**
     * URL 解码
     */
    public static String urlDecode(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    /**
     * HTML 转义
     */
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** JS 字符串转义（用于 HTML 属性内的 onclick 等 JS 上下文，单引号需转义为 \' 防止字符串逃逸） */
    public static String escapeJsAttr(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    /** JSON 字符串转义（处理反斜杠、引号、控制字符） */
    public static String escapeJsonString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 将 Map 序列化为 JSON 字符串
     */
    public static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            sb.append(toJsonValue(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 将单个对象序列化为 JSON 值
     */
    public static String toJsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number n) return n.toString();
        if (v instanceof Boolean b) return b.toString();
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJsonValue(item));
            }
            sb.append("]");
            return sb.toString();
        }
        if (v instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJsonString(String.valueOf(e.getKey()))).append("\":");
                sb.append(toJsonValue(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        // String
        return "\"" + escapeJsonString(String.valueOf(v)) + "\"";
    }

    /**
     * 格式化运行时长
     */
    public static String formatUptime(long sec) {
        long d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60;
        if (d > 0) return d + "天 " + h + "时 " + m + "分";
        if (h > 0) return h + "时 " + m + "分";
        return m + "分钟";
    }

    /**
     * 获取异常堆栈字符串
     */
    public static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}