package org.gradle.api.java.archives;

import org.gradle.api.BuildException;

/**
 * Is thrown in the case an operation is applied against a {@link org.gradle.api.java.archives.Manifest} that violates
 * the Manifest specification.
 */
public class ManifestException extends BuildException {
    public ManifestException(String message) {
        super(message);
    }

    public ManifestException(String message, Throwable cause) {
        super(message, cause);
    }
}