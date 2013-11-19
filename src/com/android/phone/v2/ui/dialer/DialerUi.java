package com.android.phone.ui.dialer;

import com.android.phone.ContactInfo;

/** Package-private? */

// Only responsible for reflecting the necessary display changes and relaying
// user actions back to the DialerController.
class DialerUi {
  private DialerController dialerController;

  void outgoingCallScenario() {
    String userInput = "5551212";  // Can also be an email address etc.
    ContactInfo contactInfo = new ContactInfo();  // In case one can be retrieved.

    dialerController.dial(userInput, contactInfo);
  }
}
