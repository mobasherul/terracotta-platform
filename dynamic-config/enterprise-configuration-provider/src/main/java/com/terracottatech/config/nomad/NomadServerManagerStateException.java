/*
 * Copyright (c) 2011-2019 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG.
 */
package com.terracottatech.config.nomad;

public class NomadServerManagerStateException extends NomadConfigurationException {

  private static final long serialVersionUID = 1L;

  private final String state;

  public NomadServerManagerStateException(final String state, final String message) {
    super(message);
    this.state = state;
  }

  public NomadServerManagerStateException(final String state, final String message, final Throwable cause) {
    super(message, cause);
    this.state = state;
  }

  public String getState() {
    return state;
  }
}
