package com.android.phone.v2.ui.dialer;

import com.android.phone.v2.ContactInfo;
import com.android.phone.v2.control.DialerAdapter;
import com.android.phone.v2.exceptions.CallServiceUnavailableException;
import com.android.phone.v2.exceptions.RestrictedCallException;

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
