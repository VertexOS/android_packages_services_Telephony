package com.android.phone.control;

import com.android.phone.ContactInfo;
import com.android.phone.exceptions.CallServiceUnavailableException;
import com.android.phone.exceptions.RestrictedCallException;
import com.android.phone.ui.dialer.DialerController;

/** Only exposes the CallsManager APIs that the Dialer should have access to. */
// TODO: This class may not be necessary since we do not interact with Dialer directly.
public class DialerAdapter {
  private CallsManager callsManager;

  private DialerController dialerUi;

  /** Package private */
  DialerAdapter(CallsManager callsManager) {
    this.callsManager = callsManager;
  }

  public void connectTo(String userInput, ContactInfo contactInfo)
      throws RestrictedCallException, CallServiceUnavailableException {

    callsManager.connectTo(userInput, contactInfo);
  }
}
