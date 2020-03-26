/*
 * Copyright Terracotta, Inc.
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
package org.terracotta.dynamic_config.system_tests.activated;

import org.junit.Rule;
import org.junit.Test;
import org.terracotta.angela.common.tcconfig.TerracottaServer;
import org.terracotta.dynamic_config.api.model.Cluster;
import org.terracotta.dynamic_config.test_support.ClusterDefinition;
import org.terracotta.dynamic_config.test_support.DynamicConfigIT;
import org.terracotta.dynamic_config.test_support.util.NodeOutputRule;

import java.time.Duration;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.terracotta.dynamic_config.test_support.util.AngelaMatchers.containsLog;
import static org.terracotta.dynamic_config.test_support.util.AngelaMatchers.containsOutput;
import static org.terracotta.dynamic_config.test_support.util.AngelaMatchers.hasExitStatus;

/**
 * @author Mathieu Carbou
 */
@ClusterDefinition(nodesPerStripe = 2, autoActivate = true)
public class DiagnosticMode1x2IT extends DynamicConfigIT {

  @Rule public final NodeOutputRule out = new NodeOutputRule();

  public DiagnosticMode1x2IT() {
    super(Duration.ofSeconds(120));
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void test_restart_active_in_diagnostic_mode() {
    int activeNodeId = findActive(1).getAsInt();
    TerracottaServer active = getNode(1, activeNodeId);
    tsa.stop(active);
    assertThat(tsa.getStopped().size(), is(1));

    startNode(active, "--diagnostic-mode", "--node-name", active.getServerSymbolicName().getSymbolicName(), "-r", active.getConfigRepo());
    waitUntil(out.getLog(1, activeNodeId), containsLog("Started the server in diagnostic mode"));
  }

  @Test
  public void test_restart_passive_in_diagnostic_mode() {
    int passiveNodeId = findPassives(1)[0];
    TerracottaServer passive = getNode(1, passiveNodeId);
    tsa.stop(passive);
    assertThat(tsa.getStopped().size(), is(1));

    startNode(passive, "--diagnostic-mode", "--node-name", passive.getServerSymbolicName().getSymbolicName(), "-r", passive.getConfigRepo());
    waitUntil(out.getLog(1, passiveNodeId), containsLog("Started the server in diagnostic mode"));
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void test_diagnostic_port_accessible_but_nomad_change_impossible() throws Exception {
    int activeNodeId = findActive(1).getAsInt();
    int passiveId = findPassives(1)[0];
    TerracottaServer active = getNode(1, activeNodeId);
    tsa.stop(active);
    assertThat(tsa.getStopped().size(), is(1));

    out.clearLog(1, 1);
    startNode(active, "--diagnostic-mode", "-n", active.getServerSymbolicName().getSymbolicName(), "-r", active.getConfigRepo());
    waitUntil(out.getLog(1, activeNodeId), containsLog("Started the server in diagnostic mode"));

    // diag port available
    Cluster cluster = getUpcomingCluster("localhost", getNodePort(1, activeNodeId));
    assertThat(cluster.getStripeCount(), is(equalTo(1)));

    // log command works, both when targeting node to repair and a normal node in the cluster
    assertThat(configToolInvocation("log", "-s", "localhost:" + getNodePort(1, activeNodeId)), containsOutput("Activating cluster"));
    assertThat(configToolInvocation("log", "-s", "localhost:" + getNodePort(1, passiveId)), containsOutput("Activating cluster"));

    // diag command works, both when targeting node to repair and a normal node in the cluster
    assertThat(configToolInvocation("diagnostic", "-s", "localhost:" + getNodePort(1, activeNodeId)),
        containsOutput("Node started in diagnostic mode for initial configuration or repair: YES"));

    assertThat(configToolInvocation("diagnostic", "-s", "localhost:" + getNodePort(1, passiveId)),
        containsOutput("Node started in diagnostic mode for initial configuration or repair: YES"));

    // unable to trigger a change on the cluster from the node in diagnostic mode
    assertThat(
        configToolInvocation("set", "-s", "localhost:" + getNodePort(1, activeNodeId), "-c", "stripe.1.node." + activeNodeId + ".tc-properties.something=value"),
        allOf(not(hasExitStatus(0)), containsOutput("Detected a mix of activated and unconfigured nodes (or being repaired).")));

    // unable to trigger a change on the cluster from any other node
    assertThat(
        configToolInvocation("set", "-s", "localhost:" + getNodePort(1, passiveId), "-c", "stripe.1.node.1.tc-properties.something=value"),
        allOf(not(hasExitStatus(0)), containsOutput("Detected a mix of activated and unconfigured nodes (or being repaired).")));
  }
}