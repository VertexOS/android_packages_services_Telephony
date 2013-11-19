package com.android.phone.control;

import com.android.phone.CallService;
import com.android.phone.ContactInfo;
import com.android.phone.exceptions.CallServiceUnavailableException;
import com.android.phone.exceptions.OutgoingCallException;

import java.util.ArrayList;
import java.util.List;

/** Package private */
class Switchboard {

  private SwitchboardOptimizer optimizer;

  private List<CallService> callServices = new ArrayList<CallService>();

  /** Package private */
  void addCallService(CallService callService) {
    if (callService != null && !callServices.contains(callService)) {
      callServices.add(callService);
    }
  }

  /** Package private */
  void placeOutgoingCall(String userInput, ContactInfo contactInfo)
      throws CallServiceUnavailableException {

    if (callServices.isEmpty()) {
      // No call services, bail out.
      // TODO(contacts-team): Add logging?
      throw new CallServiceUnavailableException();
    }

    List<CallService> compatibleCallServices = new ArrayList<CallService>();
    for (CallService service : callServices) {
      if (service.isCompatibleWith(userInput, contactInfo)) {
        // NOTE(android-contacts): If we end up taking the liberty to issue
        // calls not using the explicit user input (in case one is provided)
        // and instead pull an alternative method of communication from the
        // specified user-info object, it may be desirable to give precedence
        // to services that can in fact respect the user's intent.
        compatibleCallServices.add(service);
      }
    }

    if (compatibleCallServices.isEmpty()) {
      // None of the available call services is suitable for making this call.
      // TODO(contacts-team): Same here re logging.
      throw new CallServiceUnavailableException();
    }

    // NOTE(android-team): At this point we can also prompt the user for
    // preference, i.e. instead of the logic just below.
    if (compatibleCallServices.size() > 1) {
      compatibleCallServices = optimizer.sort(compatibleCallServices);
    }
    for (CallService service : compatibleCallServices) {
      try {
        service.placeOutgoingCall(userInput, contactInfo);
        return;
      } catch (OutgoingCallException ignored) { }
    }
  }
}
