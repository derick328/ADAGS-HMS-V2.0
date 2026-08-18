package com.adags.modules.inpatient.domain;

// Phase 6 item 3 brief: "Record discharge_orderer_role so the monthly
// report can distinguish doctor-ordered from nurse-ordered." Data for a
// report, not an authorization check (root CLAUDE.md "Authorisation":
// check permissions, never role names) — OrderDischargeService derives
// this from which permission/relationship the actor actually satisfied,
// it does not gate anything on it.
public enum DischargeOrdererRole {
    DOCTOR,
    NURSE
}
