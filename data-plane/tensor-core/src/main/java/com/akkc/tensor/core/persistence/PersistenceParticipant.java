package com.akkc.tensor.core.persistence;

public interface PersistenceParticipant {
    PersistenceParticipant NONE = new PersistenceParticipant() {
        @Override
        public void beforeWrite() {
        }

        @Override
        public void afterWrite(WriteCounts counts) {
        }
    };

    void beforeWrite();

    void afterWrite(WriteCounts counts);
}
