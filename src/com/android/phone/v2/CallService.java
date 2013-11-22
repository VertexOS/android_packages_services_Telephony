package com.android.phone.v2;

import com.android.phone.v2.control.CallServiceAdapter;
import com.android.phone.v2.exceptions.OutgoingCallException;

public interface CallService {

  public void setCallServiceAdapter(CallServiceAdapter adapter);

  public boolean isCompatibleWith(String userInput, ContactInfo contactInfo);

  public void placeOutgoingCall(String userInput, ContactInfo contactInfo)
    throws OutgoingCallException;
}
