/*
 * Copyright 2020 The gRPC Authors
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

package io.grpc.internal;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.internal.ManagedChannelImplBuilder.ChannelBuilderDefaultPortProvider;
import io.grpc.internal.ManagedChannelImplBuilder.ClientTransportFactoryBuilder;
import io.grpc.internal.ManagedChannelImplBuilder.FixedPortProvider;
import io.grpc.internal.ManagedChannelImplBuilder.UnsupportedClientTransportFactoryBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link ManagedChannelImplBuilder}. */
@RunWith(JUnit4.class)
public class ManagedChannelImplBuilderTest {
  private static final int DUMMY_PORT = 42;
  private static final String DUMMY_TARGET = "fake-target";
  private static final String DUMMY_AUTHORITY_VALID = "valid:1234";
  private static final String DUMMY_AUTHORITY_INVALID = "[ : : 1]";
  private static final ClientInterceptor DUMMY_USER_INTERCEPTOR =
      new ClientInterceptor() {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
          return next.newCall(method, callOptions);
        }
      };

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @SuppressWarnings("deprecation") // https://github.com/grpc/grpc-java/issues/7467
  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Rule public final GrpcCleanupRule grpcCleanupRule = new GrpcCleanupRule();

  @Mock private ClientTransportFactory mockClientTransportFactory;
  @Mock private ClientTransportFactoryBuilder mockClientTransportFactoryBuilder;

  private ManagedChannelImplBuilder builder;
  private ManagedChannelImplBuilder directAddressBuilder;
  private final FakeClock clock = new FakeClock();


  @Before
  public void setUp() throws Exception {
    builder = new ManagedChannelImplBuilder(
        DUMMY_TARGET,
        new UnsupportedClientTransportFactoryBuilder(),
        new FixedPortProvider(DUMMY_PORT));
    directAddressBuilder = new ManagedChannelImplBuilder(
        new SocketAddress() {},
        DUMMY_TARGET,
        new UnsupportedClientTransportFactoryBuilder(),
        new FixedPortProvider(DUMMY_PORT));
  }

  /** Ensure getDefaultPort() returns default port when no custom implementation provided. */
  @Test
  public void getDefaultPort_default() {
    builder = new ManagedChannelImplBuilder(DUMMY_TARGET,
        new UnsupportedClientTransportFactoryBuilder(), null);
    assertEquals(GrpcUtil.DEFAULT_PORT_SSL, builder.getDefaultPort());
  }

  /** Ensure getDefaultPort() delegates to the custom implementation. */
  @Test
  public void getDefaultPort_custom() {
    int customPort = 43;
    ChannelBuilderDefaultPortProvider mockChannelBuilderDefaultPortProvider = mock(
        ChannelBuilderDefaultPortProvider.class);
    when(mockChannelBuilderDefaultPortProvider.getDefaultPort()).thenReturn(customPort);

    builder = new ManagedChannelImplBuilder(DUMMY_TARGET,
        new UnsupportedClientTransportFactoryBuilder(),
        mockChannelBuilderDefaultPortProvider);
    assertEquals(customPort, builder.getDefaultPort());
    verify(mockChannelBuilderDefaultPortProvider).getDefaultPort();
  }

  /** Test FixedPortProvider(int port). */
  @Test
  public void getDefaultPort_fixedPortProvider() {
    int fixedPort = 43;
    builder = new ManagedChannelImplBuilder(DUMMY_TARGET,
        new UnsupportedClientTransportFactoryBuilder(), new FixedPortProvider(fixedPort));
    assertEquals(fixedPort, builder.getDefaultPort());
  }

  @Test
  public void executor_default() {
    assertNotNull(builder.executorPool);
  }

  @Test
  public void executor_normal() {
    Executor executor = mock(Executor.class);
    assertEquals(builder, builder.executor(executor));
    assertEquals(executor, builder.executorPool.getObject());
  }

  @Test
  public void executor_null() {
    ObjectPool<? extends Executor> defaultValue = builder.executorPool;
    builder.executor(mock(Executor.class));
    assertEquals(builder, builder.executor(null));
    assertEquals(defaultValue, builder.executorPool);
  }

  @Test
  public void directExecutor() {
    assertEquals(builder, builder.directExecutor());
    assertEquals(MoreExecutors.directExecutor(), builder.executorPool.getObject());
  }

  @Test
  public void offloadExecutor_normal() {
    Executor executor = mock(Executor.class);
    assertEquals(builder, builder.offloadExecutor(executor));
    assertEquals(executor, builder.offloadExecutorPool.getObject());
  }

  @Test
  public void offloadExecutor_null() {
    ObjectPool<? extends Executor> defaultValue = builder.offloadExecutorPool;
    builder.offloadExecutor(mock(Executor.class));
    assertEquals(builder, builder.offloadExecutor(null));
    assertEquals(defaultValue, builder.offloadExecutorPool);
  }

  @Test
  public void nameResolverFactory_default() {
    assertNotNull(builder.getNameResolverFactory());
  }

  @Test
  @SuppressWarnings("deprecation")
  public void nameResolverFactory_normal() {
    NameResolver.Factory nameResolverFactory = mock(NameResolver.Factory.class);
    assertEquals(builder, builder.nameResolverFactory(nameResolverFactory));
    assertEquals(nameResolverFactory, builder.getNameResolverFactory());
  }

  @Test
  @SuppressWarnings("deprecation")
  public void nameResolverFactory_null() {
    NameResolver.Factory defaultValue = builder.getNameResolverFactory();
    builder.nameResolverFactory(mock(NameResolver.Factory.class));
    assertEquals(builder, builder.nameResolverFactory(null));
    assertEquals(defaultValue, builder.getNameResolverFactory());
  }

  @Test(expected = IllegalStateException.class)
  @SuppressWarnings("deprecation")
  public void nameResolverFactory_notAllowedWithDirectAddress() {
    directAddressBuilder.nameResolverFactory(mock(NameResolver.Factory.class));
  }

  @Test
  public void defaultLoadBalancingPolicy_default() {
    assertEquals("pick_first", builder.defaultLbPolicy);
  }

  @Test
  public void defaultLoadBalancingPolicy_normal() {
    assertEquals(builder, builder.defaultLoadBalancingPolicy("magic_balancer"));
    assertEquals("magic_balancer", builder.defaultLbPolicy);
  }

  @Test(expected = IllegalArgumentException.class)
  public void defaultLoadBalancingPolicy_null() {
    builder.defaultLoadBalancingPolicy(null);
  }

  @Test(expected = IllegalStateException.class)
  public void defaultLoadBalancingPolicy_notAllowedWithDirectAddress() {
    directAddressBuilder.defaultLoadBalancingPolicy("magic_balancer");
  }

  @Test
  public void fullStreamDecompression_default() {
    assertFalse(builder.fullStreamDecompression);
  }

  @Test
  public void fullStreamDecompression_enabled() {
    assertEquals(builder, builder.enableFullStreamDecompression());
    assertTrue(builder.fullStreamDecompression);
  }

  @Test
  public void decompressorRegistry_default() {
    assertNotNull(builder.decompressorRegistry);
  }

  @Test
  public void decompressorRegistry_normal() {
    DecompressorRegistry decompressorRegistry = DecompressorRegistry.emptyInstance();
    assertNotEquals(decompressorRegistry, builder.decompressorRegistry);
    assertEquals(builder, builder.decompressorRegistry(decompressorRegistry));
    assertEquals(decompressorRegistry, builder.decompressorRegistry);
  }

  @Test
  public void decompressorRegistry_null() {
    DecompressorRegistry defaultValue = builder.decompressorRegistry;
    assertEquals(builder, builder.decompressorRegistry(DecompressorRegistry.emptyInstance()));
    assertNotEquals(defaultValue, builder.decompressorRegistry);
    builder.decompressorRegistry(null);
    assertEquals(defaultValue, builder.decompressorRegistry);
  }

  @Test
  public void compressorRegistry_default() {
    assertNotNull(builder.compressorRegistry);
  }

  @Test
  public void compressorRegistry_normal() {
    CompressorRegistry compressorRegistry = CompressorRegistry.newEmptyInstance();
    assertNotEquals(compressorRegistry, builder.compressorRegistry);
    assertEquals(builder, builder.compressorRegistry(compressorRegistry));
    assertEquals(compressorRegistry, builder.compressorRegistry);
  }

  @Test
  public void compressorRegistry_null() {
    CompressorRegistry defaultValue = builder.compressorRegistry;
    builder.compressorRegistry(CompressorRegistry.newEmptyInstance());
    assertNotEquals(defaultValue, builder.compressorRegistry);
    assertEquals(builder, builder.compressorRegistry(null));
    assertEquals(defaultValue, builder.compressorRegistry);
  }

  @Test
  public void userAgent_default() {
    assertNull(builder.userAgent);
  }

  @Test
  public void userAgent_normal() {
    String userAgent = "user-agent/1";
    assertEquals(builder, builder.userAgent(userAgent));
    assertEquals(userAgent, builder.userAgent);
  }

  @Test
  public void userAgent_null() {
    assertEquals(builder, builder.userAgent(null));
    assertNull(builder.userAgent);

    builder.userAgent("user-agent/1");
    builder.userAgent(null);
    assertNull(builder.userAgent);
  }

  @Test
  public void authorityIsReadable_default() {
    when(mockClientTransportFactory.getScheduledExecutorService())
        .thenReturn(clock.getScheduledExecutorService());
    when(mockClientTransportFactoryBuilder.buildClientTransportFactory())
        .thenReturn(mockClientTransportFactory);

    builder = new ManagedChannelImplBuilder(DUMMY_AUTHORITY_VALID,
        mockClientTransportFactoryBuilder, new FixedPortProvider(DUMMY_PORT));
    ManagedChannel channel = grpcCleanupRule.register(builder.build());
    assertEquals(DUMMY_AUTHORITY_VALID, channel.authority());
  }

  @Test
  public void authorityIsReadable_overrideAuthority() {
    String overrideAuthority = "best-authority";
    when(mockClientTransportFactory.getScheduledExecutorService())
        .thenReturn(clock.getScheduledExecutorService());
    when(mockClientTransportFactoryBuilder.buildClientTransportFactory())
        .thenReturn(mockClientTransportFactory);

    builder = new ManagedChannelImplBuilder(DUMMY_TARGET,
        mockClientTransportFactoryBuilder, new FixedPortProvider(DUMMY_PORT))
        .overrideAuthority(overrideAuthority);
    ManagedChannel channel = grpcCleanupRule.register(builder.build());
    assertEquals(overrideAuthority, channel.authority());
  }

  @Test
  public void overrideAuthority_default() {
    assertNull(builder.getOverrideAuthority());
  }

  @Test
  public void overrideAuthority_normal() {
    String overrideAuthority = "best-authority";
    assertEquals(builder, builder.overrideAuthority(overrideAuthority));
    assertEquals(overrideAuthority, builder.getOverrideAuthority());
  }

  @Test(expected = NullPointerException.class)
  public void overrideAuthority_null() {
    builder.overrideAuthority(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void overrideAuthority_invalid() {
    builder.overrideAuthority("not_allowed");
  }

  @Test
  public void overrideAuthority_getNameResolverFactory() {
    assertNull(builder.getOverrideAuthority());
    assertFalse(builder.getNameResolverFactory() instanceof OverrideAuthorityNameResolverFactory);
    builder.overrideAuthority("google.com");
    assertTrue(builder.getNameResolverFactory() instanceof OverrideAuthorityNameResolverFactory);
  }

  @Test
  public void checkAuthority_validAuthorityAllowed() {
    assertEquals(DUMMY_AUTHORITY_VALID, builder.checkAuthority(DUMMY_AUTHORITY_VALID));
  }

  @Test
  public void checkAuthority_invalidAuthorityFailed() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid authority");

    builder.checkAuthority(DUMMY_AUTHORITY_INVALID);
  }

  @Test
  public void disableCheckAuthority_validAuthorityAllowed() {
    builder.disableCheckAuthority();
    assertEquals(DUMMY_AUTHORITY_VALID, builder.checkAuthority(DUMMY_AUTHORITY_VALID));
  }

  @Test
  public void disableCheckAuthority_invalidAuthorityAllowed() {
    builder.disableCheckAuthority();
    assertEquals(DUMMY_AUTHORITY_INVALID, builder.checkAuthority(DUMMY_AUTHORITY_INVALID));
  }

  @Test
  public void enableCheckAuthority_validAuthorityAllowed() {
    builder.disableCheckAuthority().enableCheckAuthority();
    assertEquals(DUMMY_AUTHORITY_VALID, builder.checkAuthority(DUMMY_AUTHORITY_VALID));
  }

  @Test
  public void disableCheckAuthority_invalidAuthorityFailed() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid authority");

    builder.disableCheckAuthority().enableCheckAuthority();
    builder.checkAuthority(DUMMY_AUTHORITY_INVALID);
  }

  @Test
  public void makeTargetStringForDirectAddress_scopedIpv6() throws Exception {
    InetSocketAddress address = new InetSocketAddress("0:0:0:0:0:0:0:0%0", 10005);
    assertEquals("/0:0:0:0:0:0:0:0%0:10005", address.toString());
    String target = ManagedChannelImplBuilder.makeTargetStringForDirectAddress(address);
    URI uri = new URI(target);
    assertEquals("directaddress:////0:0:0:0:0:0:0:0%250:10005", target);
    assertEquals(target, uri.toString());
  }

  @Test
  public void getEffectiveInterceptors_default() {
    builder.intercept(DUMMY_USER_INTERCEPTOR);
    List<ClientInterceptor> effectiveInterceptors = builder.getEffectiveInterceptors();
    assertEquals(3, effectiveInterceptors.size());
    assertThat(effectiveInterceptors.get(0).getClass().getName())
        .isEqualTo("io.grpc.census.CensusTracingModule$TracingClientInterceptor");
    assertThat(effectiveInterceptors.get(1).getClass().getName())
        .isEqualTo("io.grpc.census.CensusStatsModule$StatsClientInterceptor");
    assertThat(effectiveInterceptors.get(2)).isSameInstanceAs(DUMMY_USER_INTERCEPTOR);
  }

  @Test
  public void getEffectiveInterceptors_disableStats() {
    builder.intercept(DUMMY_USER_INTERCEPTOR);
    builder.setStatsEnabled(false);
    List<ClientInterceptor> effectiveInterceptors = builder.getEffectiveInterceptors();
    assertEquals(2, effectiveInterceptors.size());
    assertThat(effectiveInterceptors.get(0).getClass().getName())
        .isEqualTo("io.grpc.census.CensusTracingModule$TracingClientInterceptor");
    assertThat(effectiveInterceptors.get(1)).isSameInstanceAs(DUMMY_USER_INTERCEPTOR);
  }

  @Test
  public void getEffectiveInterceptors_disableTracing() {
    builder.intercept(DUMMY_USER_INTERCEPTOR);
    builder.setTracingEnabled(false);
    List<ClientInterceptor> effectiveInterceptors = builder.getEffectiveInterceptors();
    assertEquals(2, effectiveInterceptors.size());
    assertThat(effectiveInterceptors.get(0).getClass().getName())
        .isEqualTo("io.grpc.census.CensusStatsModule$StatsClientInterceptor");
    assertThat(effectiveInterceptors.get(1)).isSameInstanceAs(DUMMY_USER_INTERCEPTOR);
  }

  @Test
  public void getEffectiveInterceptors_disableBoth() {
    builder.intercept(DUMMY_USER_INTERCEPTOR);
    builder.setStatsEnabled(false);
    builder.setTracingEnabled(false);
    List<ClientInterceptor> effectiveInterceptors = builder.getEffectiveInterceptors();
    assertThat(effectiveInterceptors).containsExactly(DUMMY_USER_INTERCEPTOR);
  }

  @Test
  public void idleTimeout() {
    assertEquals(ManagedChannelImplBuilder.IDLE_MODE_DEFAULT_TIMEOUT_MILLIS,
        builder.idleTimeoutMillis);

    builder.idleTimeout(Long.MAX_VALUE, TimeUnit.DAYS);
    assertEquals(ManagedChannelImpl.IDLE_TIMEOUT_MILLIS_DISABLE, builder.idleTimeoutMillis);

    builder.idleTimeout(ManagedChannelImplBuilder.IDLE_MODE_MAX_TIMEOUT_DAYS,
        TimeUnit.DAYS);
    assertEquals(ManagedChannelImpl.IDLE_TIMEOUT_MILLIS_DISABLE, builder.idleTimeoutMillis);

    try {
      builder.idleTimeout(0, TimeUnit.SECONDS);
      fail("Should throw");
    } catch (IllegalArgumentException e) {
      // expected
    }

    builder.idleTimeout(1, TimeUnit.NANOSECONDS);
    assertEquals(ManagedChannelImplBuilder.IDLE_MODE_MIN_TIMEOUT_MILLIS,
        builder.idleTimeoutMillis);

    builder.idleTimeout(30, TimeUnit.SECONDS);
    assertEquals(TimeUnit.SECONDS.toMillis(30), builder.idleTimeoutMillis);
  }

  @Test
  public void maxRetryAttempts() {
    assertEquals(5, builder.maxRetryAttempts);

    builder.maxRetryAttempts(3);
    assertEquals(3, builder.maxRetryAttempts);
  }

  @Test
  public void maxHedgedAttempts() {
    assertEquals(5, builder.maxHedgedAttempts);

    builder.maxHedgedAttempts(3);
    assertEquals(3, builder.maxHedgedAttempts);
  }

  @Test
  public void retryBufferSize() {
    assertEquals(1L << 24, builder.retryBufferSize);

    builder.retryBufferSize(3456L);
    assertEquals(3456L, builder.retryBufferSize);
  }

  @Test
  public void perRpcBufferLimit() {
    assertEquals(1L << 20, builder.perRpcBufferLimit);

    builder.perRpcBufferLimit(3456L);
    assertEquals(3456L, builder.perRpcBufferLimit);
  }

  @Test
  public void retryBufferSizeInvalidArg() {
    thrown.expect(IllegalArgumentException.class);
    builder.retryBufferSize(0L);
  }

  @Test
  public void perRpcBufferLimitInvalidArg() {
    thrown.expect(IllegalArgumentException.class);
    builder.perRpcBufferLimit(0L);
  }

  @Test
  public void disableRetry() {
    builder.enableRetry();
    assertTrue(builder.retryEnabled);

    builder.disableRetry();
    assertFalse(builder.retryEnabled);

    builder.enableRetry();
    assertTrue(builder.retryEnabled);

    builder.disableRetry();
    assertFalse(builder.retryEnabled);
  }

  @Test
  public void defaultServiceConfig_nullKey() {
    Map<String, Object> config = new HashMap<>();
    config.put(null, "val");

    thrown.expect(IllegalArgumentException.class);
    builder.defaultServiceConfig(config);
  }

  @Test
  public void defaultServiceConfig_intKey() {
    Map<Integer, Object> subConfig = new HashMap<>();
    subConfig.put(3, "val");
    Map<String, Object> config = new HashMap<>();
    config.put("key", subConfig);

    thrown.expect(IllegalArgumentException.class);
    builder.defaultServiceConfig(config);
  }

  @Test
  public void defaultServiceConfig_intValue() {
    Map<String, Object> config = new HashMap<>();
    config.put("key", 3);

    thrown.expect(IllegalArgumentException.class);
    builder.defaultServiceConfig(config);
  }

  @Test
  public void defaultServiceConfig_nested() {
    Map<String, Object> config = new HashMap<>();
    List<Object> list1 = new ArrayList<>();
    list1.add(123D);
    list1.add(null);
    list1.add(true);
    list1.add("str");
    Map<String, Object> map2 = new HashMap<>();
    map2.put("key2", false);
    map2.put("key3", null);
    map2.put("key4", Collections.singletonList("v4"));
    map2.put("key4", 3.14D);
    map2.put("key5", new HashMap<String, Object>());
    list1.add(map2);
    config.put("key1", list1);

    builder.defaultServiceConfig(config);

    assertThat(builder.defaultServiceConfig).containsExactlyEntriesIn(config);
  }

  @Test
  public void disableNameResolverServiceConfig() {
    assertThat(builder.lookUpServiceConfig).isTrue();

    builder.disableServiceConfigLookUp();
    assertThat(builder.lookUpServiceConfig).isFalse();
  }
}
