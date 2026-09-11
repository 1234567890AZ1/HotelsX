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
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

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
    private final WebConsole console;

    private static class Session {
        final String username;
        final String role;
        final String minecraftName;
        final String csrfToken; // 防 CSRF 随机令牌，登录时生成，变更类 API 需携带
        final long createdAt;
        long lastAccess;
        Session(String username, String role, String minecraftName) {
            this.username = username;
            this.role = role;
            this.minecraftName = minecraftName;
            this.csrfToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
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
        this.console = new WebConsole(plugin);
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
            console.installConsoleCapture();
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
                WebHttp.sendJson(exchange, 401, "{\"error\":\"需要登录\"}");
            } else {
                // 页面请求重定向到登录页
                WebHttp.setSecurityHeaders(exchange);
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
        if (s == null) { WebHttp.sendJson(exchange, 401, "{\"error\":\"需要登录\"}"); return false; }
        if (!"superadmin".equals(s.role)) { WebHttp.sendJson(exchange, 403, "{\"error\":\"权限不足\"}"); return false; }
        return true;
    }

    /**
     * CSRF 防护：校验请求头 X-CSRF-Token 是否与会话绑定的令牌一致
     * 所有变更类（POST）API 在权限校验后追加此检查
     */
    private boolean requireCsrf(HttpExchange exchange) throws IOException {
        Session s = getSession(exchange);
        String token = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
        if (s != null && s.csrfToken != null && token != null
                && java.security.MessageDigest.isEqual(
                        s.csrfToken.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        WebHttp.sendJson(exchange, 403, "{\"error\":\"安全校验失败，请刷新页面后重试\"}");
        return false;
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

    private class LoginPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (getSession(exchange) != null) {
                WebHttp.setSecurityHeaders(exchange);
                exchange.getResponseHeaders().set("Location", "/");
                byte[] body = new byte[0];
                exchange.sendResponseHeaders(302, -1);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                return;
            }
            WebHttp.sendHtml(exchange, 200, buildLoginPage());
        }
    }

    private class LoginApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                WebHttp.sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> form = WebHttp.parseForm(WebHttp.readBody(exchange));
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
                    WebHttp.sendJson(exchange, 200, WebHttp.toJson(r));
                    return;
                }
            }
            WebHttp.sendJson(exchange, 401, "{\"error\":\"用户名或密码错误\"}");
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            destroySession(exchange);
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                WebHttp.setSecurityHeaders(exchange);
                exchange.getResponseHeaders().set("Location", "/login");
                byte[] body = new byte[0];
                exchange.sendResponseHeaders(302, -1);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            } else {
                WebHttp.sendJson(exchange, 200, "{\"success\":true}");
            }
        }
    }

    private class MeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Session s = getSession(exchange);
            if (s == null) { WebHttp.sendJson(exchange, 401, "{\"error\":\"需要登录\"}"); return; }
            Map<String, Object> r = new HashMap<>();
            r.put("username", s.username); r.put("role", s.role);
            r.put("minecraftName", s.minecraftName);
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(r));
        }
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 未知 /api/* 路径统一返回 404，避免被根路径兜底成 200 主面板
            if (exchange.getRequestURI().getPath().startsWith("/api/")) {
                WebHttp.sendJson(exchange, 404, "{\"error\":\"接口不存在\"}");
                return;
            }
            if (!requireAuth(exchange)) return;
            try {
                WebHttp.sendHtml(exchange, 200, buildDashboard(getSession(exchange)));
            } catch (Exception e) {
                // 兜底：任何构建异常都返回可见错误页，避免空响应导致"未发送任何数据"
                plugin.getLogger().warning("构建 Web 面板页面出错: " + e);
                String errHtml = "<html><meta charset='UTF-8'><body style='font-family:sans-serif;padding:40px;'>"
                        + "<h2>HotelsX Web 面板加载失败</h2>"
                        + "<p style='color:red;'>" + WebHttp.escape(String.valueOf(e)) + "</p>"
                        + "<pre style='background:#f3f4f6;padding:12px;border-radius:8px;overflow:auto;'>"
                        + WebHttp.escape(WebHttp.stackTrace(e)) + "</pre></body></html>";
                WebHttp.sendHtml(exchange, 500, errHtml);
            }
        }
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(s));
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(s));
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(result));
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(result));
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
                WebHttp.sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            if (!requireCsrf(exchange)) return;
            Map<String, String> form = WebHttp.parseForm(WebHttp.readBody(exchange));
            String action = form.getOrDefault("action", "");
            String roomIdsStr = form.getOrDefault("roomIds", "");
            if (action.isEmpty() || roomIdsStr.isEmpty()) {
                WebHttp.sendJson(exchange, 400, "{\"error\":\"缺少参数\"}"); return;
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(r));
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
                sb.append("{\"id\":\"").append(WebHttp.escape(r.getId())).append("\",");
                sb.append("\"name\":\"").append(WebHttp.escape(r.getName() != null ? r.getName() : "")).append("\",");
                sb.append("\"owner\":\"").append(WebHttp.escape(r.getOwnerName())).append("\",");
                sb.append("\"status\":\"").append(r.getStatus()).append("\",");
                sb.append("\"price\":").append(r.getCurrentPrice()).append(",");
                sb.append("\"locked\":").append(r.isLocked()).append(",");
                sb.append("\"guest\":\"").append(WebHttp.escape(r.getCurrentGuestName() != null ? r.getCurrentGuestName() : "")).append("\",");
                sb.append("\"world\":\"").append(WebHttp.escape(r.getWorldName() != null ? r.getWorldName() : "")).append("\"}");
            }
            sb.append("]}");
            WebHttp.sendHtml(exchange, 200, sb.toString());
        }
    }

    private class RoomDeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Session session = getSession(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                WebHttp.sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = WebHttp.parseForm(WebHttp.readBody(exchange));
            String roomId = form.getOrDefault("roomId", "");
            if (roomId.isEmpty()) { WebHttp.sendJson(exchange, 400, "{\"error\":\"缺少房间ID\"}"); return; }
            HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
            if (room == null) { WebHttp.sendJson(exchange, 404, "{\"error\":\"房间不存在\"}"); return; }
            if (!requireCsrf(exchange)) return;
            if (!canDeleteRoom(session, room)) { WebHttp.sendJson(exchange, 403, "{\"error\":\"只有超级管理员可以删除房间\"}"); return; }
            plugin.getRoomStorage().removeRoom(roomId);
            plugin.getRoomStorage().saveAll();
            Map<String, Object> r = new HashMap<>();
            r.put("success", true); r.put("message", "房间已删除: " + room.getName());
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(r));
        }
    }

    private class RoomUpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            Session session = getSession(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                WebHttp.sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = WebHttp.parseForm(WebHttp.readBody(exchange));
            String roomId = form.getOrDefault("roomId", "");
            if (roomId.isEmpty()) { WebHttp.sendJson(exchange, 400, "{\"error\":\"缺少房间ID\"}"); return; }
            HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
            if (room == null) { WebHttp.sendJson(exchange, 404, "{\"error\":\"房间不存在\"}"); return; }
            if (!canManageRoom(session, room)) { WebHttp.sendJson(exchange, 403, "{\"error\":\"无权操作此房间\"}"); return; }
            if (!requireCsrf(exchange)) return;
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(r));
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
                sb.append("{\"id\":\"").append(WebHttp.escape(c.getId())).append("\",");
                sb.append("\"name\":\"").append(WebHttp.escape(c.getName())).append("\",");
                sb.append("\"owner\":\"").append(WebHttp.escape(c.getOwnerName())).append("\",");
                sb.append("\"roomCount\":").append(c.getRoomIds().size()).append("}");
            }
            sb.append("]}");
            WebHttp.sendHtml(exchange, 200, sb.toString());
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
                sb.append("{\"username\":\"").append(WebHttp.escape(a.username)).append("\",\"role\":\"").append(a.role).append("\"}");
            }
            sb.append("]}");
            WebHttp.sendHtml(exchange, 200, sb.toString());
        }
    }

    private class AdminAddHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                WebHttp.sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            if (!requireCsrf(exchange)) return;
            Map<String, String> form = WebHttp.parseForm(WebHttp.readBody(exchange));
            String u = form.getOrDefault("username", "").trim();
            String p = form.getOrDefault("password", "");
            String r = form.getOrDefault("role", "admin");
            String mc = form.getOrDefault("minecraft-name", "").trim();
            if (u.isEmpty() || p.isEmpty()) { WebHttp.sendJson(exchange, 400, "{\"error\":\"用户名和密码不能为空\"}"); return; }
            for (AdminAccount a : admins) {
                if (a.username.equals(u)) { WebHttp.sendJson(exchange, 409, "{\"error\":\"用户名已存在\"}"); return; }
            }
            admins.add(new AdminAccount(u, hashPassword(p), r, mc.isEmpty() ? null : mc));
            saveAdmins();
            Map<String, Object> res = new HashMap<>();
            res.put("success", true); res.put("message", "管理员已添加: " + u);
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(res));
        }
    }

    private class AdminDeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                WebHttp.sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = WebHttp.parseForm(WebHttp.readBody(exchange));
            String target = form.getOrDefault("username", "");
            Session s = getSession(exchange);
            if (s != null && s.username.equals(target)) { WebHttp.sendJson(exchange, 400, "{\"error\":\"不能删除自己\"}"); return; }
            boolean removed = admins.removeIf(a -> a.username.equals(target));
            if (!removed) { WebHttp.sendJson(exchange, 404, "{\"error\":\"管理员不存在\"}"); return; }
            saveAdmins();
            Map<String, Object> res = new HashMap<>();
            res.put("success", true); res.put("message", "管理员已删除: " + target);
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(res));
        }
    }

    private class AdminPasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireAuth(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                WebHttp.sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = WebHttp.parseForm(WebHttp.readBody(exchange));
            String oldPwd = form.getOrDefault("oldPassword", "");
            String newPwd = form.getOrDefault("newPassword", "");
            Session s = getSession(exchange);
            for (AdminAccount a : admins) {
                if (a.username.equals(s.username)) {
                    if (!verifyPassword(oldPwd, a.password)) { WebHttp.sendJson(exchange, 401, "{\"error\":\"原密码错误\"}"); return; }
                    a.password = hashPassword(newPwd);
                    saveAdmins();
                    Map<String, Object> r = new HashMap<>();
                    r.put("success", true); r.put("message", "密码已修改");
                    WebHttp.sendJson(exchange, 200, WebHttp.toJson(r));
                    return;
                }
            }
            WebHttp.sendJson(exchange, 404, "{\"error\":\"管理员不存在\"}");
        }
    }

    private class ConsoleLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            List<String> lines = console.getConsoleLogs();
            Map<String, Object> r = new HashMap<>();
            r.put("lines", lines);
            r.put("count", lines.size());
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(r));
        }
    }

    private class ConsoleCommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                WebHttp.sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            Map<String, String> form = WebHttp.parseForm(WebHttp.readBody(exchange));
            String command = form.getOrDefault("command", "").trim();
            if (command.isEmpty()) {
                WebHttp.sendJson(exchange, 400, "{\"error\":\"命令不能为空\"}"); return;
            }
            Session session = getSession(exchange);
            console.logConsole("[HotelsX] " + session.username + " 执行: " + command);
            SchedulerCompat.runTask(plugin, () -> {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (Exception e) {
                    console.logConsole("[HotelsX] 命令执行错误: " + e.getMessage());
                }
            });
            Map<String, Object> r = new HashMap<>();
            r.put("success", true);
            r.put("message", "命令已执行: " + command);
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(r));
        }
    }

    private class BroadcastHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireSuperAdmin(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                WebHttp.sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); return;
            }
            if (!requireCsrf(exchange)) return;
            Map<String, String> form = WebHttp.parseForm(WebHttp.readBody(exchange));
            String type = form.getOrDefault("type", "chat").trim();   // chat / title / actionbar
            String message = form.getOrDefault("message", "").trim();
            String target = form.getOrDefault("target", "all").trim(); // all / player
            if (message.isEmpty()) {
                WebHttp.sendJson(exchange, 400, "{\"error\":\"公告内容不能为空\"}"); return;
            }
            Session session = getSession(exchange);
            org.bukkit.entity.Player receiver = null;
            if ("player".equalsIgnoreCase(target)) {
                String playerName = form.getOrDefault("player", "").trim();
                if (playerName.isEmpty()) {
                    WebHttp.sendJson(exchange, 400, "{\"error\":\"请指定玩家名\"}"); return;
                }
                receiver = Bukkit.getPlayerExact(playerName);
                if (receiver == null) {
                    WebHttp.sendJson(exchange, 404, "{\"error\":\"玩家不在线: " + playerName + "\"}"); return;
                }
            }
            String logTarget = "player".equalsIgnoreCase(target) ? ("玩家 " + receiver.getName()) : "全服";
            console.logConsole("[HotelsX] " + session.username + " 向" + logTarget + "发送公告(" + type + "): " + message);
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(r));
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
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
            WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
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
            if (!requireCsrf(exchange)) return;

            if (!plugin.getConfig().getBoolean("economy.escrow-mode", false)) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "当前为实时到账模式，收益已直接发放，无需提现");
                WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
                return;
            }
            if (!plugin.getEconomyManager().isEnabled()) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "经济系统未启用");
                WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
                return;
            }

            String body = WebHttp.readBody(exchange);
            Map<String, String> form = WebHttp.parseForm(body);
            String uuid = form.get("uuid");
            String name = form.get("name");

            // 权限：superadmin/admin 可代任意房主提现；user 只能提现自己（通过 minecraft-name 匹配）
            if ("user".equals(session.role)) {
                if (session.minecraftName == null || name == null
                        || !name.equalsIgnoreCase(session.minecraftName)) {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", false);
                    resp.put("message", "您只能提现自己的收益");
                    WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
                    return;
                }
            }

            if (uuid == null || uuid.isEmpty()) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "缺少玩家信息");
                WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
                return;
            }

            double pending = plugin.getEscrowStorage().getPending(uuid);
            if (pending <= 0) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "该玩家没有待提现的收益");
                WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
                return;
            }

            try {
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                if (plugin.getEconomyManager().deposit(op, pending)) {
                    plugin.getEscrowStorage().completeWithdrawal(uuid, name, pending);
                    console.logConsole("Web提现: " + name + " 提现 " + plugin.getEconomyManager().format(pending) + " 到钱包");
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", true);
                    resp.put("message", "已发放 " + plugin.getEconomyManager().format(pending) + " 给 " + name);
                    WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
                } else {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", false);
                    resp.put("message", "发放失败，请查看服务器日志");
                    WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
                }
            } catch (Exception e) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", "提现出错: " + e.getMessage());
                WebHttp.sendJson(exchange, 200, WebHttp.toJson(resp));
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
            WebHttp.setSecurityHeaders(exchange);
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
                    String payload = "data: " + WebHttp.toJson(s) + "\n\n";
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
            WebHttp.setSecurityHeaders(exchange);
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
                    List<String> lines = console.getConsoleLogs();
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
        return loadResource("web/style.css");
    }

    /**
     * 从插件 jar 的 classpath 加载前端资源文件（UTF-8）
     */
    private String loadResource(String path) {
        try (var in = WebServer.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                plugin.getLogger().warning("Web 面板资源缺失: " + path);
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("加载 Web 面板资源失败: " + path + " - " + e.getMessage());
            return "";
        }
    }

    private String buildLoginPage() {
        return loadResource("web/login.html");
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
                    .append("<td><input type='checkbox' class='room-checkbox' value='").append(WebHttp.escape(r.getId())).append("' onchange='updateBatchToolbar()'></td>")
                    .append("<td>").append(WebHttp.escape(r.getId())).append("</td>")
                    .append("<td>").append(WebHttp.escape(r.getName()!=null?r.getName():"")).append("</td>")
                    .append("<td>").append(WebHttp.escape(r.getOwnerName())).append("</td>")
                    .append("<td><span class='status-tag ").append(sc).append("'>").append(st).append("</span></td>")
                    .append("<td>").append(String.format("%.2f",r.getCurrentPrice())).append("</td>")
                    .append("<td>").append(WebHttp.escape(r.getCurrentGuestName()!=null?r.getCurrentGuestName():"-")).append("</td>")
                    .append("<td>").append(r.isLocked()?"是":"否").append("</td>")
                    .append("<td>");
                boolean canManage = canManageRoom(session, r);
                boolean canDelete = canDeleteRoom(session, r);
                if (canManage) {
                    roomsHtml.append("<button class='btn btn-secondary btn-sm' onclick=\"openEdit('").append(WebHttp.escapeJsAttr(r.getId())).append("','").append(WebHttp.escapeJsAttr(r.getName()!=null?r.getName():"")).append("',").append(r.getCurrentPrice()).append(",'").append(WebHttp.escapeJsAttr(r.getStatus().name())).append("',").append(r.isLocked()).append(")\">编辑</button> ");
                }
                if (canDelete) {
                    roomsHtml.append("<button class='btn btn-danger btn-sm' onclick=\"delRoom('").append(WebHttp.escapeJsAttr(r.getId())).append("')\">删除</button>");
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
                    .append("<td>").append(WebHttp.escape(c.getId())).append("</td>")
                    .append("<td>").append(WebHttp.escape(c.getName())).append("</td>")
                    .append("<td>").append(WebHttp.escape(c.getOwnerName())).append("</td>")
                    .append("<td>").append(c.getRoomIds().size()).append("</td></tr>");
            }
        }

        StringBuilder adminRows = new StringBuilder();
        for (AdminAccount a : admins) {
            adminRows.append("<tr>")
                .append("<td>").append(WebHttp.escape(a.username)).append("</td>")
                .append("<td>").append(switch (a.role) {
                    case "superadmin" -> "<span class='badge'>超级管理员</span>";
                    case "admin" -> "<span class='badge' style='background:#667eea'>管理员</span>";
                    default -> "<span class='badge' style='background:#6b7280'>普通用户</span>";
                }).append("</td>")
                .append("<td>");
            if ("superadmin".equals(session.role) && !a.username.equals(session.username)) {
                adminRows.append("<button class='btn btn-danger btn-sm' onclick=\"delAdmin('").append(WebHttp.escapeJsAttr(a.username)).append("')\">删除</button>");
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

        return loadResource("web/dashboard.html").formatted(
                        commonCSS(), adminNav,
                        WebHttp.escape(session.username), roleTag,
                        online, maxP, onlinePct,
                        tps, tpsColor, Math.min(tps * 100, 100),
                        usedMem, maxMem, memPct, memColor, memPct,
                        WebHttp.formatUptime(uptime),
                        avail, occ, locked, maint, cols.size(),
                        srvVer, mcVer, tps, online, maxP,
                        usedMem, maxMem, memPct,
                        WebHttp.formatUptime(uptime), pluginCount,
                        total, roomsHtml,
                        cols.size(), colsHtml,
                        transactionsSection,
                        ratingsSection,
                        escrowSection,
                        adminSection,
                        consoleSection,
                        broadcastSection
                ).replace("__CSRF_TOKEN__", session.csrfToken);
    }
}
