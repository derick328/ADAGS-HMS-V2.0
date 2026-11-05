package com.adags.modules.encounters.domain;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
class OutpatientDeparturePolicyTest {
 @ParameterizedTest
 @CsvSource({"CONSULT_COMPLETE,CARE_COMPLETE,true","PENDING_SETTLEMENT,CARE_COMPLETE,true","CLOSED,CARE_COMPLETE,true",
 "AWAITING_RESULTS,FOLLOW_UP_PENDING,true","AWAITING_RESULTS,CARE_COMPLETE,false","IN_CONSULT,CARE_COMPLETE,false",
 "AWAITING_TRIAGE,CARE_COMPLETE,false","LEFT_WITHOUT_BEING_SEEN,LEFT_WITHOUT_BEING_SEEN,true",
 "AWAITING_TRIAGE,LEFT_WITHOUT_BEING_SEEN,false","REFERRED_OUT,REFERRED_OUT,true","IN_WARD,CARE_COMPLETE,false",
 "DISCHARGE_ORDERED,CARE_COMPLETE,false","DISCHARGED,CARE_COMPLETE,false","ABANDONED_UNPAID,ABANDONED_UNPAID,true"})
 void preservesClinicalOutcome(String state,String outcome,boolean allowed) {
  assertEquals(allowed,OutpatientDeparturePolicy.permits(state,outcome));
 }
}
