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

package io.grpc.xds;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import io.grpc.CallOptions;
import io.grpc.InternalConfigSelector;
import io.grpc.InternalConfigSelector.Result;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.NameResolver;
import io.grpc.NameResolver.ConfigOrError;
import io.grpc.NameResolver.ResolutionResult;
import io.grpc.NameResolver.ServiceConfigParser;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.SynchronizationContext;
import io.grpc.internal.JsonParser;
import io.grpc.internal.JsonUtil;
import io.grpc.internal.ObjectPool;
import io.grpc.internal.PickSubchannelArgsImpl;
import io.grpc.testing.TestMethodDescriptors;
import io.grpc.xds.EnvoyProtoData.ClusterWeight;
import io.grpc.xds.EnvoyProtoData.Route;
import io.grpc.xds.EnvoyProtoData.RouteAction;
import io.grpc.xds.EnvoyProtoData.VirtualHost;
import io.grpc.xds.XdsClient.RdsResourceWatcher;
import io.grpc.xds.XdsNameResolverProvider.XdsClientPoolFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link XdsNameResolver}. */
// TODO(chengyuanzhang): should do tests with ManagedChannelImpl.
@RunWith(JUnit4.class)
public class XdsNameResolverTest {
  private static final String AUTHORITY = "foo.googleapis.com:80";
  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();
  private final SynchronizationContext syncContext = new SynchronizationContext(
      new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
          throw new AssertionError(e);
        }
      });
  private final ServiceConfigParser serviceConfigParser = new ServiceConfigParser() {
    @Override
    public ConfigOrError parseServiceConfig(Map<String, ?> rawServiceConfig) {
      return ConfigOrError.fromConfig(rawServiceConfig);
    }
  };
  private final FakeXdsClientPoolFactory xdsClientPoolFactory = new FakeXdsClientPoolFactory();
  private final String cluster1 = "cluster-foo.googleapis.com";
  private final String cluster2 = "cluster-bar.googleapis.com";
  private final CallInfo call1 = new CallInfo("HelloService", "hi");
  private final CallInfo call2 = new CallInfo("GreetService", "bye");

  @Mock
  private ThreadSafeRandom mockRandom;
  @Mock
  private NameResolver.Listener2 mockListener;
  @Captor
  private ArgumentCaptor<ResolutionResult> resolutionResultCaptor;
  @Captor
  ArgumentCaptor<Status> errorCaptor;
  private XdsNameResolver resolver;

  @Before
  public void setUp() {
    XdsNameResolver.enableTimeout = true;
    resolver = new XdsNameResolver(AUTHORITY, serviceConfigParser, syncContext,
        xdsClientPoolFactory, mockRandom);
  }

  @After
  public void tearDown() {
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    resolver.shutdown();
    if (xdsClient != null) {
      assertThat(xdsClient.ldsWatcher).isNull();
      assertThat(xdsClient.rdsWatcher).isNull();
    }
  }

  @Test
  public void resolving_failToCreateXdsClientPool() {
    XdsClientPoolFactory xdsClientPoolFactory = new XdsClientPoolFactory() {
      @Override
      public ObjectPool<XdsClient> getXdsClientPool() throws XdsInitializationException {
        throw new XdsInitializationException("Fail to read bootstrap file");
      }
    };
    resolver = new XdsNameResolver(AUTHORITY, serviceConfigParser, syncContext,
        xdsClientPoolFactory, mockRandom);
    resolver.start(mockListener);
    verify(mockListener).onError(errorCaptor.capture());
    Status error = errorCaptor.getValue();
    assertThat(error.getCode()).isEqualTo(Code.UNAVAILABLE);
    assertThat(error.getDescription()).isEqualTo("Failed to initialize xDS");
    assertThat(error.getCause()).hasMessageThat().isEqualTo("Fail to read bootstrap file");
  }

  @Test
  public void resolving_ldsResourceNotFound() {
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    assertThat(xdsClient.ldsResource).isEqualTo(AUTHORITY);
    assertThat(xdsClient.ldsWatcher).isNotNull();
    xdsClient.deliverLdsResourceNotFound(AUTHORITY);
    assertEmptyResolutionResult();
  }

  @Test
  public void resolving_ldsResourceUpdateRdsName() {
    String rdsResource = "route-configuration-foo.googleapis.com";
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverRdsName(AUTHORITY, rdsResource);
    RdsResourceWatcher rdsWatcher = xdsClient.rdsWatcher;
    assertThat(rdsWatcher).isNotNull();
    assertThat(xdsClient.rdsResource).isEqualTo(rdsResource);
    rdsResource = "route-configuration-bar.googleapis.com";
    xdsClient.deliverRdsName(AUTHORITY, rdsResource);
    assertThat(xdsClient.rdsResource).isEqualTo(rdsResource);
    assertThat(xdsClient.rdsWatcher).isNotSameInstanceAs(rdsWatcher);
  }

  @Test
  public void resolving_rdsResourceNotFound() {
    String rdsResource = "route-configuration.googleapis.com";
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    assertThat(xdsClient.ldsResource).isEqualTo(AUTHORITY);
    assertThat(xdsClient.ldsWatcher).isNotNull();
    xdsClient.deliverRdsName(AUTHORITY, rdsResource);
    assertThat(xdsClient.rdsResource).isEqualTo(rdsResource);
    assertThat(xdsClient.rdsWatcher).isNotNull();
    xdsClient.deliverRdsResourceNotFound(rdsResource);
    assertEmptyResolutionResult();
  }

  @Test
  public void resolving_encounterErrorLdsWatcherOnly() {
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverError(Status.UNAVAILABLE.withDescription("server unreachable"));
    verify(mockListener).onError(errorCaptor.capture());
    Status error = errorCaptor.getValue();
    assertThat(error.getCode()).isEqualTo(Code.UNAVAILABLE);
    assertThat(error.getDescription()).isEqualTo("server unreachable");
  }

  @Test
  public void resolving_encounterErrorLdsAndRdsWatchers() {
    String rdsResource = "route-configuration.googleapis.com";
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverRdsName(AUTHORITY, rdsResource);
    assertThat(xdsClient.rdsWatcher).isNotNull();
    xdsClient.deliverError(Status.UNAVAILABLE.withDescription("server unreachable"));
    verify(mockListener, times(2)).onError(errorCaptor.capture());
    for (Status error : errorCaptor.getAllValues()) {
      assertThat(error.getCode()).isEqualTo(Code.UNAVAILABLE);
      assertThat(error.getDescription()).isEqualTo("server unreachable");
    }
  }

  @Test
  public void resolving_matchingVirtualHostNotFoundInLdsResource() {
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverLdsUpdate(AUTHORITY, 0L, buildUnmatchedVirtualHosts());
    assertEmptyResolutionResult();
  }

  @Test
  public void resolving_matchingVirtualHostNotFoundInRdsResource() {
    String rdsResource = "route-configuration.googleapis.com";
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverRdsName(AUTHORITY, rdsResource);
    assertThat(xdsClient.rdsWatcher).isNotNull();
    xdsClient.deliverRdsUpdate(rdsResource, buildUnmatchedVirtualHosts());
    assertEmptyResolutionResult();
  }

  private List<VirtualHost> buildUnmatchedVirtualHosts() {
    Route route1 = new Route(RouteMatch.withPathExactOnly(call2.getFullMethodNameForPath()),
        new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster2, null));
    Route route2 = new Route(RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
        new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster1, null));
    return Arrays.asList(
        new VirtualHost("virtualhost-foo", Collections.singletonList("hello.googleapis.com"),
            Collections.singletonList(route1)),
        new VirtualHost("virtualhost-bar", Collections.singletonList("hi.googleapis.com"),
            Collections.singletonList(route2)));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void resolved_noTimeout() {
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    Route route = new Route(RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
        new RouteAction(null, cluster1, null));  // per-route timeout unset
    VirtualHost virtualHost = new VirtualHost("does not matter",
        Collections.singletonList(AUTHORITY), Collections.singletonList(route));
    xdsClient.deliverLdsUpdate(AUTHORITY, 0L, Collections.singletonList(virtualHost));
    verify(mockListener).onResult(resolutionResultCaptor.capture());
    ResolutionResult result = resolutionResultCaptor.getValue();
    InternalConfigSelector configSelector = result.getAttributes().get(InternalConfigSelector.KEY);
    Result selectResult = configSelector.selectConfig(
        new PickSubchannelArgsImpl(call1.methodDescriptor, new Metadata(), CallOptions.DEFAULT));
    assertThat(selectResult.getStatus().isOk()).isTrue();
    assertThat(selectResult.getCallOptions().getOption(XdsNameResolver.CLUSTER_SELECTION_KEY))
        .isEqualTo(cluster1);
    assertThat((Map<String, ?>) selectResult.getConfig()).isEmpty();
  }

  @Test
  public void resolved_fallbackToHttpMaxStreamDurationAsTimeout() {
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    Route route = new Route(RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
        new RouteAction(null, cluster1, null));  // per-route timeout unset
    VirtualHost virtualHost = new VirtualHost("does not matter",
        Collections.singletonList(AUTHORITY), Collections.singletonList(route));
    xdsClient.deliverLdsUpdate(AUTHORITY, TimeUnit.SECONDS.toNanos(5L),
        Collections.singletonList(virtualHost));
    verify(mockListener).onResult(resolutionResultCaptor.capture());
    ResolutionResult result = resolutionResultCaptor.getValue();
    InternalConfigSelector configSelector = result.getAttributes().get(InternalConfigSelector.KEY);
    assertCallSelectResult(call1, configSelector, cluster1, 5.0);
  }

  @Test
  public void resolved_simpleCallSucceeds() {
    InternalConfigSelector configSelector = resolveToClusters();
    Result selectResult = assertCallSelectResult(call1, configSelector, cluster1, 15.0);
    selectResult.getCommittedCallback().run();
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void resolved_simpleCallFailedToRoute() {
    InternalConfigSelector configSelector = resolveToClusters();
    CallInfo call = new CallInfo("FooService", "barMethod");
    Result selectResult = configSelector.selectConfig(
        new PickSubchannelArgsImpl(call.methodDescriptor, new Metadata(), CallOptions.DEFAULT));
    Status status = selectResult.getStatus();
    assertThat(status.isOk()).isFalse();
    assertThat(status.getCode()).isEqualTo(Code.UNAVAILABLE);
    assertThat(status.getDescription()).isEqualTo("Could not find xDS route matching RPC");
    verifyNoMoreInteractions(mockListener);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void resolved_resourceUpdateAfterCallStarted() {
    InternalConfigSelector configSelector = resolveToClusters();
    Result selectResult = assertCallSelectResult(call1, configSelector, cluster1, 15.0);

    reset(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverLdsUpdate(
        AUTHORITY,
        Arrays.asList(
            new Route(
                RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(20L), "another-cluster", null)),
            new Route(
                RouteMatch.withPathExactOnly(call2.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster2, null))));
    verify(mockListener).onResult(resolutionResultCaptor.capture());
    ResolutionResult result = resolutionResultCaptor.getValue();
    // Updated service config still contains cluster1 while it is removed resource. New calls no
    // longer routed to cluster1.
    assertServiceConfigForLoadBalancingConfig(
        Arrays.asList(cluster1, cluster2, "another-cluster"),
        (Map<String, ?>) result.getServiceConfig().getConfig());
    assertThat(result.getAttributes().get(InternalConfigSelector.KEY))
        .isSameInstanceAs(configSelector);
    assertCallSelectResult(call1, configSelector, "another-cluster", 20.0);

    selectResult.getCommittedCallback().run();  // completes previous call
    verify(mockListener, times(2)).onResult(resolutionResultCaptor.capture());
    result = resolutionResultCaptor.getValue();
    assertServiceConfigForLoadBalancingConfig(
        Arrays.asList(cluster2, "another-cluster"),
        (Map<String, ?>) result.getServiceConfig().getConfig());
    verifyNoMoreInteractions(mockListener);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void resolved_resourceUpdatedBeforeCallStarted() {
    InternalConfigSelector configSelector = resolveToClusters();
    reset(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverLdsUpdate(
        AUTHORITY,
        Arrays.asList(
            new Route(
                RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(20L), "another-cluster", null)),
            new Route(
                RouteMatch.withPathExactOnly(call2.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster2, null))));
    // Two consecutive service config updates: one for removing clcuster1,
    // one for adding "another=cluster".
    verify(mockListener, times(2)).onResult(resolutionResultCaptor.capture());
    ResolutionResult result = resolutionResultCaptor.getValue();
    assertServiceConfigForLoadBalancingConfig(
        Arrays.asList(cluster2, "another-cluster"),
        (Map<String, ?>) result.getServiceConfig().getConfig());
    assertThat(result.getAttributes().get(InternalConfigSelector.KEY))
        .isSameInstanceAs(configSelector);
    assertCallSelectResult(call1, configSelector, "another-cluster", 20.0);

    verifyNoMoreInteractions(mockListener);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void resolved_raceBetweenCallAndRepeatedResourceUpdate() {
    InternalConfigSelector configSelector = resolveToClusters();
    assertCallSelectResult(call1, configSelector, cluster1, 15.0);

    reset(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverLdsUpdate(
        AUTHORITY,
        Arrays.asList(
            new Route(
                RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(20L), "another-cluster", null)),
            new Route(
                RouteMatch.withPathExactOnly(call2.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster2, null))));

    verify(mockListener).onResult(resolutionResultCaptor.capture());
    ResolutionResult result = resolutionResultCaptor.getValue();
    assertServiceConfigForLoadBalancingConfig(
        Arrays.asList(cluster1, cluster2, "another-cluster"),
        (Map<String, ?>) result.getServiceConfig().getConfig());

    xdsClient.deliverLdsUpdate(
        AUTHORITY,
        Arrays.asList(
            new Route(
                RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), "another-cluster", null)),
            new Route(
                RouteMatch.withPathExactOnly(call2.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster2, null))));
    verifyNoMoreInteractions(mockListener);  // no cluster added/deleted
    assertCallSelectResult(call1, configSelector, "another-cluster", 15.0);
  }

  @Test
  public void resolved_raceBetweenClusterReleasedAndResourceUpdateAddBackAgain() {
    InternalConfigSelector configSelector = resolveToClusters();
    Result result = assertCallSelectResult(call1, configSelector, cluster1, 15.0);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverLdsUpdate(
        AUTHORITY,
        Collections.singletonList(
            new Route(
                RouteMatch.withPathExactOnly(call2.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster2, null))));
    xdsClient.deliverLdsUpdate(
        AUTHORITY,
        Arrays.asList(
            new Route(
                RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster1, null)),
            new Route(
                RouteMatch.withPathExactOnly(call2.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster2, null))));
    result.getCommittedCallback().run();
    verifyNoMoreInteractions(mockListener);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void resolved_simpleCallSucceeds_routeToWeightedCluster() {
    when(mockRandom.nextInt(anyInt())).thenReturn(90, 10);
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverLdsUpdate(
        AUTHORITY,
        Arrays.asList(
            new Route(
                RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
                new RouteAction(
                    TimeUnit.SECONDS.toNanos(20L), null,
                    Arrays.asList(
                        new ClusterWeight(cluster1, 20), new ClusterWeight(cluster2, 80))))));
    verify(mockListener).onResult(resolutionResultCaptor.capture());
    ResolutionResult result = resolutionResultCaptor.getValue();
    assertThat(result.getAddresses()).isEmpty();
    assertServiceConfigForLoadBalancingConfig(
        Arrays.asList(cluster1, cluster2), (Map<String, ?>) result.getServiceConfig().getConfig());
    assertThat(result.getAttributes().get(XdsAttributes.XDS_CLIENT_POOL)).isNotNull();
    InternalConfigSelector configSelector = result.getAttributes().get(InternalConfigSelector.KEY);
    Result selectResult = configSelector.selectConfig(
        new PickSubchannelArgsImpl(call1.methodDescriptor, new Metadata(), CallOptions.DEFAULT));
    assertThat(selectResult.getStatus().isOk()).isTrue();
    assertThat(selectResult.getCallOptions().getOption(XdsNameResolver.CLUSTER_SELECTION_KEY))
        .isEqualTo(cluster2);
    assertServiceConfigForMethodConfig(20.0, (Map<String, ?>) selectResult.getConfig());

    selectResult = configSelector.selectConfig(
        new PickSubchannelArgsImpl(call1.methodDescriptor, new Metadata(), CallOptions.DEFAULT));
    assertThat(selectResult.getStatus().isOk()).isTrue();
    assertThat(selectResult.getCallOptions().getOption(XdsNameResolver.CLUSTER_SELECTION_KEY))
        .isEqualTo(cluster1);
    assertServiceConfigForMethodConfig(20.0, (Map<String, ?>) selectResult.getConfig());
  }

  @SuppressWarnings("unchecked")
  private void assertEmptyResolutionResult() {
    verify(mockListener).onResult(resolutionResultCaptor.capture());
    ResolutionResult result = resolutionResultCaptor.getValue();
    assertThat(result.getAddresses()).isEmpty();
    assertThat((Map<String, ?>) result.getServiceConfig().getConfig()).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static Result assertCallSelectResult(
      CallInfo call, InternalConfigSelector configSelector, String expectedCluster,
      double expectedTimeoutSec) {
    Result result = configSelector.selectConfig(
        new PickSubchannelArgsImpl(call.methodDescriptor, new Metadata(), CallOptions.DEFAULT));
    assertThat(result.getStatus().isOk()).isTrue();
    assertThat(result.getCallOptions().getOption(XdsNameResolver.CLUSTER_SELECTION_KEY))
        .isEqualTo(expectedCluster);
    assertServiceConfigForMethodConfig(expectedTimeoutSec, (Map<String, ?>) result.getConfig());
    return result;
  }

  @SuppressWarnings("unchecked")
  private InternalConfigSelector resolveToClusters() {
    resolver.start(mockListener);
    FakeXdsClient xdsClient = (FakeXdsClient) resolver.getXdsClient();
    xdsClient.deliverLdsUpdate(
        AUTHORITY,
        Arrays.asList(
            new Route(
                RouteMatch.withPathExactOnly(call1.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster1, null)),
            new Route(
                RouteMatch.withPathExactOnly(call2.getFullMethodNameForPath()),
                new RouteAction(TimeUnit.SECONDS.toNanos(15L), cluster2, null))));
    verify(mockListener).onResult(resolutionResultCaptor.capture());
    ResolutionResult result = resolutionResultCaptor.getValue();
    assertThat(result.getAddresses()).isEmpty();
    assertServiceConfigForLoadBalancingConfig(
        Arrays.asList(cluster1, cluster2), (Map<String, ?>) result.getServiceConfig().getConfig());
    assertThat(result.getAttributes().get(XdsAttributes.XDS_CLIENT_POOL)).isNotNull();
    return result.getAttributes().get(InternalConfigSelector.KEY);
  }

  /**
   * Verifies the raw service config contains a single method config for method with the
   * specified timeout.
   */
  private static void assertServiceConfigForMethodConfig(
      double timeoutSec, Map<String, ?> actualServiceConfig) {
    List<Map<String, ?>> rawMethodConfigs =
        JsonUtil.getListOfObjects(actualServiceConfig, "methodConfig");
    Map<String, ?> methodConfig = Iterables.getOnlyElement(rawMethodConfigs);
    List<Map<String, ?>> methods = JsonUtil.getListOfObjects(methodConfig, "name");
    assertThat(Iterables.getOnlyElement(methods)).isEmpty();
    assertThat(JsonUtil.getString(methodConfig, "timeout")).isEqualTo(timeoutSec + "s");
  }

  /**
   * Verifies the raw service config contains an xDS load balancing config for the given clusters.
   */
  private static void assertServiceConfigForLoadBalancingConfig(
      List<String> clusters, Map<String, ?> actualServiceConfig) {
    List<Map<String, ?>> rawLbConfigs =
        JsonUtil.getListOfObjects(actualServiceConfig, "loadBalancingConfig");
    Map<String, ?> lbConfig = Iterables.getOnlyElement(rawLbConfigs);
    assertThat(lbConfig.keySet()).containsExactly("cluster_manager_experimental");
    Map<String, ?> clusterManagerLbConfig =
        JsonUtil.getObject(lbConfig, "cluster_manager_experimental");
    Map<String, ?> clusterManagerChildLbPolicies =
        JsonUtil.getObject(clusterManagerLbConfig, "childPolicy");
    assertThat(clusterManagerChildLbPolicies.keySet()).containsExactlyElementsIn(clusters);
    for (String cluster : clusters) {
      Map<String, ?> childLbConfig = JsonUtil.getObject(clusterManagerChildLbPolicies, cluster);
      assertThat(childLbConfig.keySet()).containsExactly("lbPolicy");
      List<Map<String, ?>> childLbConfigValues =
          JsonUtil.getListOfObjects(childLbConfig, "lbPolicy");
      Map<String, ?> cdsLbPolicy = Iterables.getOnlyElement(childLbConfigValues);
      assertThat(cdsLbPolicy.keySet()).containsExactly("cds_experimental");
      assertThat(JsonUtil.getObject(cdsLbPolicy, "cds_experimental"))
          .containsExactly("cluster", cluster);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void generateServiceConfig_forLoadBalancingConfig() throws IOException {
    List<String> clusters = Arrays.asList("cluster-foo", "cluster-bar", "cluster-baz");
    String expectedServiceConfigJson = "{\n"
        + "  \"loadBalancingConfig\": [{\n"
        + "    \"cluster_manager_experimental\": {\n"
        + "      \"childPolicy\": {\n"
        + "        \"cluster-foo\": {\n"
        + "          \"lbPolicy\": [{\n"
        + "            \"cds_experimental\": {\n"
        + "              \"cluster\": \"cluster-foo\"\n"
        + "            }\n"
        + "          }]\n"
        + "        },\n"
        + "        \"cluster-bar\": {\n"
        + "          \"lbPolicy\": [{\n"
        + "            \"cds_experimental\": {\n"
        + "              \"cluster\": \"cluster-bar\"\n"
        + "            }\n"
        + "          }]\n"
        + "        },\n"
        + "        \"cluster-baz\": {\n"
        + "          \"lbPolicy\": [{\n"
        + "            \"cds_experimental\": {\n"
        + "              \"cluster\": \"cluster-baz\"\n"
        + "            }\n"
        + "          }]\n"
        + "        }\n"
        + "      }\n"
        + "    }\n"
        + "  }]\n"
        + "}";
    Map<String, ?> expectedServiceConfig =
        (Map<String, ?>) JsonParser.parse(expectedServiceConfigJson);
    assertThat(XdsNameResolver.generateServiceConfigWithLoadBalancingConfig(clusters))
        .isEqualTo(expectedServiceConfig);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void generateServiceConfig_forMethodTimeoutConfig() throws IOException {
    long timeoutNano = TimeUnit.SECONDS.toNanos(1L) + 1L; // 1.0000000001s
    String expectedServiceConfigJson = "{\n"
        + "  \"methodConfig\": [{\n"
        + "    \"name\": [ {} ],\n"
        + "    \"timeout\": \"1.000000001s\"\n"
        + "  }]\n"
        + "}";
    Map<String, ?> expectedServiceConfig =
        (Map<String, ?>) JsonParser.parse(expectedServiceConfigJson);
    assertThat(XdsNameResolver.generateServiceConfigWithMethodTimeoutConfig(timeoutNano))
        .isEqualTo(expectedServiceConfig);
  }

  @Test
  public void matchHostName_exactlyMatch() {
    String pattern = "foo.googleapis.com";
    assertThat(XdsNameResolver.matchHostName("bar.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("fo.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("oo.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("foo.googleapis", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("foo.googleapis.com", pattern)).isTrue();
  }

  @Test
  public void matchHostName_prefixWildcard() {
    String pattern = "*.foo.googleapis.com";
    assertThat(XdsNameResolver.matchHostName("foo.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("bar-baz.foo.googleapis", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("bar.foo.googleapis.com", pattern)).isTrue();
    pattern = "*-bar.foo.googleapis.com";
    assertThat(XdsNameResolver.matchHostName("bar.foo.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("baz-bar.foo.googleapis", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("-bar.foo.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("baz-bar.foo.googleapis.com", pattern))
        .isTrue();
  }

  @Test
  public void matchHostName_postfixWildCard() {
    String pattern = "foo.*";
    assertThat(XdsNameResolver.matchHostName("bar.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("bar.foo.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("foo.googleapis.com", pattern)).isTrue();
    assertThat(XdsNameResolver.matchHostName("foo.com", pattern)).isTrue();
    pattern = "foo-*";
    assertThat(XdsNameResolver.matchHostName("bar-.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("foo.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("foo.googleapis.com", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("foo-", pattern)).isFalse();
    assertThat(XdsNameResolver.matchHostName("foo-bar.com", pattern)).isTrue();
    assertThat(XdsNameResolver.matchHostName("foo-.com", pattern)).isTrue();
    assertThat(XdsNameResolver.matchHostName("foo-bar", pattern)).isTrue();
  }

  @Test
  public void findVirtualHostForHostName_exactMatchFirst() {
    String hostname = "a.googleapis.com";
    List<Route> routes = Collections.emptyList();
    VirtualHost vHost1 = new VirtualHost("virtualhost01.googleapis.com",
        Arrays.asList("a.googleapis.com", "b.googleapis.com"), routes);
    VirtualHost vHost2 = new VirtualHost("virtualhost02.googleapis.com",
        Collections.singletonList("*.googleapis.com"), routes);
    VirtualHost vHost3 =
        new VirtualHost("virtualhost03.googleapis.com", Collections.singletonList("*"), routes);
    List<VirtualHost> virtualHosts = Arrays.asList(vHost1, vHost2, vHost3);
    assertThat(XdsNameResolver.findVirtualHostForHostName(virtualHosts, hostname))
        .isEqualTo(vHost1);
  }

  @Test
  public void findVirtualHostForHostName_preferSuffixDomainOverPrefixDomain() {
    String hostname = "a.googleapis.com";
    List<Route> routes = Collections.emptyList();
    VirtualHost vHost1 =
        new VirtualHost("virtualhost01.googleapis.com",
            Arrays.asList("*.googleapis.com", "b.googleapis.com"), routes);
    VirtualHost vHost2 =
        new VirtualHost("virtualhost02.googleapis.com",
            Collections.singletonList("a.googleapis.*"), routes);
    VirtualHost vHost3 =
        new VirtualHost("virtualhost03.googleapis.com", Collections.singletonList("*"), routes);
    List<VirtualHost> virtualHosts = Arrays.asList(vHost1, vHost2, vHost3);
    assertThat(XdsNameResolver.findVirtualHostForHostName(virtualHosts, hostname))
        .isEqualTo(vHost1);
  }

  @Test
  public void findVirtualHostForHostName_asteriskMatchAnyDomain() {
    String hostname = "a.googleapis.com";
    List<Route> routes = Collections.emptyList();
    VirtualHost vHost1 =
        new VirtualHost("virtualhost01.googleapis.com", Collections.singletonList("*"), routes);
    VirtualHost vHost2 =
        new VirtualHost("virtualhost02.googleapis.com",
            Collections.singletonList("b.googleapis.com"), routes);
    List<VirtualHost> virtualHosts = Arrays.asList(vHost1, vHost2);
    assertThat(XdsNameResolver.findVirtualHostForHostName(virtualHosts, hostname))
        .isEqualTo(vHost1);;
  }

  private final class FakeXdsClientPoolFactory implements XdsClientPoolFactory {

    @Override
    public ObjectPool<XdsClient> getXdsClientPool() throws XdsInitializationException {
      return new ObjectPool<XdsClient>() {
        @Override
        public XdsClient getObject() {
          return new FakeXdsClient();
        }

        @Override
        public XdsClient returnObject(Object object) {
          return null;
        }
      };
    }
  }

  private class FakeXdsClient extends XdsClient {
    private String ldsResource;
    private String rdsResource;
    private LdsResourceWatcher ldsWatcher;
    private RdsResourceWatcher rdsWatcher;

    @Override
    void watchLdsResource(String resourceName, LdsResourceWatcher watcher) {
      Preconditions.checkArgument(ldsResource == null && ldsWatcher == null, "already watched");
      ldsResource = resourceName;
      ldsWatcher = watcher;
    }

    @Override
    void cancelLdsResourceWatch(String resourceName, LdsResourceWatcher watcher) {
      Preconditions.checkArgument(resourceName.equals(ldsResource), "unknown resource");
      Preconditions.checkArgument(watcher.equals(ldsWatcher), "unknown watcher");
      ldsResource = null;
      ldsWatcher = null;
    }

    @Override
    void watchRdsResource(String resourceName, RdsResourceWatcher watcher) {
      Preconditions.checkArgument(rdsResource == null && rdsWatcher == null, "already watched");
      rdsResource = resourceName;
      rdsWatcher = watcher;
    }

    @Override
    void cancelRdsResourceWatch(String resourceName, RdsResourceWatcher watcher) {
      Preconditions.checkArgument(resourceName.equals(rdsResource), "unknown resource");
      Preconditions.checkArgument(watcher.equals(rdsWatcher), "unknown watcher");
      rdsResource = null;
      rdsWatcher = null;
    }

    @Override
    void shutdown() {
      // no-op
    }

    void deliverLdsUpdate(final String resourceName, final long httpMaxStreamDurationNano,
        final List<VirtualHost> virtualHosts) {
      syncContext.execute(new Runnable() {
        @Override
        public void run() {
          if (!resourceName.equals(ldsResource)) {
            return;
          }
          LdsUpdate.Builder updateBuilder = LdsUpdate.newBuilder();
          updateBuilder.setHttpMaxStreamDurationNano(httpMaxStreamDurationNano);
          for (VirtualHost virtualHost : virtualHosts) {
            updateBuilder.addVirtualHost(virtualHost);
          }
          ldsWatcher.onChanged(updateBuilder.build());
        }
      });
    }

    void deliverLdsUpdate(final String resourceName, final List<Route> routes) {
      syncContext.execute(new Runnable() {
        @Override
        public void run() {
          if (!resourceName.equals(ldsResource)) {
            return;
          }
          VirtualHost virtualHost =
              new VirtualHost("virtual-host", Collections.singletonList(AUTHORITY), routes);
          ldsWatcher.onChanged(LdsUpdate.newBuilder().addVirtualHost(virtualHost).build());
        }
      });
    }

    void deliverRdsName(final String resourceName, final String rdsName) {
      syncContext.execute(new Runnable() {
        @Override
        public void run() {
          if (!resourceName.equals(ldsResource)) {
            return;
          }
          ldsWatcher.onChanged(LdsUpdate.newBuilder().setRdsName(rdsName).build());
        }
      });
    }

    void deliverLdsResourceNotFound(final String resourceName) {
      syncContext.execute(new Runnable() {
        @Override
        public void run() {
          if (!resourceName.equals(ldsResource)) {
            return;
          }
          ldsWatcher.onResourceDoesNotExist(ldsResource);
        }
      });
    }

    void deliverRdsUpdate(final String resourceName, final List<VirtualHost> virtualHosts) {
      syncContext.execute(new Runnable() {
        @Override
        public void run() {
          if (!resourceName.equals(rdsResource)) {
            return;
          }
          rdsWatcher.onChanged(RdsUpdate.fromVirtualHosts(virtualHosts));
        }
      });
    }

    void deliverRdsResourceNotFound(final String resourceName) {
      syncContext.execute(new Runnable() {
        @Override
        public void run() {
          if (!resourceName.equals(rdsResource)) {
            return;
          }
          rdsWatcher.onResourceDoesNotExist(rdsResource);
        }
      });
    }

    void deliverError(final Status error) {
      syncContext.execute(new Runnable() {
        @Override
        public void run() {
          if (ldsWatcher != null) {
            ldsWatcher.onError(error);
          }
          if (rdsWatcher != null) {
            rdsWatcher.onError(error);
          }
        }
      });
    }
  }

  private static final class CallInfo {
    private final String service;
    private final String method;
    private final MethodDescriptor<Void, Void> methodDescriptor;

    private CallInfo(String service, String method) {
      this.service = service;
      this.method = method;
      methodDescriptor =
          MethodDescriptor.<Void, Void>newBuilder()
              .setType(MethodType.UNARY).setFullMethodName(service + "/" + method)
              .setRequestMarshaller(TestMethodDescriptors.voidMarshaller())
              .setResponseMarshaller(TestMethodDescriptors.voidMarshaller())
              .build();
    }

    private String getFullMethodNameForPath() {
      return "/" + service + "/" + method;
    }
  }
}
