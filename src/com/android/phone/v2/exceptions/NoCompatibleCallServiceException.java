package com.android.phone.v2.exceptions;

public class NoCompatibleCallServiceException extends Exception {
  public NoCompatibleCallServiceException() {
    super();
  }

  public NoCompatibleCallServiceException(String message) {
    super(message);
  }

  public NoCompatibleCallServiceException(String message, Throwable cause) {
    super(message, cause);
  }

  public NoCompatibleCallServiceException(Throwable cause) {
    super(cause);
  }
}
