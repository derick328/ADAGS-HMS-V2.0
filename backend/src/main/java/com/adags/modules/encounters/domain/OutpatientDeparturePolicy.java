package com.adags.modules.encounters.domain;
import java.util.Set;
/** Records physical departure only; never transitions clinical or billing state. */
public final class OutpatientDeparturePolicy {
 private OutpatientDeparturePolicy() {}
 public static boolean permits(String state,String outcome) {
  if(state==null || outcome==null) return false;
  return switch(outcome) {
   case "CARE_COMPLETE" -> Set.of("CONSULT_COMPLETE","PENDING_SETTLEMENT","CLOSED").contains(state);
   case "FOLLOW_UP_PENDING" -> state.equals("AWAITING_RESULTS");
   case "REFERRED_OUT","LEFT_WITHOUT_BEING_SEEN","ABANDONED_UNPAID" -> state.equals(outcome);
   default -> false;
  };
 }
}
