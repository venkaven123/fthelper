package dev.fthelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Конфиг: .minecraft/config/fthelper.json */
public class Config {
    public static final int NONE = -1;
    public static final int MOUSE = 1000; // MOUSE + номер кнопки GLFW (3 = Mouse 4, 4 = Mouse 5)

    public static class ItemBind {
        public String item;
        public int key;
        public ItemBind() {}
        public ItemBind(String item, int key) { this.item = item; this.key = key; }
    }

    public int guiKey = 74; // J

    public boolean hudEnabled = true;
    public double hudScale = 1.0;
    public int hudYOffset = 75; // насколько выше низа экрана (над хотбаром и полосками)

    public List<ItemBind> binds = new ArrayList<>(List.of(
            new ItemBind("minecraft:netherite_scrap", 71),      // G
            new ItemBind("minecraft:snowball", MOUSE + 3),      // Mouse 4
            new ItemBind("minecraft:ender_eye", MOUSE + 4),     // Mouse 5
            new ItemBind("minecraft:phantom_membrane", 90),     // Z
            new ItemBind("minecraft:dried_kelp", 66),           // B
            new ItemBind("minecraft:sugar", 89)                 // Y
    ));

    public boolean returnItem = true; // после использования вернуть предмет на прежнее место

    public boolean espEnabled = false;
    public int espKey = NONE;
    public int espRadius = 60;        // расстояние стрелок от прицела (px)
    public int espMaxDistance = 200;  // макс. дистанция до игрока (блоки)
    public String espColor = "#FF3030";

    public int freecamKey = NONE;
    public double freecamSpeed = 1.0;

    public List<String> friends = new ArrayList<>();

    private static Config inst;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("fthelper.json");
    }

    public static Config get() {
        if (inst == null) load();
        return inst;
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) inst = GSON.fromJson(Files.readString(p), Config.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (inst == null) inst = new Config();
        if (inst.binds == null) inst.binds = new ArrayList<>();
        if (inst.friends == null) inst.friends = new ArrayList<>();
        if (inst.espColor == null) inst.espColor = "#FF3030";
        save();
    }

    public static void save() {
        if (inst == null) return;
        try {
            Files.writeString(path(), GSON.toJson(inst));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int espColorArgb() {
        try {
            String s = espColor.startsWith("#") ? espColor.substring(1) : espColor;
            return 0xFF000000 | Integer.parseInt(s, 16);
        } catch (Exception e) {
            return 0xFFFF3030;
        }
    }

    public boolean isFriend(String name) {
        for (String f : friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }
}
