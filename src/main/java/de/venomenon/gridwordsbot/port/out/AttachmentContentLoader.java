package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;

/** Loads an attachment selected by the application without exposing Discord types or URLs. */
@FunctionalInterface
public interface AttachmentContentLoader {
    byte[] load(AttachmentMetadata attachment) throws AttachmentContentLoadException;

    class AttachmentContentLoadException extends RuntimeException {
        public AttachmentContentLoadException(String message) {
            super(message);
        }

        public AttachmentContentLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    final class AttachmentTooLargeException extends AttachmentContentLoadException {
        public AttachmentTooLargeException(String message) {
            super(message);
        }
    }

    final class AttachmentUnavailableException extends AttachmentContentLoadException {
        public AttachmentUnavailableException(String message) {
            super(message);
        }

        public AttachmentUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    final class RetryableAttachmentException extends AttachmentContentLoadException {
        public RetryableAttachmentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
