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

import com.google.common.annotations.VisibleForTesting;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import io.grpc.alts.GoogleDefaultChannelCredentials;
import io.grpc.xds.Bootstrapper.ChannelCreds;
import io.grpc.xds.Bootstrapper.ServerInfo;
import io.grpc.xds.XdsClient.XdsChannel;
import io.grpc.xds.XdsLogger.XdsLogLevel;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating channels to xDS severs.
 */
abstract class XdsChannelFactory {
  @VisibleForTesting
  static boolean experimentalV3SupportEnvVar = Boolean.parseBoolean(
      System.getenv("GRPC_XDS_EXPERIMENTAL_V3_SUPPORT"));

  private static final String XDS_V3_SERVER_FEATURE = "xds_v3";
  private static final XdsChannelFactory DEFAULT_INSTANCE = new XdsChannelFactory() {
    /**
     * Creates a channel to the first server in the given list.
     */
    @Override
    XdsChannel createChannel(List<ServerInfo> servers) throws XdsInitializationException {
      if (servers.isEmpty()) {
        throw new XdsInitializationException("No server provided");
      }
      XdsLogger logger = XdsLogger.withPrefix("xds-client-channel-factory");
      ServerInfo serverInfo = servers.get(0);
      String serverUri = serverInfo.getServerUri();
      logger.log(XdsLogLevel.INFO, "Creating channel to {0}", serverUri);
      List<ChannelCreds> channelCredsList = serverInfo.getChannelCredentials();
      ChannelCredentials channelCreds = null;
      // Use the first supported channel credentials configuration.
      for (ChannelCreds creds : channelCredsList) {
        switch (creds.getType()) {
          case "google_default":
            logger.log(XdsLogLevel.INFO, "Using channel credentials: google_default");
            channelCreds = GoogleDefaultChannelCredentials.create();
            break;
          case "insecure":
            logger.log(XdsLogLevel.INFO, "Using channel credentials: insecure");
            channelCreds = InsecureChannelCredentials.create();
            break;
          case "tls":
            logger.log(XdsLogLevel.INFO, "Using channel credentials: tls");
            channelCreds = TlsChannelCredentials.create();
            break;
          default:
        }
        if (channelCreds != null) {
          break;
        }
      }
      if (channelCreds == null) {
        throw new XdsInitializationException("No server with supported channel creds found");
      }

      ManagedChannel channel = Grpc.newChannelBuilder(serverUri, channelCreds)
          .keepAliveTime(5, TimeUnit.MINUTES)
          .build();
      boolean useProtocolV3 = experimentalV3SupportEnvVar
          && serverInfo.getServerFeatures().contains(XDS_V3_SERVER_FEATURE);

      return new XdsChannel(channel, useProtocolV3);
    }
  };

  static XdsChannelFactory getInstance() {
    return DEFAULT_INSTANCE;
  }

  /**
   * Creates a channel to one of the provided management servers.
   *
   * @throws XdsInitializationException if failed to create a channel with the given list of
   *         servers.
   */
  abstract XdsChannel createChannel(List<ServerInfo> servers) throws XdsInitializationException;
}
