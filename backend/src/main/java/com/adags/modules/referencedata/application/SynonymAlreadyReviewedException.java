package com.adags.modules.referencedata.application;

import java.util.UUID;

// A synonym only moves out of PROPOSED once — a second approve/reject call
// (a stale review screen, a double-click) must not silently re-decide it.
public class SynonymAlreadyReviewedException extends RuntimeException {

    public SynonymAlreadyReviewedException(UUID synonymId) {
        super("Synonym " + synonymId + " has already been reviewed.");
    }
}
