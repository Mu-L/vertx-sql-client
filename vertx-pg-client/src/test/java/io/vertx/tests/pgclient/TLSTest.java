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

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgConnection;
import io.vertx.pgclient.SslMode;
import io.vertx.tests.pgclient.junit.ContainerPgRule;
import io.vertx.sqlclient.Tuple;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TLSTest {

  @ClassRule
  public static ContainerPgRule ruleOptionalSll = new ContainerPgRule().ssl(true);

  @ClassRule
  public static ContainerPgRule ruleForceSsl = new ContainerPgRule().ssl(true).forceSsl(true);

  @ClassRule
  public static ContainerPgRule ruleSllOff = new ContainerPgRule().ssl(false);

  private Vertx vertx;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
  }

  @After
  public void teardown(TestContext ctx) {
    vertx.close().onComplete(ctx.asyncAssertSuccess());
  }

  @Test
  public void testTLS(TestContext ctx) {
    Async async = ctx.async();

    PgConnectOptions options = new PgConnectOptions(ruleOptionalSll.options())
      .setSslMode(SslMode.REQUIRE)
      .setSslOptions(new ClientSSLOptions().setTrustOptions(new PemTrustOptions().addCertPath("tls/server.crt")));
    PgConnection.connect(vertx, options.setSslMode(SslMode.REQUIRE)).onComplete(ctx.asyncAssertSuccess(conn -> {
      ctx.assertTrue(conn.isSSL());
      conn
        .query("SELECT * FROM Fortune WHERE id=1")
        .execute()
        .onComplete(ctx.asyncAssertSuccess(result -> {
          ctx.assertEquals(1, result.size());
          Tuple row = result.iterator().next();
          ctx.assertEquals(1, row.getInteger(0));
          ctx.assertEquals("fortune: No such file or directory", row.getString(1));
          async.complete();
        }));
    }));
  }

  @Test
  public void testTLSTrustAll(TestContext ctx) {
    Async async = ctx.async();
    PgConnection.connect(vertx, ruleOptionalSll.options().setSslMode(SslMode.REQUIRE).setSslOptions(new ClientSSLOptions().setTrustAll(true))).onComplete(ctx.asyncAssertSuccess(conn -> {
      ctx.assertTrue(conn.isSSL());
      async.complete();
    }));
  }

  @Test
  public void testTLSInvalidCertificate(TestContext ctx) {
    Async async = ctx.async();
    PgConnection.connect(vertx, ruleOptionalSll.options().setSslMode(SslMode.REQUIRE).setSslOptions(new ClientSSLOptions().setTrustOptions(new PemTrustOptions().addCertPath("tls/another.crt")))).onComplete(ctx.asyncAssertFailure(err -> {
//      ctx.assertEquals(err.getClass(), VertxException.class);
      ctx.assertEquals(err.getMessage(), "SSL handshake failed");
      async.complete();
    }));
  }

  @Test
  public void testSslModeDisable(TestContext ctx) {
    Async async = ctx.async();
    PgConnectOptions options = ruleOptionalSll.options()
      .setSslMode(SslMode.DISABLE);
    PgConnection.connect(vertx, new PgConnectOptions(options)).onComplete(ctx.asyncAssertSuccess(conn -> {
      ctx.assertFalse(conn.isSSL());
      async.complete();
    }));
  }

  @Test
  public void testSslModeAllow(TestContext ctx) {
    Async async = ctx.async();
    PgConnectOptions options = ruleOptionalSll.options()
      .setSslMode(SslMode.ALLOW);
    PgConnection.connect(vertx, new PgConnectOptions(options)).onComplete(ctx.asyncAssertSuccess(conn -> {
      ctx.assertFalse(conn.isSSL());
      async.complete();
    }));
  }

  @Test
  public void testSslModeAllowFallback(TestContext ctx) {
    Async async = ctx.async();
    PgConnectOptions options = ruleForceSsl.options()
      .setSslMode(SslMode.ALLOW)
      .setSslOptions(new ClientSSLOptions().setTrustAll(true));
    PgConnection.connect(vertx, new PgConnectOptions(options)).onComplete(ctx.asyncAssertSuccess(conn -> {
      ctx.assertTrue(conn.isSSL());
      async.complete();
    }));
  }

  @Test
  public void testSslModePrefer(TestContext ctx) {
    Async async = ctx.async();
    PgConnectOptions options = ruleOptionalSll.options()
      .setSslMode(SslMode.PREFER)
      .setSslOptions(new ClientSSLOptions().setTrustAll(true));
    PgConnection.connect(vertx, new PgConnectOptions(options)).onComplete(ctx.asyncAssertSuccess(conn -> {
      ctx.assertTrue(conn.isSSL());
      async.complete();
    }));
  }

  @Test
  public void testSslModePreferFallback(TestContext ctx) {
    Async async = ctx.async();
    PgConnectOptions options = ruleSllOff.options()
      .setSslMode(SslMode.PREFER)
      .setSslOptions(new ClientSSLOptions().setTrustAll(true));
    PgConnection.connect(vertx, options).onComplete(ctx.asyncAssertSuccess(conn -> {
      ctx.assertFalse(conn.isSSL());
      async.complete();
    }));
  }

  @Test
  public void testSslModeVerifyCaConf(TestContext ctx) {
    PgConnectOptions options = ruleOptionalSll.options()
      .setSslMode(SslMode.VERIFY_CA)
      .setSslOptions(new ClientSSLOptions().setTrustAll(true));
    PgConnection.connect(vertx, new PgConnectOptions(options)).onComplete(ctx.asyncAssertFailure(error -> {
      ctx.assertEquals("Trust options must be specified under verify-full or verify-ca sslmode", error.getMessage());
    }));
  }

  @Test
  public void testSslModeVerifyFullConf(TestContext ctx) {
    PgConnectOptions options = ruleOptionalSll.options()
      .setSslOptions(new ClientSSLOptions().setTrustOptions(new PemTrustOptions().addCertPath("tls/another.crt")))
      .setSslMode(SslMode.VERIFY_FULL);
    PgConnection.connect(vertx, new PgConnectOptions(options)).onComplete(ctx.asyncAssertFailure(error -> {
      ctx.assertEquals("Host verification algorithm must be specified under verify-full sslmode", error.getMessage());
    }));
  }

  @Test
  public void testSslModeVerifyFullInvalidHostname(TestContext ctx) {
    PgConnectOptions options = ruleOptionalSll.options()
      .setSslMode(SslMode.VERIFY_FULL)
      // The hostname in the test certificate is thebrain.ca, so 'localhost' should make for a failed connection
      .setHost("localhost")
      .setSslOptions(
        new ClientSSLOptions()
          .setHostnameVerificationAlgorithm("HTTPS")
          .setTrustOptions(new PemTrustOptions().addCertPath("tls/server.crt"))
      );

    PgConnection.connect(vertx, options).onComplete( ctx.asyncAssertFailure(err -> {
      ctx.assertEquals(err.getMessage(), "SSL handshake failed");
    }));
  }

  @Test
  public void testSslModeVerifyFullCorrectHostname(TestContext ctx) {
    Vertx vertxWithHosts = Vertx.vertx(
      new VertxOptions()
        .setAddressResolverOptions(
          new AddressResolverOptions()
            .setHostsValue(Buffer.buffer("127.0.0.1 thebrain.ca\n"))
        )
    );

    PgConnectOptions options = ruleOptionalSll.options()
      .setSslMode(SslMode.VERIFY_FULL)
      // The hostname in the test certificate is thebrain.ca
      .setHost("thebrain.ca")
      .setSslOptions(
        new ClientSSLOptions()
          .setHostnameVerificationAlgorithm("HTTPS")
          .setTrustOptions(new PemTrustOptions().addCertPath("tls/server.crt"))
      );

    PgConnection.connect(vertxWithHosts, options).onComplete( ctx.asyncAssertSuccess(conn -> {
      ctx.assertTrue(conn.isSSL());
      vertxWithHosts.close();
    }));
  }
}
