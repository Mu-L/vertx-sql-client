/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.oracleclient.impl.commands;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
import io.vertx.oracleclient.OraclePrepareOptions;
import io.vertx.sqlclient.PrepareOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.QueryResultHandler;
import io.vertx.sqlclient.impl.command.ExtendedQueryCommand;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OraclePreparedStatement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.stream.Collector;

public class OraclePreparedQueryCommand<C, R> extends OracleQueryCommand<C, R> {

  private final String sql;
  private final Tuple params;
  private final PrepareOptions prepareOptions;
  private final QueryResultHandler<R> resultHandler;

  public OraclePreparedQueryCommand(OracleConnection oracleConnection, ContextInternal connectionContext, ExtendedQueryCommand<R> cmd, Collector<Row, C, R> collector) {
    super(oracleConnection, connectionContext, collector);
    sql = cmd.sql();
    params = cmd.params();
    prepareOptions = cmd.options();
    resultHandler = cmd.resultHandler();
  }

  @Override
  protected OraclePrepareOptions prepareOptions() {
    return OraclePrepareOptions.createFrom(prepareOptions);
  }

  @Override
  protected String query() {
    return sql;
  }

  @Override
  protected void fillStatement(PreparedStatement ps, Connection conn) throws SQLException {
    for (int i = 0; i < params.size(); i++) {
      // we must convert types (to comply to JDBC)
      Object value = adaptType(conn, params.getValue(i));
      ps.setObject(i + 1, value);
    }
  }

  @Override
  protected Future<Boolean> doExecute(OraclePreparedStatement ps, boolean returnAutoGeneratedKeys) {
    return executeBlocking(ps::executeAsyncOracle)
      .compose(pub -> first(pub))
      .compose(returnedResultSet -> executeBlocking(() -> decode(ps, returnedResultSet, returnAutoGeneratedKeys)))
      .map(oracleResponse -> {
        oracleResponse.handle(resultHandler);
        return false;
      });
  }
}