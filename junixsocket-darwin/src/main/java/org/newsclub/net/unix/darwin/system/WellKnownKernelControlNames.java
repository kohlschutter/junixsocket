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
package org.newsclub.net.unix.darwin.system;

/**
 * Well-known AF_SYSTEM control names.
 *
 * Most of these can only be accessed by the superuser (root). Unless otherwise specified, one
 * usually accesses these as datagrams (SOCK_DGRAM).
 *
 * You can see which control names are registered via {@code netstat -an}; check for the IDs below
 * "Registered kernel control modules".
 *
 * Also see <a href="http://newosxbook.com/bonus/vol1ch16.html">New OSX Book Volume 1 Chapter 16</a>
 *
 * @author Christian Kohlschütter
 */
public enum WellKnownKernelControlNames {

  /**
   * {@code com.apple.netsrc}. Network/route policies and statistics.
   *
   * Available to non-root.
   *
   * See https://github.com/apple-oss-distributions/xnu/blob/main/bsd/net/netsrc.h and
   * https://github.com/apple-oss-distributions/xnu/blob/main/bsd/net/netsrc.c, as well as
   * https://github.com/appleopen/Libinfo/blob/master/lookup.subproj/si_compare.c
   */
  NETSRC("com.apple.netsrc"), //

  /**
   * {@code com.apple.network.statistics}. Live socket statistics and notifications.
   *
   * Available to non-root.
   *
   * See {@code http://newosxbook.com/src.jl?tree=listings&file=lsock.c} as well as
   * https://github.com/packetzero/libntstat
   */
  NETWORK_STATISTICS("com.apple.network.statistics"), //

  /**
   * {@code com.apple.flow-divert}. MPTCP flow diversions (XNU-2422).
   *
   * See https://github.com/apple-oss-distributions/xnu/blob/main/bsd/netinet/flow_divert.c and
   * https://github.com/apple-oss-distributions/xnu/blob/main/bsd/netinet/flow_divert.h
   */
  FLOW_DIVERT("com.apple.flow-divert"), //

  /**
   * {@code com.apple.net.netagent}.
   */
  NET_NETAGENT("com.apple.net.netagent"), //

  /**
   * {@code com.apple.content-filter}.
   */
  CONTENT_FILTER("com.apple.content-filter"), //

  /**
   * {@code com.apple.net.utun_control}. User-mode tunneling (VPN).
   */
  UTUN_CONTROL("com.apple.net.utun_control"), //

  /**
   * {@code com.apple.net.ipsec_control}.
   */
  IPSEC_CONTROL("com.apple.net.ipsec_control"), //

  /**
   * {@code com.apple.network.tcp_ccdebug}. Requires SOCK_STREAM.
   */
  NETWORK_TCP_CCDEBUG("com.apple.network.tcp_ccdebug"), //

  /**
   * {@code com.apple.network.advisory}.
   */
  NETWORK_ADVISORY("com.apple.network.advisory"), //

  /**
   * {@code com.apple.net.rvi_control}.
   */
  NET_RVI_CONTROL("com.apple.net.rvi_control"), //

  /**
   * {@code com.apple.nke.sockwall}.
   */
  NKE_SOCKWALL("com.apple.nke.sockwall"), //

  /**
   * {@code com.apple.spmi.nfc}. Available to non-root. Requires SOCK_STREAM.
   */
  SPMI_NFC("com.apple.spmi.nfc"), //

  /**
   * {@code com.apple.packet-mangler}.
   */
  PACKET_MANGLER("com.apple.packet-mangler"), //

  /**
   * {@code com.apple.net.necp_control}.
   */
  NECP_CONTROL("com.apple.net.necp_control"), //

  /**
   * {@code com.apple.fileutil.kext.stateful.ctl}.
   */
  FILEUTIL_KEXT_STATEFUL_CTL("com.apple.fileutil.kext.stateful.ctl"), //

  /**
   * {@code com.apple.fileutil.kext.stateless.ctl}.
   */
  FILEUTIL_KEXT_STATELESS_CTL("com.apple.fileutil.kext.stateless.ctl"), //

  /**
   * {@code com.apple.mcx.kernctl.alr}.
   */
  MCX_KERNCTL_ALR("com.apple.mcx.kernctl.alr"), //

  /**
   * {@code com.apple.nke.webcontentfilter}.
   */
  NKE_WEBCONTENTFILTER("com.apple.nke.webcontentfilter"), //

  /**
   * {@code com.apple.uart.wlan-debug}.
   */
  UART_WLAN_DEBUG("com.apple.uart.wlan-debug"), //

  /**
   * {@code com.apple.uart.sk.wlan-debug}.
   */
  UART_SK_WLAN_DEBUG("com.apple.uart.sk.wlan-debug"), //

  /**
   * {@code com.apple.uart.debug-console}.
   */
  UART_DEBUG_CONSOLE("com.apple.uart.debug-console"), //

  /**
   * {@code com.apple.uart.sk.debug-console}.
   */
  UART_SK_DEBUG_CONSOLE("com.apple.uart.sk.debug-console"), //

  /**
   * {@code com.apple.userspace_ethernet}.
   */
  USERSPACE_ETHERNET("com.apple.userspace_ethernet"), //

  ;

  // non-Apple:
  // - com.avira.fac
  // - com.vmware.kext.vmci
  // - com.vmware.kext.vmnet
  // - com.vmware.kext.vmx86

  private final String name;

  WellKnownKernelControlNames(String name) {
    this.name = name;
  }

  /**
   * Returns the control name identifier.
   *
   * @return The name.
   */
  public String getControlName() {
    return name;
  }
}
