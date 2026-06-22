package aoc.kingdoms.lukasz.jakowski.android;

import android.content.Context;
import android.os.Bundle;

import androidx.multidex.MultiDex;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import aoc.kingdoms.lukasz.jakowski.AA_Game;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;
        initialize(new AA_Game(), configuration);
    }
}