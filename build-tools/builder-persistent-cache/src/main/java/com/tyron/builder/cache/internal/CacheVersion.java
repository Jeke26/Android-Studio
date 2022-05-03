package com.tyron.builder.cache.internal;

import com.google.common.primitives.Ints;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public final class CacheVersion implements Comparable<CacheVersion> {

    public static final String COMPONENT_SEPARATOR = ".";

    public static CacheVersion parse(String version) {
        String[] parts = StringUtils.split(version, COMPONENT_SEPARATOR);
        int[] components = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            components[i] = Integer.parseInt(parts[i]);
        }
        return new CacheVersion(components);
    }

    public static CacheVersion empty() {
        return new CacheVersion(ArrayUtils.EMPTY_INT_ARRAY);
    }

    public static CacheVersion of(int component) {
        return new CacheVersion(new int[] {component});
    }

    public static CacheVersion of(int... components) {
        return new CacheVersion(ArrayUtils.clone(components));
    }

    private final int[] components;

    private CacheVersion(int[] components) {
        this.components = components;
    }

    public CacheVersion append(int additionalComponent) {
        int[] appendedComponents = new int[components.length + 1];
        System.arraycopy(components, 0, appendedComponents, 0, components.length);
        appendedComponents[components.length] = additionalComponent;
        return new CacheVersion(appendedComponents);
    }

    @Override
    public String toString() {
        return Ints.join(COMPONENT_SEPARATOR, components);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CacheVersion that = (CacheVersion) o;
        return Arrays.equals(this.components, that.components);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(components);
    }

    @Override
    public int compareTo(CacheVersion that) {
        int minLength = Math.min(this.components.length, that.components.length);
        for (int i = 0; i < minLength; i++) {
            int result = Ints.compare(this.components[i], that.components[i]);
            if (result != 0) {
                return result;
            }
        }
        return Ints.compare(this.components.length, that.components.length);
    }
}
