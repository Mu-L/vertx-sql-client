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
import io.vertx.mysqlclient.MySQLException;
import io.vertx.mysqlclient.impl.command.ChangeUserCommand;
import io.vertx.mysqlclient.impl.command.DebugCommand;
import io.vertx.mysqlclient.impl.command.InitDbCommand;
import io.vertx.mysqlclient.impl.command.InitialHandshakeCommand;
import io.vertx.mysqlclient.impl.command.PingCommand;
import io.vertx.mysqlclient.impl.command.ResetConnectionCommand;
import io.vertx.mysqlclient.impl.command.SetOptionCommand;
import io.vertx.mysqlclient.impl.command.StatisticsCommand;
import io.vertx.mysqlclient.impl.datatype.DataType;
import io.vertx.mysqlclient.impl.protocol.CapabilitiesFlag;
import io.vertx.mysqlclient.impl.protocol.ColumnDefinition;
import io.vertx.mysqlclient.impl.util.BufferUtils;
import io.vertx.sqlclient.codec.CommandMessage;
import io.vertx.sqlclient.spi.protocol.CloseConnectionCommand;
import io.vertx.sqlclient.spi.protocol.CloseCursorCommand;
import io.vertx.sqlclient.spi.protocol.CloseStatementCommand;
import io.vertx.sqlclient.spi.protocol.CommandBase;
import io.vertx.sqlclient.codec.CommandResponse;
import io.vertx.sqlclient.spi.protocol.PrepareStatementCommand;
import io.vertx.sqlclient.spi.protocol.SimpleQueryCommand;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static io.vertx.mysqlclient.impl.protocol.Packets.*;

public abstract class MySQLCommand<R, C extends CommandBase<R>> extends CommandMessage<R, C> {

  public Throwable failure;
  public R result;
  MySQLEncoder encoder;
  int sequenceId;

  MySQLCommand(C cmd) {
    super(cmd);
  }

  public static MySQLCommand<?, ?> wrap(CommandBase<?> cmd) {
    if (cmd instanceof InitialHandshakeCommand) {
      return new InitialHandshakeMySQLCommand((InitialHandshakeCommand) cmd);
    } else if (cmd instanceof SimpleQueryCommand) {
      return new SimpleQueryMySQLCommand<>((SimpleQueryCommand<?>) cmd);
    } else if (cmd instanceof CloseConnectionCommand) {
      return new CloseConnectionMySQLCommand((CloseConnectionCommand) cmd);
    } else if (cmd instanceof PrepareStatementCommand) {
      return new PrepareStatementMySQLCommand((PrepareStatementCommand) cmd);
    } else if (cmd instanceof CloseStatementCommand) {
      return new CloseStatementMySQLCommand((CloseStatementCommand) cmd);
    } else if (cmd instanceof CloseCursorCommand) {
      return new ResetStatementMySQLCommand((CloseCursorCommand) cmd);
    } else if (cmd instanceof PingCommand) {
      return new PingMySQLCommand((PingCommand) cmd);
    } else if (cmd instanceof InitDbCommand) {
      return new InitDbMySQLCommand((InitDbCommand) cmd);
    } else if (cmd instanceof StatisticsCommand) {
      return new StatisticsMySQLCommand((StatisticsCommand) cmd);
    } else if (cmd instanceof SetOptionCommand) {
      return new SetOptionMySQLCommand((SetOptionCommand) cmd);
    } else if (cmd instanceof ResetConnectionCommand) {
      return new ResetConnectionMySQLCommand((ResetConnectionCommand) cmd);
    } else if (cmd instanceof DebugCommand) {
      return new DebugMySQLCommand((DebugCommand) cmd);
    } else if (cmd instanceof ChangeUserCommand) {
      return new ChangeUserMySQLCommand((ChangeUserCommand) cmd);
    } else {
      System.out.println("Unsupported command " + cmd);
      throw new UnsupportedOperationException("Todo");
    }
  }

  abstract void decodePayload(ByteBuf payload, int payloadLength);

  void encode(MySQLEncoder encoder) {
    this.encoder = encoder;
    this.sequenceId = 0;
  }

  ByteBuf allocateBuffer() {
    return encoder.chctx.alloc().ioBuffer();
  }

  ByteBuf allocateBuffer(int capacity) {
    return encoder.chctx.alloc().ioBuffer(capacity);
  }

  void sendPacket(ByteBuf packet, int payloadLength) {
    if (payloadLength >= PACKET_PAYLOAD_LENGTH_LIMIT) {
      /*
         The original packet exceeds the limit of packet length, split the packet here.
         if payload length is exactly 16MBytes-1byte(0xFFFFFF), an empty packet is needed to indicate the termination.
       */
      sendSplitPacket(packet);
    } else {
      sendNonSplitPacket(packet);
    }
  }

  private void sendSplitPacket(ByteBuf packet) {
    ByteBuf payload = packet.skipBytes(4);
    while (payload.readableBytes() >= PACKET_PAYLOAD_LENGTH_LIMIT) {
      // send a packet with 0xFFFFFF length payload
      ByteBuf packetHeader = allocateBuffer(4);
      packetHeader.writeMediumLE(PACKET_PAYLOAD_LENGTH_LIMIT);
      packetHeader.writeByte(sequenceId++);
      encoder.chctx.write(packetHeader, encoder.chctx.voidPromise());
      encoder.chctx.write(payload.readRetainedSlice(PACKET_PAYLOAD_LENGTH_LIMIT), encoder.chctx.voidPromise());
    }

    // send a packet with last part of the payload
    ByteBuf packetHeader = allocateBuffer(4);
    packetHeader.writeMediumLE(payload.readableBytes());
    packetHeader.writeByte(sequenceId++);
    encoder.chctx.write(packetHeader, encoder.chctx.voidPromise());
    encoder.chctx.writeAndFlush(payload, encoder.chctx.voidPromise());
  }

