package com.tyron.builder.api.internal.fingerprint.impl;

import com.tyron.builder.api.internal.execution.fingerprint.FileCollectionSnapshotter;
import com.tyron.builder.api.internal.fingerprint.AbsolutePathInputNormalizer;
import com.tyron.builder.api.internal.fingerprint.DirectorySensitivity;
import com.tyron.builder.api.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher;
import com.tyron.builder.api.internal.service.scopes.Scopes;
import com.tyron.builder.api.internal.service.scopes.ServiceScope;
import com.tyron.builder.api.tasks.FileNormalizer;

@ServiceScope(Scopes.BuildSession.class)
public class AbsolutePathFileCollectionFingerprinter extends AbstractFileCollectionFingerprinter {

    public AbsolutePathFileCollectionFingerprinter(DirectorySensitivity directorySensitivity, FileCollectionSnapshotter fileCollectionSnapshotter, FileSystemLocationSnapshotHasher normalizedContentHasher) {
        super(new AbsolutePathFingerprintingStrategy(directorySensitivity, normalizedContentHasher), fileCollectionSnapshotter);
    }

    @Override
    public Class<? extends FileNormalizer> getRegisteredType() {
        return AbsolutePathInputNormalizer.class;
    }
}