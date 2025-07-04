/*
 * Copyright (C) 2019,2020 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vertx.db2client.impl.command;

import java.util.Map;

import io.vertx.sqlclient.spi.protocol.CommandBase;

public class AuthenticationCommandBase<R> extends CommandBase<R> {

  private final String username;
  private final String password;
  private final String database;
  private final Map<String, String> connectionAttributes;

  public AuthenticationCommandBase(String username, String password, String database,
      Map<String, String> connectionAttributes) {
    this.username = username;
    this.password = password;
    this.database = database;
    this.connectionAttributes = connectionAttributes;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }

  public String database() {
    return database;
  }

  public Map<String, String> connectionAttributes() {
    return connectionAttributes;
  }
}
