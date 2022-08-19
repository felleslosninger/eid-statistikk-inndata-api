package no.difi.statistics.api;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;

public class IngestResponse {

    public enum Status {Ok, Failed, Conflict}

    private List<Status> statuses = new ArrayList<>();

    private IngestResponse() {
        // Use builder
    }

    public List<Status> getStatuses() {
        return unmodifiableList(statuses);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private IngestResponse instance;

        Builder() {
            this.instance = new IngestResponse();
        }

        /**
         * Append status to the (ordered) list.
         */
        public Builder status(Status status) {
            instance.statuses.add(status);
            return this;
        }

        public IngestResponse build() {
            try {
                return instance;
            } finally {
                instance = null;
            }
        }

    }

}
