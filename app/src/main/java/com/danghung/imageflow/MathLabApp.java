package com.danghung.imageflow;

import android.app.Application;

public class MathLabApp extends Application {
    private static MathLabApp instance;

    public static MathLabApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}

