/*
 * Copyright (C) 2020 IBM Corporation
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
 *
 */
package io.vertx.tests.mysqlclient.tck;

import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.tests.sqlclient.tck.DriverTestBase;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.tests.mysqlclient.junit.MySQLRule;

import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MySQLDriverTest extends DriverTestBase {

  @ClassRule
  public static MySQLRule rule = MySQLRule.SHARED_INSTANCE;

  @Override
  protected SqlConnectOptions defaultOptions() {
    return rule.options();
  }

}
