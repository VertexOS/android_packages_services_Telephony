package com.android.phone.v2.exceptions;

public class RestrictedCallException extends Exception {
  public RestrictedCallException() {
    super();
  }

  public RestrictedCallException(String message) {
    super(message);
  }

  public RestrictedCallException(String message, Throwable cause) {
    super(message, cause);
  }

  public RestrictedCallException(Throwable cause) {
    super(cause);
  }
}
