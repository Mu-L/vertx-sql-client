/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.mssqlclient.impl.codec;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.vertx.sqlclient.ClosedConnectionException;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TdsMessageCodec extends CombinedChannelDuplexHandler<TdsMessageDecoder, TdsMessageEncoder> {

  private final ArrayDeque<MSSQLCommandMessage<?, ?>> inflight = new ArrayDeque<>();
  private final TdsMessageEncoder encoder;
  private final TdsMessageDecoder decoder;

  private ChannelHandlerContext chctx;
  private ByteBufAllocator alloc;
  private long transactionDescriptor;
  private Map<String, CursorData> cursorDataMap;

  public TdsMessageCodec(int desiredPacketSize) {
    decoder = new TdsMessageDecoder(this);
    encoder = new TdsMessageEncoder(this, desiredPacketSize);
    init(decoder, encoder);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
    chctx = ctx;
    alloc = chctx.alloc();
  }

  TdsMessageEncoder encoder() {
    return encoder;
  }

  TdsMessageDecoder decoder() {
    return decoder;
  }

  ChannelHandlerContext chctx() {
    return chctx;
  }

  ByteBufAllocator alloc() {
    return alloc;
  }

  long transactionDescriptor() {
    return transactionDescriptor;
  }

  void setTransactionDescriptor(long transactionDescriptor) {
    this.transactionDescriptor = transactionDescriptor;
  }

  MSSQLCommandMessage<?, ?> peek() {
    return inflight.peek();
  }

  MSSQLCommandMessage<?, ?> poll() {
    return inflight.poll();
  }

  void add(MSSQLCommandMessage<?, ?> codec) {
    inflight.add(codec);
  }

  CursorData getOrCreateCursorData(String cursorId) {
    if (cursorDataMap == null) {
      cursorDataMap = new HashMap<>();
    }
    CursorData cd = cursorDataMap.get(cursorId);
    if (cd == null) {
      cd = new CursorData();
      cursorDataMap.put(cursorId, cd);
    }
    return cd;
  }

  CursorData removeCursorData(String cursorId) {
    if (cursorDataMap == null) {
      return null;
    }
    CursorData cd = cursorDataMap.remove(cursorId);
    if (cursorDataMap.isEmpty()) {
      cursorDataMap = null;
    }
    return cd;
  }
}
