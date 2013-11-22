package com.android.phone.v2.control;

import com.android.phone.v2.CallService;
import com.android.phone.v2.ContactInfo;
import com.android.phone.v2.exceptions.CallServiceUnavailableException;
import com.android.phone.v2.exceptions.RestrictedCallException;

import java.util.ArrayList;
import java.util.List;

/** Singleton */
public class CallsManager {

  private static final CallsManager INSTANCE = new CallsManager();

  private DialerAdapter dialerAdapter;

  private InCallAdapter inCallAdapter;

  private Switchboard switchboard;

  private CallLogManager callLogManager;

  private VoicemailManager voicemailManager;

  private List<CallRestrictionPolicy> restrictionPolicies =
      new ArrayList<CallRestrictionPolicy>();

  private List<CallRejectionPolicy> rejectionPolicies =
      new ArrayList<CallRejectionPolicy>();

  // Singleton, private constructor (see getInstance).
  private CallsManager() {
    switchboard = new Switchboard();
    callLogManager = new CallLogManager();
    voicemailManager = new VoicemailManager();  // As necessary etc.
  }

  /** Package private */
  static CallsManager getInstance() {
    return INSTANCE;
  }

  /** Package private */
  void addCallService(CallService callService) {
    if (callService != null) {
      switchboard.addCallService(callService);
      callService.setCallServiceAdapter(new CallServiceAdapter(this));
    }
  }

  /** Package private */
  void connectTo(String userInput, ContactInfo contactInfo)
      throws RestrictedCallException, CallServiceUnavailableException {

    for (CallRestrictionPolicy policy : restrictionPolicies) {
      policy.validate(userInput, contactInfo);
    }

    // No objection to issue the call, proceed with trying to put it through.
    switchboard.placeOutgoingCall(userInput, contactInfo);
  }
}
