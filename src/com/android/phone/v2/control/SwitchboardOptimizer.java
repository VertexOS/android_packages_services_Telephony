package com.android.phone.v2.control;

import com.android.phone.v2.CallService;

import java.util.List;

/** Package private */
// TODO(android-contacts): See SwitchboardOptimizer (inner class) under
// https://critique.corp.google.com/#review/55374749/depot/google3/experimental/users/gilad/nova/prototype/phone/core/Switchboard.java
class SwitchboardOptimizer {

  /** Package private */
  List<CallService> sort(List<CallService> callServices) {
    // TODO(android-contacts): Sort by reliability, cost, and ultimately
    // the desirability to issue a given call over each of the specified
    // call services.
    return callServices;
  }
}
