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

package io.vertx.sqlclient.impl;

import io.vertx.sqlclient.PropertyKind;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.desc.ColumnDescriptor;
import io.vertx.sqlclient.desc.RowDescriptor;
import io.vertx.sqlclient.internal.RowDescriptorBase;

import java.util.List;

public abstract class SqlResultBase<T> implements SqlResult<T> {

  int updated;
  RowDescriptorBase rowDescriptor;
  int size;
  SqlResult<T> next;
  protected PropertyKindMap properties;

  @Override
  public List<String> columnsNames() {
    RowDescriptorBase desc = rowDescriptor;
    return desc != null ? desc.columnNames() : null;
  }

  @Override
  public RowDescriptor rowDescriptor() {
    return rowDescriptor;
  }

  @Override
  public List<ColumnDescriptor> columnDescriptors() {
    RowDescriptorBase desc = rowDescriptor;
    return desc != null ? desc.columnDescriptors() : null;
  }

  @Override
  public int rowCount() {
    return updated;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public <V> V property(PropertyKind<V> property) {
    if (property == null) {
      throw new IllegalArgumentException("Property can not be null");
    }
    if (properties == null) {
      return null;
    } else {
      Object value = properties.get(property);
      if (value == null) {
        return null;
      }
      Class<V> type = property.type();
      if (type.isInstance(value)) {
        return type.cast(value);
      } else {
        throw new IllegalArgumentException("Invalid property kind: " + value.getClass().getName() + " is not an instance of " + type.getName());
      }
    }
  }

  @Override
  public SqlResult<T> next() {
    return next;
  }

}
