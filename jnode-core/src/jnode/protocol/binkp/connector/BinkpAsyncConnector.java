/*
 * Licensed to the jNode FTN Platform Development Team (jNode Team)
 * under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for 
 * additional information regarding copyright ownership.  
 * The jNode Team licenses this file to you under the 
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package jnode.protocol.binkp.connector;

import static jnode.protocol.binkp.BinkpProtocolTools.getCommand;
import static jnode.protocol.binkp.BinkpProtocolTools.write;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import jnode.logger.Logger;
import jnode.protocol.binkp.exceprion.ConnectionEndException;
import jnode.protocol.binkp.types.BinkpCommand;
import jnode.protocol.binkp.types.BinkpFrame;
import jnode.protocol.binkp.util.AddressParser;

/**
 * TCP/IP connection
 * 
 * @author kreon
 * 
 */
public class BinkpAsyncConnector extends BinkpAbstractConnector {
	private static final Logger logger = Logger.getLogger(BinkpAsyncConnector.class);
	private Selector selector;
	private long connectionStartTime;
	private boolean connectionEstablished = false;

	/**
	 * accept ()
	 * 
	 * @param socket
	 * @throws Exception
	 */
	public BinkpAsyncConnector(SocketChannel socket) throws IOException {
		super();
		init(socket);

	}

	public BinkpAsyncConnector(String protocolAddress) throws IOException {
		super(protocolAddress);
		SocketChannel socket = SocketChannel.open();
		try {
			InetSocketAddress address = AddressParser.parseAddress(protocolAddress);
			socket.connect(address);
		} catch (IOException e) {
			throw new IOException("Invalid protocolAddress (" + protocolAddress
					+ ") for this scheme: " + e.getMessage());
		}
		init(socket);
	}

	private void init(SocketChannel socket) throws IOException {
		socket.configureBlocking(false);
		selector = Selector.open();
		socket.register(selector, socket.validOps());
		connectionStartTime = System.currentTimeMillis();
		connectionEstablished = socket.isConnected();
	}

