package space.ashalinux.skvote;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import ch.njol.skript.lang.util.SimpleEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;

import javax.crypto.Cipher;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;

public class Main extends JavaPlugin implements Listener {

    private final HashMap<String, Integer> voteQueue = new HashMap<>();
    private PrivateKey privateKey;
    private ServerSocket serverSocket;
    private boolean running = true;
    private boolean debugEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.debugEnabled = getConfig().getBoolean("debug", false);

        SkriptAddon addon = Skript.registerAddon(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        initializeKeys();

        Skript.registerEvent("Online Vote", SimpleEvent.class, OnlineVoteEvent.class, "online vote");
        EventValueRegistry registry = addon.registry(EventValueRegistry.class);
        registry.register(EventValue.simple(OnlineVoteEvent.class, String.class, e -> e.getPlayer().getName()));
        registry.register(EventValue.simple(OnlineVoteEvent.class, Player.class, OnlineVoteEvent::getPlayer));

        startVoteListener();
        logDebug("SkVote initialized.");
    }

    @Override
    public void onDisable() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }

    private void initializeKeys() {
        File rsaDir = new File(getDataFolder(), "rsa");
        File privFile = new File(rsaDir, "private.key");
        if (!rsaDir.exists()) rsaDir.mkdirs();

        try {
            if (!privFile.exists()) {
                logDebug("Generating new RSA keys...");
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair pair = keyGen.generateKeyPair();
                try (FileOutputStream out = new FileOutputStream(privFile)) {
                    out.write(pair.getPrivate().getEncoded());
                }
                String pubBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
                try (PrintWriter pw = new PrintWriter(new File(rsaDir, "public.key"))) {
                    pw.print(pubBase64);
                }
                privateKey = pair.getPrivate();
            } else {
                byte[] privBytes = java.nio.file.Files.readAllBytes(privFile.toPath());
                privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                logDebug("RSA keys loaded.");
            }
        } catch (Exception e) {
            getLogger().severe("RSA init error: " + e.getMessage());
        }
    }

    private void startVoteListener() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int port = getConfig().getInt("port", 8192);
            try {
                serverSocket = new ServerSocket(port);
                logDebug("Listening on port " + port);

                while (running) {
                    try (Socket socket = serverSocket.accept()) {
                        logDebug("Connection from: " + socket.getInetAddress().getHostAddress());

                        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        out.write("VOTIFIER 1.9\n");
                        out.flush();

                        InputStream in = socket.getInputStream();
                        byte[] data = new byte[256];
                        int read = in.read(data);

                        if (read > 0) {
                            String decrypted = decrypt(data);
                            if (decrypted != null) {
                                logDebug("Data: " + decrypted);
                                String[] parts = decrypted.split("\\r?\\n");
                                int userIndex = 1;

                                if (parts[0].trim().equalsIgnoreCase("VOTE")) {
                                    userIndex = 2;
                                }

                                if (parts.length > userIndex) {
                                    handleVote(parts[userIndex].trim());
                                }
                            }
                        }
                    } catch (Exception e) {
                        if (running) logDebug("Socket error: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                getLogger().severe("Port error: " + e.getMessage());
            }
        });
    }

    private String decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(data));
        } catch (Exception e) {
            logDebug("Decrypt error: " + e.getMessage());
            return null;
        }
    }

    private void handleVote(String name) {
        Bukkit.getScheduler().runTask(this, () -> {
            Player p = Bukkit.getPlayer(name);
            if (p != null && p.isOnline()) {
                logDebug("Online vote: " + name);
                Bukkit.getPluginManager().callEvent(new OnlineVoteEvent(p));
            } else {
                logDebug("Offline vote: " + name);
                voteQueue.put(name, voteQueue.getOrDefault(name, 0) + 1);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String n = e.getPlayer().getName();
        if (voteQueue.containsKey(n)) {
            int count = voteQueue.get(n);
            logDebug("Releasing " + count + " vote(s) for " + n);
            for (int i = 0; i < count; i++) {
                Bukkit.getPluginManager().callEvent(new OnlineVoteEvent(e.getPlayer()));
            }
            voteQueue.remove(n);
        }
    }

    private void logDebug(String message) {
        if (debugEnabled) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public static class OnlineVoteEvent extends Event {
        private static final HandlerList h = new HandlerList();
        private final Player p;
        public OnlineVoteEvent(Player p) { this.p = p; }
        public Player getPlayer() { return p; }
        public HandlerList getHandlers() { return h; }
        public static HandlerList getHandlerList() { return h; }
    }
}