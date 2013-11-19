package com.android.phone.control;

import com.android.phone.CallService;

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
