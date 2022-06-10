package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hasher;
import org.gradle.internal.hash.Hashable;

import java.nio.charset.StandardCharsets;
import java.util.List;

class AbstractListSnapshot<T extends Hashable> implements Hashable {
    protected final ImmutableList<T> elements;

    public AbstractListSnapshot(ImmutableList<T> elements) {
        this.elements = elements;
    }

    public List<T> getElements() {
        return elements;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putString("List", StandardCharsets.UTF_8);
        hasher.putInt(elements.size());
        for (T element : elements) {
            element.appendToHasher(hasher);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        AbstractListSnapshot other = (AbstractListSnapshot) obj;
        return elements.equals(other.elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }
}