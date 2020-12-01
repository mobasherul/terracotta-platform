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
package org.terracotta.dynamic_config.cli.config_tool.parsing.deprecated;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.BooleanConverter;
import org.terracotta.dynamic_config.api.model.Configuration;
import org.terracotta.dynamic_config.cli.api.command.Command;
import org.terracotta.dynamic_config.cli.api.command.GetCommand;
import org.terracotta.dynamic_config.cli.command.DeprecatedUsage;
import org.terracotta.dynamic_config.cli.command.JCommanderCommand;
import org.terracotta.dynamic_config.cli.converter.ConfigurationConverter;
import org.terracotta.dynamic_config.cli.converter.InetSocketAddressConverter;
import org.terracotta.dynamic_config.cli.converter.MultiConfigCommaSplitter;

import java.net.InetSocketAddress;
import java.util.List;

@Parameters(commandNames = "get", commandDescription = "Read configuration properties")
@DeprecatedUsage("get -s <hostname[:port]> [-r] -c <[namespace:]property>,<[namespace:]property>...")
public class DeprecatedGetJCommanderCommand extends JCommanderCommand {

  @Parameter(names = {"-s"}, description = "Node to connect to", required = true, converter = InetSocketAddressConverter.class)
  InetSocketAddress node;

  @Parameter(names = {"-c"}, description = "Configuration properties", splitter = MultiConfigCommaSplitter.class, required = true, converter = ConfigurationConverter.class)
  List<Configuration> configurations;

  @Parameter(names = {"-r"}, description = "Read the properties from the current runtime configuration instead of reading them from the last configuration saved on disk", converter = BooleanConverter.class)
  private boolean wantsRuntimeConfig;

  private final GetCommand underlying = new GetCommand();

  @Override
  public void run() {
    underlying.setNode(node);
    underlying.setConfigurations(configurations);
    underlying.setRuntimConfig(wantsRuntimeConfig);

    underlying.run();
  }

  @Override
  public Command getCommand() {
    return underlying;
  }
}
