package com.android.phone.exceptions;

public class CallServiceUnavailableException extends Exception {
  public CallServiceUnavailableException() {
    super();
  }

  public CallServiceUnavailableException(String message) {
    super(message);
  }

  public CallServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public CallServiceUnavailableException(Throwable cause) {
    super(cause);
  }
}
