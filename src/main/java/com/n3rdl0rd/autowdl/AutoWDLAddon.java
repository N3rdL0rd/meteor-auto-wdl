package com.n3rdl0rd.autowdl;

import com.n3rdl0rd.autowdl.modules.AutoWDLModule;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AutoWDLAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Init auto wdl...");
        Modules.get().add(new AutoWDLModule());
    }

    @Override
    public String getPackage() {
        return "com.n3rdl0rd.autowdl";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("N3rdL0rd", "meteor-auto-wdl");
    }
}
