package com.android.phone.ui.dialer;

import com.android.phone.ContactInfo;
import com.android.phone.control.DialerAdapter;
import com.android.phone.exceptions.CallServiceUnavailableException;
import com.android.phone.exceptions.RestrictedCallException;

public class DialerController {

  DialerUi ui;

  DialerAdapter dialerAdapter;

  /** Package private */
  void dial(String userInput, ContactInfo ontactInfo) {
    try {
      dialerAdapter.connectTo(userInput, ontactInfo);
    } catch (RestrictedCallException e) {
      // TODO(android-contacts): Handle the exception.
    } catch (CallServiceUnavailableException e) {
      // TODO(android-contacts): Handle the exception.
    }
  }
}
