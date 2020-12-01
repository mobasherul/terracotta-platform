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
package org.terracotta.dynamic_config.cli.config_tool.parsing;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.terracotta.dynamic_config.cli.api.command.Command;
import org.terracotta.dynamic_config.cli.api.command.LogCommand;
import org.terracotta.dynamic_config.cli.command.JCommanderCommand;
import org.terracotta.dynamic_config.cli.command.Usage;
import org.terracotta.dynamic_config.cli.converter.InetSocketAddressConverter;

import java.net.InetSocketAddress;

@Parameters(commandNames = "log", commandDescription = "Log all the configuration changes of a node and their details")
@Usage("log -connect-to <hostname[:port]>")
public class LogJCommanderCommand extends JCommanderCommand {
  @Parameter(names = {"-connect-to"}, description = "Node to connect to", required = true, converter = InetSocketAddressConverter.class)
  private InetSocketAddress node;

  private final LogCommand underlying = new LogCommand();

  @Override
  public void run() {
    underlying.setNode(node);

    underlying.run();
  }

  @Override
  public Command getCommand() {
    return underlying;
  }
}
