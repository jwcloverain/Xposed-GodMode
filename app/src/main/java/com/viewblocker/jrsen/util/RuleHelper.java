package com.viewblocker.jrsen.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viewblocker.jrsen.injection.bridge.GodModeManager;
import com.viewblocker.jrsen.injection.util.FileUtils;
import com.viewblocker.jrsen.injection.util.Logger;
import com.viewblocker.jrsen.rule.ViewRule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import static com.viewblocker.jrsen.GodModeApplication.TAG;
import static com.viewblocker.jrsen.injection.util.CommonUtils.recycleBitmap;

public final class RuleHelper {

    private static final File EXPORT_DIR = Environment.getExternalStoragePublicDirectory("GodMode");
    private static final File TEMP_DIR = new File(EXPORT_DIR, ".temp");
    private static final String MANIFEST = "manifest.json";
    private static final String PACK_SUFFIX = ".gm";

    public static String exportRules(ViewRule... viewRules) {
        FileUtils.rmdir(TEMP_DIR.getPath());
        if (TEMP_DIR.mkdirs()) {
            JsonArray jsonArray = new JsonArray();
            ArrayList<String> filepaths = new ArrayList<>();
            Gson gson = new Gson();
            //Save view rule snapshot to
            for (ViewRule viewRule : viewRules) {
                ViewRule copyViewRule = viewRule.clone();
                Bitmap snapshot = copyViewRule.snapshot;
                if (Preconditions.checkBitmap(snapshot)) {
                    long timeMillis = System.currentTimeMillis();
                    File file = new File(TEMP_DIR, timeMillis + ".webp");
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        if (snapshot.compress(Bitmap.CompressFormat.WEBP, 100, out)) {
                            copyViewRule.imagePath = file.getName();
                            filepaths.add(file.getPath());
                        }
                    } catch (IOException e) {
                        Logger.e(TAG, "Compress view rule snapshot fail", e);
                    }
                }
                jsonArray.add(gson.toJsonTree(copyViewRule));
            }
            try {
                File manifestFile = new File(TEMP_DIR, MANIFEST);
                FileUtils.stringToFile(manifestFile, jsonArray.toString());
                filepaths.add(manifestFile.getPath());
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss", Locale.US);
                String zipFile = new File(EXPORT_DIR, sdf.format(new Date()) + PACK_SUFFIX).getAbsolutePath();
                if (ZipUtils.compress(zipFile, filepaths.toArray(new String[0]))) {
                    return zipFile;
                }
            } catch (IOException e) {
                Logger.e(TAG, "Write manifest file fail", e);
                return "";
            } finally {
                FileUtils.rmdir(TEMP_DIR.getPath());
            }
        }
        return "";
    }

    public static boolean importRules(String filepath) {
        FileUtils.rmdir(TEMP_DIR.getPath());
        if (TEMP_DIR.mkdirs() && ZipUtils.uncompress(filepath, TEMP_DIR.getPath())) {
            File manifestFile = new File(TEMP_DIR, MANIFEST);
            if (manifestFile.exists()) {
                try {
                    String json = FileUtils.readTextFile(manifestFile, 0, null);
                    JsonArray jsonArray = (JsonArray) JsonParser.parseString(json);
                    Iterator<JsonElement> iterator = jsonArray.iterator();
                    Gson gson = new Gson();
                    while (iterator.hasNext()) {
                        JsonObject jsonObject = (JsonObject) iterator.next();
                        ViewRule viewRule = gson.fromJson(jsonObject.toString(), ViewRule.class);
                        Bitmap bitmap = BitmapFactory.decodeFile(new File(TEMP_DIR, viewRule.imagePath).getPath());
                        GodModeManager.getDefault().writeRule(viewRule.packageName, viewRule, bitmap);
                        recycleBitmap(bitmap);
                    }
                    return true;
                } catch (IOException e) {
                    Logger.e(TAG, "import rules fail", e);
                }
            }
        }
        return false;
    }
}