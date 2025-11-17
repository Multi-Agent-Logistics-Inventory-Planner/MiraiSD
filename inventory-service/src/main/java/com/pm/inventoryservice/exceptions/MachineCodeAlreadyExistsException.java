package com.pm.inventoryservice.exceptions;

public class MachineCodeAlreadyExistsException extends RuntimeException {
  public MachineCodeAlreadyExistsException(String message) {
    super(message);
  }
}
