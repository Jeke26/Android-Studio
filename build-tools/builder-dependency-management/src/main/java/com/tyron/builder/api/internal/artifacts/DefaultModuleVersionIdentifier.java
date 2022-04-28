package com.tyron.builder.api.internal.artifacts;

import com.tyron.builder.api.artifacts.ModuleIdentifier;
import com.tyron.builder.api.artifacts.ModuleVersionIdentifier;
import com.tyron.builder.api.artifacts.component.ModuleComponentIdentifier;

public class DefaultModuleVersionIdentifier implements ModuleVersionIdentifier {

    private final ModuleIdentifier id;
    private final String version;
    private final int hashCode;

    private DefaultModuleVersionIdentifier(String group, String name, String version) {
        assert group != null : "group cannot be null";
        assert name != null : "name cannot be null";
        assert version != null : "version cannot be null";
        this.id = DefaultModuleIdentifier.newId(group, name);
        this.version = version;
        this.hashCode = 31 * id.hashCode() ^ version.hashCode();
    }

    private DefaultModuleVersionIdentifier(ModuleIdentifier id, String version) {
        assert version != null : "version cannot be null";
        this.id = id;
        this.version = version;
        // pre-compute the hashcode as it's going to be used anyway, and this object
        // is used as a key in several hash maps
        this.hashCode = 31 * id.hashCode() ^ version.hashCode();
    }

    @Override
    public String getGroup() {
        return id.getGroup();
    }

    @Override
    public String getName() {
        return id.getName();
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        String group = id.getGroup();
        String module = id.getName();
        return group + ":" + module + ":" + version;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultModuleVersionIdentifier other = (DefaultModuleVersionIdentifier) obj;
        if (!id.equals(other.id)) {
            return false;
        }
        return version.equals(other.version);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public ModuleIdentifier getModule() {
        return id;
    }

    public static ModuleVersionIdentifier newId(Module module) {
        return new DefaultModuleVersionIdentifier(module.getGroup(), module.getName(), module.getVersion());
    }

    public static ModuleVersionIdentifier newId(ModuleIdentifier id, String version) {
        return new DefaultModuleVersionIdentifier(id, version);
    }

    public static ModuleVersionIdentifier newId(String group, String name, String version) {
        return new DefaultModuleVersionIdentifier(group, name, version);
    }

    public static ModuleVersionIdentifier newId(ModuleComponentIdentifier componentId) {
        return new DefaultModuleVersionIdentifier(componentId.getGroup(), componentId.getModule(), componentId.getVersion());
    }
}