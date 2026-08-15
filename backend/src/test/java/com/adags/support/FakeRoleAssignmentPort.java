package com.adags.support;

import com.adags.shared.authz.RoleAssignmentPort;
import com.adags.shared.authz.RoleAssignmentView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FakeRoleAssignmentPort implements RoleAssignmentPort {

    private record Entry(UUID userId, RoleAssignmentView view) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(UUID userId, RoleAssignmentView view) {
        entries.add(new Entry(userId, view));
    }

    @Override
    public List<RoleAssignmentView> findAssignments(UUID userId) {
        return entries.stream()
                .filter(entry -> entry.userId().equals(userId))
                .map(Entry::view)
                .toList();
    }
}
