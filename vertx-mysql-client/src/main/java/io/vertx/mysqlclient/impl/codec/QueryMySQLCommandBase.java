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
package io.vertx.mysqlclient.impl.codec;

import io.netty.buffer.ByteBuf;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.mysqlclient.impl.MySQLRowDescriptor;
import io.vertx.mysqlclient.impl.datatype.DataFormat;
import io.vertx.mysqlclient.impl.protocol.ColumnDefinition;
import io.vertx.mysqlclient.impl.util.BufferUtils;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.internal.RowDescriptorBase;
import io.vertx.sqlclient.codec.CommandResponse;
import io.vertx.sqlclient.spi.protocol.QueryCommandBase;

import java.util.stream.Collector;

import static io.vertx.mysqlclient.impl.protocol.Packets.*;

abstract class QueryMySQLCommandBase<T, C extends QueryCommandBase<T>> extends MySQLCommand<Boolean, C> {

  private final DataFormat format;

  protected CommandHandlerState commandHandlerState = CommandHandlerState.INIT;
  protected ColumnDefinition[] columnDefinitions;
  protected RowResultDecoder<?, T> decoder;
  private int currentColumn;

  QueryMySQLCommandBase(C cmd, DataFormat format) {
    super(cmd);
    this.format = format;
  }

  private static <A, T> T emptyResult(Collector<Row, A, T> collector) {
    return collector.finisher().apply(collector.supplier().get());
  }

  @Override
  void decodePayload(ByteBuf payload, int payloadLength) {
    switch (commandHandlerState) {
      case INIT:
        handleInitPacket(payload);
        break;
      case HANDLING_COLUMN_DEFINITION:
        handleResultsetColumnDefinitions(payload);
        break;
      case COLUMN_DEFINITIONS_DECODING_COMPLETED:
        skipEofPacketIfNeeded(payload);
        handleResultsetColumnDefinitionsDecodingCompleted();
        break;
      case HANDLING_ROW_DATA_OR_END_PACKET:
        handleRows(payload, payloadLength);
        break;
    }
  }

  protected abstract void handleInitPacket(ByteBuf payload);

  protected void handleResultsetColumnCountPacketBody(ByteBuf payload) {
    int columnCount = decodeColumnCountPacketPayload(payload);
    commandHandlerState = CommandHandlerState.HANDLING_COLUMN_DEFINITION;
    columnDefinitions = new ColumnDefinition[columnCount];
  }

  protected void handleResultsetColumnDefinitions(ByteBuf payload) {
    ColumnDefinition def = decodeColumnDefinitionPacketPayload(payload);
    columnDefinitions[currentColumn++] = def;
    if (currentColumn == columnDefinitions.length) {
      // all column definitions have been decoded, switch to column definitions decoding completed state
      if (isDeprecatingEofFlagEnabled()) {
        // we enabled the DEPRECATED_EOF flag and don't need to accept an EOF_Packet
        handleResultsetColumnDefinitionsDecodingCompleted();
      } else {
        // we need to decode an EOF_Packet before handling rows, to be compatible with MySQL version below 5.7.5
        commandHandlerState = CommandHandlerState.COLUMN_DEFINITIONS_DECODING_COMPLETED;
      }
    }
  }

  protected void handleResultsetColumnDefinitionsDecodingCompleted() {
    commandHandlerState = CommandHandlerState.HANDLING_ROW_DATA_OR_END_PACKET;
    MySQLRowDescriptor mySQLRowDesc = MySQLRowDescriptor.create(columnDefinitions, format); // use the column definitions if provided by execute or fetch response instead of prepare response
    decoder = new RowResultDecoder<>(cmd.collector(), mySQLRowDesc);
  }

  protected void handleRows(ByteBuf payload, int payloadLength) {
  /*
    Resultset row can begin with 0xfe byte (when using text protocol with a field length > 0xffffff)
    To ensure that packets beginning with 0xfe correspond to the ending packet (EOF_Packet or OK_Packet with a 0xFE header),
    the packet length must be checked and must be less than 0xffffff in length.
   */
    int first = payload.getUnsignedByte(payload.readerIndex());
    if (first == ERROR_PACKET_HEADER) {
      handleErrorPacketPayload(payload);
    }
    // enabling CLIENT_DEPRECATE_EOF capability will receive an OK_Packet with a EOF_Packet header here
    // we need check this is not a row data by checking packet length < 0xFFFFFF
    else if (first == EOF_PACKET_HEADER && payloadLength < 0xFFFFFF) {
      int serverStatusFlags;
      int affectedRows = -1;
      long lastInsertId = -1;
      if (isDeprecatingEofFlagEnabled()) {
        OkPacket okPacket = decodeOkPacketPayload(payload);
        serverStatusFlags = okPacket.serverStatusFlags();
        affectedRows = okPacket.affectedRows();
        lastInsertId = okPacket.lastInsertId();
      } else {
        serverStatusFlags = decodeEofPacketPayload(payload).serverStatusFlags();
      }
      handleSingleResultsetDecodingCompleted(serverStatusFlags, affectedRows, lastInsertId);
    } else {
      // accept a row data
      decoder.handleRow(columnDefinitions.length, payload);
    }
  }

  protected void handleSingleResultsetDecodingCompleted(int serverStatusFlags, int affectedRows, long lastInsertId) {
    handleSingleResultsetEndPacket(serverStatusFlags, affectedRows, lastInsertId);
    resetIntermediaryResult();
    if (isDecodingCompleted(serverStatusFlags)) {
      // no more sql result
      handleAllResultsetDecodingCompleted();
    }
  }

  protected boolean isDecodingCompleted(int serverStatusFlags) {
    return (serverStatusFlags & ServerStatusFlags.SERVER_MORE_RESULTS_EXISTS) == 0;
  }

  private void handleSingleResultsetEndPacket(int serverStatusFlags, int affectedRows, long lastInsertId) {
    this.result = (serverStatusFlags & ServerStatusFlags.SERVER_STATUS_LAST_ROW_SENT) == 0;
    T result;
    Throwable failure;
    int size;
    RowDescriptorBase rowDescriptor;
    if (decoder != null) {
      failure = decoder.complete();
      result = decoder.result();
      rowDescriptor = decoder.rowDesc;
      size = decoder.size();
      decoder.reset();
    } else {
      result = emptyResult(cmd.collector());
      failure = null;
      size = 0;
      rowDescriptor = null;
    }
    cmd.resultHandler().handleResult(affectedRows, size, rowDescriptor, result, failure);
    if (lastInsertId > 0) {
      cmd.resultHandler().addProperty(MySQLClient.LAST_INSERTED_ID, lastInsertId);
    }
  }

  protected void handleAllResultsetDecodingCompleted() {
    CommandResponse<Boolean> response;
    if (this.failure != null) {
      response = CommandResponse.failure(this.failure);
    } else {
      response = CommandResponse.success(this.result);
    }
    encoder.fireCommandResponse(response);
  }

  private int decodeColumnCountPacketPayload(ByteBuf payload) {
    long columnCount = BufferUtils.readLengthEncodedInteger(payload);
    return (int) columnCount;
  }

  private void resetIntermediaryResult() {
    commandHandlerState = CommandHandlerState.INIT;
    columnDefinitions = null;
    currentColumn = 0;
  }

  protected enum CommandHandlerState {
    INIT,
    HANDLING_COLUMN_DEFINITION,
    COLUMN_DEFINITIONS_DECODING_COMPLETED,
    HANDLING_ROW_DATA_OR_END_PACKET
  }
}
