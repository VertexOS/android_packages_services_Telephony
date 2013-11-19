package com.android.phone.exceptions;

public class OutgoingCallException extends Exception {
  public OutgoingCallException() {
    super();
  }

  public OutgoingCallException(String message) {
    super(message);
  }

  public OutgoingCallException(String message, Throwable cause) {
    super(message, cause);
  }

  public OutgoingCallException(Throwable cause) {
    super(cause);
  }
}

