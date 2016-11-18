package no.difi.statistics.ingest.client.exception;

import java.io.IOException;

public class CommunicationError extends IngestException {
    public CommunicationError(String message, IOException exception) {
        super(message, exception);
    }

    public CommunicationError(String message) {
        super(message);
    }
}