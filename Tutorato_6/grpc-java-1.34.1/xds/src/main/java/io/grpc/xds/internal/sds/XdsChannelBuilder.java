/*
 * Copyright 2019 The gRPC Authors
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

package io.grpc.xds.internal.sds;

import io.grpc.ForwardingChannelBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.InternalNettyChannelBuilder;
import io.grpc.netty.InternalProtocolNegotiator;
import io.grpc.netty.NettyChannelBuilder;
import java.net.SocketAddress;
import javax.annotation.CheckReturnValue;

/**
 * A version of {@link ManagedChannelBuilder} to create xDS managed channels that will use SDS to
 * set up SSL with peers. Note, this is not ready to use yet.
 */
public final class XdsChannelBuilder extends ForwardingChannelBuilder<XdsChannelBuilder> {

  private final NettyChannelBuilder delegate;
  private InternalProtocolNegotiator.ProtocolNegotiator fallbackProtocolNegotiator;

  private XdsChannelBuilder(NettyChannelBuilder delegate) {
    this.delegate = delegate;
  }

  /**
   * Creates a new builder with the given server address. See {@link
   * NettyChannelBuilder#forAddress(SocketAddress)} for more info.
   */
  @CheckReturnValue
  public static XdsChannelBuilder forAddress(SocketAddress serverAddress) {
    return new XdsChannelBuilder(NettyChannelBuilder.forAddress(serverAddress));
  }

  /**
   * Creates a new builder with the given host and port. See {@link
   * NettyChannelBuilder#forAddress(String, int)} for more info.
   */
  @CheckReturnValue
  public static XdsChannelBuilder forAddress(String host, int port) {
    return new XdsChannelBuilder(NettyChannelBuilder.forAddress(host, port));
  }

  /**
   * Creates a new builder with the given target string. See {@link
   * NettyChannelBuilder#forTarget(String)} for more info.
   */
  @CheckReturnValue
  public static XdsChannelBuilder forTarget(String target) {
    return new XdsChannelBuilder(NettyChannelBuilder.forTarget(target));
  }

  /** Set the fallback protocolNegotiator. Pass null to unset a previously set value. */
  public XdsChannelBuilder fallbackProtocolNegotiator(
          InternalProtocolNegotiator.ProtocolNegotiator fallbackProtocolNegotiator) {
    this.fallbackProtocolNegotiator = fallbackProtocolNegotiator;
    return this;
  }

  @Override
  protected ManagedChannelBuilder<?> delegate() {
    return delegate;
  }

  @Override
  public ManagedChannel build() {
    InternalNettyChannelBuilder.setProtocolNegotiatorFactory(
        delegate,
        SdsProtocolNegotiators.clientProtocolNegotiatorFactory(fallbackProtocolNegotiator));
    return delegate.build();
  }
}
