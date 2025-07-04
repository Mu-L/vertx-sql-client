/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.pgclient.impl;

import io.vertx.pgclient.impl.PgRow;
import io.vertx.tests.sqlclient.TestRowDescriptor;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class PgRowTest {
  enum EnumValue {
    SOME, NONE
  }
  @Test
  public void testGetNullEnum() {
    PgRow row = new PgRow(TestRowDescriptor.create("enum"));
    row.addValue(null);
    assertNull(row.get(EnumValue.class, 0));

    row.addValue(LocalDate.now());
    assertThrows(ClassCastException.class, () -> row.get(EnumValue.class, 1));
  }
}
