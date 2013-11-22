package com.android.phone.v2.control;

import com.android.phone.v2.CallService;

/**
 * Only exposes the CallsManager APIs that CallService implementations should
 * have access to.
 */
public class CallServiceAdapter {
  private CallsManager callsManager;

  private CallService callService;

  /** Package private */
  CallServiceAdapter(CallsManager callsManager) {
    this.callsManager = callsManager;
  }
}
