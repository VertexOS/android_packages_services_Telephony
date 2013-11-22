package com.android.phone.v2.control;

import com.android.phone.v2.ui.incall.InCallController;

/** Only exposes the CallsManager APIs that In-Call should have access to. */
public class InCallAdapter {
  private CallsManager callsManager;

  private InCallController inCallUi;

  /** Package private */
  InCallAdapter(CallsManager callsManager) {
    this.callsManager = callsManager;
  }
}
