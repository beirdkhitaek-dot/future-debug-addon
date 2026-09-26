package com.example.futuredebug;

import com.example.futuredebug.modules.FutureDebug;
import com.example.futuredebug.modules.FutureDebugRender;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class FutureDebugAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Future Debug");

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public void onInitialize() {
        Modules.get().add(new FutureDebug());
        Modules.get().add(new FutureDebugRender());
    }

    @Override
    public String getPackage() {
        return "com.example.futuredebug";
    }
}