	@Override
	public void run() {
		try {
			greet();
			int loopCounter = 0;
			while (true) {
				try {
					long timeoutToUse = connectionEstablished ? staticMaxTimeout : staticConnectTimeout;
					selector.select(timeoutToUse);
					
					// Check for connection timeout
					if (!connectionEstablished) {
						long elapsed = System.currentTimeMillis() - connectionStartTime;
						if (elapsed > staticConnectTimeout) {
							finish("Connection establishment timeout after " + (elapsed / 1000) + " seconds");
						}
					}
					
					loopCounter++;
					// logger.l5("Selector loop #" + loopCounter + ", keys=" + selector.selectedKeys().size());
					for (SelectionKey key : selector.selectedKeys()) {
						SocketChannel channel = (SocketChannel) key.channel();
						if (key.isValid()) {
							if (key.isConnectable()) {
								try {
									if (!channel.finishConnect()) {
										key.cancel();
										InetSocketAddress addr = (InetSocketAddress) channel
												.getLocalAddress();
										String host = addr != null ? addr.getHostString() : "unknown";
										finish("Connection failed to " + host + " - unable to complete connection");
									} else {
										connectionEstablished = true;
										InetSocketAddress addr = (InetSocketAddress) channel
												.getRemoteAddress();
										long elapsed = System.currentTimeMillis() - connectionStartTime;
										logger.l2(String.format(
												"Connected with %s:%d in %d ms",
												addr.getHostString(),
												addr.getPort(),
												elapsed));
									}
								} catch (ConnectException e) {
									key.cancel();
									InetSocketAddress addr = (InetSocketAddress) channel
											.getLocalAddress();
									String host = addr != null ? addr.getHostString() : "unknown";
									finish("Connection timed out to " + host + ": " + e.getLocalizedMessage());
								} catch (IOException e) {
									key.cancel();
									InetSocketAddress addr = (InetSocketAddress) channel
											.getLocalAddress();
									String host = addr != null ? addr.getHostString() : "unknown";
									finish("IO error connecting to " + host + ": " + e.getLocalizedMessage());
								}
							}
							if (key.isWritable()) {
								checkForMessages();
								// CRITICAL: Send ALL queued frames before processing any reads
								while (!frames.isEmpty()) {
									// DEBUG: Log queue state before sending
									if (frames.size() > 1) {
										logger.l5("BEFORE SEND: Queue has " + frames.size() + " frames");
										for (int i = 0; i < Math.min(3, frames.size()); i++) {
											BinkpFrame peek = frames.get(i);
											if (peek.getCommand() != null) {
												logger.l5("  Frame[" + i + "]: " + peek.getCommand() + " " + (peek.getArg() != null ? peek.getArg() : ""));
											} else {
												logger.l5("  Frame[" + i + "]: DATA " + (peek.getBytes().length - 2) + " bytes");
											}
										}
									}
									
									BinkpFrame frame = frames.removeFirst();
									logger.l5("SENDING Frame: " + frame
											+ ", next " + frames.size()
											+ " frames, total sent "
											+ total_sent_bytes);
									
									try {
										// Track actual bytes sent
										int bytesSent = 0;
										if (frame.getCommand() == null) {
											// Data frame
											bytesSent = frame.getBytes().length;
											logger.l5("SENDING DATA to network: " + bytesSent + " bytes");
										}
										
										write(frame, channel);
										
										if (frame.getCommand() == null) {
											// Log first few bytes of data frame to verify content
											byte[] data = frame.getBytes();
											StringBuilder hex = new StringBuilder();
											for (int i = 2; i < Math.min(data.length, 18); i++) {
												hex.append(String.format("%02X ", data[i] & 0xFF));
											}
											logger.l5("DATA SENT to network: " + (data.length - 2) + " bytes, preview: " + hex.toString());
										} else {
											logger.l5("COMMAND SENT to network: " + frame);
										}
									} catch (Exception e) {
										logger.l5("SEND FAILED: " + frame + ", error: " + e.getMessage());
										throw e;
									}
								}
							}
							if (!isConnected()) {
								finish("Connect ended");
							}
							// CRITICAL: Only process reads AFTER all writes are complete
							if (key.isReadable()) {
								logger.l5("Key is READABLE, frames.isEmpty()=" + frames.isEmpty());
								if (!frames.isEmpty()) {
									logger.l5("SKIPPING READ: Still have " + frames.size() + " frames to send");
								} else {
								BinkpFrame frame = null;
								ByteBuffer head = ByteBuffer.allocate(2);
								for (int len = 0; len < 2;) {
									len += readOrDie(head, channel);
								}
								((Buffer)head).flip();
								int header = ((int) head.getShort()) & 0xffff;
								int datalen = header & 0x7fff;
								ByteBuffer data = ByteBuffer.allocate(datalen);
								for (int len = 0; len < datalen;) {
									len += readOrDie(data, channel);
								}
								((Buffer)data).flip();
								if ((header & 0x8000) >= 0x8000) {
									// command
									BinkpCommand cmd = getCommand(data.get());
									if (datalen > 1) {
										if (data.get(datalen - 1) == 0) {
											datalen--;
										}
										byte[] buf = new byte[datalen - 1];
										data.get(buf);
										frame = new BinkpFrame(cmd, new String(
												buf));
									} else {
										frame = new BinkpFrame(cmd);
									}
								} else {
									frame = new BinkpFrame(data.array(),
											datalen);
								}
								if (frame != null) {
									logger.l5("Frame received: " + frame);
									proccessFrame(frame);
								}
								}
							}
						} else {
							finish("Key is invalid");
						}
					}
					// CRITICAL: Clear selected keys after processing to prevent re-processing
					selector.selectedKeys().clear();
				} catch (ConnectException e) {
					error("Connection timeout: " + e.getLocalizedMessage());
				} catch (SocketTimeoutException e) {
					error("Socket timeout: " + e.getLocalizedMessage());
				} catch (UnknownHostException e) {
					error("Unknown host: " + e.getLocalizedMessage());
				} catch (IOException e) {
					error("IO error: " + e.getLocalizedMessage());
				}
			}
		} catch (ConnectionEndException e) {
			try {
				for (SelectionKey key : selector.keys()) {
					key.channel().close();
					key.cancel();
				}
				selector.close();
				if (currentOS != null) {
					currentOS.close();
				}
			} catch (IOException e2) {
				logger.l2("Error while closing key", e2);
			}
			done();
		}
	}

	private int readOrDie(ByteBuffer buffer, SocketChannel channel)
			throws IOException {
		int x = channel.read(buffer);
		if (x == -1) {
			if (flag_leob && flag_reob) {
				connectionState = STATE_END;
			}
			finish("readOrDie failed");
		}
		return x;
	}
}
