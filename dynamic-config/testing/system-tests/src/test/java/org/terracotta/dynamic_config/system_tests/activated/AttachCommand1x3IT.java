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
import org.terracotta.dynamic_config.test_support.ClusterDefinition;
import org.terracotta.dynamic_config.test_support.DynamicConfigIT;
import org.terracotta.dynamic_config.test_support.util.NodeOutputRule;

import java.time.Duration;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.terracotta.dynamic_config.test_support.util.AngelaMatchers.containsLog;
import static org.terracotta.dynamic_config.test_support.util.AngelaMatchers.containsOutput;
import static org.terracotta.dynamic_config.test_support.util.AngelaMatchers.successful;

/**
 * @author Mathieu Carbou
 */
@ClusterDefinition(nodesPerStripe = 3, autoStart = false)
public class AttachCommand1x3IT extends DynamicConfigIT {

  @Rule
  public final NodeOutputRule out = new NodeOutputRule();

  public AttachCommand1x3IT() {
    super(Duration.ofSeconds(120));
  }

  @Test
  public void test_attach_to_activated_cluster_with_offline_node() throws Exception {
    // activate a 1x2 cluster
    startNode(1, 1);
    startNode(1, 2);
    waitUntil(out.getLog(1, 1), containsLog("Started the server in diagnostic mode"));
    waitUntil(out.getLog(1, 2), containsLog("Started the server in diagnostic mode"));
    assertThat(configToolInvocation("attach", "-d", "localhost:" + getNodePort(1, 1), "-s", "localhost:" + getNodePort(1, 2)), is(successful()));

    // activation restarts the node, so either 1 or 2 will become active
    activateCluster();
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 1)).getNodeCount(), is(equalTo(2)));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 2)).getNodeCount(), is(equalTo(2)));

    int activeId = findActive(1).getAsInt(); // either 1 or 2
    int passiveId = findPassives(1)[0]; // either 1 or 2

    stopNode(1, passiveId);

    // start a third node
    startNode(1, 3);
    waitUntil(out.getLog(1, 3), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    // attach
    assertThat(configToolInvocation("-v", "attach", "-d", "localhost:" + getNodePort(1, activeId), "-s", "localhost:" + getNodePort(1, 3)), is(successful()));
    waitUntil(out.getLog(1, 3), containsLog("Moved to State[ PASSIVE-STANDBY ]"));

    // verify that the active node topology has 3 nodes
    assertThat(getUpcomingCluster("localhost", getNodePort(1, activeId)).getNodeCount(), is(equalTo(3)));
    assertThat(getRuntimeCluster("localhost", getNodePort(1, activeId)).getNodeCount(), is(equalTo(3)));

    // verify that the added node topology has 3 nodes
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(3)));
    assertThat(getRuntimeCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(3)));

    // then try to start the passive that was down
    out.clearLog(1, passiveId);
    startNode(1, passiveId);
    waitUntil(out.getLog(1, passiveId), containsLog("Moved to State[ PASSIVE-STANDBY ]"));

    // verify that the restarted passive topology has 3 nodes
    assertThat(getUpcomingCluster("localhost", getNodePort(1, passiveId)).getNodeCount(), is(equalTo(3)));
    assertThat(getRuntimeCluster("localhost", getNodePort(1, passiveId)).getNodeCount(), is(equalTo(3)));
  }

  @Test
  public void attachNodeFailAtPrepare() throws Exception {
    startNode(1, 1);
    waitUntil(out.getLog(1, 1), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 1)).getNodeCount(), is(equalTo(1)));

    // start a second node
    startNode(1, 2);
    waitUntil(out.getLog(1, 1), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 2)).getNodeCount(), is(equalTo(1)));

    //attach
    assertThat(configToolInvocation("attach", "-d", "localhost:" + getNodePort(1, 1), "-s", "localhost:" + getNodePort(1, 2)), is(successful()));
    //Activate cluster
    activateCluster();

    //create prepare failure 
    assertThat(configToolInvocation("set", "-s", "localhost:" + getNodePort(1, 1), "-c", "stripe.1.node.1.tc-properties.attachStatus=prepareAddition-failure"), is(successful()));

    startNode(1, 3);
    waitUntil(out.getLog(1, 3), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    // attach failure (forcing attach otherwise we have to restart cluster)
    assertThat(
        configToolInvocation("attach", "-f", "-d", "localhost:" + getNodePort(1, 1),
            "-s", "localhost:" + getNodePort(1, 3)),
        containsOutput("Two-Phase commit failed"));

    assertThat(getUpcomingCluster("localhost", getNodePort(1, 1)).getNodeCount(), is(equalTo(2)));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 2)).getNodeCount(), is(equalTo(2)));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    withTopologyService(1, 1, topologyService -> assertTrue(topologyService.isActivated()));
    withTopologyService(1, 2, topologyService -> assertTrue(topologyService.isActivated()));
    withTopologyService(1, 3, topologyService -> assertFalse(topologyService.isActivated()));
  }

  @Test
  public void attachNodeFailingBecauseOfNodeGoingDownInPreparePhase() throws Exception {
    startNode(1, 1);
    waitUntil(out.getLog(1, 1), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 1)).getNodeCount(), is(equalTo(1)));

    // start a second node
    startNode(1, 2);
    waitUntil(out.getLog(1, 2), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 2)).getNodeCount(), is(equalTo(1)));

    //attach to create cluster
    assertThat(configToolInvocation("attach", "-d", "localhost:" + getNodePort(1, 1), "-s", "localhost:" + getNodePort(1, 2)), is(successful()));
    //Activate cluster
    activateCluster();

    int activeId = findActive(1).getAsInt();
    int passiveId = findPassives(1)[0];

    startNode(1, 3);
    waitUntil(out.getLog(1, 3), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    //create failover in prepare phase for active
    String propertySettingString = "stripe.1.node." + activeId + ".tc-properties.failoverAddition=killAddition-prepare";
    assertThat(configToolInvocation("set", "-s", "localhost:" + getNodePort(1, 1), "-c", propertySettingString), is(successful()));

    assertThat(
        configToolInvocation("attach", "-f", "-d", "localhost:" + getNodePort(1, activeId),
            "-s", "localhost:" + getNodePort(1, 3)),
        containsOutput("Two-Phase commit failed"));

    assertThat(getUpcomingCluster("localhost", getNodePort(1, passiveId)).getNodeCount(), is(equalTo(2)));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    withTopologyService(1, passiveId, topologyService -> assertTrue(topologyService.isActivated()));
    withTopologyService(1, 3, topologyService -> assertFalse(topologyService.isActivated()));
  }

  @Test
  public void testFailoverDuringNomadCommitForPassiveAddition() throws Exception {
    startNode(1, 1);
    waitUntil(out.getLog(1, 1), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 1)).getNodeCount(), is(equalTo(1)));

    // start a second node
    startNode(1, 2);
    waitUntil(out.getLog(1, 2), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 2)).getNodeCount(), is(equalTo(1)));

    //attach to create cluster
    assertThat(configToolInvocation("attach", "-d", "localhost:" + getNodePort(1, 1), "-s", "localhost:" + getNodePort(1, 2)), is(successful()));
    //Activate cluster
    activateCluster();

    int activeId = findActive(1).getAsInt();
    int passiveId = findPassives(1)[0];
    String propertySettingString = "stripe.1.node." + activeId + ".tc-properties.failoverAddition=killAddition-commit";

    startNode(1, 3);
    waitUntil(out.getLog(1, 3), containsLog("Started the server in diagnostic mode"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(1)));

    //setup for failover in commit phase on active
    assertThat(configToolInvocation("set", "-s", "localhost:" + getNodePort(1, 1), "-c", propertySettingString), is(successful()));

    assertThat(
        configToolInvocation("attach", "-f", "-d", "localhost:" + getNodePort(1, activeId),
            "-s", "localhost:" + getNodePort(1, 3)),
        is(successful()));

    waitUntil(out.getLog(1, 3), containsLog("Moved to State[ PASSIVE-STANDBY ]"));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, passiveId)).getNodeCount(), is(equalTo(3)));
    assertThat(getUpcomingCluster("localhost", getNodePort(1, 3)).getNodeCount(), is(equalTo(3)));

    withTopologyService(1, passiveId, topologyService -> assertTrue(topologyService.isActivated()));
    withTopologyService(1, 3, topologyService -> assertTrue(topologyService.isActivated()));
  }

}
