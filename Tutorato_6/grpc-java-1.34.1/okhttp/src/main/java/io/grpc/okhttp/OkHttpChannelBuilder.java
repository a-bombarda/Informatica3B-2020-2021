/*
 * Copyright 2014 The gRPC Authors
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

package io.grpc.okhttp;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.internal.GrpcUtil.DEFAULT_KEEPALIVE_TIMEOUT_NANOS;
import static io.grpc.internal.GrpcUtil.KEEPALIVE_TIME_NANOS_DISABLED;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.grpc.CallCredentials;
import io.grpc.ChannelCredentials;
import io.grpc.ChannelLogger;
import io.grpc.ChoiceChannelCredentials;
import io.grpc.CompositeCallCredentials;
import io.grpc.CompositeChannelCredentials;
import io.grpc.ExperimentalApi;
import io.grpc.InsecureChannelCredentials;
import io.grpc.Internal;
import io.grpc.ManagedChannelBuilder;
import io.grpc.TlsChannelCredentials;
import io.grpc.internal.AbstractManagedChannelImplBuilder;
import io.grpc.internal.AtomicBackoff;
import io.grpc.internal.ClientTransportFactory;
import io.grpc.internal.ConnectionClientTransport;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.KeepAliveManager;
import io.grpc.internal.ManagedChannelImplBuilder;
import io.grpc.internal.ManagedChannelImplBuilder.ChannelBuilderDefaultPortProvider;
import io.grpc.internal.ManagedChannelImplBuilder.ClientTransportFactoryBuilder;
import io.grpc.internal.SharedResourceHolder;
import io.grpc.internal.SharedResourceHolder.Resource;
import io.grpc.internal.TransportTracer;
import io.grpc.okhttp.internal.CipherSuite;
import io.grpc.okhttp.internal.ConnectionSpec;
import io.grpc.okhttp.internal.Platform;
import io.grpc.okhttp.internal.TlsVersion;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/** Convenience class for building channels with the OkHttp transport. */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/1785")
public final class OkHttpChannelBuilder extends
    AbstractManagedChannelImplBuilder<OkHttpChannelBuilder> {

  public static final int DEFAULT_FLOW_CONTROL_WINDOW = 65535;
  private final ManagedChannelImplBuilder managedChannelImplBuilder;
  private TransportTracer.Factory transportTracerFactory = TransportTracer.getDefaultFactory();


  /** Identifies the negotiation used for starting up HTTP/2. */
  private enum NegotiationType {
    /** Uses TLS ALPN/NPN negotiation, assumes an SSL connection. */
    TLS,

    /**
     * Just assume the connection is plaintext (non-SSL) and the remote endpoint supports HTTP/2
     * directly without an upgrade.
     */
    PLAINTEXT
  }

  @VisibleForTesting
  static final ConnectionSpec INTERNAL_DEFAULT_CONNECTION_SPEC =
      new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
          .cipherSuites(
              // The following items should be sync with Netty's Http2SecurityUtil.CIPHERS.
              CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
              CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
              CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
              CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
              CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
              CipherSuite.TLS_DHE_DSS_WITH_AES_128_GCM_SHA256,
              CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
              CipherSuite.TLS_DHE_DSS_WITH_AES_256_GCM_SHA384)
          .tlsVersions(TlsVersion.TLS_1_2)
          .supportsTlsExtensions(true)
          .build();

  private static final long AS_LARGE_AS_INFINITE = TimeUnit.DAYS.toNanos(1000L);
  private static final Resource<Executor> SHARED_EXECUTOR =
      new Resource<Executor>() {
        @Override
        public Executor create() {
          return Executors.newCachedThreadPool(GrpcUtil.getThreadFactory("grpc-okhttp-%d", true));
        }

        @Override
        public void close(Executor executor) {
          ((ExecutorService) executor).shutdown();
        }
      };

  /** Creates a new builder for the given server host and port. */
  public static OkHttpChannelBuilder forAddress(String host, int port) {
    return new OkHttpChannelBuilder(host, port);
  }

  /** Creates a new builder with the given host and port. */
  public static OkHttpChannelBuilder forAddress(String host, int port, ChannelCredentials creds) {
    return forTarget(GrpcUtil.authorityFromHostAndPort(host, port), creds);
  }

  /**
   * Creates a new builder for the given target that will be resolved by
   * {@link io.grpc.NameResolver}.
   */
  public static OkHttpChannelBuilder forTarget(String target) {
    return new OkHttpChannelBuilder(target);
  }

  /**
   * Creates a new builder for the given target that will be resolved by
   * {@link io.grpc.NameResolver}.
   */
  public static OkHttpChannelBuilder forTarget(String target, ChannelCredentials creds) {
    SslSocketFactoryResult result = sslSocketFactoryFrom(creds);
    if (result.error != null) {
      throw new IllegalArgumentException(result.error);
    }
    return new OkHttpChannelBuilder(target, result.factory, result.callCredentials);
  }

  private Executor transportExecutor;
  private ScheduledExecutorService scheduledExecutorService;

  private SocketFactory socketFactory;
  private SSLSocketFactory sslSocketFactory;
  private final boolean freezeSecurityConfiguration;
  private HostnameVerifier hostnameVerifier;
  private ConnectionSpec connectionSpec = INTERNAL_DEFAULT_CONNECTION_SPEC;
  private NegotiationType negotiationType = NegotiationType.TLS;
  private long keepAliveTimeNanos = KEEPALIVE_TIME_NANOS_DISABLED;
  private long keepAliveTimeoutNanos = DEFAULT_KEEPALIVE_TIMEOUT_NANOS;
  private int flowControlWindow = DEFAULT_FLOW_CONTROL_WINDOW;
  private boolean keepAliveWithoutCalls;
  private int maxInboundMessageSize = GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE;
  private int maxInboundMetadataSize = Integer.MAX_VALUE;

  /**
   * If true, indicates that the transport may use the GET method for RPCs, and may include the
   * request body in the query params.
   */
  private final boolean useGetForSafeMethods = false;

  private OkHttpChannelBuilder(String host, int port) {
    this(GrpcUtil.authorityFromHostAndPort(host, port));
  }

  private OkHttpChannelBuilder(String target) {
    managedChannelImplBuilder = new ManagedChannelImplBuilder(target,
        new OkHttpChannelTransportFactoryBuilder(),
        new OkHttpChannelDefaultPortProvider());
    this.freezeSecurityConfiguration = false;
  }

  OkHttpChannelBuilder(String target, @Nullable SSLSocketFactory factory,
      @Nullable CallCredentials callCredentials) {
    managedChannelImplBuilder = new ManagedChannelImplBuilder(target, callCredentials,
        new OkHttpChannelTransportFactoryBuilder(),
        new OkHttpChannelDefaultPortProvider());
    this.sslSocketFactory = factory;
    this.negotiationType = factory == null ? NegotiationType.PLAINTEXT : NegotiationType.TLS;
    this.freezeSecurityConfiguration = true;
  }

  private final class OkHttpChannelTransportFactoryBuilder
      implements ClientTransportFactoryBuilder {
    @Override
    public ClientTransportFactory buildClientTransportFactory() {
      return buildTransportFactory();
    }
  }

  private final class OkHttpChannelDefaultPortProvider
      implements ChannelBuilderDefaultPortProvider {
    @Override
    public int getDefaultPort() {
      return OkHttpChannelBuilder.this.getDefaultPort();
    }
  }

  @Internal
  @Override
  protected ManagedChannelBuilder<?> delegate() {
    return managedChannelImplBuilder;
  }

  @VisibleForTesting
  OkHttpChannelBuilder setTransportTracerFactory(TransportTracer.Factory transportTracerFactory) {
    this.transportTracerFactory = transportTracerFactory;
    return this;
  }

  /**
   * Override the default executor necessary for internal transport use.
   *
   * <p>The channel does not take ownership of the given executor. It is the caller' responsibility
   * to shutdown the executor when appropriate.
   */
  public OkHttpChannelBuilder transportExecutor(@Nullable Executor transportExecutor) {
    this.transportExecutor = transportExecutor;
    return this;
  }

  /**
   * Override the default {@link SocketFactory} used to create sockets. If the socket factory is not
   * set or set to null, a default one will be used.
   *
   * @since 1.20.0
   */
  public OkHttpChannelBuilder socketFactory(@Nullable SocketFactory socketFactory) {
    this.socketFactory = socketFactory;
    return this;
  }

  /**
   * Sets the negotiation type for the HTTP/2 connection.
   *
   * <p>If TLS is enabled a default {@link SSLSocketFactory} is created using the best
   * {@link java.security.Provider} available and is NOT based on
   * {@link SSLSocketFactory#getDefault}. To more precisely control the TLS configuration call
   * {@link #sslSocketFactory} to override the socket factory used.
   *
   * <p>Default: <code>TLS</code>
   *
   * @deprecated use {@link #usePlaintext()} or {@link #useTransportSecurity()} instead.
   */
  @Deprecated
  public OkHttpChannelBuilder negotiationType(io.grpc.okhttp.NegotiationType type) {
    Preconditions.checkState(!freezeSecurityConfiguration,
        "Cannot change security when using ChannelCredentials");
    Preconditions.checkNotNull(type, "type");
    switch (type) {
      case TLS:
        negotiationType = NegotiationType.TLS;
        break;
      case PLAINTEXT:
        negotiationType = NegotiationType.PLAINTEXT;
        break;
      default:
        throw new AssertionError("Unknown negotiation type: " + type);
    }
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 1.3.0
   */
  @Override
  public OkHttpChannelBuilder keepAliveTime(long keepAliveTime, TimeUnit timeUnit) {
    Preconditions.checkArgument(keepAliveTime > 0L, "keepalive time must be positive");
    keepAliveTimeNanos = timeUnit.toNanos(keepAliveTime);
    keepAliveTimeNanos = KeepAliveManager.clampKeepAliveTimeInNanos(keepAliveTimeNanos);
    if (keepAliveTimeNanos >= AS_LARGE_AS_INFINITE) {
      // Bump keepalive time to infinite. This disables keepalive.
      keepAliveTimeNanos = KEEPALIVE_TIME_NANOS_DISABLED;
    }
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 1.3.0
   */
  @Override
  public OkHttpChannelBuilder keepAliveTimeout(long keepAliveTimeout, TimeUnit timeUnit) {
    Preconditions.checkArgument(keepAliveTimeout > 0L, "keepalive timeout must be positive");
    keepAliveTimeoutNanos = timeUnit.toNanos(keepAliveTimeout);
    keepAliveTimeoutNanos = KeepAliveManager.clampKeepAliveTimeoutInNanos(keepAliveTimeoutNanos);
    return this;
  }

  /**
   * Sets the flow control window in bytes. If not called, the default value
   * is {@link #DEFAULT_FLOW_CONTROL_WINDOW}).
   */
  public OkHttpChannelBuilder flowControlWindow(int flowControlWindow) {
    Preconditions.checkState(flowControlWindow > 0, "flowControlWindow must be positive");
    this.flowControlWindow = flowControlWindow;
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 1.3.0
   * @see #keepAliveTime(long, TimeUnit)
   */
  @Override
  public OkHttpChannelBuilder keepAliveWithoutCalls(boolean enable) {
    keepAliveWithoutCalls = enable;
    return this;
  }

  /**
   * Override the default {@link SSLSocketFactory} and enable TLS negotiation.
   */
  public OkHttpChannelBuilder sslSocketFactory(SSLSocketFactory factory) {
    Preconditions.checkState(!freezeSecurityConfiguration,
        "Cannot change security when using ChannelCredentials");
    this.sslSocketFactory = factory;
    negotiationType = NegotiationType.TLS;
    return this;
  }

  /**
   * Set the hostname verifier to use when using TLS negotiation. The hostnameVerifier is only used
   * if using TLS negotiation. If the hostname verifier is not set, a default hostname verifier is
   * used.
   *
   * <p>Be careful when setting a custom hostname verifier! By setting a non-null value, you are
   * replacing all default verification behavior. If the hostname verifier you supply does not
   * effectively supply the same checks, you may be removing the security assurances that TLS aims
   * to provide.</p>
   *
   * <p>This method should not be used to avoid hostname verification, even during testing, since
   * {@link #overrideAuthority} is a safer alternative as it does not disable any security checks.
   * </p>
   *
   * @see io.grpc.okhttp.internal.OkHostnameVerifier
   *
   * @since 1.6.0
   * @return this
   *
   */
  public OkHttpChannelBuilder hostnameVerifier(@Nullable HostnameVerifier hostnameVerifier) {
    Preconditions.checkState(!freezeSecurityConfiguration,
        "Cannot change security when using ChannelCredentials");
    this.hostnameVerifier = hostnameVerifier;
    return this;
  }

  /**
   * For secure connection, provides a ConnectionSpec to specify Cipher suite and
   * TLS versions.
   *
   * <p>By default a modern, HTTP/2-compatible spec will be used.
   *
   * <p>This method is only used when building a secure connection. For plaintext
   * connection, use {@link #usePlaintext()} instead.
   *
   * @throws IllegalArgumentException
   *         If {@code connectionSpec} is not with TLS
   */
  public OkHttpChannelBuilder connectionSpec(
      com.squareup.okhttp.ConnectionSpec connectionSpec) {
    Preconditions.checkState(!freezeSecurityConfiguration,
        "Cannot change security when using ChannelCredentials");
    Preconditions.checkArgument(connectionSpec.isTls(), "plaintext ConnectionSpec is not accepted");
    this.connectionSpec = Utils.convertSpec(connectionSpec);
    return this;
  }

  /** Sets the negotiation type for the HTTP/2 connection to plaintext. */
  @Override
  public OkHttpChannelBuilder usePlaintext() {
    Preconditions.checkState(!freezeSecurityConfiguration,
        "Cannot change security when using ChannelCredentials");
    negotiationType = NegotiationType.PLAINTEXT;
    return this;
  }

  /**
   * Sets the negotiation type for the HTTP/2 connection to TLS (this is the default).
   *
   * <p>With TLS enabled, a default {@link SSLSocketFactory} is created using the best {@link
   * java.security.Provider} available and is NOT based on {@link SSLSocketFactory#getDefault}. To
   * more precisely control the TLS configuration call {@link #sslSocketFactory(SSLSocketFactory)}
   * to override the socket factory used.
   */
  @Override
  public OkHttpChannelBuilder useTransportSecurity() {
    Preconditions.checkState(!freezeSecurityConfiguration,
        "Cannot change security when using ChannelCredentials");
    negotiationType = NegotiationType.TLS;
    return this;
  }

  /**
   * Provides a custom scheduled executor service.
   *
   * <p>It's an optional parameter. If the user has not provided a scheduled executor service when
   * the channel is built, the builder will use a static cached thread pool.
   *
   * @return this
   *
   * @since 1.11.0
   */
  public OkHttpChannelBuilder scheduledExecutorService(
      ScheduledExecutorService scheduledExecutorService) {
    this.scheduledExecutorService =
        checkNotNull(scheduledExecutorService, "scheduledExecutorService");
    return this;
  }

  /**
   * Sets the maximum size of metadata allowed to be received. {@code Integer.MAX_VALUE} disables
   * the enforcement. Defaults to no limit ({@code Integer.MAX_VALUE}).
   *
   * <p>The implementation does not currently limit memory usage; this value is checked only after
   * the metadata is decoded from the wire. It does prevent large metadata from being passed to the
   * application.
   *
   * @param bytes the maximum size of received metadata
   * @return this
   * @throws IllegalArgumentException if bytes is non-positive
   * @since 1.17.0
   */
  @Override
  public OkHttpChannelBuilder maxInboundMetadataSize(int bytes) {
    Preconditions.checkArgument(bytes > 0, "maxInboundMetadataSize must be > 0");
    this.maxInboundMetadataSize = bytes;
    return this;
  }

  /**
   * Sets the maximum message size allowed for a single gRPC frame. If an inbound messages
   * larger than this limit is received it will not be processed and the RPC will fail with
   * RESOURCE_EXHAUSTED.
   */
  @Override
  public OkHttpChannelBuilder maxInboundMessageSize(int max) {
    Preconditions.checkArgument(max >= 0, "negative max");
    maxInboundMessageSize = max;
    return this;
  }

  ClientTransportFactory buildTransportFactory() {
    boolean enableKeepAlive = keepAliveTimeNanos != KEEPALIVE_TIME_NANOS_DISABLED;
    return new OkHttpTransportFactory(
        transportExecutor,
        scheduledExecutorService,
        socketFactory,
        createSslSocketFactory(),
        hostnameVerifier,
        connectionSpec,
        maxInboundMessageSize,
        enableKeepAlive,
        keepAliveTimeNanos,
        keepAliveTimeoutNanos,
        flowControlWindow,
        keepAliveWithoutCalls,
        maxInboundMetadataSize,
        transportTracerFactory,
        useGetForSafeMethods);
  }

  OkHttpChannelBuilder disableCheckAuthority() {
    this.managedChannelImplBuilder.disableCheckAuthority();
    return this;
  }

  OkHttpChannelBuilder enableCheckAuthority() {
    this.managedChannelImplBuilder.enableCheckAuthority();
    return this;
  }

  int getDefaultPort() {
    switch (negotiationType) {
      case PLAINTEXT:
        return GrpcUtil.DEFAULT_PORT_PLAINTEXT;
      case TLS:
        return GrpcUtil.DEFAULT_PORT_SSL;
      default:
        throw new AssertionError(negotiationType + " not handled");
    }
  }

  void setStatsEnabled(boolean value) {
    this.managedChannelImplBuilder.setStatsEnabled(value);
  }

  @VisibleForTesting
  @Nullable
  SSLSocketFactory createSslSocketFactory() {
    switch (negotiationType) {
      case TLS:
        try {
          if (sslSocketFactory == null) {
            SSLContext sslContext = SSLContext.getInstance("Default", Platform.get().getProvider());
            sslSocketFactory = sslContext.getSocketFactory();
          }
          return sslSocketFactory;
        } catch (GeneralSecurityException gse) {
          throw new RuntimeException("TLS Provider failure", gse);
        }
      case PLAINTEXT:
        return null;
      default:
        throw new RuntimeException("Unknown negotiation type: " + negotiationType);
    }
  }

  private static final EnumSet<TlsChannelCredentials.Feature> understoodTlsFeatures =
      EnumSet.noneOf(TlsChannelCredentials.Feature.class);

  static SslSocketFactoryResult sslSocketFactoryFrom(ChannelCredentials creds) {
    if (creds instanceof TlsChannelCredentials) {
      TlsChannelCredentials tlsCreds = (TlsChannelCredentials) creds;
      Set<TlsChannelCredentials.Feature> incomprehensible =
          tlsCreds.incomprehensible(understoodTlsFeatures);
      if (!incomprehensible.isEmpty()) {
        return SslSocketFactoryResult.error(
            "TLS features not understood: " + incomprehensible);
      }
      SSLSocketFactory sslSocketFactory;
      try {
        SSLContext sslContext = SSLContext.getInstance("Default", Platform.get().getProvider());
        sslSocketFactory = sslContext.getSocketFactory();
      } catch (GeneralSecurityException gse) {
        throw new RuntimeException("TLS Provider failure", gse);
      }
      return SslSocketFactoryResult.factory(sslSocketFactory);

    } else if (creds instanceof InsecureChannelCredentials) {
      return SslSocketFactoryResult.plaintext();

    } else if (creds instanceof CompositeChannelCredentials) {
      CompositeChannelCredentials compCreds = (CompositeChannelCredentials) creds;
      return sslSocketFactoryFrom(compCreds.getChannelCredentials())
          .withCallCredentials(compCreds.getCallCredentials());

    } else if (creds instanceof SslSocketFactoryChannelCredentials.ChannelCredentials) {
      SslSocketFactoryChannelCredentials.ChannelCredentials factoryCreds =
          (SslSocketFactoryChannelCredentials.ChannelCredentials) creds;
      return SslSocketFactoryResult.factory(factoryCreds.getFactory());

    } else if (creds instanceof ChoiceChannelCredentials) {
      ChoiceChannelCredentials choiceCreds = (ChoiceChannelCredentials) creds;
      StringBuilder error = new StringBuilder();
      for (ChannelCredentials innerCreds : choiceCreds.getCredentialsList()) {
        SslSocketFactoryResult result = sslSocketFactoryFrom(innerCreds);
        if (result.error == null) {
          return result;
        }
        error.append(", ");
        error.append(result.error);
      }
      return SslSocketFactoryResult.error(error.substring(2));

    } else {
      return SslSocketFactoryResult.error(
          "Unsupported credential type: " + creds.getClass().getName());
    }
  }

  static final class SslSocketFactoryResult {
    /** {@code null} implies plaintext if {@code error == null}. */
    public final SSLSocketFactory factory;
    public final CallCredentials callCredentials;
    public final String error;

    private SslSocketFactoryResult(SSLSocketFactory factory, CallCredentials creds, String error) {
      this.factory = factory;
      this.callCredentials = creds;
      this.error = error;
    }

    public static SslSocketFactoryResult error(String error) {
      return new SslSocketFactoryResult(
          null, null, Preconditions.checkNotNull(error, "error"));
    }

    public static SslSocketFactoryResult plaintext() {
      return new SslSocketFactoryResult(null, null, null);
    }

    public static SslSocketFactoryResult factory(
        SSLSocketFactory factory) {
      return new SslSocketFactoryResult(
          Preconditions.checkNotNull(factory, "factory"), null, null);
    }

    public SslSocketFactoryResult withCallCredentials(CallCredentials callCreds) {
      Preconditions.checkNotNull(callCreds, "callCreds");
      if (error != null) {
        return this;
      }
      if (this.callCredentials != null) {
        callCreds = new CompositeCallCredentials(this.callCredentials, callCreds);
      }
      return new SslSocketFactoryResult(factory, callCreds, null);
    }
  }


  /**
   * Creates OkHttp transports. Exposed for internal use, as it should be private.
   */
  @Internal
  static final class OkHttpTransportFactory implements ClientTransportFactory {
    private final Executor executor;
    private final boolean usingSharedExecutor;
    private final boolean usingSharedScheduler;
    private final TransportTracer.Factory transportTracerFactory;
    private final SocketFactory socketFactory;
    @Nullable private final SSLSocketFactory sslSocketFactory;
    @Nullable
    private final HostnameVerifier hostnameVerifier;
    private final ConnectionSpec connectionSpec;
    private final int maxMessageSize;
    private final boolean enableKeepAlive;
    private final AtomicBackoff keepAliveTimeNanos;
    private final long keepAliveTimeoutNanos;
    private final int flowControlWindow;
    private final boolean keepAliveWithoutCalls;
    private final int maxInboundMetadataSize;
    private final ScheduledExecutorService timeoutService;
    private final boolean useGetForSafeMethods;
    private boolean closed;

    private OkHttpTransportFactory(
        Executor executor,
        @Nullable ScheduledExecutorService timeoutService,
        @Nullable SocketFactory socketFactory,
        @Nullable SSLSocketFactory sslSocketFactory,
        @Nullable HostnameVerifier hostnameVerifier,
        ConnectionSpec connectionSpec,
        int maxMessageSize,
        boolean enableKeepAlive,
        long keepAliveTimeNanos,
        long keepAliveTimeoutNanos,
        int flowControlWindow,
        boolean keepAliveWithoutCalls,
        int maxInboundMetadataSize,
        TransportTracer.Factory transportTracerFactory,
        boolean useGetForSafeMethods) {
      usingSharedScheduler = timeoutService == null;
      this.timeoutService = usingSharedScheduler
          ? SharedResourceHolder.get(GrpcUtil.TIMER_SERVICE) : timeoutService;
      this.socketFactory = socketFactory;
      this.sslSocketFactory = sslSocketFactory;
      this.hostnameVerifier = hostnameVerifier;
      this.connectionSpec = connectionSpec;
      this.maxMessageSize = maxMessageSize;
      this.enableKeepAlive = enableKeepAlive;
      this.keepAliveTimeNanos = new AtomicBackoff("keepalive time nanos", keepAliveTimeNanos);
      this.keepAliveTimeoutNanos = keepAliveTimeoutNanos;
      this.flowControlWindow = flowControlWindow;
      this.keepAliveWithoutCalls = keepAliveWithoutCalls;
      this.maxInboundMetadataSize = maxInboundMetadataSize;
      this.useGetForSafeMethods = useGetForSafeMethods;

      usingSharedExecutor = executor == null;
      this.transportTracerFactory =
          Preconditions.checkNotNull(transportTracerFactory, "transportTracerFactory");
      if (usingSharedExecutor) {
        // The executor was unspecified, using the shared executor.
        this.executor = SharedResourceHolder.get(SHARED_EXECUTOR);
      } else {
        this.executor = executor;
      }
    }

    @Override
    public ConnectionClientTransport newClientTransport(
        SocketAddress addr, ClientTransportOptions options, ChannelLogger channelLogger) {
      if (closed) {
        throw new IllegalStateException("The transport factory is closed.");
      }
      final AtomicBackoff.State keepAliveTimeNanosState = keepAliveTimeNanos.getState();
      Runnable tooManyPingsRunnable = new Runnable() {
        @Override
        public void run() {
          keepAliveTimeNanosState.backoff();
        }
      };
      InetSocketAddress inetSocketAddr = (InetSocketAddress) addr;
      // TODO(carl-mastrangelo): Pass channelLogger in.
      OkHttpClientTransport transport = new OkHttpClientTransport(
          inetSocketAddr,
          options.getAuthority(),
          options.getUserAgent(),
          options.getEagAttributes(),
          executor,
          socketFactory,
          sslSocketFactory,
          hostnameVerifier,
          connectionSpec,
          maxMessageSize,
          flowControlWindow,
          options.getHttpConnectProxiedSocketAddress(),
          tooManyPingsRunnable,
          maxInboundMetadataSize,
          transportTracerFactory.create(),
          useGetForSafeMethods);
      if (enableKeepAlive) {
        transport.enableKeepAlive(
            true, keepAliveTimeNanosState.get(), keepAliveTimeoutNanos, keepAliveWithoutCalls);
      }
      return transport;
    }

    @Override
    public ScheduledExecutorService getScheduledExecutorService() {
      return timeoutService;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;

      if (usingSharedScheduler) {
        SharedResourceHolder.release(GrpcUtil.TIMER_SERVICE, timeoutService);
      }

      if (usingSharedExecutor) {
        SharedResourceHolder.release(SHARED_EXECUTOR, executor);
      }
    }
  }
}
