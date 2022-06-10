package org.gradle.api.artifacts;

import org.gradle.api.BuildException;
import org.gradle.internal.exceptions.Contextual;

/**
 * <p>A <code>PublishException</code> is thrown when a dependency configuration cannot be published for some reason.</p>
 */
@Contextual
public class PublishException extends BuildException {
    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }

    public PublishException(String message) {
        super(message);
    }
}
