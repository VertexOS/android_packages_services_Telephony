package com.android.phone;

import com.android.phone.control.CallServiceAdapter;
import com.android.phone.exceptions.OutgoingCallException;

public interface CallService {

  public void setCallServiceAdapter(CallServiceAdapter adapter);

  public boolean isCompatibleWith(String userInput, ContactInfo contactInfo);

  public void placeOutgoingCall(String userInput, ContactInfo contactInfo)
    throws OutgoingCallException;
}
