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
 */
package io.vertx.tests.db2client.tck;

import static org.junit.Assume.assumeFalse;

import io.vertx.db2client.DB2Builder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import io.vertx.db2client.DB2ConnectOptions;
import io.vertx.tests.db2client.junit.DB2Resource;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.tests.sqlclient.tck.TransactionTestBase;

@RunWith(VertxUnitRunner.class)
public class DB2TransactionTest extends TransactionTestBase {

  @ClassRule
  public static DB2Resource rule = DB2Resource.SHARED_INSTANCE;

  @Rule
  public TestName testName = new TestName();

  @Before
  public void printTestName(TestContext ctx) throws Exception {
    System.out.println(">>> BEGIN " + getClass().getSimpleName() + "." + testName.getMethodName());
  }

  @Override
  protected Pool createPool() {
    return DB2Builder.pool()
      .with(new PoolOptions().setMaxSize(1))
      .connectingTo(new DB2ConnectOptions(rule.options()))
      .using(vertx)
      .build();
  }

  @Override
  protected Pool nonTxPool() {
    return DB2Builder.pool()
      .with(new PoolOptions().setMaxSize(1))
      .connectingTo(new DB2ConnectOptions(rule.options()))
      .using(vertx)
      .build();
  }

  @Override
  protected void cleanTestTable(TestContext ctx) {
    // use DELETE FROM because DB2 does not support TRUNCATE TABLE
    getPool()
      .query("DELETE FROM mutable")
      .execute()
      .onComplete(ctx.asyncAssertSuccess());
  }

  @Override
  protected String statement(String... parts) {
    return String.join("?", parts);
  }

  @Test
  public void testDelayedCommit(TestContext ctx) {
    assumeFalse("DB2 on Z holds write locks on inserted columns with isolation level = 2", rule.isZOS());
    super.testDelayedCommit(ctx);
  }

  @Test
  public void testFailureWithPendingQueries(TestContext ctx) {
    assumeFalse("DB2 on Z holds write locks on inserted columns with isolation level = 2", rule.isZOS());
    super.testDelayedCommit(ctx);
  }

}
