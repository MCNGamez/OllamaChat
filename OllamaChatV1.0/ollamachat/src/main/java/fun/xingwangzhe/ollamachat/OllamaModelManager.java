package fun.xingwangzhe.ollamachat;

import net.minecraft.text.Text;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OllamaModelManager {
    private static final CopyOnWriteArrayList<String> cachedModels = new CopyOnWriteArrayList<>();
    private static String currentModel = "";
    private static final int MODEL_UPDATE_INTERVAL = 300;

    public static List<String> getCachedModels() {
        return new ArrayList<>(cachedModels);
    }

    public static synchronized void updateModelsFromSystem() {
        try {
            Process process = new ProcessBuilder("ollama", "list").start();
            List<String> newModels = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean isFirstLine = true;
                while ((line = reader.readLine()) != null) {
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }
                    if (line.matches("^\\S+\\s+.*")) {
                        String modelName = line.split("\\s+")[0];
                        newModels.add(modelName);
                    }
                }
            }
            cachedModels.clear();
            cachedModels.addAll(newModels);
        } catch (Exception e) {
            cachedModels.clear();
            cachedModels.add(Text.translatable("command.ollama.error.model_list_failed").getString());
        }
    }

    public static boolean isModelValid(String modelName) {
        return cachedModels.contains(modelName);
    }

    public static String getCurrentModel() {
        return currentModel;
    }

    public static void setCurrentModel(String model) {
        currentModel = model;
    }

    static {
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        OllamaModelManager::updateModelsFromSystem,
                        0, MODEL_UPDATE_INTERVAL, TimeUnit.SECONDS
                );
    }
}