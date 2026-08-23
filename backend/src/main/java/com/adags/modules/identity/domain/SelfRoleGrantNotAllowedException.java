package com.adags.modules.identity.domain;

// 18-admin.md §11: "Admin cannot silently elevate themselves." The
// friendly, application-layer half; the DB CHECK (role_assignment_no_self_grant,
// V57) is the backstop — same second-person-constraint discipline as
// PharmacyItemProposalSelfReviewNotAllowedException and its siblings.
public class SelfRoleGrantNotAllowedException extends RuntimeException {

    public SelfRoleGrantNotAllowedException() {
        super("A role assignment cannot be granted to yourself.");
    }
}
