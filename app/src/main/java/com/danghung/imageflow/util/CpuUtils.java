package com.danghung.imageflow.util;

import android.os.Process;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/**
 * CpuUtils: Lấy số Core CPU và Core ID thực tế mà Thread đang chạy từ Linux kernel.
 */
public class CpuUtils {

    public static int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static int getCurrentCoreId() {
        try {
            int tid = Process.myTid();
            String statPath = "/proc/self/task/" + tid + "/stat";
            try (BufferedReader reader = new BufferedReader(new FileReader(statPath))) {
                String line = reader.readLine();
                if (line != null) {
                    int lastCloseParen = line.lastIndexOf(')');
                    if (lastCloseParen != -1) {
                        String afterParen = line.substring(lastCloseParen + 2);
                        String[] fields = afterParen.split("\\s+");
                        if (fields.length > 36) {
                            return Integer.parseInt(fields[36]);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    public static String getThreadAndCore() {
        int core = getCurrentCoreId();
        int tid = Process.myTid();
        String name = Thread.currentThread().getName();

        if (core >= 0) {
            return String.format(Locale.US, "%s (TID=%d, Core=#%d)", name, tid, core);
        }
        return String.format(Locale.US, "%s (TID=%d)", name, tid);
    }
}

