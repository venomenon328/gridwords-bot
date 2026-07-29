package de.venomenon.gridwordsbot.port.out;

/** Loads an attachment selected by the application without exposing Discord types or URLs. */
@FunctionalInterface
public interface AttachmentContentLoader {
    byte[] load(String attachmentReference) throws RetryableAttachmentException;
    final class RetryableAttachmentException extends RuntimeException { public RetryableAttachmentException(String message, Throwable cause) { super(message, cause); } }
}