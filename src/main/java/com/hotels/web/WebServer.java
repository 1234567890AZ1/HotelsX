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
import com.hotels.model.HotelRoom;
import com.hotels.model.RoomCollection;
import com.hotels.model.Transaction;
import com.hotels.util.SchedulerCompat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class WebServer {

    private final HotelsPlugin plugin;
    private HttpServer server;
    private int port;
    private String bindAddress;
    private boolean sslEnabled = false;
    private boolean running = false;
    private long serverStartTime;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final List<AdminAccount> admins = new ArrayList<>();
    private final ReentrantLock adminsLock = new ReentrantLock();
    private final Deque<String> consoleBuffer = new ConcurrentLinkedDeque<>();
    private static final int CONSOLE_BUFFER_MAX = 500;

    private static class Session {
        final String username;
        final String role;
        final String minecraftName;
        final long createdAt;
        long lastAccess;
        Session(String username, String role, String minecraftName) {
            this.username = username;
            this.role = role;
            this.minecraftName = minecraftName;
            this.createdAt = System.currentTimeMillis();
            this.lastAccess = createdAt;
        }
    }

    private static class AdminAccount {
        String username;
        String password;
        String role;
        String minecraftName; // 关联的游戏内玩家名，用于限制编辑权限
        AdminAccount(String u, String p, String r, String mc) { username=u; password=p; role=r; minecraftName=mc; }
    }

    public WebServer(HotelsPlugin plugin) {
        this.plugin = plugin;
        this.serverStartTime = System.currentTimeMillis();
    }

    public boolean start() {
        port = plugin.getConfig().getInt("web-port", plugin.getConfig().getInt("api-port", 17409));
        bindAddress = plugin.getConfig().getString("web-bind", "127.0.0.1");
        if (bindAddress == null || bindAddress.isBlank()) bindAddress = "127.0.0.1";
        sslEnabled = plugin.getConfig().getBoolean("web-ssl-enabled", false);
        if (!plugin.getConfig().getBoolean("web-enabled", plugin.getConfig().getBoolean("api-enabled", true))) {
            plugin.getLogger().info("Web 面板已在配置中禁用");
            return false;
        }
        loadAdmins();
        try {
            InetSocketAddress addr = new InetSocketAddress(bindAddress, port);
            if (sslEnabled) {
                SSLContext sslContext = loadSslContext();
                if (sslContext == null) {
                    plugin.getLogger().warning("SSL 已启用但证书加载失败，回退到 HTTP");
                    sslEnabled = false;
                } else {
                    HttpsServer https = HttpsServer.create(addr, 0);
                    https.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                        @Override
                        public void configure(HttpsParameters params) {
                            SSLContext c = getSSLContext();
                            SSLEngine engine = c.createSSLEngine();
                            params.setNeedClientAuth(false);
                            params.setCipherSuites(engine.getEnabledCipherSuites());
                            params.setProtocols(engine.getEnabledProtocols());
                            SSLParameters sslParams = c.getDefaultSSLParameters();
                            // 现代 TLS 配置：禁用老旧协议
                            sslParams.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                            params.setSSLParameters(sslParams);
                        }
                    });
                    server = https;
                }
            }
            if (server == null) {
                server = HttpServer.create(addr, 0);
            }
            registerHandlers();
            installConsoleCapture();
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            running = true;
            String proto = sslEnabled ? "https" : "http";
            if ("0.0.0.0".equals(bindAddress)) {
                String warn = "Web 面板监听 0.0.0.0:" + port + "（" + proto.toUpperCase() + "，公网/局域网可访问），请确保已设置强密码！";
                if (sslEnabled) plugin.getLogger().warning(warn);
                else plugin.getLogger().warning(warn);
            } else {
                plugin.getLogger().info("Web 面板已启动 - " + proto + "://" + bindAddress + ":" + port + "（仅本机可访问）");
            }
            if (sslEnabled) {
                plugin.getLogger().info("HTTPS 已启用，TLSv1.2/1.3");
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("无法启动 Web 面板: " + e.getMessage());
            return false;
        }
    }

    /**
     * 注册所有 HTTP 路由 handler（HTTP 与 HTTPS 共用）
     */
    private void registerHandlers() {
        server.createContext("/", new DashboardHandler());
        server.createContext("/login", new LoginPageHandler());
        server.createContext("/api/login", new LoginApiHandler());
        server.createContext("/api/logout", new LogoutHandler());
        server.createContext("/api/me", new MeHandler());
        server.createContext("/api/server", new ServerStatusHandler());
        server.createContext("/api/stats", new StatsHandler());
        server.createContext("/api/stats/detail", new StatsDetailHandler());
        server.createContext("/api/stats/report", new StatsReportHandler());
        server.createContext("/api/rooms", new RoomsHandler());
        server.createContext("/api/room/delete", new RoomDeleteHandler());
        server.createContext("/api/room/update", new RoomUpdateHandler());
        server.createContext("/api/room/batch", new RoomBatchHandler());
        server.createContext("/api/collections", new CollectionsHandler());
        server.createContext("/api/admins", new AdminsHandler());
        server.createContext("/api/admin/add", new AdminAddHandler());
        server.createContext("/api/admin/delete", new AdminDeleteHandler());
        server.createContext("/api/admin/change-password", new AdminPasswordHandler());
        server.createContext("/api/console/logs", new ConsoleLogHandler());
        server.createContext("/api/console/execute", new ConsoleCommandHandler());
        server.createContext("/api/broadcast", new BroadcastHandler());
        server.createContext("/api/events", new EventsHandler());
        server.createContext("/api/events/console", new ConsoleEventsHandler());
        server.createContext("/api/transactions", new TransactionsHandler());
        server.createContext("/api/ratings", new RatingsHandler());
        server.createContext("/api/escrow", new EscrowHandler());
        server.createContext("/api/escrow/withdraw", new EscrowWithdrawHandler());
    }

    /**
     * 加载 SSL 密钥库并构造 SSLContext
     * 支持 PKCS12（.p12/.pfx，推荐）和 JKS 格式
     */
    private SSLContext loadSslContext() {
        String keystorePath = plugin.getConfig().getString("web-ssl-keystore", "web-keystore.p12");
        String keystorePassword = plugin.getConfig().getString("web-ssl-password", "changeit");
        String keystoreType = plugin.getConfig().getString("web-ssl-keystore-type", "PKCS12");

        File ksFile = new File(keystorePath);
        if (!ksFile.isAbsolute()) {
            ksFile = new File(plugin.getDataFolder(), keystorePath);
        }
        if (!ksFile.exists()) {
            plugin.getLogger().warning("SSL 密钥库文件不存在: " + ksFile.getAbsolutePath());
            plugin.getLogger().warning("请按以下步骤生成自签名证书（Windows 在 PowerShell 执行）：");
            plugin.getLogger().warning("  1) 生成证书: openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -days 3650 -subj \"/CN=localhost\" -addext \"subjectAltName=DNS:localhost,IP:127.0.0.1\"");
            plugin.getLogger().warning("  2) 转换为 PKCS12: openssl pkcs12 -export -out web-keystore.p12 -inkey key.pem -in cert.pem -passout pass:changeit");
            plugin.getLogger().warning("  3) 把 web-keystore.p12 放入 plugins/HotelsX/ 目录");
            plugin.getLogger().warning("  或使用 Let's Encrypt 证书（公网域名推荐）");
            return null;
        }
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            KeyStore ks = KeyStore.getInstance(keystoreType);
            ks.load(fis, keystorePassword.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keystorePassword.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            plugin.getLogger().info("SSL 证书已加载: " + ksFile.getAbsolutePath());
            return ctx;
        } catch (Exception e) {
            plugin.getLogger().warning("加载 SSL 密钥库失败: " + e.getMessage());
            return null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            running = false;
            sessions.clear();
            plugin.getLogger().info("Web 面板已停止");
        }
    }

    /**
     * 检查用户名是否已存在
     */
    public boolean usernameExists(String username) {
        adminsLock.lock();
        try {
            for (AdminAccount a : admins) {
                if (a.username.equalsIgnoreCase(username)) return true;
            }
            return false;
        } finally { adminsLock.unlock(); }
    }

    /**
     * 检查某个游戏ID是否已关联管理员账户
     */
    public boolean minecraftNameExists(String mcName) {
        if (mcName == null) return false;
        adminsLock.lock();
        try {
            for (AdminAccount a : admins) {
                if (mcName.equalsIgnoreCase(a.minecraftName)) return true;
            }
            return false;
        } finally { adminsLock.unlock(); }
    }

    /**
     * 游戏内玩家自助注册 Web 账户
     * 自动将角色设为 user，并关联玩家的游戏名
     * 不允许创建 admin 或 superadmin 角色
     */
    public String registerUser(String username, String password, String minecraftName) {
        if (username == null || username.trim().isEmpty()) return "用户名不能为空";
        if (password == null || password.length() < 4) return "密码至少4位";
        if (minecraftName == null || minecraftName.isEmpty()) return "缺少关联的游戏ID";
        adminsLock.lock();
        try {
            if (usernameExists(username)) return "用户名已存在";
            if (minecraftNameExists(minecraftName)) return "该游戏ID已经注册过账户，请先找回或联系超级管理员删除旧账户";
            // 自助注册默认创建 user 角色，只能管理自己的房间
            admins.add(new AdminAccount(username.trim(), hashPassword(password), "user", minecraftName));
            saveAdmins();
        } finally { adminsLock.unlock(); }
        plugin.getLogger().info("[Web] 玩家 " + minecraftName + " 已自助注册 Web 账户: " + username + " (角色: user)");
        return null; // 成功
    }

    /**
     * 玩家修改自己的密码（通过游戏内命令）
     */
    public String changePasswordByMinecraft(String minecraftName, String newPassword) {
        if (newPassword == null || newPassword.length() < 4) return "密码至少4位";
        adminsLock.lock();
        try {
            for (AdminAccount a : admins) {
                if (minecraftName.equalsIgnoreCase(a.minecraftName)) {
                    a.password = hashPassword(newPassword);
                    saveAdmins();
                    return null;
                }
            }
        } finally { adminsLock.unlock(); }
        return "您尚未注册 Web 管理员账户，请先输入 /ht web register 注册";
    }

    /**
     * 获取某游戏ID对应的账户信息（用于游戏内提示）
     */
    public String getUsernameByMinecraft(String minecraftName) {
        if (minecraftName == null) return null;
        adminsLock.lock();
        try {
            for (AdminAccount a : admins) {
                if (minecraftName.equalsIgnoreCase(a.minecraftName)) return a.username;
            }
            return null;
        } finally { adminsLock.unlock(); }
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }

    private void loadAdmins() {
        admins.clear();
        List<?> adminList = plugin.getConfig().getList("web-admins");
        if (adminList != null) {
            for (Object obj : adminList) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = (Map<String, String>) obj;
                    String u = map.get("username");
                    String p = map.get("password");
                    String r = map.get("role");
                    String mc = map.get("minecraft-name");
                    if (u != null && p != null) {
                        admins.add(new AdminAccount(u, p, r != null ? r : "admin", mc));
                    }
                }
            }
        }
        // 迁移：若检测到默认弱口令 admin/admin123，自动重置为随机密码
        boolean hasWeakDefault = admins.size() == 1
                && "admin".equals(admins.get(0).username)
                && "admin123".equals(admins.get(0).password);
        if (admins.isEmpty() || hasWeakDefault) {
            String randomPwd = generateRandomPassword(14);
            admins.clear();
            admins.add(new AdminAccount("admin", hashPassword(randomPwd), "superadmin", null));
            saveAdmins();
            plugin.getLogger().warning("========================================");
            plugin.getLogger().warning("Web 面板首次启动（或检测到默认弱口令），已自动生成超级管理员密码");
            plugin.getLogger().warning("用户名: admin");
            plugin.getLogger().warning("密码:   " + randomPwd);
            plugin.getLogger().warning("密码已自动保存到 plugins/HotelsX/config.yml 的 web-admins 节点");
            plugin.getLogger().warning("请登录后立即在 [管理员账号 -> 修改密码] 中更改密码！");
            plugin.getLogger().warning("========================================");
            // 同时写入一个密码提示文件（方便查阅）
            writePasswordFile(randomPwd);
        }
    }

    /**
     * 生成指定长度的强随机密码（字母+数字，排除易混淆字符）
     */
    private String generateRandomPassword(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        SecureRandom sr = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(sr.nextInt(chars.length())));
        return sb.toString();
    }

    /** PBKDF2 哈希迭代次数 */
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int PBKDF2_KEY_BITS = 256;
    private static final String PBKDF2_PREFIX = "pbkdf2$";

    /**
     * 使用 PBKDF2WithHmacSHA256 对密码加盐哈希（存储格式：pbkdf2$iter$saltBase64$hashBase64）
     */
    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return PBKDF2_PREFIX + PBKDF2_ITERATIONS + "$"
                    + java.util.Base64.getEncoder().encodeToString(salt) + "$"
                    + java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            plugin.getLogger().warning("密码哈希失败: " + e.getMessage());
            return password; // 极端异常兜底，仅当 PBKDF2 不可用时
        }
    }

    /**
     * 验证密码：兼容旧版明文存储（自动迁移到哈希）与当前 PBKDF2 哈希存储
     */
    private static boolean verifyPassword(String plain, String stored) {
        if (stored == null) return false;
        if (stored.startsWith(PBKDF2_PREFIX)) {
            try {
                String[] parts = stored.split("\\$");
                if (parts.length != 4) return false;
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = java.util.Base64.getDecoder().decode(parts[2]);
                byte[] expected = java.util.Base64.getDecoder().decode(parts[3]);
                javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                        plain.toCharArray(), salt, iterations, expected.length * 8);
                byte[] actual = skf.generateSecret(spec).getEncoded();
                return java.security.MessageDigest.isEqual(expected, actual);
            } catch (Exception e) {
                return false;
            }
        }
        // 旧版明文：等值比较（兼容历史配置），登录成功后调用方负责迁移到哈希
        return plain.equals(stored);
    }

    /** 判断存储的密码是否还是明文（需要迁移） */
    private static boolean isPlainPassword(String stored) {
        return stored != null && !stored.startsWith(PBKDF2_PREFIX);
    }

    /**
     * 将初始密码写入 plugins/HotelsX/web-admin.txt 供管理员查阅
     */
    private void writePasswordFile(String pwd) {
        try {
            File f = new File(plugin.getDataFolder(), "web-admin.txt");
            try (FileWriter fw = new FileWriter(f, StandardCharsets.UTF_8)) {
                fw.write("HotelsX Web 面板初始管理员账号\n");
                fw.write("============================\n");
                fw.write("用户名: admin\n");
                fw.write("密码:   " + pwd + "\n");
                fw.write("============================\n");
                fw.write("登录后请立即修改密码！\n");
                fw.write("此文件可安全删除。\n");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("写入初始密码文件失败: " + e.getMessage());
        }
    }

    private void saveAdmins() {
        List<Map<String, String>> list = new ArrayList<>();
        for (AdminAccount a : admins) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("username", a.username);
            map.put("password", a.password);
            map.put("role", a.role);
            if (a.minecraftName != null && !a.minecraftName.isEmpty()) {
                map.put("minecraft-name", a.minecraftName);
            }
            list.add(map);
        }
        plugin.getConfig().set("web-admins", list);
        plugin.saveConfig();
    }

    private Session getSession(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            for (String part : cookieHeader.split(";")) {
                part = part.trim();
                if (part.startsWith("hotelsx_session=")) {
                    String sid = part.substring("hotelsx_session=".length());
                    Session s = sessions.get(sid);
                    if (s != null) {
                        int timeout = plugin.getConfig().getInt("web-session-timeout", 30);
                        long elapsed = System.currentTimeMillis() - s.lastAccess;
                        if (elapsed < timeout * 60_000L) {
                            s.lastAccess = System.currentTimeMillis();
                            return s;
                        } else {
                            sessions.remove(sid);
                        }
                    }
                }
            }
        }
        return null;
    }

    private String createSession(String username, String role, String minecraftName, HttpExchange exchange) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        sessions.put(sid, new Session(username, role, minecraftName));
        exchange.getResponseHeaders().add("Set-Cookie",
                "hotelsx_session=" + sid + "; Path=/; HttpOnly; SameSite=Lax" + (sslEnabled ? "; Secure" : ""));
        return sid;
    }

    private void destroySession(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            for (String part : cookieHeader.split(";")) {
                part = part.trim();
                if (part.startsWith("hotelsx_session=")) {
                    sessions.remove(part.substring("hotelsx_session=".length()));
                }
            }
        }
        exchange.getResponseHeaders().add("Set-Cookie",
                "hotelsx_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0" + (sslEnabled ? "; Secure" : ""));
    }

    private boolean requireAuth(HttpExchange exchange) throws IOException {
        if (getSession(exchange) == null) {
            // API 请求统一返回 401 JSON（避免依赖 Accept 头导致 401/302 行为不一致）
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                sendJson(exchange, 401, "{\"error\":\"需要登录\"}");
            } else {
                // 页面请求重定向到登录页
                setSecurityHeaders(exchange);
                exchange.getResponseHeaders().set("Location", "/login");
                byte[] body = new byte[0];
                exchange.sendResponseHeaders(302, -1);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            }
            return false;
        }
        return true;
    }

    private boolean requireSuperAdmin(HttpExchange exchange) throws IOException {
        Session s = getSession(exchange);
        if (s == null) { sendJson(exchange, 401, "{\"error\":\"需要登录\"}"); return false; }
        if (!"superadmin".equals(s.role)) { sendJson(exchange, 403, "{\"error\":\"权限不足\"}"); return false; }
        return true;
    }

    /**
     * 检查当前会话用户是否有权管理指定房间
     * superadmin: 可管理所有房间
     * admin: 可编辑（修改）所有房间，但不能删除
     * user: 只能管理自己名下的房间
     */
    private boolean canManageRoom(Session session, HotelRoom room) {
        if ("superadmin".equals(session.role)) return true;
        if ("admin".equals(session.role)) return true;
        // user 角色只能管理自己的房间
        if (session.minecraftName != null && !session.minecraftName.isEmpty()) {
            return session.minecraftName.equalsIgnoreCase(room.getOwnerName());
        }
        return false;
    }

    /**
     * 检查当前会话用户是否有权删除指定房间
     * superadmin: 可删除所有房间
     * admin: 不可删除任何房间
     * user: 只能删除自己名下的房间
     */
    private boolean canDeleteRoom(Session session, HotelRoom room) {
        if ("superadmin".equals(session.role)) return true;
        if ("admin".equals(session.role)) return false;
        // user 只能删除自己的房间
        if (session.minecraftName != null && !session.minecraftName.isEmpty()) {
            return session.minecraftName.equalsIgnoreCase(room.getOwnerName());
        }
        return false;
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        setSecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
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
    private void setSecurityHeaders(HttpExchange exchange) {
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

    private String readBody(HttpExchange exchange) throws IOException {
        try (java.io.InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseForm(String body) {
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

    private String urlDecode(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** JS 字符串转义（用于 HTML 属性内的 onclick 等 JS 上下文，单引号需转义为 \' 防止字符串逃逸） */
    private String escapeJsAttr(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    /** JSON 字符串转义（处理反斜杠、引号、控制字符） */
    private String escapeJsonString(String s) {
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

    private String toJson(Map<String, Object> map) {
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

    private String toJsonValue(Object v) {
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

    private String formatUptime(long sec) {
        long d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60;
        if (d > 0) return d + "天 " + h + "时 " + m + "分";
        if (h > 0) return h + "时 " + m + "分";
        return m + "分钟";
    }

    private void installConsoleCapture() {
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
    private List<String> getConsoleLogs() {
        return new ArrayList<>(consoleBuffer);
    }

    private WebConsoleHandler consoleHandler;

    private void logConsoleLine(String line) {
        if (line == null || line.isEmpty()) return;
        logConsole(line);
    }

    private void logConsole(String line) {
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
                logConsoleLine(msg);
            } catch (Exception ignored) {}
        }
        @Override
        public void flush() {}
        @Override
        public void close() throws SecurityException {}
    }

    // ==================== Handlers ====================

    private class LoginPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (getSession(exchange) != null) {
                setSecurityHeaders(exchange);
                exchange.getResponseHeaders().set("Location", "/");
                byte[] body = new byte[0];
                exchange.sendResponseHeaders(302, -1);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                return;
            }
            sendHtml(exchange, 200, buildLoginPage());
        }
    }

    private class LoginApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> form = parseForm(readBody(exchange));
            String u = form.getOrDefault("username", "");
            String p = form.getOrDefault("password", "");
            for (AdminAccount a : admins) {
                if (a.username.equals(u) && verifyPassword(p, a.password)) {
                    // 旧版明文密码登录成功后自动迁移为 PBKDF2 哈希存储
                    if (isPlainPassword(a.password)) {
                        a.password = hashPassword(p);
                        saveAdmins();
                    }
                    createSession(a.username, a.role, a.minecraftName, exchange);
                    Map<String, Object> r = new HashMap<>();
                    r.put("success", true); r.put("username", a.username); r.put("role", a.role);
                    r.put("minecraftName", a.minecraftName);
                    sendJson(exchange, 200, toJson(r));
                    return;
                }
            }
            sendJson(exchange, 401, "{\"error\":\"用户名或密码错误\"}");
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            destroySession(exchange);
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                setSecurityHeaders(exchange);
                exchange.getResponseHeaders().set("Location", "/login");
                byte[] body = new byte[0];
                exchange.sendResponseHeaders(302, -1);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            } else {
                sendJson(exchange, 200, "{\"success\":true}");
            }
        }
    }

    private class MeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Session s = getSession(exchange);
            if (s == null) { sendJson(exchange, 401, "{\"error\":\"需要登录\"}"); return; }
            Map<String, Object> r = new HashMap<>();
            r.put("username", s.username); r.put("role", s.role);
            r.put("minecraftName", s.minecraftName);
            sendJson(exchange, 200, toJson(r));
        }
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 未知 /api/* 路径统一返回 404，避免被根路径兜底成 200 主面板
            if (exchange.getRequestURI().getPath().startsWith("/api/")) {
                sendJson(exchange, 404, "{\"error\":\"接口不存在\"}");
                return;
            }
            if (!requireAuth(exchange)) return;
            try {
                sendHtml(exchange, 200, buildDashboard(getSession(exchange)));
            } catch (Exception e) {
                // 兜底：任何构建异常都返回可见错误页，避免空响应导致"未发送任何数据"
                plugin.getLogger().warning("构建 Web 面板页面出错: " + e);
                String errHtml = "<html><meta charset='UTF-8'><body style='font-family:sans-serif;padding:40px;'>"
                        + "<h2>HotelsX Web 面板加载失败</h2>"
                        + "<p style='color:red;'>" + escape(String.valueOf(e)) + "</p>"
                        + "<pre style='background:#f3f4f6;padding:12px;border-radius:8px;overflow:auto;'>"
                        + escape(stackTrace(e)) + "</pre></body></html>";
                sendHtml(exchange, 500, errHtml);
            }
        }
    }

    private String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private class ServerStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
            s.put("maxPlayers", Bukkit.getMaxPlayers());
            s.put("serverName", Bukkit.getVersion());
            s.put("mcVersion", Bukkit.getBukkitVersion());
            s.put("uptimeSeconds", (System.currentTimeMillis() - serverStartTime) / 1000);
            s.put("tps", Math.round(Bukkit.getTPS()[0] * 100.0) / 100.0);
            Runtime rt = Runtime.getRuntime();
            s.put("usedMemoryMB", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
            s.put("maxMemoryMB", rt.maxMemory() / (1024 * 1024));
            sendJson(exchange, 200, toJson(s));
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Map<String, Object> s = new LinkedHashMap<>();
            Collection<HotelRoom> rooms = plugin.getRoomStorage().getAllRooms();
            Collection<RoomCollection> cols = plugin.getRoomStorage().getAllCollections();
            int occ=0, avail=0, maint=0, locked=0;
            for (HotelRoom r : rooms) {
                switch (r.getStatus()) {
                    case OCCUPIED -> occ++;
                    case AVAILABLE -> avail++;
                    case MAINTENANCE -> maint++;
                }
                if (r.isLocked()) locked++;
            }
            s.put("totalRooms", rooms.size()); s.put("occupied", occ); s.put("available", avail);
            s.put("maintenance", maint); s.put("locked", locked); s.put("totalCollections", cols.size());
            sendJson(exchange, 200, toJson(s));
        }
    }

    private class StatsDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Collection<HotelRoom> rooms = plugin.getRoomStorage().getAllRooms();
            int occ=0, avail=0, maint=0, locked=0;
            double totalPrice=0, occupiedRevenue=0;
            Map<String, Integer> ownerCount = new LinkedHashMap<>();
            // 价格区间
            int p0=0, p1_50=0, p51_100=0, p101_200=0, p200p=0;
            for (HotelRoom r : rooms) {
                switch (r.getStatus()) {
                    case OCCUPIED -> occ++;
                    case AVAILABLE -> avail++;
                    case MAINTENANCE -> maint++;
                }
                if (r.isLocked()) locked++;
                double price = r.getCurrentPrice();
                totalPrice += price;
                if (r.getStatus() == HotelRoom.RoomStatus.OCCUPIED) occupiedRevenue += price;
                String owner = r.getOwnerName() != null ? r.getOwnerName() : "未知";
                ownerCount.merge(owner, 1, Integer::sum);
                if (price <= 0) p0++;
                else if (price <= 50) p1_50++;
                else if (price <= 100) p51_100++;
                else if (price <= 200) p101_200++;
                else p200p++;
            }
            // Top 5 房主
            List<Map<String, Object>> topOwners = new ArrayList<>();
            ownerCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("count", e.getValue());
                    topOwners.add(m);
                });
            // 最近 30 天每日入住次数（基于入住付款流水）
            List<Map<String, Object>> dailyCheckins = buildDailyCheckins(30);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalRooms", rooms.size());
            result.put("occupied", occ);
            result.put("available", avail);
            result.put("maintenance", maint);
            result.put("locked", locked);
            result.put("occupancyRate", rooms.isEmpty() ? 0 : Math.round(occ * 100.0 / rooms.size() * 100.0) / 100.0);
            result.put("totalPriceValue", Math.round(totalPrice * 100.0) / 100.0);
            result.put("occupiedRevenue", Math.round(occupiedRevenue * 100.0) / 100.0);
            result.put("avgPrice", rooms.isEmpty() ? 0 : Math.round(totalPrice / rooms.size() * 100.0) / 100.0);
            // 价格分布
            List<Map<String, Object>> priceDist = new ArrayList<>();
            priceDist.add(Map.of("range", "免费", "count", p0));
            priceDist.add(Map.of("range", "1-50", "count", p1_50));
            priceDist.add(Map.of("range", "51-100", "count", p51_100));
            priceDist.add(Map.of("range", "101-200", "count", p101_200));
            priceDist.add(Map.of("range", "200+", "count", p200p));
            result.put("priceDist", priceDist);
            result.put("topOwners", topOwners);
            result.put("dailyCheckins", dailyCheckins);
            sendJson(exchange, 200, toJson(result));
        }
    }

    /**
     * 统计最近 days 天每天入住付款的次数（从新到旧）
     */
    private List<Map<String, Object>> buildDailyCheckins(int days) {
        List<Map<String, Object>> list = new ArrayList<>();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        long now = System.currentTimeMillis();
        List<Transaction> txs = plugin.getTransactionStorage().getAll();
        for (int i = days - 1; i >= 0; i--) {
            cal.setTimeInMillis(now);
            cal.add(java.util.Calendar.DAY_OF_YEAR, -i);
            java.util.Calendar dayStart = (java.util.Calendar) cal.clone();
            dayStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
            dayStart.set(java.util.Calendar.MINUTE, 0);
            dayStart.set(java.util.Calendar.SECOND, 0);
            dayStart.set(java.util.Calendar.MILLISECOND, 0);
            long start = dayStart.getTimeInMillis();
            long end = start + 86_400_000L;
            int count = 0;
            for (Transaction tx : txs) {
                if (tx.getType() == Transaction.TxType.CHECKIN_PAY
                        && tx.getTimestamp() >= start && tx.getTimestamp() < end) {
                    count++;
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", new java.text.SimpleDateFormat("MM-dd").format(dayStart.getTime()));
            m.put("count", count);
            list.add(m);
        }
        return list;
    }

    /**
     * 运营报表：房主收益排行 Top 10 + 数据对比（今日/本周/本月 vs 上一周期）
     */
    private class StatsReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Map<String, Object> result = new LinkedHashMap<>();
            List<Transaction> allTxs = plugin.getTransactionStorage().getAll();

            // 1. 房主收益排行 Top 10
            Map<String, Double> ownerRevenue = new LinkedHashMap<>();
            Map<String, Integer> ownerCount = new LinkedHashMap<>();
            for (Transaction tx : allTxs) {
                if (tx.getType() == Transaction.TxType.CHECKIN_RECV) {
                    String name = tx.getPlayerName() != null ? tx.getPlayerName() : "未知";
                    ownerRevenue.merge(name, tx.getAmount(), Double::sum);
                    ownerCount.merge(name, 1, Integer::sum);
                }
            }
            List<Map<String, Object>> topOwners = new ArrayList<>();
            ownerRevenue.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("revenue", Math.round(e.getValue() * 100.0) / 100.0);
                    m.put("count", ownerCount.getOrDefault(e.getKey(), 0));
                    topOwners.add(m);
                });
            result.put("topOwners", topOwners);

            // 2. 数据对比
            long now = System.currentTimeMillis();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            result.put("today", periodStats(allTxs, now, 0, 1, false));
            result.put("yesterday", periodStats(allTxs, now, -1, 1, true));
            java.util.Calendar weekCal = (java.util.Calendar) cal.clone();
            weekCal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY);
            weekCal.set(java.util.Calendar.HOUR_OF_DAY, 0); weekCal.set(java.util.Calendar.MINUTE, 0);
            weekCal.set(java.util.Calendar.SECOND, 0); weekCal.set(java.util.Calendar.MILLISECOND, 0);
            long thisWeekStart = weekCal.getTimeInMillis();
            long lastWeekStart = thisWeekStart - 7 * 86400_000L;
            result.put("thisWeek", rangeStats(allTxs, thisWeekStart, now));
            result.put("lastWeek", rangeStats(allTxs, lastWeekStart, thisWeekStart));
            java.util.Calendar monthCal = (java.util.Calendar) cal.clone();
            monthCal.set(java.util.Calendar.DAY_OF_MONTH, 1);
            monthCal.set(java.util.Calendar.HOUR_OF_DAY, 0); monthCal.set(java.util.Calendar.MINUTE, 0);
            monthCal.set(java.util.Calendar.SECOND, 0); monthCal.set(java.util.Calendar.MILLISECOND, 0);
            long thisMonthStart = monthCal.getTimeInMillis();
            monthCal.add(java.util.Calendar.MONTH, -1);
            long lastMonthStart = monthCal.getTimeInMillis();
            result.put("thisMonth", rangeStats(allTxs, thisMonthStart, now));
            result.put("lastMonth", rangeStats(allTxs, lastMonthStart, thisMonthStart));
            sendJson(exchange, 200, toJson(result));
        }
        private Map<String, Object> periodStats(List<Transaction> txs, long now, int dayOffset, int days, boolean clampEnd) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(now);
            cal.add(java.util.Calendar.DAY_OF_YEAR, dayOffset);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0);
            long start = cal.getTimeInMillis();
            long end = start + days * 86400_000L;
            if (clampEnd && end > now) end = now;
            return rangeStats(txs, start, end);
        }
        private Map<String, Object> rangeStats(List<Transaction> txs, long start, long end) {
            int checkins = 0; double revenue = 0;
            for (Transaction tx : txs) {
                if (tx.getTimestamp() >= start && tx.getTimestamp() < end) {
                    if (tx.getType() == Transaction.TxType.CHECKIN_PAY) checkins++;
                    if (tx.getType() == Transaction.TxType.CHECKIN_RECV) revenue += tx.getAmount();
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("checkins", checkins);
            m.put("revenue", Math.round(revenue * 100.0) / 100.0);
            return m;
        }
    }

    private class RoomBatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Session session = getSession(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = parseForm(readBody(exchange));
            String action = form.getOrDefault("action", "");
            String roomIdsStr = form.getOrDefault("roomIds", "");
            if (action.isEmpty() || roomIdsStr.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"缺少参数\"}"); return;
            }
            String[] roomIds = roomIdsStr.split(",");
            int success = 0, failed = 0;
            StringBuilder errors = new StringBuilder();
            for (String rid : roomIds) {
                rid = rid.trim();
                if (rid.isEmpty()) continue;
                HotelRoom room = plugin.getRoomStorage().getRoom(rid);
                if (room == null) { failed++; errors.append(rid).append("不存在; "); continue; }
                try {
                    switch (action) {
                        case "lock" -> {
                            if (!canManageRoom(session, room)) { failed++; errors.append(rid).append("无权操作; "); continue; }
                            room.setLocked(true);
                        }
                        case "unlock" -> {
                            if (!canManageRoom(session, room)) { failed++; errors.append(rid).append("无权操作; "); continue; }
                            room.setLocked(false);
                        }
                        case "delete" -> {
                            if (!canDeleteRoom(session, room)) { failed++; errors.append(rid).append("无权删除; "); continue; }
                            plugin.getRoomStorage().removeRoom(rid);
                            success++;
                            continue;
                        }
                        case "setPrice" -> {
                            if (!canManageRoom(session, room)) { failed++; errors.append(rid).append("无权操作; "); continue; }
                            try { room.setPrice(Double.parseDouble(form.getOrDefault("price", "0"))); }
                            catch (NumberFormatException e) { failed++; errors.append(rid).append("价格格式错误; "); continue; }
                        }
                        case "setStatus" -> {
                            if (!canManageRoom(session, room)) { failed++; errors.append(rid).append("无权操作; "); continue; }
                            try { room.setStatus(HotelRoom.RoomStatus.valueOf(form.getOrDefault("status", "AVAILABLE"))); }
                            catch (IllegalArgumentException e) { failed++; errors.append(rid).append("状态无效; "); continue; }
                        }
                        default -> { failed++; errors.append("未知操作; "); continue; }
                    }
                    success++;
                } catch (Exception e) {
                    failed++;
                    errors.append(rid).append("操作失败; ");
                }
            }
            plugin.getRoomStorage().saveAll();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", true);
            r.put("message", "批量操作完成: 成功 " + success + " 个, 失败 " + failed + " 个");
            if (failed > 0) r.put("errors", errors.toString());
            sendJson(exchange, 200, toJson(r));
        }
    }

    private class RoomsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            StringBuilder sb = new StringBuilder("{\"rooms\":[");
            boolean first = true;
            for (HotelRoom r : plugin.getRoomStorage().getAllRooms()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"id\":\"").append(escape(r.getId())).append("\",");
                sb.append("\"name\":\"").append(escape(r.getName() != null ? r.getName() : "")).append("\",");
                sb.append("\"owner\":\"").append(escape(r.getOwnerName())).append("\",");
                sb.append("\"status\":\"").append(r.getStatus()).append("\",");
                sb.append("\"price\":").append(r.getCurrentPrice()).append(",");
                sb.append("\"locked\":").append(r.isLocked()).append(",");
                sb.append("\"guest\":\"").append(escape(r.getCurrentGuestName() != null ? r.getCurrentGuestName() : "")).append("\",");
                sb.append("\"world\":\"").append(escape(r.getWorldName() != null ? r.getWorldName() : "")).append("\"}");
            }
            sb.append("]}");
            sendHtml(exchange, 200, sb.toString());
        }
    }

    private class RoomDeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Session session = getSession(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = parseForm(readBody(exchange));
            String roomId = form.getOrDefault("roomId", "");
            if (roomId.isEmpty()) { sendJson(exchange, 400, "{\"error\":\"缺少房间ID\"}"); return; }
            HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
            if (room == null) { sendJson(exchange, 404, "{\"error\":\"房间不存在\"}"); return; }
            if (!canDeleteRoom(session, room)) { sendJson(exchange, 403, "{\"error\":\"只有超级管理员可以删除房间\"}"); return; }
            plugin.getRoomStorage().removeRoom(roomId);
            plugin.getRoomStorage().saveAll();
            Map<String, Object> r = new HashMap<>();
            r.put("success", true); r.put("message", "房间已删除: " + room.getName());
            sendJson(exchange, 200, toJson(r));
        }
    }

    private class RoomUpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Session session = getSession(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = parseForm(readBody(exchange));
            String roomId = form.getOrDefault("roomId", "");
            if (roomId.isEmpty()) { sendJson(exchange, 400, "{\"error\":\"缺少房间ID\"}"); return; }
            HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
            if (room == null) { sendJson(exchange, 404, "{\"error\":\"房间不存在\"}"); return; }
            if (!canManageRoom(session, room)) { sendJson(exchange, 403, "{\"error\":\"无权操作此房间\"}"); return; }
            if (form.containsKey("name")) room.setName(form.get("name"));
            if (form.containsKey("price")) {
                try { room.setPrice(Double.parseDouble(form.get("price"))); } catch (NumberFormatException ignored) {}
            }
            if (form.containsKey("locked")) room.setLocked(Boolean.parseBoolean(form.get("locked")));
            if (form.containsKey("status")) {
                try { room.setStatus(HotelRoom.RoomStatus.valueOf(form.get("status"))); } catch (IllegalArgumentException ignored) {}
            }
            plugin.getRoomStorage().saveRoom(room);
            plugin.getRoomStorage().saveAll();
            Map<String, Object> r = new HashMap<>();
            r.put("success", true); r.put("message", "房间已更新");
            sendJson(exchange, 200, toJson(r));
        }
    }

    private class CollectionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            StringBuilder sb = new StringBuilder("{\"collections\":[");
            boolean first = true;
            for (RoomCollection c : plugin.getRoomStorage().getAllCollections()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"id\":\"").append(escape(c.getId())).append("\",");
                sb.append("\"name\":\"").append(escape(c.getName())).append("\",");
                sb.append("\"owner\":\"").append(escape(c.getOwnerName())).append("\",");
                sb.append("\"roomCount\":").append(c.getRoomIds().size()).append("}");
            }
            sb.append("]}");
            sendHtml(exchange, 200, sb.toString());
        }
    }

    private class AdminsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            StringBuilder sb = new StringBuilder("{\"admins\":[");
            boolean first = true;
            for (AdminAccount a : admins) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"username\":\"").append(escape(a.username)).append("\",\"role\":\"").append(a.role).append("\"}");
            }
            sb.append("]}");
            sendHtml(exchange, 200, sb.toString());
        }
    }

    private class AdminAddHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = parseForm(readBody(exchange));
            String u = form.getOrDefault("username", "").trim();
            String p = form.getOrDefault("password", "");
            String r = form.getOrDefault("role", "admin");
            String mc = form.getOrDefault("minecraft-name", "").trim();
            if (u.isEmpty() || p.isEmpty()) { sendJson(exchange, 400, "{\"error\":\"用户名和密码不能为空\"}"); return; }
            for (AdminAccount a : admins) {
                if (a.username.equals(u)) { sendJson(exchange, 409, "{\"error\":\"用户名已存在\"}"); return; }
            }
            admins.add(new AdminAccount(u, hashPassword(p), r, mc.isEmpty() ? null : mc));
            saveAdmins();
            Map<String, Object> res = new HashMap<>();
            res.put("success", true); res.put("message", "管理员已添加: " + u);
            sendJson(exchange, 200, toJson(res));
        }
    }

    private class AdminDeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = parseForm(readBody(exchange));
            String target = form.getOrDefault("username", "");
            Session s = getSession(exchange);
            if (s != null && s.username.equals(target)) { sendJson(exchange, 400, "{\"error\":\"不能删除自己\"}"); return; }
            boolean removed = admins.removeIf(a -> a.username.equals(target));
            if (!removed) { sendJson(exchange, 404, "{\"error\":\"管理员不存在\"}"); return; }
            saveAdmins();
            Map<String, Object> res = new HashMap<>();
            res.put("success", true); res.put("message", "管理员已删除: " + target);
            sendJson(exchange, 200, toJson(res));
        }
    }

    private class AdminPasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = parseForm(readBody(exchange));
            String oldPwd = form.getOrDefault("oldPassword", "");
            String newPwd = form.getOrDefault("newPassword", "");
            Session s = getSession(exchange);
            for (AdminAccount a : admins) {
                if (a.username.equals(s.username)) {
                    if (!verifyPassword(oldPwd, a.password)) { sendJson(exchange, 401, "{\"error\":\"原密码错误\"}"); return; }
                    a.password = hashPassword(newPwd);
                    saveAdmins();
                    Map<String, Object> r = new HashMap<>();
                    r.put("success", true); r.put("message", "密码已修改");
                    sendJson(exchange, 200, toJson(r));
                    return;
                }
            }
            sendJson(exchange, 404, "{\"error\":\"管理员不存在\"}");
        }
    }

    private class ConsoleLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            List<String> lines = getConsoleLogs();
            Map<String, Object> r = new HashMap<>();
            r.put("lines", lines);
            r.put("count", lines.size());
            sendJson(exchange, 200, toJson(r));
        }
    }

    private class ConsoleCommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = parseForm(readBody(exchange));
            String command = form.getOrDefault("command", "").trim();
            if (command.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"命令不能为空\"}"); return;
            }
            Session session = getSession(exchange);
            logConsole("[HotelsX] " + session.username + " 执行: " + command);
            SchedulerCompat.runTask(plugin, () -> {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (Exception e) {
                    logConsole("[HotelsX] 命令执行错误: " + e.getMessage());
                }
            });
            Map<String, Object> r = new HashMap<>();
            r.put("success", true);
            r.put("message", "命令已执行: " + command);
            sendJson(exchange, 200, toJson(r));
        }
    }

    private class BroadcastHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = parseForm(readBody(exchange));
            String type = form.getOrDefault("type", "chat").trim();   // chat / title / actionbar
            String message = form.getOrDefault("message", "").trim();
            String target = form.getOrDefault("target", "all").trim(); // all / player
            if (message.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"公告内容不能为空\"}"); return;
            }
            Session session = getSession(exchange);
            org.bukkit.entity.Player receiver = null;
            if ("player".equalsIgnoreCase(target)) {
                String playerName = form.getOrDefault("player", "").trim();
                if (playerName.isEmpty()) {
                    sendJson(exchange, 400, "{\"error\":\"请指定玩家名\"}"); return;
                }
                receiver = Bukkit.getPlayerExact(playerName);
                if (receiver == null) {
                    sendJson(exchange, 404, "{\"error\":\"玩家不在线: " + playerName + "\"}"); return;
                }
            }
            String logTarget = "player".equalsIgnoreCase(target) ? ("玩家 " + receiver.getName()) : "全服";
            logConsole("[HotelsX] " + session.username + " 向" + logTarget + "发送公告(" + type + "): " + message);
            org.bukkit.entity.Player finalReceiver = receiver;
            String finalType = type;
            SchedulerCompat.runTask(plugin, () -> {
                String finalMsg = message.replace("&", "§");
                switch (finalType.toLowerCase()) {
                    case "title" -> {
                        if (finalReceiver != null) {
                            finalReceiver.sendTitle(finalMsg, "", 10, 60, 10);
                        } else {
                            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                                p.sendTitle(finalMsg, "", 10, 60, 10);
                            }
                        }
                    }
                    case "actionbar" -> {
                        if (finalReceiver != null) {
                            finalReceiver.sendActionBar(finalMsg);
                        } else {
                            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                                p.sendActionBar(finalMsg);
                            }
                        }
                    }
                    default -> {
                        // chat
                        String prefix = "§e[§6公告§e] §r";
                        if (finalReceiver != null) {
                            finalReceiver.sendMessage(prefix + finalMsg);
                        } else {
                            Bukkit.broadcastMessage(prefix + finalMsg);
                        }
                    }
                }
            });
            Map<String, Object> r = new HashMap<>();
            r.put("success", true);
            r.put("message", "公告已发送" + (receiver != null ? "给 " + receiver.getName() : "给全服"));
            sendJson(exchange, 200, toJson(r));
        }
    }

    private class TransactionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Session session = getSession(exchange);
            if (session == null) return;

            List<Transaction> all = plugin.getTransactionStorage().getAll();
            List<Map<String, Object>> result = new ArrayList<>();
            double totalIn = 0, totalOut = 0;
            for (Transaction tx : all) {
                // 权限过滤：admin/superadmin 看全部；user 只看自己的
                if ("user".equals(session.role) && session.minecraftName != null) {
                    String playerNameMatch = tx.getPlayerName();
                    if (playerNameMatch == null || !playerNameMatch.equalsIgnoreCase(session.minecraftName)) {
                        continue;
                    }
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", tx.getId());
                m.put("type", tx.getType().name());
                m.put("timestamp", tx.getTimestamp());
                m.put("timeStr", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new java.util.Date(tx.getTimestamp())));
                m.put("player", tx.getPlayerName());
                m.put("amount", tx.getAmount());
                m.put("roomId", tx.getRoomId());
                m.put("roomName", tx.getRoomName());
                m.put("remark", tx.getRemark());
                if (tx.getType() == Transaction.TxType.CHECKIN_RECV) totalIn += tx.getAmount();
                if (tx.getType() == Transaction.TxType.CHECKIN_PAY) totalOut += tx.getAmount();
                result.add(m);
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("total", result.size());
            resp.put("totalIn", Math.round(totalIn * 100) / 100.0);
            resp.put("totalOut", Math.round(totalOut * 100) / 100.0);
            resp.put("list", result);
            sendJson(exchange, 200, toJson(resp));
        }
    }

    /**
     * 评分评价数据
     */
    private class RatingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Session session = getSession(exchange);
            if (session == null) return;

            List<com.hotels.model.Rating> all = plugin.getRatingStorage().getAll();
            List<Map<String, Object>> result = new ArrayList<>();
            double sumAll = 0;
            for (com.hotels.model.Rating r : all) {
                // user 角色过滤：只看自己评的或自己房主房间的评分
                if ("user".equals(session.role) && session.minecraftName != null) {
                    boolean related = r.getGuestName() != null && r.getGuestName().equalsIgnoreCase(session.minecraftName);
                    if (!related) {
                        HotelRoom room = plugin.getRoomStorage().getRoom(r.getRoomId());
                        if (room != null && room.getOwnerName() != null
                                && room.getOwnerName().equalsIgnoreCase(session.minecraftName)) {
                            related = true;
                        }
                    }
                    if (!related) continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("roomId", r.getRoomId());
                m.put("roomName", r.getRoomName());
                m.put("guest", r.getGuestName());
                m.put("score", r.getScore());
                m.put("comment", r.getComment());
                m.put("timeStr", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new java.util.Date(r.getTime())));
                sumAll += r.getScore();
                result.add(m);
            }

            // 房间评分统计（按平均分排序）
            List<Map<String, Object>> roomStats = new ArrayList<>();
            Set<String> roomIds = new LinkedHashSet<>();
            for (com.hotels.model.Rating r : all) {
                roomIds.add(r.getRoomId());
            }
            for (String rid : roomIds) {
                HotelRoom room = plugin.getRoomStorage().getRoom(rid);
                if ("user".equals(session.role) && session.minecraftName != null) {
                    if (room == null || room.getOwnerName() == null
                            || !room.getOwnerName().equalsIgnoreCase(session.minecraftName)) {
                        continue;
                    }
                }
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("roomId", rid);
                sm.put("roomName", room != null && room.getName() != null ? room.getName() : rid);
                sm.put("owner", room != null && room.getOwnerName() != null ? room.getOwnerName() : "");
                sm.put("avg", plugin.getRatingStorage().getAverage(rid));
                sm.put("count", plugin.getRatingStorage().countByRoom(rid));
                roomStats.add(sm);
            }
            roomStats.sort((a, b) -> {
                int c = Double.compare((double) b.get("avg"), (double) a.get("avg"));
                return c != 0 ? c : Integer.compare((int) b.get("count"), (int) a.get("count"));
            });

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("total", result.size());
            resp.put("avgAll", result.isEmpty() ? 0 : Math.round((sumAll / result.size()) * 10) / 10.0);
            resp.put("list", result);
            resp.put("roomStats", roomStats);
            sendJson(exchange, 200, toJson(resp));
        }
    }

    /**
     * 收益提现数据
     */
    private class EscrowHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Session session = getSession(exchange);
            if (session == null) return;

            boolean escrowMode = plugin.getConfig().getBoolean("economy.escrow-mode", false);
            List<com.hotels.storage.EscrowStorage.PendingInfo> pendingList = plugin.getEscrowStorage().getPendingList();
            List<Map<String, Object>> balances = new ArrayList<>();
            double totalPending = 0;
            for (com.hotels.storage.EscrowStorage.PendingInfo p : pendingList) {
                // user 角色只看自己的
                if ("user".equals(session.role) && session.minecraftName != null
                        && !p.name.equalsIgnoreCase(session.minecraftName)) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("uuid", p.uuid);
                m.put("name", p.name);
                m.put("pending", Math.round(p.pending * 100) / 100.0);
                m.put("totalWithdrawn", Math.round(p.totalWithdrawn * 100) / 100.0);
                m.put("totalEarned", Math.round(p.totalEarned * 100) / 100.0);
                totalPending += p.pending;
                balances.add(m);
            }

            List<com.hotels.storage.EscrowStorage.Withdrawal> ws = plugin.getEscrowStorage().getWithdrawals();
            List<Map<String, Object>> wList = new ArrayList<>();
            for (com.hotels.storage.EscrowStorage.Withdrawal w : ws) {
                if ("user".equals(session.role) && session.minecraftName != null
                        && !w.name.equalsIgnoreCase(session.minecraftName)) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", w.name);
                m.put("amount", Math.round(w.amount * 100) / 100.0);
                m.put("timeStr", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new java.util.Date(w.time)));
                wList.add(m);
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("escrowMode", escrowMode);
            resp.put("totalPending", Math.round(totalPending * 100) / 100.0);
            resp.put("balances", balances);
            resp.put("withdrawals", wList);
            sendJson(exchange, 200, toJson(resp));
        }
    }

    /**
     * 执行提现
     */
    private class EscrowWithdrawHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Session session = getSession(exchange);
            if (session == null) return;

            if (!plugin.getConfig().getBoolean("economy.escrow-mode", false)) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "当前为实时到账模式，收益已直接发放，无需提现");
                sendJson(exchange, 200, toJson(resp));
                return;
            }
            if (!plugin.getEconomyManager().isEnabled()) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "经济系统未启用");
                sendJson(exchange, 200, toJson(resp));
                return;
            }

            String body = readBody(exchange);
            Map<String, String> form = parseForm(body);
            String uuid = form.get("uuid");
            String name = form.get("name");

            // 权限：superadmin/admin 可代任意房主提现；user 只能提现自己（通过 minecraft-name 匹配）
            if ("user".equals(session.role)) {
                if (session.minecraftName == null || name == null
                        || !name.equalsIgnoreCase(session.minecraftName)) {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", false);
                    resp.put("message", "您只能提现自己的收益");
                    sendJson(exchange, 200, toJson(resp));
                    return;
                }
            }

            if (uuid == null || uuid.isEmpty()) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "缺少玩家信息");
                sendJson(exchange, 200, toJson(resp));
                return;
            }

            double pending = plugin.getEscrowStorage().getPending(uuid);
            if (pending <= 0) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "该玩家没有待提现的收益");
                sendJson(exchange, 200, toJson(resp));
                return;
            }

            try {
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                if (plugin.getEconomyManager().deposit(op, pending)) {
                    plugin.getEscrowStorage().completeWithdrawal(uuid, name, pending);
                    logConsole("Web提现: " + name + " 提现 " + plugin.getEconomyManager().format(pending) + " 到钱包");
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", true);
                    resp.put("message", "已发放 " + plugin.getEconomyManager().format(pending) + " 给 " + name);
                    sendJson(exchange, 200, toJson(resp));
                } else {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", false);
                    resp.put("message", "发放失败，请查看服务器日志");
                    sendJson(exchange, 200, toJson(resp));
                }
            } catch (Exception e) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "提现出错: " + e.getMessage());
                sendJson(exchange, 200, toJson(resp));
            }
        }
    }

    /**
     * SSE 实时推送：服务器状态每 1 秒推送一次（替代前端轮询）
     */
    private class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            setSecurityHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            try {
                while (true) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
                    s.put("maxPlayers", Bukkit.getMaxPlayers());
                    s.put("uptimeSeconds", (System.currentTimeMillis() - serverStartTime) / 1000);
                    s.put("tps", Math.round(Bukkit.getTPS()[0] * 100.0) / 100.0);
                    Runtime rt = Runtime.getRuntime();
                    s.put("usedMemoryMB", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
                    s.put("maxMemoryMB", rt.maxMemory() / (1024 * 1024));
                    String payload = "data: " + toJson(s) + "\n\n";
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // 客户端断开连接，正常退出
            } finally {
                try { exchange.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * SSE 实时推送：控制台日志每 2 秒推送一次（替代前端轮询）
     */
    private class ConsoleEventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            setSecurityHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            try {
                // 从内存缓冲 consoleBuffer 读取完整快照推送，与 /api/console/logs 保持一致，
                // 不依赖 logs/latest.log 文件的相对路径（服务端工作目录不同或文件未生成时也能正常输出）
                while (true) {
                    List<String> lines = getConsoleLogs();
                    int from = Math.max(0, lines.size() - 100);
                    StringBuilder content = new StringBuilder();
                    for (int i = from; i < lines.size(); i++) {
                        if (content.length() > 0) content.append("\n");
                        content.append(lines.get(i));
                    }
                    if (content.length() > 0) {
                        // SSE 规范：多行 data 需每行以 "data: " 开头，浏览器才会拼接出真实换行符
                        StringBuilder sse = new StringBuilder();
                        for (String l : content.toString().split("\n", -1)) {
                            sse.append("data: ").append(l).append("\n");
                        }
                        sse.append("\n");
                        os.write(sse.toString().getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt();
            } catch (Exception e) { /* 客户端断开 */ }
            finally {
                try { exchange.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== HTML ====================

    private String commonCSS() {
        return """
                <style>
                *{margin:0;padding:0;box-sizing:border-box;}
                body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;background:#f3f4f6;color:#1f2937;}
                .sidebar{position:fixed;left:0;top:0;width:220px;height:100vh;background:#111827;color:#fff;display:flex;flex-direction:column;z-index:100;transition:transform 0.3s ease;}
                .sidebar .logo{padding:20px;font-size:18px;font-weight:700;border-bottom:1px solid #374151;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);}
                .sidebar .menu{flex:1;padding:12px 0;overflow-y:auto;}
                .sidebar .menu a{display:block;padding:10px 20px;color:#d1d5db;text-decoration:none;font-size:14px;border-left:3px solid transparent;}
                .sidebar .menu a:hover{background:#1f2937;color:#fff;}
                .sidebar .menu a.active{background:#1f2937;color:#60a5fa;border-left-color:#60a5fa;}
                .sidebar .menu .st{padding:12px 20px 6px;font-size:11px;color:#6b7280;text-transform:uppercase;letter-spacing:1px;}
                .sidebar .user{padding:16px;border-top:1px solid #374151;font-size:12px;color:#9ca3af;}
                .sidebar .user .name{color:#fff;font-weight:600;font-size:14px;margin-bottom:2px;}
                .main{margin-left:220px;padding:20px;min-height:100vh;}
                .topbar{background:#fff;border-radius:12px;padding:16px 20px;margin-bottom:20px;box-shadow:0 1px 3px rgba(0,0,0,0.05);display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;}
                .topbar h2{font-size:20px;font-weight:700;}
                .topbar .actions{display:flex;gap:10px;}
                .btn{display:inline-flex;align-items:center;gap:6px;padding:8px 16px;border:none;border-radius:8px;font-size:13px;font-weight:500;cursor:pointer;text-decoration:none;transition:all 0.2s;}
                .btn-primary{background:#3b82f6;color:#fff;}
                .btn-primary:hover{background:#2563eb;}
                .btn-danger{background:#ef4444;color:#fff;}
                .btn-danger:hover{background:#dc2626;}
                .btn-secondary{background:#6b7280;color:#fff;}
                .btn-sm{padding:4px 10px;font-size:12px;}
                .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:14px;margin-bottom:20px;}
                .card{background:#fff;border-radius:12px;padding:16px;box-shadow:0 1px 3px rgba(0,0,0,0.05);}
                .card .label{font-size:12px;color:#6b7280;margin-bottom:6px;}
                .card .value{font-size:clamp(24px,3vw,30px);font-weight:700;color:#111827;}
                .card .sub{font-size:11px;color:#9ca3af;margin-top:4px;}
                .card.blue .value{color:#3b82f6;} .card.green .value{color:#10b981;}
                .card.orange .value{color:#f59e0b;} .card.purple .value{color:#8b5cf6;}
                .card.red .value{color:#ef4444;} .card.cyan .value{color:#06b6d4;}
                .section{background:#fff;border-radius:12px;padding:16px;box-shadow:0 1px 3px rgba(0,0,0,0.05);margin-bottom:20px;overflow-x:auto;-webkit-overflow-scrolling:touch;}
                .section-title{font-size:15px;font-weight:600;margin-bottom:12px;padding-bottom:10px;border-bottom:1px solid #e5e7eb;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;}
                .sub-title{font-size:13px;font-weight:600;color:#374151;margin:18px 0 10px;padding-bottom:8px;border-bottom:1px dashed #e5e7eb;}
                .alert-bar{padding:10px 14px;border-radius:8px;font-size:13px;margin-bottom:14px;}
                .badge{background:#eef2ff;color:#4338ca;padding:3px 8px;border-radius:20px;font-size:11px;font-weight:500;}
                table{width:100%;border-collapse:collapse;min-width:500px;}
                th{background:#f9fafb;padding:10px 12px;text-align:left;font-size:12px;font-weight:600;color:#374151;border-bottom:1px solid #e5e7eb;white-space:nowrap;}
                td{padding:10px 12px;border-bottom:1px solid #f3f4f6;font-size:13px;white-space:nowrap;}
                tr:hover{background:#f9fafb;}
                .status-tag{padding:3px 8px;border-radius:4px;font-size:11px;font-weight:500;display:inline-block;}
                .status-occupied{background:#fef3c7;color:#92400e;}
                .status-available{background:#d1fae5;color:#065f46;}
                .status-maintenance{background:#ede9fe;color:#5b21b6;}
                .status-locked{background:#f3f4f6;color:#374151;}
                .progress-bar{height:8px;background:#e5e7eb;border-radius:4px;overflow:hidden;margin-top:8px;}
                .progress-fill{height:100%;background:linear-gradient(90deg,#3b82f6,#8b5cf6);border-radius:4px;transition:width 0.3s;}
                .progress-fill.green{background:linear-gradient(90deg,#10b981,#22c55e);}
                .progress-fill.orange{background:linear-gradient(90deg,#f59e0b,#f97316);}
                .modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,0.5);display:none;align-items:center;justify-content:center;z-index:200;}
                .modal-overlay.show{display:flex;}
                .modal{background:#fff;border-radius:12px;padding:24px;min-width:320px;max-width:90%;max-height:90vh;overflow-y:auto;}
                .modal h3{font-size:18px;margin-bottom:16px;}
                .form-group{margin-bottom:16px;}
                .form-group label{display:block;font-size:14px;font-weight:600;color:#374151;margin-bottom:8px;}
                .form-group input,.form-group select,.form-group textarea{width:100%;padding:12px 16px;border:1px solid #d1d5db;border-radius:8px;font-size:16px;box-sizing:border-box;font-family:inherit;background:#fff;}
                .form-group input:focus,.form-group select:focus,.form-group textarea:focus{outline:none;border-color:#667eea;box-shadow:0 0 0 3px rgba(102,126,234,0.1);}
                .inline-form{display:flex;gap:10px;flex-wrap:wrap;align-items:flex-end;}
                .inline-form label{font-size:13px;font-weight:600;color:#374151;margin-bottom:6px;display:block;}
                .inline-form input{padding:10px 14px;border:1px solid #d1d5db;border-radius:8px;font-size:14px;min-width:180px;box-sizing:border-box;font-family:inherit;background:#fff;}
                .inline-form input:focus{outline:none;border-color:#667eea;box-shadow:0 0 0 3px rgba(102,126,234,0.1);}
                .modal .form-group{margin-bottom:14px;}
                .modal input,.modal select{width:100%;padding:10px 14px;border:1px solid #d1d5db;border-radius:8px;font-size:14px;}
                .modal input:focus,.modal select:focus{outline:none;border-color:#667eea;box-shadow:0 0 0 3px rgba(102,126,234,0.1);}
                .modal-actions{display:flex;gap:10px;justify-content:flex-end;margin-top:16px;flex-wrap:wrap;}
                .modal-actions .btn{flex:1;min-width:100px;justify-content:center;}
                .mobile-toggle{display:none;background:none;border:none;color:#111827;font-size:24px;cursor:pointer;padding:4px 8px;}
                .sidebar-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,0.5);z-index:99;}
                @media(max-width:1024px){
                .sidebar{width:180px;}
                .main{margin-left:180px;}
                }
                @media(max-width:768px){
                .mobile-toggle{display:block;}
                .sidebar{transform:translateX(-100%);width:220px;}
                .sidebar.open{transform:translateX(0);}
                .sidebar-overlay.show{display:block;}
                .main{margin-left:0;padding:12px;}
                .topbar{padding:12px 16px;}
                .topbar h2{font-size:16px;}
                .stats{grid-template-columns:repeat(2,1fr);}
                .section{padding:12px;}
                table{min-width:480px;}
                th,td{padding:8px 10px;font-size:12px;}
                .btn-sm{padding:4px 8px;font-size:11px;}
                .modal{min-width:0;padding:20px;border-radius:10px;}
                .modal-actions{flex-direction:column-reverse;}
                .modal-actions .btn{width:100%;}
                .inline-form{flex-direction:column;align-items:stretch;}
                .inline-form input{min-width:100%;}
                .section-title{flex-direction:column;align-items:stretch;}
                .section-title input{max-width:100%%!important;}
                }
                @media(max-width:480px){
                .stats{grid-template-columns:1fr;}
                .card{padding:14px;}
                .card .value{font-size:20px;}
                .section-title{font-size:14px;}
                .toast{top:10px;right:10px;left:10px;text-align:center;}
                table{min-width:420px;}
                th,td{padding:6px 8px;font-size:11px;}
                .btn{padding:6px 12px;font-size:12px;}
                }
                /* ===== 夜间模式 ===== */
                body.dark{background:#0f172a;color:#e2e8f0;}
                body.dark .topbar,body.dark .card,body.dark .section,body.dark .modal{background:#1e293b;border-color:#334155;box-shadow:0 1px 3px rgba(0,0,0,0.4);}
                body.dark .topbar h2{color:#f1f5f9;}
                body.dark .card .label,body.dark .card .sub{color:#94a3b8;}
                body.dark .card .value{color:#f1f5f9;}
                body.dark .section-title{color:#f1f5f9;border-bottom-color:#334155;}
                body.dark .sub-title{color:#cbd5e1;border-bottom-color:#334155;}
                body.dark .alert-bar{background:#1e293b;border-color:#334155;}
                body.dark table{color:#e2e8f0;}
                body.dark th{background:#0f172a;color:#cbd5e1;border-bottom-color:#334155;}
                body.dark td{border-bottom-color:#334155;}
                body.dark tr:hover{background:#293548;}
                body.dark .empty{color:#64748b;}
                body.dark .form-group label,body.dark .inline-form label,body.dark .modal h3{color:#e2e8f0;}
                body.dark .form-group input,body.dark .form-group select,body.dark .form-group textarea,body.dark .inline-form input,body.dark .modal input,body.dark .modal select{background:#0f172a;border-color:#334155;color:#e2e8f0;}
                body.dark input::placeholder{color:#64748b;}
                body.dark .modal input{background:#0f172a;}
                body.dark .badge{background:#1e3a8a;color:#bfdbfe;}
                body.dark .status-locked{background:#334155;color:#cbd5e1;}
                body.dark .status-occupied{background:#78350f;color:#fcd34d;}
                body.dark .status-available{background:#064e3b;color:#6ee7b7;}
                body.dark .status-maintenance{background:#4c1d95;color:#ddd6fe;}
                body.dark .progress-bar{background:#334155;}
                body.dark .section-title input,body.dark #roomSearch,body.dark #txSearch,body.dark #txFilter{background:#0f172a;border-color:#334155;color:#e2e8f0;}
                body.dark .toast{background:#1e293b;border-color:#334155;color:#e2e8f0;}
                body.dark #consoleOutput{background:#020617;border-color:#334155;}
                body.dark #consoleCmd{background:#0f172a;border-color:#334155;color:#e2e8f0;}
                body.dark #batchToolbar{background:#172554;border-color:#1e3a8a;}
                body.dark #batchToolbar span{color:#dbeafe;}
                body.dark .inline-form input:focus{outline:none;border-color:#667eea;box-shadow:0 0 0 3px rgba(102,126,234,0.2);}
                body.dark #reportContent > div > div,body.dark #reportContent table{background:#1e293b;color:#e2e8f0;}
                body.dark #reportContent th{background:#0f172a;color:#cbd5e1;border-bottom-color:#334155;}
                body.dark #reportContent td{border-bottom-color:#334155;}
                /* ===== 运营报表 ===== */
                .rp-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px;}
                .rp-card{background:#fff;border-radius:12px;padding:16px;box-shadow:0 1px 3px rgba(0,0,0,0.05);}
                .rp-card h4{font-size:14px;font-weight:600;color:#111827;margin-bottom:12px;display:flex;align-items:center;gap:8px;}
                .rp-card h4::before{content:'';width:4px;height:14px;background:linear-gradient(180deg,#667eea,#764ba2);border-radius:2px;}
                .rp-rank-item{display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid #f3f4f6;}
                .rp-rank-item:last-child{border-bottom:none;}
                .rp-rank-badge{width:24px;height:24px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;color:#fff;flex-shrink:0;}
                .rp-rank-badge.gold{background:linear-gradient(135deg,#fbbf24,#f59e0b);}
                .rp-rank-badge.silver{background:linear-gradient(135deg,#d1d5db,#9ca3af);}
                .rp-rank-badge.bronze{background:linear-gradient(135deg,#f59e0b,#b45309);}
                .rp-rank-badge.other{background:#e5e7eb;color:#6b7280;}
                .rp-rank-name{font-size:13px;font-weight:600;color:#111827;flex:0 0 80px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
                .rp-rank-bar{flex:1;height:8px;background:#f3f4f6;border-radius:4px;overflow:hidden;}
                .rp-rank-bar-fill{height:100%;background:linear-gradient(90deg,#667eea,#764ba2);border-radius:4px;transition:width .5s;}
                .rp-rank-count{font-size:12px;color:#9ca3af;flex:0 0 44px;text-align:right;}
                .rp-rank-rev{font-size:13px;font-weight:700;color:#10b981;flex:0 0 72px;text-align:right;}
                .rp-cmp{display:flex;flex-direction:column;gap:10px;}
                .rp-cmp-item{background:#f9fafb;border-radius:10px;padding:12px;}
                .rp-cmp-top{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;}
                .rp-cmp-title{font-size:13px;font-weight:700;color:#111827;}
                .rp-cmp-tag{font-size:11px;color:#9ca3af;}
                .rp-cmp-rows{display:flex;gap:20px;}
                .rp-cmp-metric{flex:1;}
                .rp-cmp-metric .lbl{font-size:11px;color:#9ca3af;margin-bottom:2px;}
                .rp-cmp-metric .val{font-size:16px;font-weight:700;color:#111827;}
                .rp-cmp-delta{font-size:11px;font-weight:600;margin-left:6px;}
                .rp-delta-up{color:#10b981;}
                .rp-delta-down{color:#ef4444;}
                .rp-cmp-row{display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;}
                .rp-cmp-label{font-size:12px;color:#6b7280;}
                .rp-cmp-val{font-size:18px;font-weight:700;color:#111827;}
                .rp-cmp-sub{font-size:11px;color:#9ca3af;margin-bottom:8px;}
                .rp-delta{font-size:12px;font-weight:600;margin-left:8px;}
                .rp-rank-list{display:flex;flex-direction:column;gap:4px;}
                /* 运营报表 - 夜间模式 */
                body.dark .rp-card{background:#1e293b;box-shadow:0 1px 3px rgba(0,0,0,0.4);}
                body.dark .rp-card h4{color:#f1f5f9;}
                body.dark .rp-rank-item{border-bottom-color:#334155;}
                body.dark .rp-rank-name{color:#f1f5f9;}
                body.dark .rp-rank-bar{background:#334155;}
                body.dark .rp-rank-badge.other{background:#334155;color:#cbd5e1;}
                body.dark .rp-cmp-item{background:#0f172a;}
                body.dark .rp-cmp-title{color:#f1f5f9;}
                body.dark .rp-cmp-tag{color:#64748b;}
                body.dark .rp-cmp-metric .val{color:#f1f5f9;}
                body.dark .rp-cmp-metric .lbl{color:#94a3b8;}
                body.dark .rp-cmp-row .rp-cmp-label{color:#94a3b8;}
                body.dark .rp-cmp-val{color:#f1f5f9;}
                body.dark .rp-cmp-sub{color:#64748b;}
                /* ===== 全局 UI 增强 ===== */
                body{background:linear-gradient(180deg,#eef2f7 0%,#e5eaf3 100%);}
                ::-webkit-scrollbar{width:8px;height:8px;}
                ::-webkit-scrollbar-thumb{background:#c7cdd8;border-radius:4px;}
                ::-webkit-scrollbar-thumb:hover{background:#a8b0c0;}
                ::-webkit-scrollbar-track{background:transparent;}
                .sidebar{background:linear-gradient(180deg,#111827 0%,#1e1b4b 100%);box-shadow:2px 0 16px rgba(0,0,0,0.12);}
                .sidebar .logo{background:linear-gradient(135deg,#667eea 0%,#a855f7 100%);font-weight:800;letter-spacing:1px;text-shadow:0 2px 6px rgba(0,0,0,0.25);}
                .sidebar .menu a{position:relative;border-radius:10px;margin:2px 10px;padding:10px 16px;display:flex;align-items:center;gap:10px;transition:all .2s;}
                .sidebar .menu a svg{width:16px;height:16px;fill:currentColor;flex-shrink:0;}
                .sidebar .menu a:hover{background:rgba(255,255,255,0.08);color:#fff;transform:translateX(3px);}
                .sidebar .menu a.active{background:linear-gradient(90deg,rgba(102,126,234,0.35),rgba(168,85,247,0.25));color:#fff;border-left:3px solid #a855f7;box-shadow:inset 0 0 12px rgba(102,126,234,0.15);}
                .sidebar .menu .st{font-weight:700;color:#8b94a7;padding:14px 20px 6px;}
                .sidebar .user{background:rgba(255,255,255,0.05);border-top:1px solid rgba(255,255,255,0.1);}
                .topbar{background:rgba(255,255,255,0.85);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);border:1px solid rgba(255,255,255,0.6);box-shadow:0 4px 20px rgba(15,23,42,0.06);}
                .topbar h2{background:linear-gradient(135deg,#4338ca,#9333ea);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;font-weight:800;}
                .card{position:relative;border:1px solid rgba(255,255,255,0.7);transition:transform .2s,box-shadow .2s;overflow:hidden;}
                .card:hover{transform:translateY(-3px);box-shadow:0 12px 28px rgba(15,23,42,0.1);}
                .card::before{content:'';position:absolute;top:0;left:0;right:0;height:3px;background:linear-gradient(90deg,#6366f1,#a855f7);opacity:0;transition:opacity .2s;}
                .card:hover::before{opacity:1;}
                .card .value{font-weight:800;letter-spacing:.3px;}
                .card.gray .value{color:#6b7280;}
                .card .icon{position:absolute;top:14px;right:14px;width:36px;height:36px;border-radius:10px;display:flex;align-items:center;justify-content:center;}
                .card .icon svg{width:20px;height:20px;fill:#fff;}
                .card.cyan .icon{background:linear-gradient(135deg,#06b6d4,#0891b2);}
                .card.green .icon{background:linear-gradient(135deg,#10b981,#059669);}
                .card.orange .icon{background:linear-gradient(135deg,#f59e0b,#ea580c);}
                .card.purple .icon{background:linear-gradient(135deg,#8b5cf6,#7c3aed);}
                .card.blue .icon{background:linear-gradient(135deg,#3b82f6,#2563eb);}
                .card.gray .icon{background:linear-gradient(135deg,#6b7280,#4b5563);}
                .card.red .icon{background:linear-gradient(135deg,#ef4444,#dc2626);}
                .btn{border-radius:10px;font-weight:600;box-shadow:0 2px 6px rgba(15,23,42,0.08);transition:all .2s;}
                .btn:hover{transform:translateY(-1px);box-shadow:0 6px 16px rgba(15,23,42,0.15);}
                .btn:active{transform:translateY(0);}
                .btn-primary{background:linear-gradient(135deg,#6366f1,#8b5cf6);}
                .btn-primary:hover{background:linear-gradient(135deg,#4f46e5,#7c3aed);filter:brightness(1.05);}
                .btn-secondary{background:linear-gradient(135deg,#64748b,#475569);}
                .btn-danger{background:linear-gradient(135deg,#ef4444,#dc2626);}
                .btn-danger:hover{background:linear-gradient(135deg,#dc2626,#b91c1c);filter:brightness(1.05);}
                table{border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;}
                th{background:linear-gradient(180deg,#f8fafc,#eef2f7);font-weight:700;letter-spacing:.3px;border-bottom:2px solid #e2e8f0;}
                td{border-bottom:1px solid #f1f5f9;}
                tr:hover td{background:#eef2ff;}
                .status-tag{border-radius:20px;padding:4px 12px;font-weight:600;letter-spacing:.3px;box-shadow:inset 0 1px 2px rgba(0,0,0,0.05);}
                .badge{border-radius:20px;padding:4px 10px;font-weight:600;box-shadow:0 1px 3px rgba(15,23,42,0.08);}
                .form-group input,.form-group select,.form-group textarea,.inline-form input,.modal input,.modal select{border-radius:10px;transition:border-color .2s,box-shadow .2s;background:#fff;}
                .form-group input:focus,.form-group select:focus,.form-group textarea:focus,.inline-form input:focus,.modal input:focus,.modal select:focus{border-color:#6366f1;box-shadow:0 0 0 4px rgba(99,102,241,0.12);}
                .modal{animation:modalIn .25s ease;border-radius:16px;box-shadow:0 25px 60px rgba(15,23,42,0.25);}
                @keyframes modalIn{from{opacity:0;transform:translateY(24px) scale(0.96);}to{opacity:1;transform:translateY(0) scale(1);}}
                .toast{position:fixed;top:24px;right:24px;z-index:999;background:#1e293b;color:#fff;padding:12px 20px;border-radius:12px;font-size:14px;font-weight:600;box-shadow:0 10px 30px rgba(15,23,42,0.3);opacity:0;transform:translateY(-12px);transition:opacity .3s,transform .3s;max-width:360px;}
                .toast.show{opacity:1;transform:translateY(0);}
                .toast.toast-success{background:linear-gradient(135deg,#10b981,#059669);}
                .toast.toast-error{background:linear-gradient(135deg,#ef4444,#dc2626);}
                .toast.toast-info{background:linear-gradient(135deg,#3b82f6,#2563eb);}
                .progress-bar{background:#e2e8f0;box-shadow:inset 0 1px 3px rgba(15,23,42,0.08);}
                .progress-fill{box-shadow:0 1px 4px rgba(59,130,246,0.3);}
                .refresh-note{font-size:12px;color:#94a3b8;background:#f1f5f9;padding:4px 12px;border-radius:20px;}
                .section{transition:box-shadow .2s;}
                .section:hover{box-shadow:0 6px 20px rgba(15,23,42,0.07);}
                .empty{color:#94a3b8;font-weight:500;}
                #batchToolbar{background:linear-gradient(135deg,#eef2ff,#f5f3ff);border:1px solid #e0e7ff;border-radius:10px;}
                #consoleOutput{font-family:Consolas,"Courier New",monospace;background:#0f172a;color:#a5b4fc;}
                #roomSearch,#txSearch,#txFilter,#consoleCmd{border:1px solid #d1d5db;border-radius:10px;transition:border-color .2s,box-shadow .2s;}
                #roomSearch:focus,#txSearch:focus,#txFilter:focus,#consoleCmd:focus{outline:none;border-color:#6366f1;box-shadow:0 0 0 4px rgba(99,102,241,0.12);}
                .modal-overlay{backdrop-filter:blur(3px);-webkit-backdrop-filter:blur(3px);}
                .modal h3{font-weight:700;color:#1f2937;}
                .section-title{font-weight:700;}
                .section-title .badge{vertical-align:middle;}
                /* ===== 统计图表卡片 ===== */
                .chart-card{background:#fff;border-radius:12px;padding:16px;box-shadow:0 1px 3px rgba(0,0,0,0.05);}
                .chart-card h4{font-size:14px;font-weight:600;color:#111827;margin:0 0 12px;display:flex;align-items:center;gap:8px;}
                .chart-card h4::before{content:'';width:4px;height:14px;background:linear-gradient(180deg,#667eea,#764ba2);border-radius:2px;}
                .chart-bar{background:#e5e7eb;border-radius:4px;height:8px;overflow:hidden;margin-top:6px;}
                .muted{color:#9ca3af;}
                /* 统计图表卡片 - 夜间模式 */
                body.dark .chart-card{background:#1e293b;box-shadow:0 1px 3px rgba(0,0,0,0.4);}
                body.dark .chart-card h4{color:#f1f5f9;}
                body.dark .chart-bar{background:#334155;}
                body.dark .muted{color:#94a3b8;}
                /* 夜间模式增强 */
                body.dark{background:linear-gradient(180deg,#0f172a 0%,#111827 100%);}
                body.dark .topbar{background:rgba(30,41,59,0.85);border-color:rgba(51,65,85,0.6);}
                body.dark .card,body.dark .section{background:linear-gradient(180deg,#1e293b,#182234);}
                body.dark .card:hover{box-shadow:0 12px 28px rgba(0,0,0,0.5);}
                body.dark th{background:linear-gradient(180deg,#1e293b,#162032);}
                body.dark tr:hover td{background:rgba(99,102,241,0.08);}
                body.dark .btn-secondary{background:linear-gradient(135deg,#334155,#1e293b);}
                body.dark .refresh-note{background:#1e293b;color:#94a3b8;}
                body.dark .modal{background:linear-gradient(180deg,#1e293b,#182234);}
                body.dark .progress-bar{background:#334155;}
                body.dark .modal h3{color:#f1f5f9;}
                body.dark #roomSearch,body.dark #txSearch,body.dark #txFilter,body.dark #consoleCmd{background:#0f172a;border-color:#334155;color:#e2e8f0;}
                body.dark .toast{box-shadow:0 10px 30px rgba(0,0,0,0.6);}
                body.dark .sidebar{background:linear-gradient(180deg,#0b1220 0%,#151130 100%);}
                body.dark .card .icon{box-shadow:0 4px 10px rgba(0,0,0,0.3);}
                </style>
                """;
    }

    private String buildLoginPage() {
        return """
                <!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>HotelsX - 登录</title>
                <style>
                *{margin:0;padding:0;box-sizing:border-box;}
                body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px;position:relative;overflow:hidden;
                background:linear-gradient(135deg,#0f172a 0%,#1e1b4b 45%,#4c1d95 100%);}
                body::before{content:'';position:absolute;width:520px;height:520px;background:radial-gradient(circle,rgba(102,126,234,.35),transparent 70%);top:-160px;right:-120px;border-radius:50%;pointer-events:none;}
                body::after{content:'';position:absolute;width:420px;height:420px;background:radial-gradient(circle,rgba(167,139,250,.28),transparent 70%);bottom:-140px;left:-100px;border-radius:50%;pointer-events:none;}
                .wrap{position:relative;z-index:1;width:100%;max-width:400px;animation:up .5s ease;}
                @keyframes up{from{opacity:0;transform:translateY(24px);}to{opacity:1;transform:translateY(0);}}
                .box{background:rgba(255,255,255,.97);backdrop-filter:blur(20px);border-radius:20px;padding:40px 32px;box-shadow:0 25px 60px rgba(0,0,0,.45);border:1px solid rgba(255,255,255,.25);}
                .brand{text-align:center;margin-bottom:28px;}
                .logo{width:64px;height:64px;margin:0 auto 14px;border-radius:18px;background:linear-gradient(135deg,#667eea,#764ba2);display:flex;align-items:center;justify-content:center;box-shadow:0 12px 24px rgba(102,126,234,.4);}
                .logo svg{width:34px;height:34px;fill:#fff;}
                .box h1{font-size:26px;font-weight:800;letter-spacing:1px;background:linear-gradient(135deg,#667eea,#764ba2);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;}
                .box .sub{color:#6b7280;font-size:13px;margin-top:6px;}
                .fg{margin-bottom:18px;}
                .fg label{display:block;font-size:13px;font-weight:600;margin-bottom:7px;color:#374151;}
                .iwrap{position:relative;}
                .iwrap input{width:100%;padding:12px 14px 12px 42px;border:1.5px solid #e5e7eb;border-radius:10px;font-size:14px;background:#f9fafb;transition:all .2s;color:#1f2937;}
                .iwrap input:focus{outline:none;border-color:#667eea;background:#fff;box-shadow:0 0 0 3px rgba(102,126,234,.15);}
                .iwrap svg{position:absolute;left:14px;top:50%;transform:translateY(-50%);width:18px;height:18px;fill:#9ca3af;transition:fill .2s;pointer-events:none;}
                .iwrap input:focus+svg{fill:#667eea;}
                .btn-login{width:100%;padding:13px;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:#fff;border:none;border-radius:10px;font-size:15px;font-weight:700;letter-spacing:6px;cursor:pointer;margin-top:10px;box-shadow:0 8px 20px rgba(102,126,234,.35);transition:all .25s;}
                .btn-login:hover{transform:translateY(-2px);box-shadow:0 12px 26px rgba(102,126,234,.45);filter:brightness(1.06);}
                .btn-login:active{transform:translateY(0);box-shadow:0 4px 12px rgba(102,126,234,.3);}
                .err{background:#fef2f2;color:#dc2626;padding:10px 14px;border-radius:10px;font-size:13px;margin-bottom:16px;display:none;border:1px solid #fecaca;}
                .foot{text-align:center;margin-top:24px;font-size:12px;color:#9ca3af;letter-spacing:.5px;}
                @media(max-width:480px){
                .box{padding:30px 24px;border-radius:16px;}
                .box h1{font-size:22px;}
                .logo{width:56px;height:56px;border-radius:15px;}
                }
                </style></head><body>
                <div class="wrap"><div class="box">
                <div class="brand">
                <h1>HotelsX</h1><p class="sub">酒店管理系统 · 管理员登录</p></div>
                <div id="err" class="err"></div>
                <form id="lf">
                <div class="fg"><label for="lgUser">用户名</label><div class="iwrap"><input type="text" id="lgUser" name="username" required placeholder="请输入用户名"><svg viewBox="0 0 24 24"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg></div></div>
                <div class="fg"><label for="lgPass">密码</label><div class="iwrap"><input type="password" id="lgPass" name="password" required placeholder="请输入密码"><svg viewBox="0 0 24 24"><path d="M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-3 0H9V6c0-1.66 1.34-3 3-3s3 1.34 3 3v2z"/></svg></div></div>
                <button type="submit" class="btn-login">登 录</button></form>
                <div class="foot">HotelsX v1.4.0 · 安全登录</div>
                </div></div>
                <script>
                document.getElementById('lf').addEventListener('submit',async function(e){
                e.preventDefault();const el=document.getElementById('err');el.style.display='none';
                try{const r=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(new FormData(this))});
                if(r.ok){window.location.href='/';}else{const d=await r.json();el.textContent=d.error||'登录失败';el.style.display='block';}}
                catch(err){el.textContent='网络错误';el.style.display='block';}});
                </script></body></html>
                """;
    }

    private String buildDashboard(Session session) {
        Collection<HotelRoom> rooms = plugin.getRoomStorage().getAllRooms();
        Collection<RoomCollection> cols = plugin.getRoomStorage().getAllCollections();
        int total=rooms.size(), occ=0, avail=0, maint=0, locked=0;
        for (HotelRoom r : rooms) {
            switch (r.getStatus()) {
                case OCCUPIED -> occ++; case AVAILABLE -> avail++; case MAINTENANCE -> maint++;
            }
            if (r.isLocked()) locked++;
        }
        long uptime = (System.currentTimeMillis() - serverStartTime) / 1000;
        int online = Bukkit.getOnlinePlayers().size();
        int maxP = Bukkit.getMaxPlayers();
        double tps = Math.round(Bukkit.getTPS()[0] * 100.0) / 100.0;
        Runtime rt = Runtime.getRuntime();
        long usedMem = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMem = rt.maxMemory() / (1024 * 1024);
        int memPct = maxMem > 0 ? (int)((usedMem * 100) / maxMem) : 0;

        StringBuilder roomsHtml = new StringBuilder();
        if (rooms.isEmpty()) {
            roomsHtml.append("<tr><td colspan='9' class='empty'>暂无房间</td></tr>");
        } else {
            for (HotelRoom r : rooms) {
                String sc, st;
                if (r.isLocked()) { sc="status-locked"; st="已锁定"; }
                else {
                    sc = switch (r.getStatus()) {
                        case OCCUPIED -> "status-occupied"; case AVAILABLE -> "status-available";
                        case MAINTENANCE -> "status-maintenance";
                    };
                    st = switch (r.getStatus()) {
                        case OCCUPIED -> "已入住"; case AVAILABLE -> "可入住";
                        case MAINTENANCE -> "维护中";
                    };
                }
                roomsHtml.append("<tr>")
                    .append("<td><input type='checkbox' class='room-checkbox' value='").append(escape(r.getId())).append("' onchange='updateBatchToolbar()'></td>")
                    .append("<td>").append(escape(r.getId())).append("</td>")
                    .append("<td>").append(escape(r.getName()!=null?r.getName():"")).append("</td>")
                    .append("<td>").append(escape(r.getOwnerName())).append("</td>")
                    .append("<td><span class='status-tag ").append(sc).append("'>").append(st).append("</span></td>")
                    .append("<td>").append(String.format("%.2f",r.getCurrentPrice())).append("</td>")
                    .append("<td>").append(escape(r.getCurrentGuestName()!=null?r.getCurrentGuestName():"-")).append("</td>")
                    .append("<td>").append(r.isLocked()?"是":"否").append("</td>")
                    .append("<td>");
                boolean canManage = canManageRoom(session, r);
                boolean canDelete = canDeleteRoom(session, r);
                if (canManage) {
                    roomsHtml.append("<button class='btn btn-secondary btn-sm' onclick=\"openEdit('").append(escapeJsAttr(r.getId())).append("','").append(escapeJsAttr(r.getName()!=null?r.getName():"")).append("',").append(r.getCurrentPrice()).append(",'").append(escapeJsAttr(r.getStatus().name())).append("',").append(r.isLocked()).append(")\">编辑</button> ");
                }
                if (canDelete) {
                    roomsHtml.append("<button class='btn btn-danger btn-sm' onclick=\"delRoom('").append(escapeJsAttr(r.getId())).append("')\">删除</button>");
                }
                if (!canManage && !canDelete) {
                    roomsHtml.append("<span style='color:#999;font-size:12px'>无权操作</span>");
                }
                roomsHtml.append("</td></tr>");
            }
        }

        StringBuilder colsHtml = new StringBuilder();
        if (cols.isEmpty()) {
            colsHtml.append("<tr><td colspan='4' class='empty'>暂无合集</td></tr>");
        } else {
            for (RoomCollection c : cols) {
                colsHtml.append("<tr>")
                    .append("<td>").append(escape(c.getId())).append("</td>")
                    .append("<td>").append(escape(c.getName())).append("</td>")
                    .append("<td>").append(escape(c.getOwnerName())).append("</td>")
                    .append("<td>").append(c.getRoomIds().size()).append("</td></tr>");
            }
        }

        StringBuilder adminRows = new StringBuilder();
        for (AdminAccount a : admins) {
            adminRows.append("<tr>")
                .append("<td>").append(escape(a.username)).append("</td>")
                .append("<td>").append(switch (a.role) {
                    case "superadmin" -> "<span class='badge'>超级管理员</span>";
                    case "admin" -> "<span class='badge' style='background:#667eea'>管理员</span>";
                    default -> "<span class='badge' style='background:#6b7280'>普通用户</span>";
                }).append("</td>")
                .append("<td>");
            if ("superadmin".equals(session.role) && !a.username.equals(session.username)) {
                adminRows.append("<button class='btn btn-danger btn-sm' onclick=\"delAdmin('").append(escapeJsAttr(a.username)).append("')\">删除</button>");
            }
            adminRows.append("</td></tr>");
        }

        String adminSection = "";
        String adminNav = "";
        String consoleSection = "";
        String broadcastSection = "";
        String transactionsSection;
        String ratingsSection = "";
        String escrowSection = "";
        if ("superadmin".equals(session.role)) {
            adminNav = "<a href='#admins'><svg viewBox='0 0 24 24'><path d='M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/></svg>管理员账号</a>" +
                "<a href='#console'><svg viewBox='0 0 24 24'><path d='M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM8.9 14.83l-1.41 1.41L4 12.83l3.49-3.41 1.41 1.41L6.83 12l2.07 2.83zM15 16H9v-2h6v2z'/></svg>控制台</a>" +
                "<a href='#broadcast'><svg viewBox='0 0 24 24'><path d='M20 4l-8 3v10l8 3V4zM10 7H4c-1.1 0-2 .9-2 2v6c0 1.1.9 2 2 2h2v3h2v-3h2l2 1V6l-2 1z'/></svg>公告系统</a>";
            adminSection = "<div id='admins' class='page-section'><div class='section'>" +
                "<div class='section-title'>管理员账号 <span class='badge'>" + admins.size() + " 个</span></div>" +
                "<div style='margin-bottom:12px;'><button class='btn btn-primary btn-sm' onclick='openAddAdmin()'>添加管理员</button></div>" +
                "<table><thead><tr><th>用户名</th><th>角色</th><th>操作</th></tr></thead><tbody>" + adminRows + "</tbody></table>" +
                "</div></div>";
            broadcastSection = """
                <div id='broadcast' class='page-section'>
                <div class='section'>
                <div class='section-title'>公告系统</div>
                <div class='form-group'><label for='bcType'>公告类型</label>
                <select id='bcType' onchange='toggleBcPlayer()'>
                <option value='chat'>聊天消息（显示在聊天栏）</option>
                <option value='title'>标题（屏幕中央大字）</option>
                <option value='actionbar'>快捷栏消息（物品栏上方）</option>
                </select></div>
                <div class='form-group'><label for='bcTarget'>发送目标</label>
                <select id='bcTarget' onchange='toggleBcPlayer()'>
                <option value='all'>全服玩家</option>
                <option value='player'>指定玩家</option>
                </select></div>
                <div class='form-group' id='bcPlayerGroup' style='display:none;'><label for='bcPlayer'>玩家名</label>
                <input type='text' id='bcPlayer' placeholder='输入在线玩家名'></div>
                <div class='form-group'><label for='bcMessage'>公告内容（支持 &a &b &c 等颜色代码）</label>
                <textarea id='bcMessage' rows='6' placeholder='输入公告内容...' style='width:100%;padding:12px 16px;border:1px solid #d1d5db;border-radius:8px;font-size:16px;resize:vertical;'></textarea></div>
                <button class='btn btn-primary' onclick='sendBroadcast()'>发送公告</button>
                </div></div>
                """;
            consoleSection = """
                <div id='console' class='page-section'>
                <div class='section'>
                <div class='section-title'>服务器控制台
                <div>
                <button class='btn btn-secondary btn-sm' onclick='clearConsole()'>清空</button>
                <button class='btn btn-secondary btn-sm' onclick='refreshConsole()'>刷新</button>
                </div></div>
                <div id='consoleOutput' style='background:#1e1e2e;color:#cdd6f4;font-family:Consolas,"Courier New",monospace;font-size:12px;padding:12px;border-radius:8px;height:400px;overflow-y:auto;white-space:pre-wrap;line-height:1.5;border:1px solid #313244;'></div>
                <div style='margin-top:12px;display:flex;gap:8px;'>
                <label for='consoleCmd' style='display:none;'>控制台命令</label>
                <input type='text' id='consoleCmd' aria-label='输入控制台命令' placeholder='输入命令，回车执行...' style='flex:1;padding:10px 14px;border:1px solid #d1d5db;border-radius:8px;font-size:14px;font-family:Consolas,"Courier New",monospace;' onkeydown='if(event.key==="Enter")execConsole()'>
                <button class='btn btn-primary' onclick='execConsole()'>执行</button>
                </div>
                </div></div>
                """;
        }

        transactionsSection = """
                <div id='transactions' class='page-section'>
                <div class='section'>
                <div class='section-title'>经济流水
                <div>
                <label for='txFilter' style='display:none;'>流水类型</label>
                <select id='txFilter' aria-label='流水类型筛选' onchange='renderTxPage()' style='padding:6px 12px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;'>
                <option value='all'>全部类型</option>
                <option value='CHECKIN_PAY'>付款</option>
                <option value='CHECKIN_RECV'>收款</option>
                <option value='EXTEND_PAY'>续费</option>
                </select>
                <label for='txSearch' style='display:none;'>流水搜索</label>
                <input type='text' id='txSearch' aria-label='搜索流水的玩家或房间' placeholder='搜索玩家/房间...' onkeyup='filterTx()' style='margin-left:6px;padding:6px 12px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;width:200px;'>
                <button class='btn btn-secondary btn-sm' onclick='loadTransactions()' style='margin-left:6px;'>刷新</button>
                </div></div>
                <div id='txStats' style='display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-bottom:14px;'></div>
                <table><thead><tr><th>时间</th><th>类型</th><th>玩家</th><th>房间</th><th style='text-align:right;'>金额</th><th>备注</th></tr></thead>
                <tbody id='txTbody'><tr><td colspan='6' style='text-align:center;color:#999;padding:30px;'>加载中...</td></tr></tbody></table>
                <div id='txPager' style='margin-top:12px;display:flex;align-items:center;gap:8px;justify-content:flex-end;flex-wrap:wrap;'></div>
                </div></div>
                """;

        ratingsSection = """
                <div id='ratings' class='page-section'>
                <div class='section'>
                <div class='section-title'>评分评价
                <div>
                <button class='btn btn-secondary btn-sm' onclick='loadRatings()'>刷新</button>
                </div></div>
                <div id='rtStats' style='display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-bottom:14px;'></div>
                <div class='sub-title'>房间评分排行</div>
                <table><thead><tr><th>房间</th><th>房主</th><th style='text-align:center;'>平均分</th><th style='text-align:center;'>评分数</th></tr></thead>
                <tbody id='rtRoomStats'><tr><td colspan='4' style='text-align:center;color:#999;padding:30px;'>加载中...</td></tr></tbody></table>
                <div class='sub-title'>全部评价</div>
                <table><thead><tr><th>时间</th><th>房间</th><th>评分人</th><th style='text-align:center;'>评分</th><th>评语</th></tr></thead>
                <tbody id='rtTbody'><tr><td colspan='5' style='text-align:center;color:#999;padding:30px;'>加载中...</td></tr></tbody></table>
                </div></div>
                """;

        escrowSection = """
                <div id='escrow' class='page-section'>
                <div class='section'>
                <div class='section-title'>收益提现
                <div>
                <button class='btn btn-secondary btn-sm' onclick='loadEscrow()'>刷新</button>
                </div></div>
                <div id='escModeBar' class='alert-bar'></div>
                <div id='escStats' style='display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-bottom:14px;'></div>
                <div class='sub-title'>房主收益</div>
                <table><thead><tr><th>房主</th><th style='text-align:right;'>累计收益</th><th style='text-align:right;'>已提现</th><th style='text-align:right;'>待提现</th><th style='text-align:center;'>操作</th></tr></thead>
                <tbody id='escTbody'><tr><td colspan='5' style='text-align:center;color:#999;padding:30px;'>加载中...</td></tr></tbody></table>
                <div class='sub-title'>提现记录</div>
                <table><thead><tr><th>时间</th><th>玩家</th><th style='text-align:right;'>金额</th></tr></thead>
                <tbody id='escWTbody'><tr><td colspan='3' style='text-align:center;color:#999;padding:30px;'>加载中...</td></tr></tbody></table>
                </div></div>
                """;

        String roleTag = switch (session.role) {
            case "superadmin" -> "<span class='badge'>超级管理员</span>";
            case "admin" -> "<span class='badge' style='background:#667eea'>管理员</span>";
            default -> "<span class='badge' style='background:#6b7280'>普通用户</span>";
        };
        double onlinePct = (online * 100.0) / Math.max(maxP, 1);
        String tpsColor = tps >= 19.0 ? "green" : "orange";
        String memColor = memPct >= 80 ? "orange" : (memPct >= 60 ? "orange" : "green");
        String mcVer = Bukkit.getBukkitVersion();
        String srvVer = Bukkit.getVersion();
        int pluginCount = Bukkit.getPluginManager().getPlugins().length;

        return """
                <!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>HotelsX Web 面板</title>
                <meta http-equiv="refresh" content="60">
                %s
                </head><body>
                <div class="sidebar">
                <div class="logo">HotelsX v1.4.0</div>
                <div class="menu">
                <div class="st">监控</div>
                <a href="#overview"><svg viewBox="0 0 24 24"><path d="M4 13h6c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v8c0 .55.45 1 1 1zm10 8h6c.55 0 1-.45 1-1v-4c0-.55-.45-1-1-1h-6c-.55 0-1 .45-1 1v4c0 .55.45 1 1 1zM4 21h6c.55 0 1-.45 1-1v-4c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v4c0 .55.45 1 1 1zm10-8h6c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1h-6c-.55 0-1 .45-1 1v8c0 .55.45 1 1 1z"/></svg>概览</a>
                <a href="#charts"><svg viewBox="0 0 24 24"><path d="M3 21h18v-2H3v2zM6 17h3V9H6v8zm5 0h3V5h-3v12zm5 0h3V12h-3v5z"/></svg>统计图表</a>
                <a href="#report"><svg viewBox="0 0 24 24"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-6 14H7v-2h6v2zm3-4H7v-2h9v2zm0-4H7V7h9v2z"/></svg>运营报表</a>
                <a href="#server"><svg viewBox="0 0 24 24"><path d="M20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM8 20H4v-4h4v4zm0-6H4v-4h4v4zm0-6H4V4h4v4zm6 12h-4v-4h4v4zm0-6h-4v-4h4v4zm0-6h-4V4h4v4zm6 12h-4v-4h4v4zm0-6h-4v-4h4v4zm0-6h-4V4h4v4z"/></svg>服务器状态</a>
                <div class="st">管理</div>
                <a href="#rooms"><svg viewBox="0 0 24 24"><path d="M7 13c1.66 0 3-1.34 3-3S8.66 7 7 7s-3 1.34-3 3 1.34 3 3 3zm12-6h-8v7H3V5H1v15h2v-3h18v3h2V9c0-2.21-1.79-4-4-4z"/></svg>房间列表</a>
                <a href="#collections"><svg viewBox="0 0 24 24"><path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg>合集列表</a>
                <a href="#transactions"><svg viewBox="0 0 24 24"><path d="M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.78 0 2.44.85 2.5 2.1h2.21c-.07-1.72-1.12-3.3-3.21-3.81V3h-3v2.16c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-2.06 0-2.87-.92-2.98-2.1h-2.2c.12 2.19 1.76 3.42 3.68 3.83V21h3v-2.15c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4z"/></svg>经济流水</a>
                <a href="#ratings"><svg viewBox="0 0 24 24"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>评分评价</a>
                <a href="#escrow"><svg viewBox="0 0 24 24"><path d="M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z"/></svg>收益提现</a>
                %s
                <div class="st">账户</div>
                <a href="#profile"><svg viewBox="0 0 24 24"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg>修改密码</a>
                </div>
                <div class="user">
                <div class="name">%s</div>
                %s
                <a href="/api/logout" style="color:#f87171;text-decoration:none;font-size:12px;display:flex;align-items:center;gap:6px;margin-top:8px;"><svg viewBox="0 0 24 24" style="width:14px;height:14px;fill:#f87171;"><path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z"/></svg>退出登录</a>
                </div></div>
                <div class="sidebar-overlay" id="sidebarOverlay"></div>
                <div class="main">
                <div class="topbar"><button class="mobile-toggle" onclick="toggleSidebar()">&#9776;</button><h2>HotelsX 管理面板</h2>
                <div class="actions"><span class="refresh-note" id="refreshNote">服务器状态实时推送 · 页面 60 秒刷新</span>
                <button class="btn btn-secondary btn-sm" id="themeBtn" onclick="toggleTheme()">夜间模式</button>
                <button class="btn btn-secondary btn-sm" onclick="location.reload()">刷新</button></div></div>

                <div id="overview" class="page-section">
                <div class="stats">
                <div class="card cyan"><div class="icon"><svg viewBox="0 0 24 24"><path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/></svg></div><div class="label">在线玩家</div>
                <div class="value" id="statOnline">%d / %d</div><div class="sub">当前在线人数</div>
                <div class="progress-bar"><div class="progress-fill" id="statOnlineBar" style="width:%.0f%%"></div></div></div>
                <div class="card blue"><div class="icon"><svg viewBox="0 0 24 24"><path d="M20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM8 20H4v-4h4v4zm0-6H4v-4h4v4zm0-6H4V4h4v4zm6 12h-4v-4h4v4zm0-6h-4v-4h4v4zm0-6h-4V4h4v4zm6 12h-4v-4h4v4zm0-6h-4v-4h4v4zm0-6h-4V4h4v4z"/></svg></div><div class="label">服务器 TPS</div>
                <div class="value" id="statTps">%.2f</div><div class="sub">Tick Per Second</div>
                <div class="progress-bar"><div class="progress-fill %s" id="statTpsBar" style="width:%.0f%%"></div></div></div>
                <div class="card purple"><div class="icon"><svg viewBox="0 0 24 24"><path d="M9 3v4c0 .55-.45 1-1 1H4v10h16V8h-4c-.55 0-1-.45-1-1V3H9zm-1-2h8v6h6v14H2V7h6V1z"/></svg></div><div class="label">内存使用</div>
                <div class="value" id="statMem">%d / %d MB</div><div class="sub">使用率 %d%%</div>
                <div class="progress-bar"><div class="progress-fill %s" id="statMemBar" style="width:%d%%"></div></div></div>
                <div class="card green"><div class="icon"><svg viewBox="0 0 24 24"><path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67V7z"/></svg></div><div class="label">运行时间</div>
                <div class="value" id="statUptime" style="font-size:18px;">%s</div><div class="sub">自启动以来</div></div>
                </div>

                <div class="stats">
                <div class="card green"><div class="icon"><svg viewBox="0 0 24 24"><path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/></svg></div><div class="label">可入住</div><div class="value">%d</div></div>
                <div class="card orange"><div class="icon"><svg viewBox="0 0 24 24"><path d="M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"/></svg></div><div class="label">已入住</div><div class="value">%d</div></div>
                <div class="card gray"><div class="icon"><svg viewBox="0 0 24 24"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg></div><div class="label">已锁定</div><div class="value">%d</div></div>
                <div class="card purple"><div class="icon"><svg viewBox="0 0 24 24"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/></svg></div><div class="label">维护中</div><div class="value">%d</div></div>
                <div class="card blue"><div class="icon"><svg viewBox="0 0 24 24"><path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg></div><div class="label">合集数量</div><div class="value">%d</div></div>
                </div>

                <div id="charts" class="page-section">
                <div class="section">
                <div class="section-title">房间统计图表</div>
                <div id="chartsContent" style="text-align:center;color:#999;padding:40px;">加载中...</div>
                </div></div>

                <div id="report" class="page-section">
                <div class="section">
                <div class="section-title">运营报表</div>
                <div id="reportContent" style="text-align:center;color:#999;padding:40px;">加载中...</div>
                </div></div>

                <div id="server" class="page-section">
                <div class="section"><div class="section-title">服务器信息</div>
                <table>
                <tr><th>服务器版本</th><td>%s</td></tr>
                <tr><th>Minecraft 版本</th><td>%s</td></tr>
                <tr><th>服务器 TPS</th><td>%.2f</td></tr>
                <tr><th>在线玩家</th><td>%d / %d</td></tr>
                <tr><th>内存使用</th><td>%d MB / %d MB (%d%%)</td></tr>
                <tr><th>运行时间</th><td>%s</td></tr>
                <tr><th>已加载插件数</th><td>%d</td></tr>
                </table></div></div>

                <div id="rooms" class="page-section">
                <div class="section">
                <div class="section-title">房间列表 <span class="badge">%d 个房间</span>
                <label for="roomSearch" style="display:none;">房间搜索</label>
                <input type="text" id="roomSearch" aria-label="搜索房间名ID房主" placeholder="搜索房间名/ID/房主..." onkeyup="filterRooms()" style="padding:6px 12px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;width:100%%;max-width:240px;">
                </div>
                <div id="batchToolbar" style="display:none;margin-bottom:12px;padding:10px 14px;background:#f0f4ff;border-radius:8px;gap:8px;flex-wrap:wrap;align-items:center;">
                <span style="font-size:13px;color:#333;font-weight:600;" id="batchSelectedText">已选 0 个</span>
                <button class="btn btn-secondary btn-sm" onclick="batchAction('lock')">批量上锁</button>
                <button class="btn btn-secondary btn-sm" onclick="batchAction('unlock')">批量解锁</button>
                <button class="btn btn-secondary btn-sm" onclick="batchSetPrice()">批量改价</button>
                <button class="btn btn-secondary btn-sm" onclick="batchSetStatus()">批量改状态</button>
                <button class="btn btn-danger btn-sm" onclick="batchAction('delete')">批量删除</button>
                </div>
                <table><thead><tr><th style="width:36px;"><input type="checkbox" id="selectAll" aria-label="全选所有房间" onchange="toggleSelectAll(this)"></th><th>ID</th><th>名称</th><th>房主</th><th>状态</th><th>价格</th><th>当前住客</th><th>上锁</th><th>操作</th></tr></thead>
                <tbody id="roomTbody">%s</tbody></table>
                <div id="roomPager" style="margin-top:12px;display:flex;align-items:center;gap:8px;justify-content:flex-end;flex-wrap:wrap;"></div>
                </div></div>

                <div id="collections" class="page-section">
                <div class="section"><div class="section-title">合集列表 <span class="badge">%d 个合集</span></div>
                <table><thead><tr><th>ID</th><th>名称</th><th>创建者</th><th>房间数</th></tr></thead>
                <tbody>%s</tbody></table></div></div>

                %s
                %s
                %s
                %s
                %s
                %s

                <div id="profile" class="page-section">
                <div class="section"><div class="section-title">账户设置</div>
                <p style="margin-bottom:14px;color:#6b7280;">修改您的登录密码</p>
                <div class="inline-form">
                <div><label for="oldPwd">原密码</label>
                <input type="password" id="oldPwd" placeholder="原密码"></div>
                <div><label for="newPwd">新密码</label>
                <input type="password" id="newPwd" placeholder="新密码"></div>
                <div><label for="confirmPwd">确认新密码</label>
                <input type="password" id="confirmPwd" placeholder="确认新密码"></div>
                <button class="btn btn-primary" onclick="changeMyPwd()">修改密码</button>
                </div></div></div>

                </div>

                <div id="editModal" class="modal-overlay"><div class="modal">
                <h3>编辑房间</h3>
                <input type="hidden" id="eRid">
                <div class="form-group"><label for="eName">房间名称</label><input type="text" id="eName"></div>
                <div class="form-group"><label for="ePrice">价格</label><input type="number" step="0.01" id="ePrice"></div>
                <div class="form-group"><label for="eStatus">状态</label>
                <select id="eStatus"><option value="AVAILABLE">可入住</option><option value="OCCUPIED">已入住</option><option value="MAINTENANCE">维护中</option></select></div>
                <div class="form-group"><label for="eLocked">上锁</label>
                <select id="eLocked"><option value="false">否</option><option value="true">是</option></select></div>
                <div class="modal-actions">
                <button class="btn btn-secondary" onclick="closeM('editModal')">取消</button>
                <button class="btn btn-primary" onclick="saveRoom()">保存</button></div></div></div>

                <div id="addAdminModal" class="modal-overlay"><div class="modal">
                <h3>添加管理员</h3>
                <div class="form-group"><label for="nAU">用户名</label><input type="text" id="nAU"></div>
                <div class="form-group"><label for="nAP">密码</label><input type="password" id="nAP"></div>
                <div class="form-group"><label for="nAR">角色</label>
                <select id="nAR">
                <option value="user">普通用户（只能管理自己的房间）</option>
                <option value="admin" selected>管理员（可修改所有房间，不能删除）</option>
                <option value="superadmin">超级管理员（完全权限）</option>
                </select></div>
                <div class="form-group"><label for="nAMC">关联游戏ID（可选）</label><input type="text" id="nAMC" placeholder="填写游戏内玩家名"></div>
                <div class="modal-actions">
                <button class="btn btn-secondary" onclick="closeM('addAdminModal')">取消</button>
                <button class="btn btn-primary" onclick="addAdmin()">添加</button></div></div></div>

                <div id="toast" class="toast"></div>

                <script>
                function applyTheme(dark){document.body.classList.toggle('dark',dark);document.getElementById('themeBtn').textContent=dark?'白天模式':'夜间模式';try{localStorage.setItem('hotelsx_theme',dark?'dark':'light');}catch(e){}}
                function toggleTheme(){applyTheme(!document.body.classList.contains('dark'));}
                try{if(localStorage.getItem('hotelsx_theme')==='dark')applyTheme(true);}catch(e){}
                function toast(msg,t){const el=document.getElementById('toast');el.textContent=msg;el.className='toast toast-'+(t||'info')+' show';setTimeout(()=>el.classList.remove('show'),3000);}
                function closeM(id){document.getElementById(id).classList.remove('show');}
                function toggleSidebar(){const s=document.querySelector('.sidebar');const o=document.getElementById('sidebarOverlay');s.classList.toggle('open');o.classList.toggle('show');}
                document.getElementById('sidebarOverlay').addEventListener('click',toggleSidebar);
                function openEdit(id,name,price,status,locked){
                document.getElementById('eRid').value=id;document.getElementById('eName').value=name;
                document.getElementById('ePrice').value=price;document.getElementById('eStatus').value=status;
                document.getElementById('eLocked').value=String(locked);
                document.getElementById('editModal').classList.add('show');}
                async function saveRoom(){
                const d={roomId:document.getElementById('eRid').value,name:document.getElementById('eName').value,
                price:document.getElementById('ePrice').value,status:document.getElementById('eStatus').value,
                locked:document.getElementById('eLocked').value};
                try{const r=await fetch('/api/room/update',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(d)});
                const j=await r.json();if(j.success){toast('房间已更新','success');closeM('editModal');}else toast(j.error||'更新失败','error');}
                catch(err){toast('网络错误','error');}}
                async function delRoom(id){if(!confirm('确定删除此房间？'))return;
                try{const r=await fetch('/api/room/delete',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'roomId='+encodeURIComponent(id)});
                const j=await r.json();if(j.success){toast('房间已删除','success');setTimeout(()=>location.reload(),500);}else toast(j.error||'删除失败','error');}
                catch(err){toast('网络错误','error');}}
                function openAddAdmin(){document.getElementById('nAU').value='';document.getElementById('nAP').value='';document.getElementById('nAMC').value='';document.getElementById('addAdminModal').classList.add('show');}
                async function addAdmin(){
                const d={username:document.getElementById('nAU').value,password:document.getElementById('nAP').value,role:document.getElementById('nAR').value,'minecraft-name':document.getElementById('nAMC').value};
                if(!d.username||!d.password){toast('用户名和密码不能为空','error');return;}
                try{const r=await fetch('/api/admin/add',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(d)});
                const j=await r.json();if(j.success){toast('管理员已添加','success');closeM('addAdminModal');setTimeout(()=>location.reload(),500);}else toast(j.error||'添加失败','error');}
                catch(err){toast('网络错误','error');}}
                async function delAdmin(u){if(!confirm('确定删除管理员 '+u+'？'))return;
                try{const r=await fetch('/api/admin/delete',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'username='+encodeURIComponent(u)});
                const j=await r.json();if(j.success){toast('管理员已删除','success');setTimeout(()=>location.reload(),500);}else toast(j.error||'删除失败','error');}
                catch(err){toast('网络错误','error');}}
                async function changeMyPwd(){
                const o=document.getElementById('oldPwd').value,n=document.getElementById('newPwd').value,c=document.getElementById('confirmPwd').value;
                if(!o||!n){toast('请填写完整信息','error');return;}
                if(n!==c){toast('两次输入的新密码不一致','error');return;}
                try{const r=await fetch('/api/admin/change-password',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({oldPassword:o,newPassword:n})});
                const j=await r.json();if(j.success){toast('密码已修改','success');document.getElementById('oldPwd').value='';document.getElementById('newPwd').value='';document.getElementById('confirmPwd').value='';}else toast(j.error||'修改失败','error');}
                catch(err){toast('网络错误','error');}}
                function filterRooms(){roomPage=1;renderRoomPage();}
                let roomPage=1;
                const ROOM_PAGE_SIZE=10;
                function getVisibleRoomRows(){
                const q=document.getElementById('roomSearch').value.toLowerCase().trim();
                return Array.from(document.querySelectorAll('#roomTbody tr')).filter(r=>{
                if(!q)return true;
                const tds=r.querySelectorAll('td');
                for(const td of tds){if(td.textContent.toLowerCase().includes(q))return true;}
                return false;});
                }
                function renderRoomPage(){
                const rows=getVisibleRoomRows();
                const totalPages=Math.max(1,Math.ceil(rows.length/ROOM_PAGE_SIZE));
                if(roomPage>totalPages)roomPage=totalPages;
                rows.forEach((r,i)=>{
                r.style.display=(i>=(roomPage-1)*ROOM_PAGE_SIZE&&i<roomPage*ROOM_PAGE_SIZE)?'':'none';});
                const pager=document.getElementById('roomPager');
                if(!pager)return;
                if(rows.length===1&&rows[0].classList.contains('empty')){pager.innerHTML='';return;}
                pager.innerHTML='<span style="font-size:13px;color:#6b7280;">共 '+rows.length+' 间</span>'
                +'<button class="btn btn-secondary btn-sm" onclick="gotoRoomPage(1)">首页</button>'
                +'<button class="btn btn-secondary btn-sm" onclick="gotoRoomPage('+(roomPage-1)+')">上一页</button>'
                +'<span style="font-size:13px;color:#374151;font-weight:600;min-width:50px;text-align:center;">'+roomPage+' / '+totalPages+'</span>'
                +'<button class="btn btn-secondary btn-sm" onclick="gotoRoomPage('+(roomPage+1)+')">下一页</button>'
                +'<button class="btn btn-secondary btn-sm" onclick="gotoRoomPage('+totalPages+')">末页</button>';
                }
                function gotoRoomPage(p){
                const rows=getVisibleRoomRows();
                const totalPages=Math.max(1,Math.ceil(rows.length/ROOM_PAGE_SIZE));
                if(p<1)p=1;if(p>totalPages)p=totalPages;
                roomPage=p;renderRoomPage();
                }
                function applyStats(j){
                if(!j||j.onlinePlayers===undefined)return;
                document.getElementById('statOnline').textContent=j.onlinePlayers+' / '+j.maxPlayers;
                document.getElementById('statOnlineBar').style.width=((j.onlinePlayers/Math.max(j.maxPlayers,1))*100)+'%%';
                const tpsEl=document.getElementById('statTps');tpsEl.textContent=j.tps.toFixed(2);
                const tpsBar=document.getElementById('statTpsBar');
                tpsBar.style.width=Math.min(j.tps*100,100)+'%%';
                tpsBar.className='progress-fill '+(j.tps>=19?'green':(j.tps>=15?'orange':''));
                const memUsed=j.usedMemoryMB||0,memMax=j.maxMemoryMB||1;
                const memPct=Math.round((memUsed/memMax)*100);
                document.getElementById('statMem').textContent=memUsed+' / '+memMax+' MB';
                document.getElementById('statMemBar').style.width=memPct+'%%';
                document.getElementById('statMemBar').className='progress-fill '+(memPct>=80?'orange':(memPct>=60?'':'green'));
                if(j.uptimeSeconds!=null){const s=j.uptimeSeconds;const d=Math.floor(s/86400),h=Math.floor((s%%86400)/3600),m=Math.floor((s%%3600)/60),sec=s%%60;document.getElementById('statUptime').textContent=(d>0?d+'天 ':'')+h+'时 '+m+'分 '+sec+'秒';}}
                async function refreshStats(){
                try{const r=await fetch('/api/server');const j=await r.json();applyStats(j);}catch(e){}}
                if(window.EventSource){
                const evtSource=new EventSource('/api/events');
                evtSource.onmessage=function(e){try{applyStats(JSON.parse(e.data));}catch(err){}};
                }else{setInterval(refreshStats,1000);}
                function updateConsole(text){
                const el=document.getElementById('consoleOutput');if(!el)return;
                const atBottom=el.scrollTop+el.clientHeight>=el.scrollHeight-30;
                const oldH=el.scrollHeight;
                el.textContent=text;
                if(atBottom){el.scrollTop=el.scrollHeight;}
                else{el.scrollTop+=(el.scrollHeight-oldH);}
                }
                async function refreshConsole(){
                try{const r=await fetch('/api/console/logs');
                if(!r.ok){updateConsole('[错误] 无法获取日志 ('+r.status+')');return;}
                const j=await r.json();
                if(j.lines&&j.lines.length>0){updateConsole(j.lines.join('\\n'));}
                else{updateConsole('[等待日志输出...]');}}catch(e){updateConsole('[连接错误: '+e.message+']');}}
                function clearConsole(){const el=document.getElementById('consoleOutput');if(el)el.textContent='';}
                async function execConsole(){
                const cmd=document.getElementById('consoleCmd').value.trim();if(!cmd)return;
                try{const r=await fetch('/api/console/execute',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'command='+encodeURIComponent(cmd)});
                const j=await r.json();if(j.success){toast(j.message,'success');document.getElementById('consoleCmd').value='';setTimeout(refreshConsole,300);}else toast(j.error||'执行失败','error');}
                catch(err){toast('网络错误','error');}}
                if(document.getElementById('console')){
                refreshConsole();
                if(window.EventSource){
                let ce=null,lastMsg=Date.now();
                function connectConsoleSSE(){try{ce=new EventSource('/api/events/console');}catch(err){ce=null;return;}
                ce.onmessage=function(e){lastMsg=Date.now();if(e.data)updateConsole(e.data);};
                ce.onerror=function(){try{ce.close();}catch(_){}};}
                connectConsoleSSE();
                setInterval(function(){
                // 每3秒轮询一次兜底；SSE 8秒无消息则重建连接
                if(Date.now()-lastMsg>8000){try{if(ce)ce.close();}catch(_){}connectConsoleSSE();lastMsg=Date.now();}
                refreshConsole();
                },3000);
                }else{setInterval(refreshConsole,3000);}}
                // 统计图表
                async function loadCharts(){
                try{const r=await fetch('/api/stats/detail');const j=await r.json();
                const el=document.getElementById('chartsContent');if(!el)return;
                let html='<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:20px;">';
                // 入住率
                html+='<div class="chart-card"><h4>入住率</h4>';
                html+='<div style="font-size:32px;font-weight:700;color:#667eea;">'+j.occupancyRate+'%%</div>';
                html+='<div class="muted" style="font-size:12px;margin-top:4px;">'+j.occupied+'/'+j.totalRooms+' 间已入住</div></div>';
                // 收入统计
                html+='<div class="chart-card"><h4>收入统计</h4>';
                html+='<div style="font-size:24px;font-weight:700;color:#10b981;">'+j.occupiedRevenue+'</div>';
                html+='<div class="muted" style="font-size:12px;margin-top:4px;">已入住房间总收入</div>';
                html+='<div class="muted" style="font-size:14px;margin-top:8px;">平均价格: '+j.avgPrice+'</div></div>';
                // 房间状态分布
                html+='<div class="chart-card"><h4>房间状态分布</h4>';
                const maxStatus=Math.max(j.available,j.occupied,j.maintenance,j.locked,1);
                html+=barChart('可入住',j.available,maxStatus,'#10b981');
                html+=barChart('已入住',j.occupied,maxStatus,'#f59e0b');
                html+=barChart('维护中',j.maintenance,maxStatus,'#8b5cf6');
                html+=barChart('已锁定',j.locked,maxStatus,'#6b7280');
                html+='</div>';
                // 价格分布
                html+='<div class="chart-card"><h4>价格分布</h4>';
                if(j.priceDist){const maxP=Math.max(...j.priceDist.map(p=>p.count),1);
                j.priceDist.forEach(p=>{html+=barChart(p.range,p.count,maxP,'#667eea');});}
                html+='</div>';
                // 最近 30 天入住趋势
                html+='<div class="chart-card"><h4>近 30 天入住趋势</h4>';
                if(j.dailyCheckins&&j.dailyCheckins.length>0){const maxC=Math.max(...j.dailyCheckins.map(d=>d.count),1);
                html+='<div style="display:flex;align-items:flex-end;gap:3px;height:110px;padding-top:6px;">';
                j.dailyCheckins.forEach(d=>{const h=Math.max(Math.round(d.count/maxC*100),2);
                html+='<div style="flex:1;min-width:6px;text-align:center;"><div style="height:'+h+'px;background:linear-gradient(180deg,#3b82f6,#8b5cf6);border-radius:3px 3px 0 0;" title="'+d.date+': '+d.count+' 次"></div></div>';});
                html+='</div>';
                const totalCheckins=j.dailyCheckins.reduce((a,d)=>a+d.count,0);
                html+='<div class="muted" style="font-size:12px;margin-top:6px;">30 天共入住 '+totalCheckins+' 次，日均 '+(totalCheckins/30).toFixed(1)+' 次</div>';}
                else{html+='<div class="muted" style="font-size:13px;">暂无数据</div>';}
                html+='</div>';
                // Top 5 房主
                html+='<div class="chart-card"><h4>Top 5 房主</h4>';
                if(j.topOwners&&j.topOwners.length>0){const maxO=Math.max(...j.topOwners.map(o=>o.count),1);
                j.topOwners.forEach(o=>{html+=barChart(o.name,o.count,maxO,'#ec4899');});}
                else{html+='<div class="muted" style="font-size:13px;">暂无数据</div>';}
                html+='</div>';
                html+='</div>';
                el.innerHTML=html;}catch(e){const el=document.getElementById('chartsContent');if(el)el.textContent='加载失败';}}
                function barChart(label,value,max,color){
                const pct=Math.round(value/max*100);
                return '<div style="margin-bottom:8px;"><div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:2px;"><span>'+label+'</span><span style="font-weight:600;">'+value+'</span></div><div class="chart-bar"><div style="width:'+pct+'%%;height:100%%;background:'+color+';border-radius:4px;transition:width 0.5s;"></div></div></div>';}
                // 批量操作
                function toggleSelectAll(cb){
                document.querySelectorAll('.room-checkbox').forEach(c=>c.checked=cb.checked);
                updateBatchToolbar();}
                function updateBatchToolbar(){
                const cbs=document.querySelectorAll('.room-checkbox:checked');
                const toolbar=document.getElementById('batchToolbar');
                if(cbs.length>0){toolbar.style.display='flex';
                document.getElementById('batchSelectedText').textContent='已选 '+cbs.length+' 个';}
                else{toolbar.style.display='none';}
                document.getElementById('selectAll').checked=cbs.length>0&&cbs.length===document.querySelectorAll('.room-checkbox').length;}
                function getSelectedRoomIds(){return Array.from(document.querySelectorAll('.room-checkbox:checked')).map(c=>c.value);}
                async function batchAction(action){
                const ids=getSelectedRoomIds();if(ids.length===0)return;
                if(action==='delete'&&!confirm('确定批量删除 '+ids.length+' 个房间？此操作不可撤销！'))return;
                if(action!=='delete'&&!confirm('确定对 '+ids.length+' 个房间执行此操作？'))return;
                try{const body='action='+action+'&roomIds='+encodeURIComponent(ids.join(','));
                const r=await fetch('/api/room/batch',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body});
                const j=await r.json();toast(j.message||'操作完成',j.success?'success':'error');
                if(j.errors)console.warn(j.errors);
                setTimeout(()=>location.reload(),800);}catch(e){toast('网络错误','error');}}
                async function batchSetPrice(){
                const ids=getSelectedRoomIds();if(ids.length===0)return;
                const price=prompt('输入新的价格（对所有选中的 '+ids.length+' 个房间生效）:');if(price===null)return;
                try{const body='action=setPrice&roomIds='+encodeURIComponent(ids.join(','))+'&price='+encodeURIComponent(price);
                const r=await fetch('/api/room/batch',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body});
                const j=await r.json();toast(j.message||'操作完成',j.success?'success':'error');
                setTimeout(()=>location.reload(),800);}catch(e){toast('网络错误','error');}}
                async function batchSetStatus(){
                const ids=getSelectedRoomIds();if(ids.length===0)return;
                const status=prompt('输入状态 (AVAILABLE / OCCUPIED / MAINTENANCE):');if(status===null)return;
                try{const body='action=setStatus&roomIds='+encodeURIComponent(ids.join(','))+'&status='+encodeURIComponent(status);
                const r=await fetch('/api/room/batch',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body});
                const j=await r.json();toast(j.message||'操作完成',j.success?'success':'error');
                setTimeout(()=>location.reload(),800);}catch(e){toast('网络错误','error');}}
                function toggleBcPlayer(){
                    const target=document.getElementById('bcTarget').value;
                    document.getElementById('bcPlayerGroup').style.display = target==='player' ? 'block' : 'none';
                }
                async function sendBroadcast(){
                    const type=document.getElementById('bcType').value;
                    const target=document.getElementById('bcTarget').value;
                    const message=document.getElementById('bcMessage').value.trim();
                    const player=target==='player' ? document.getElementById('bcPlayer').value.trim() : '';
                    if(!message){toast('公告内容不能为空','error');return;}
                    if(target==='player'&&!player){toast('请输入玩家名','error');return;}
                    try{
                        const body='type='+encodeURIComponent(type)+'&target='+encodeURIComponent(target)+'&message='+encodeURIComponent(message)+'&player='+encodeURIComponent(player);
                        const r=await fetch('/api/broadcast',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body});
                        const j=await r.json();
                        if(j.success){toast(j.message,'success');document.getElementById('bcMessage').value='';}
                        else toast(j.error||'发送失败','error');
                    }catch(e){toast('网络错误','error');}
                }
                // 经济流水
                let allTx=[],txPage=1;
                const TX_PAGE_SIZE=20;
                async function loadTransactions(){
                    try{const r=await fetch('/api/transactions');if(!r.ok){showTxEmpty('加载失败 ('+r.status+')');return;}
                    const j=await r.json();allTx=j.list||[];
                    const statsEl=document.getElementById('txStats');if(statsEl){
                    let html='<div class="card"><div class="label">交易总笔数</div><div class="value">'+(j.total||0)+'</div></div>';
                    html+='<div class="card green"><div class="label">总收款</div><div class="value">'+(j.totalIn||0).toFixed(2)+'</div></div>';
                    html+='<div class="card red"><div class="label">总付款</div><div class="value">'+(j.totalOut||0).toFixed(2)+'</div></div>';
                    statsEl.innerHTML=html;}
                    txPage=1;renderTxPage();}catch(e){showTxEmpty('加载失败: '+e.message);}}
                function showTxEmpty(msg){
                    const t=document.getElementById('txTbody');if(t)t.innerHTML='<tr><td colspan="6" style="text-align:center;color:#999;padding:30px;">'+msg+'</td></tr>';
                    const p=document.getElementById('txPager');if(p)p.innerHTML='';}
                function getVisibleTx(){
                    const q=document.getElementById('txSearch')?.value.toLowerCase().trim()||'';
                    const f=document.getElementById('txFilter')?.value||'all';
                    return allTx.filter(t=>{
                    if(f!=='all'&&t.type!==f)return false;
                    if(!q)return true;
                    return (t.player||'').toLowerCase().includes(q)
                    ||(t.roomName||'').toLowerCase().includes(q)
                    ||(t.roomId||'').toLowerCase().includes(q)
                    ||(t.remark||'').toLowerCase().includes(q);});}
                function filterTx(){txPage=1;renderTxPage();}
                function renderTxPage(){
                    const rows=getVisibleTx();
                    const totalPages=Math.max(1,Math.ceil(rows.length/TX_PAGE_SIZE));
                    if(txPage>totalPages)txPage=totalPages;
                    const start=(txPage-1)*TX_PAGE_SIZE;const slice=rows.slice(start,start+TX_PAGE_SIZE);
                    const tbody=document.getElementById('txTbody');
                    if(slice.length===0){showTxEmpty('暂无数据');return;}
                    let html='';
                    for(const t of slice){
                    const typeMap={'CHECKIN_PAY':['付款','status-occupied'],'CHECKIN_RECV':['收款','status-available'],'EXTEND_PAY':['续费','status-maintenance']};
                    const tm=typeMap[t.type]||['付款','status-occupied'];
                    const typeTag="<span class='status-tag "+tm[1]+"'>"+tm[0]+"</span>";
                    const isRecv=t.type==='CHECKIN_RECV';
                    const amt=(isRecv?'+':'-')+Number(t.amount).toFixed(2);
                    const amtStyle=isRecv?'color:#10b981;font-weight:600;':'color:#ef4444;font-weight:600;';
                    html+='<tr><td>'+(t.timeStr||'')+'</td><td>'+typeTag+'</td><td>'+escapeHtml(t.player||'')+'</td><td>'+escapeHtml(t.roomName||t.roomId||'')+'</td><td style="text-align:right;'+amtStyle+'">'+amt+'</td><td>'+escapeHtml(t.remark||'')+'</td></tr>';}
                    tbody.innerHTML=html;
                    const pager=document.getElementById('txPager');if(!pager)return;
                    pager.innerHTML='<span style="font-size:13px;color:#6b7280;">共 '+rows.length+' 条</span>'
                    +'<button class="btn btn-secondary btn-sm" onclick="gotoTxPage(1)">首页</button>'
                    +'<button class="btn btn-secondary btn-sm" onclick="gotoTxPage('+(txPage-1)+')">上一页</button>'
                    +'<span style="font-size:13px;color:#374151;font-weight:600;min-width:50px;text-align:center;">'+txPage+' / '+totalPages+'</span>'
                    +'<button class="btn btn-secondary btn-sm" onclick="gotoTxPage('+(txPage+1)+')">下一页</button>'
                    +'<button class="btn btn-secondary btn-sm" onclick="gotoTxPage('+totalPages+')">末页</button>';}
                function gotoTxPage(p){
                    const rows=getVisibleTx();
                    const totalPages=Math.max(1,Math.ceil(rows.length/TX_PAGE_SIZE));
                    if(p<1)p=1;if(p>totalPages)p=totalPages;txPage=p;renderTxPage();}
                function escapeHtml(s){if(s==null)return'';return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
                function starText(v){const n=Math.max(0,Math.min(5,Math.round(Number(v))));return '★'.repeat(n)+'☆'.repeat(5-n);}
                async function loadRatings(){
                    try{const r=await fetch('/api/ratings');if(!r.ok)return;
                    const j=await r.json();
                    const statsEl=document.getElementById('rtStats');if(statsEl){
                    let html='<div class="card"><div class="label">总评分数</div><div class="value">'+(j.total||0)+'</div></div>';
                    html+='<div class="card orange"><div class="label">平均评分</div><div class="value">'+(j.avgAll||0)+'</div></div>';
                    html+='<div class="card purple"><div class="label">已评分房间</div><div class="value">'+((j.roomStats||[]).length)+'</div></div>';
                    statsEl.innerHTML=html;}
                    const rsEl=document.getElementById('rtRoomStats');
                    if(rsEl){
                    const stats=j.roomStats||[];
                    if(stats.length===0){rsEl.innerHTML='<tr><td colspan="4" style="text-align:center;color:#999;padding:30px;">暂无评分</td></tr>';}
                    else{
                    let html='';
                    stats.forEach(s=>{
                    html+='<tr><td>'+escapeHtml(s.roomName)+' <span style="color:#999;font-size:12px;">('+escapeHtml(s.roomId)+')</span></td><td>'+escapeHtml(s.owner)+'</td><td style="text-align:center;color:#f59e0b;font-weight:600;">'+Number(s.avg).toFixed(1)+' '+starText(s.avg)+'</td><td style="text-align:center;">'+s.count+'</td></tr>';});
                    rsEl.innerHTML=html;}}
                    const tb=document.getElementById('rtTbody');
                    if(tb){
                    const list=j.list||[];
                    if(list.length===0){tb.innerHTML='<tr><td colspan="5" style="text-align:center;color:#999;padding:30px;">暂无评价</td></tr>';return;}
                    let html='';
                    list.forEach(r=>{
                    html+='<tr><td>'+(r.timeStr||'')+'</td><td>'+escapeHtml(r.roomName||r.roomId||'')+'</td><td>'+escapeHtml(r.guest||'')+'</td><td style="text-align:center;color:#f59e0b;">'+starText(r.score)+'</td><td>'+escapeHtml(r.comment||'')+'</td></tr>';});
                    tb.innerHTML=html;}}
                    catch(e){const tb=document.getElementById('rtTbody');if(tb)tb.innerHTML='<tr><td colspan="5" style="text-align:center;color:#999;padding:30px;">加载失败: '+e.message+'</td></tr>';}}
                async function loadEscrow(){
                    try{const r=await fetch('/api/escrow');if(!r.ok)return;
                    const j=await r.json();
                    const modeBar=document.getElementById('escModeBar');
                    if(modeBar){
                    const isDark=document.body.classList.contains('dark');
                    if(j.escrowMode){modeBar.innerHTML='<span>当前为托管模式：房主收益进入待提现余额，可在此一键提现到钱包</span>';modeBar.style.background=isDark?'#422006':'#fffbeb';modeBar.style.border='1px solid '+(isDark?'#78350f':'#fde68a');modeBar.style.color=isDark?'#fbbf24':'#d97706';}
                    else{modeBar.innerHTML='<span>当前为实时到账模式：收益已直接发放到房主钱包。可在 config.yml 开启 economy.escrow-mode 启用托管提现</span>';modeBar.style.background=isDark?'#1e293b':'#f3f4f6';modeBar.style.border='1px solid '+(isDark?'#334155':'#e5e7eb');modeBar.style.color=isDark?'#94a3b8':'#6b7280';}}
                    const statsEl=document.getElementById('escStats');if(statsEl){
                    let html='<div class="card orange"><div class="label">待提现总额</div><div class="value">'+Number(j.totalPending||0).toFixed(2)+'</div></div>';
                    html+='<div class="card green"><div class="label">待提现账户</div><div class="value">'+(j.balances||[]).length+'</div></div>';
                    html+='<div class="card blue"><div class="label">提现记录</div><div class="value">'+(j.withdrawals||[]).length+'</div></div>';
                    statsEl.innerHTML=html;}
                    const tb=document.getElementById('escTbody');
                    if(tb){
                    const bal=j.balances||[];
                    if(bal.length===0){tb.innerHTML='<tr><td colspan="5" style="text-align:center;color:#999;padding:30px;">暂无待提现收益</td></tr>';}
                    else{
                    let html='';
                    bal.forEach(b=>{
                    let op='<span style="color:#999;">-</span>';
                    if(j.escrowMode&&Number(b.pending)>0){op='<button class="btn btn-primary btn-sm" onclick="withdrawEscrow(\''+b.uuid+'\',\''+escapeHtml(b.name)+'\')">提现</button>';}
                    html+='<tr><td>'+escapeHtml(b.name)+'</td><td style="text-align:right;color:#10b981;font-weight:600;">+'+Number(b.totalEarned).toFixed(2)+'</td><td style="text-align:right;">'+Number(b.totalWithdrawn).toFixed(2)+'</td><td style="text-align:right;color:#f59e0b;font-weight:600;">'+Number(b.pending).toFixed(2)+'</td><td style="text-align:center;">'+op+'</td></tr>';});
                    tb.innerHTML=html;}}
                    const wtb=document.getElementById('escWTbody');
                    if(wtb){
                    const list=j.withdrawals||[];
                    if(list.length===0){wtb.innerHTML='<tr><td colspan="3" style="text-align:center;color:#999;padding:30px;">暂无提现记录</td></tr>';return;}
                    let html='';
                    list.forEach(w=>{
                    html+='<tr><td>'+(w.timeStr||'')+'</td><td>'+escapeHtml(w.name)+'</td><td style="text-align:right;color:#10b981;font-weight:600;">+'+Number(w.amount).toFixed(2)+'</td></tr>';});
                    wtb.innerHTML=html;}}
                    catch(e){const tb=document.getElementById('escTbody');if(tb)tb.innerHTML='<tr><td colspan="5" style="text-align:center;color:#999;padding:30px;">加载失败: '+e.message+'</td></tr>';}}
                async function withdrawEscrow(uuid,name){
                    if(!confirm('确认将 '+name+' 的待提现收益发放到其钱包？'))return;
                    try{const body=new URLSearchParams({uuid:uuid,name:name});
                    const r=await fetch('/api/escrow/withdraw',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body.toString()});
                    const j=await r.json();
                    if(j.success){alert(j.message);loadEscrow();}
                    else{alert(j.message||'提现失败');}}
                    catch(e){alert('网络错误: '+e.message);}}
                async function loadReport(){
                try{const r=await fetch('/api/stats/report');const j=await r.json();
                const el=document.getElementById('reportContent');if(!el)return;
                let html='<div class="rp-grid">';
                // 数据对比卡片
                if(j.today){
                const pairs=[['今日','昨天','today','yesterday'],['本周','上周','thisWeek','lastWeek'],['本月','上月','thisMonth','lastMonth']];
                pairs.forEach(p=>{
                const cur=j[p[2]],prev=j[p[3]];
                const ck=cur.checkins,pk=prev.checkins,cr=cur.revenue,pr=prev.revenue;
                const ckPct=pk>0?Math.round((ck-pk)/pk*100):(ck>0?100:0);
                const crPct=pr>0?Math.round((cr-pr)/pr*100):(cr>0?100:0);
                const delta=function(v,pct){return '<span class="rp-delta '+(v>=0?'rp-delta-up':'rp-delta-down')+'">'+(v>=0?'▲':'▼')+' '+Math.abs(pct)+'%%</span>';};
                html+='<div class="rp-card"><h4>'+p[0]+'</h4>'
                +'<div class="rp-cmp-row"><div class="rp-cmp-label">入住次数</div><div class="rp-cmp-val">'+ck+delta(ck-pk,ckPct)+'</div></div>'
                +'<div class="rp-cmp-sub">'+p[1]+': '+pk+' 次</div>'
                +'<div class="rp-cmp-row"><div class="rp-cmp-label">收入</div><div class="rp-cmp-val">'+cr+delta(cr-pr,crPct)+'</div></div>'
                +'<div class="rp-cmp-sub">'+p[1]+': '+pr+'</div></div>';});}
                else{html+='<div class="rp-card"><h4>数据对比</h4><div style="color:#999;font-size:13px;padding:20px;text-align:center;">暂无数据</div></div>';}
                // 房主收益排行 Top 10
                html+='<div class="rp-card"><h4>房主收益排行 Top 10</h4>';
                if(j.topOwners&&j.topOwners.length>0){
                const maxR=Math.max(...j.topOwners.map(o=>o.revenue),1);
                html+='<div class="rp-rank-list">';
                j.topOwners.forEach((o,i)=>{
                const w=Math.max(Math.round(o.revenue/maxR*100),3);
                const badge=i===0?'rp-rank-badge gold':i===1?'rp-rank-badge silver':i===2?'rp-rank-badge bronze':'rp-rank-badge other';
                html+='<div class="rp-rank-item"><span class="'+badge+'">'+(i+1)+'</span><span class="rp-rank-name" title="'+escapeHtml(o.name)+'">'+escapeHtml(o.name)+'</span><div class="rp-rank-bar"><div class="rp-rank-bar-fill" style="width:'+w+'%%"></div></div><span class="rp-rank-count">'+o.count+'次</span><span class="rp-rank-rev">+'+o.revenue+'</span></div>';});
                html+='</div>';}
                else{html+='<div style="color:#999;font-size:13px;padding:20px;text-align:center;">暂无数据</div>';}
                html+='</div>';
                html+='</div>';
                el.innerHTML=html;}catch(e){const el=document.getElementById('reportContent');if(el)el.textContent='加载失败';}}
                renderRoomPage();
                loadCharts();
                loadReport();
                loadTransactions();
                loadRatings();
                loadEscrow();
                </script></body></html>
                """.formatted(
                        commonCSS(), adminNav,
                        escape(session.username), roleTag,
                        online, maxP, onlinePct,
                        tps, tpsColor, Math.min(tps * 100, 100),
                        usedMem, maxMem, memPct, memColor, memPct,
                        formatUptime(uptime),
                        avail, occ, locked, maint, cols.size(),
                        srvVer, mcVer, tps, online, maxP,
                        usedMem, maxMem, memPct,
                        formatUptime(uptime), pluginCount,
                        total, roomsHtml,
                        cols.size(), colsHtml,
                        adminSection,
                        consoleSection,
                        broadcastSection,
                        transactionsSection,
                        ratingsSection,
                        escrowSection
                );
    }
}
