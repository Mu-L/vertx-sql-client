/*
 * Copyright (C) 2017 Julien Viet
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

package io.vertx.tests.pgclient;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.*;
import io.vertx.tests.sqlclient.ProxyServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class PgPoolTestBase extends PgTestBase {

  Vertx vertx;

  @Before
  public void setup() throws Exception {
    super.setup();
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext ctx) {
    vertx.close().onComplete(ctx.asyncAssertSuccess());
  }

  protected Pool createPool(PgConnectOptions connectOptions, int size) {
    return createPool(connectOptions, new PoolOptions().setMaxSize(size));
  }

  protected Pool createPool(PgConnectOptions connectOptions, PoolOptions poolOptions) {
    return createPool(connectOptions, poolOptions, null);
  }

  protected abstract Pool createPool(PgConnectOptions connectOptions, PoolOptions poolOptions, Handler<SqlConnection> connectHandler);

  @Test
  public void testPool(TestContext ctx) {
    int num = 1000;
    Async async = ctx.async(num);
    Pool pool = createPool(options, 4);
    for (int i = 0;i < num;i++) {
      pool
        .getConnection()
        .onComplete(ctx.asyncAssertSuccess(conn -> {
        conn
          .query("SELECT id, randomnumber from WORLD")
          .execute()
          .onComplete(ar -> {
          if (ar.succeeded()) {
            RowSet<Row> result = ar.result();
            ctx.assertEquals(10000, result.size());
          } else {
            ctx.assertEquals("closed", ar.cause().getMessage());
          }
          conn.close();
          async.countDown();
        });
      }));
    }
  }

  @Test
  public void testQuery(TestContext ctx) {
    int num = 1000;
    Async async = ctx.async(num);
    Pool pool = createPool(options, 4);
    for (int i = 0;i < num;i++) {
      pool
        .query("SELECT id, randomnumber from WORLD")
        .execute()
        .onComplete(ar -> {
        if (ar.succeeded()) {
          SqlResult<?> result = ar.result();
          ctx.assertEquals(10000, result.size());
        } else {
          ctx.assertEquals("closed", ar.cause().getMessage());
        }
        async.countDown();
      });
    }
  }

  @Test
  public void testQueryWithParams(TestContext ctx) {
    testQueryWithParams(ctx, options);
  }

  @Test
  public void testCachedQueryWithParams(TestContext ctx) {
    testQueryWithParams(ctx, new PgConnectOptions(options).setCachePreparedStatements(true));
  }

  private void testQueryWithParams(TestContext ctx, PgConnectOptions options) {
    int num = 2;
    Async async = ctx.async(num);
    Pool pool = createPool(options, 1);
    for (int i = 0;i < num;i++) {
      pool
        .preparedQuery("SELECT id, randomnumber from WORLD where id=$1")
        .execute(Tuple.of(i + 1))
        .onComplete(ar -> {
        if (ar.succeeded()) {
          RowSet<Row> result = ar.result();
          ctx.assertEquals(1, result.size());
        } else {
          ar.cause().printStackTrace();
          ctx.assertEquals("closed", ar.cause().getMessage());
        }
        async.countDown();
      });
    }
  }

  @Test
  public void testUpdate(TestContext ctx) {
    int num = 1000;
    Async async = ctx.async(num);
    Pool pool = createPool(options, 4);
    for (int i = 0;i < num;i++) {
      pool
        .query("UPDATE Fortune SET message = 'Whatever' WHERE id = 9")
        .execute()
        .onComplete(ar -> {
        if (ar.succeeded()) {
          RowSet<Row> result = ar.result();
          ctx.assertEquals(1, result.rowCount());
        } else {
          ctx.assertEquals("closed", ar.cause().getMessage());
        }
        async.countDown();
      });
    }
  }

  @Test
  public void testUpdateWithParams(TestContext ctx) {
    int num = 1000;
    Async async = ctx.async(num);
    Pool pool = createPool(options, 4);
    for (int i = 0;i < num;i++) {
      pool
        .preparedQuery("UPDATE Fortune SET message = 'Whatever' WHERE id = $1")
        .execute(Tuple.of(9))
        .onComplete(ar -> {
        if (ar.succeeded()) {
          RowSet<Row> result = ar.result();
          ctx.assertEquals(1, result.rowCount());
        } else {
          ctx.assertEquals("closed", ar.cause().getMessage());
        }
        async.countDown();
      });
    }
  }

  @Test
  public void testReconnect(TestContext ctx) {
    Async async = ctx.async();
    ProxyServer proxy = ProxyServer.create(vertx, options.getPort(), options.getHost());
    AtomicReference<ProxyServer.Connection> proxyConn = new AtomicReference<>();
    proxy.proxyHandler(conn -> {
      proxyConn.set(conn);
      conn.connect();
    });
    proxy.listen(8080, "localhost", ctx.asyncAssertSuccess(v1 -> {
      Pool pool = createPool(new PgConnectOptions(options).setPort(8080).setHost("localhost"), 1);
      pool
        .getConnection()
        .onComplete(ctx.asyncAssertSuccess(conn1 -> {
        proxyConn.get().close();
        conn1.closeHandler(v2 -> {
          conn1
            .query("never-read")
            .execute()
            .onComplete(ctx.asyncAssertFailure(err -> {
            pool
              .getConnection()
              .onComplete(ctx.asyncAssertSuccess(conn2 -> {
              conn2
                .query("SELECT id, randomnumber from WORLD")
                .execute()
                .onComplete(ctx.asyncAssertSuccess(v3 -> {
                async.complete();
              }));
            }));
          }));
        });
      }));
    }));
  }

  @Test
  public void testCancelRequest(TestContext ctx) {
    Async async = ctx.async();
    Pool pool = createPool(options, 4);
    pool
      .getConnection()
      .onComplete(ctx.asyncAssertSuccess(conn -> {
      conn
        .query("SELECT pg_sleep(10)")
        .execute()
        .onComplete(ctx.asyncAssertFailure(error -> {
        ctx.assertTrue(hasSqlstateCode(error, ERRCODE_QUERY_CANCELED), error.getMessage());
        conn.close();
        async.complete();
      }));
      ((PgConnection)conn)
        .cancelRequest()
        .onComplete(ctx.asyncAssertSuccess());
    }));
  }

  @Test
  public void testWithConnection(TestContext ctx) {
    Async async = ctx.async(10);
    Pool pool = createPool(options, 1);
    Function<SqlConnection, Future<RowSet<Row>>> success = conn -> conn.query("SELECT 1").execute();
    Function<SqlConnection, Future<RowSet<Row>>> failure = conn -> conn.query("SELECT does_not_exist").execute();
    for (int i = 0;i < 10;i++) {
      if (i % 2 == 0) {
        pool
          .withConnection(success)
          .onComplete(ctx.asyncAssertSuccess(v -> async.countDown()));
      } else {
        pool
          .withConnection(failure)
          .onComplete(ctx.asyncAssertFailure(v -> async.countDown()));
      }
    }
  }
}