  void sendNonSplitPacket(ByteBuf packet) {
    sequenceId++;
    encoder.chctx.writeAndFlush(packet, encoder.chctx.voidPromise());
  }

  final void sendBytesAsPacket(byte[] payload) {
    int payloadLength = payload.length;
    ByteBuf packet = allocateBuffer(payloadLength + 4);
    // encode packet header
    packet.writeMediumLE(payloadLength);
    packet.writeByte(sequenceId);

    // encode packet payload
    packet.writeBytes(payload);

    sendNonSplitPacket(packet);
  }

  void handleOkPacketOrErrorPacketPayload(ByteBuf payload) {
    int header = payload.getUnsignedByte(payload.readerIndex());
    switch (header) {
      case EOF_PACKET_HEADER:
      case OK_PACKET_HEADER:
        encoder.fireCommandResponse(CommandResponse.success(null));
        break;
      case ERROR_PACKET_HEADER:
        handleErrorPacketPayload(payload);
        break;
    }
  }

  void handleErrorPacketPayload(ByteBuf payload) {
    MySQLException mySQLException = decodeErrorPacketPayload(payload);
    encoder.fireCommandResponse(CommandResponse.failure(mySQLException));
  }

  final MySQLException decodeErrorPacketPayload(ByteBuf payload) {
    payload.skipBytes(1); // skip ERR packet header
    int errorCode = payload.readUnsignedShortLE();
    // CLIENT_PROTOCOL_41 capability flag will always be set
    payload.skipBytes(1); // SQL state marker will always be #
    String sqlState = BufferUtils.readFixedLengthString(payload, 5, StandardCharsets.UTF_8);
    String errorMessage = readRestOfPacketString(payload, StandardCharsets.UTF_8);
    return new MySQLException(errorMessage, errorCode, sqlState);
  }

  // simplify the ok packet as those properties are actually not used for now
  OkPacket decodeOkPacketPayload(ByteBuf payload) {
    payload.skipBytes(1); // skip OK packet header
    int affectedRows = (int) BufferUtils.readLengthEncodedInteger(payload);
    long lastInsertId = BufferUtils.readLengthEncodedInteger(payload);
    int serverStatusFlags = payload.readUnsignedShortLE();
    return new OkPacket(affectedRows, lastInsertId, serverStatusFlags);
  }

  EofPacket decodeEofPacketPayload(ByteBuf payload) {
    payload.skipBytes(1); // skip EOF_Packet header
    int numberOfWarnings = payload.readUnsignedShortLE();
    int serverStatusFlags = payload.readUnsignedShortLE();
    return new EofPacket(numberOfWarnings, serverStatusFlags);
  }

  String readRestOfPacketString(ByteBuf payload, Charset charset) {
    return BufferUtils.readFixedLengthString(payload, payload.readableBytes(), charset);
  }

  ColumnDefinition decodeColumnDefinitionPacketPayload(ByteBuf payload) {
    int start = payload.readerIndex();
    int bytesToSkip = 0;

    bytesToSkip += BufferUtils.countBytesOfLengthEncodedString(payload, start + bytesToSkip); // catalog
    bytesToSkip += BufferUtils.countBytesOfLengthEncodedString(payload, start + bytesToSkip); // schema
    bytesToSkip += BufferUtils.countBytesOfLengthEncodedString(payload, start + bytesToSkip); // table
    bytesToSkip += BufferUtils.countBytesOfLengthEncodedString(payload, start + bytesToSkip); // orgTable

    payload.skipBytes(bytesToSkip);

    String name = BufferUtils.readLengthEncodedString(payload, StandardCharsets.UTF_8);

    start = payload.readerIndex();
    bytesToSkip = 0;

    bytesToSkip += BufferUtils.countBytesOfLengthEncodedString(payload, start + bytesToSkip); // orgName
    bytesToSkip += BufferUtils.countBytesOfLengthEncodedInteger(payload, start + bytesToSkip); // lengthOfFixedLengthFields

    int characterSet = payload.getUnsignedShortLE(start + bytesToSkip);
    bytesToSkip += 6; // characterSet + columnLength

    short type = payload.getUnsignedByte(start + bytesToSkip);
    bytesToSkip++;

    int flags = payload.getUnsignedShortLE(start + bytesToSkip);
    bytesToSkip += 2; // flags + decimals

    payload.skipBytes(bytesToSkip);

    // convert type+characterset+flags to dataType
    DataType dataType = DataType.parseDataType(type, characterSet, flags);
    return new ColumnDefinition(name, characterSet, dataType, flags);
  }

  void skipEofPacketIfNeeded(ByteBuf payload) {
    if (!isDeprecatingEofFlagEnabled()) {
      payload.skipBytes(5);
    }
  }

  boolean isDeprecatingEofFlagEnabled() {
    return (encoder.clientCapabilitiesFlag & CapabilitiesFlag.CLIENT_DEPRECATE_EOF) != 0;
  }

  boolean expectNoResponsePacket() {
    return false;
  }
}
