/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix.tipc;

import java.io.Serializable;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.newsclub.net.unix.NamedInteger;

/**
 * The TIPC-specific error info response that may be included as ancillary data.
 *
 * @author Christian Kohlschütter
 */
public final class AFTIPCErrInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Error code.
   */
  private final ErrorCode errorCode;

  /**
   * The length of the returned data.
   */
  private final int dataLength;

  /**
   * Some TIPC error code.
   *
   * @author Christian Kohlschütter
   */
  @NonNullByDefault
  public static final class ErrorCode extends NamedInteger {
    private static final long serialVersionUID = 1L;

    /**
     * No error.
     */
    public static final ErrorCode TIPC_OK;

    /**
     * Destination port name is unknown.
     */
    public static final ErrorCode TIPC_ERR_NO_NAME;

    /**
     * Destination port id does not exist.
     */
    public static final ErrorCode TIPC_ERR_NO_PORT;

    /**
     * Destination node is unreachable.
     */
    public static final ErrorCode TIPC_ERR_NO_NODE;

    /**
     * Destination is congested.
     */
    public static final ErrorCode TIPC_ERR_OVERLOAD;

    /**
     * Normal connection shutdown occurred.
     */
    public static final ErrorCode TIPC_ERR_CONN_SHUTDOWN;

    private static final @NonNull ErrorCode[] VALUES = init(new @NonNull ErrorCode[] {
        TIPC_OK = new ErrorCode("TIPC_OK", 0), //
        TIPC_ERR_NO_NAME = new ErrorCode("TIPC_ERR_NO_NAME", 1), //
        TIPC_ERR_NO_PORT = new ErrorCode("TIPC_ERR_NO_PORT", 2), //
        TIPC_ERR_NO_NODE = new ErrorCode("TIPC_ERR_NO_NODE", 3), //
        TIPC_ERR_OVERLOAD = new ErrorCode("TIPC_ERR_OVERLOAD", 4), //
        TIPC_ERR_CONN_SHUTDOWN = new ErrorCode("TIPC_ERR_CONN_SHUTDOWN", 5), //
    });

    private ErrorCode(int id) {
      super(id);
    }

    private ErrorCode(String name, int id) {
      super(name, id);
    }

    /**
     * Returns an {@link ErrorCode} instance given an integer.
     *
     * @param v The value.
     * @return The instance.
     */
    public static ErrorCode ofValue(int v) {
      return ofValue(VALUES, ErrorCode::new, v);
    }
  }

  /**
   * Creates a new instance.
   *
   * @param errorCode The error code.
   * @param dataLength The length of the returned data.
   */
  public AFTIPCErrInfo(ErrorCode errorCode, int dataLength) {
    this.errorCode = errorCode;
    this.dataLength = dataLength;
  }

  /**
   * Returns the error code.
   *
   * @return The error code.
   */
  public ErrorCode getErrorCode() {
    return errorCode;
  }

  /**
   * The length of the corresponding data.
   *
   * @return The length in bytes.
   */
  public int getDataLength() {
    return dataLength;
  }

  @Override
  public String toString() {
    return getClass().getName() + "(" + errorCode + ";dataLength=" + dataLength + ")";
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataLength, errorCode);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj == null) {
      return false;
    } else if (getClass() != obj.getClass()) {
      return false;
    }
    AFTIPCErrInfo other = (AFTIPCErrInfo) obj;
    return dataLength == other.dataLength && errorCode.equals(other.errorCode);
  }

  @SuppressWarnings("PMD.ShortMethodName")
  static AFTIPCErrInfo of(int[] tipcErrInfo) {
    if (tipcErrInfo == null) {
      return null;
    }
    if (tipcErrInfo.length != 2) {
      throw new IllegalArgumentException();
    }
    return new AFTIPCErrInfo(ErrorCode.ofValue(tipcErrInfo[0]), tipcErrInfo[1]);
  }
}
