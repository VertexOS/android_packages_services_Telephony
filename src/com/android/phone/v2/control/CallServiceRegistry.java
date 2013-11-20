package com.android.phone.control;

import com.android.phone.CallService;

/** Singleton */
public class CallServiceRegistry {
  private static final CallServiceRegistry INSTANCE = new CallServiceRegistry();

  private CallServiceRegistry() {
  }

  public static CallServiceRegistry getInstance() {
    return INSTANCE;
  }

  public void register(CallService callService) {

    // TODO: Remove concept of registering call service since they will be short-lived
    if (callService != null) {
      CallsManager callsManager = CallsManager.getInstance();
      if (callsManager != null) {
        callsManager.addCallService(callService);
      }
    }
  }
}
