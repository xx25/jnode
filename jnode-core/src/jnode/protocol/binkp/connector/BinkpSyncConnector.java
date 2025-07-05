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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

import jnode.logger.Logger;
import jnode.main.threads.ThreadPool;
import jnode.protocol.binkp.BinkpProtocolTools;
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
public class BinkpSyncConnector extends BinkpAbstractConnector {
	private static final Logger logger = Logger.getLogger(BinkpSyncConnector.class);
	private volatile Socket socket;
	private volatile boolean closed = false;
	private volatile boolean connected = true;
	
	public BinkpSyncConnector(String protocolAddress) throws IOException {
		super(protocolAddress);
		try {
			socket = new Socket();
			InetSocketAddress address = AddressParser.parseAddress(protocolAddress);
			socket.connect(address);
		} catch (IOException e) {
			throw new IOException("Invalid protocolAddress (" + protocolAddress
					+ ") for this scheme: " + e.getMessage());
		}
	}

	@Override
	public void run() {
		Runnable processOutputObserver = new Runnable() {

			@Override
			public void run() {
				logger.l5("processOutputObserver thread started");
				boolean last = false;
				while (connected) {
					connected = isConnected();
					
					if (socket == null || last) {
						break;
					}
					if (closed) {
						last = true;
					}
					checkForMessages();
					logger.l5("Frames queue size: " + frames.size());
					if (frames.isEmpty()) {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
						}
					} else {
						try {
							BinkpFrame frame = frames.removeFirst();
							byte[] frameBytes = frame.getBytes();
							
							if (frame.getCommand() != null) {
								logger.l5("Sending command frame: " + frame.getCommand() + 
									", arg: " + frame.getArg() + ", size: " + frameBytes.length);
							} else {
								logger.l5("Sending data frame, size: " + frameBytes.length);
								// Log first few bytes
								if (frameBytes.length > 2) {
									StringBuilder sb = new StringBuilder();
									for (int i = 0; i < Math.min(frameBytes.length, 18); i++) {
										sb.append(String.format("%02X ", frameBytes[i] & 0xFF));
									}
									logger.l5("Frame bytes: " + sb.toString() + 
										(frameBytes.length > 18 ? "..." : ""));
								}
							}
							try {
								socket.getOutputStream().write(frameBytes);
								socket.getOutputStream().flush();
								logger.l5("Frame sent successfully");
							} catch (IOException e) {
								logger.l5("IOException sending frame: " + e.getLocalizedMessage());
								break;
							}
						} catch (NoSuchElementException ignore) {
						}
					}
				}
				logger.l5("processOutputObserver thread exiting");
				closed = true;
				return;
			}
		};
		ThreadPool.execute(processOutputObserver);

		try {
			greet();
			while (!closed) {
				if (!isConnected()) {
					try {
						Thread.sleep(100); // let's proccess to write messages;
					} catch (InterruptedException ignore) {
					}
					continue;
				}
				try {
					int[] head = new int[2];
					for (int i = 0; i < 2; i++) {
						head[i] = readOrDie(socket.getInputStream());
					}
					int len = ((head[0] & 0xff) << 8 | (head[1] & 0xff)) & 0x7FFF;
					boolean command = (head[0] & 0x80) > 0;
					
					// Drop empty frames as per BinkP specification (FTS-1026)
					// "Empty frames (SIZE=0): Obsolete, SHOULD NOT be used"
					// "Handling empty frames: Silently drop, treat total length as 2"
					if (len == 0) {
						logger.l5("Dropping empty frame (SIZE=0) as per BinkP specification");
						continue;
					}
					
					// Protect against oversized frames that violate BinkP specification
					if (len > 32767) {
						error(String.format("Received frame size %d exceeds BinkP maximum of 32767 bytes", len));
						break;
					}
					
					int remaining = len;
					ByteBuffer data = ByteBuffer.allocate(len);
					while (remaining > 0) {
						byte[] buf = readOrDie(socket.getInputStream(),
								remaining);
						remaining -= buf.length;
						data.put(buf);
					}
					((Buffer)data).flip();
					BinkpFrame frame;
					if (command) {
						if (len < 1) {
							logger.l3("Malformed command frame: no command byte");
							continue;
						}
						BinkpCommand cmd = BinkpProtocolTools.getCommand(data.get());
						if (cmd == null) {
							int unknownCmd = data.array()[0] & 0xFF;
							logger.l3("Unknown command received: " + unknownCmd + " (ignoring as per BinkP specification)");
							continue;
						}
						
						// Handle null-terminated command arguments
						if (len > 1) {
							if (data.get(len - 1) == 0) {
								len--;
							}
							byte[] ndata = new byte[len - 1];
							data.get(ndata);
							
							try {
								String arg = new String(ndata, StandardCharsets.UTF_8);
								frame = new BinkpFrame(cmd, arg);
							} catch (Exception e) {
								logger.l3("Malformed command argument encoding, using default: " + e.getMessage());
								frame = new BinkpFrame(cmd, new String(ndata));
							}
						} else {
							frame = new BinkpFrame(cmd);
						}
					} else {
						try {
							frame = new BinkpFrame(data.array());
						} catch (Exception e) {
							logger.l3("Failed to create data frame: " + e.getMessage());
							continue;
						}
					}
					logger.l5("Frame received: " + frame + 
						", command=" + (command ? "yes" : "no") + 
						", len=" + len);
					proccessFrame(frame);
				} catch (IOException e) {
					error("IOException");
				}
			}
			finish("Connection closed");
		} catch (ConnectionEndException e) {
			try {
				Thread.sleep(100); // let's proccess to write messages;
				socket.close();
			} catch (InterruptedException ignore) {
			} catch (IOException ignore) {
			}
			closed = true;
			logger.l5("Connection end: " + e.getLocalizedMessage());
			socket = null;
			done();
		}
	}

	private int readOrDie(InputStream inputStream) {
		try {
			int x = inputStream.read();
			if (x == -1) {
				if (flag_leob && flag_reob) {
					connectionState = STATE_END;
				}
				finish("readOrDie(1) EOF");
			}
			return x;
		} catch (IOException e) {
			finish("readOrDie(1) Exception");
			return -1;
		}
	}

	private byte[] readOrDie(InputStream inputStream, int remaining) {
		try {
			int len = (remaining > staticBufMaxSize) ? staticBufMaxSize
					: remaining;
			byte[] buf = new byte[len];
			int x = inputStream.read(buf);
			if (x == -1) {
				if (flag_leob && flag_reob) {
					connectionState = STATE_END;
				}
				finish("readOrDie(2) EOF");
			}
			ByteBuffer ret = ByteBuffer.wrap(buf, 0, x);
			return ret.array();
		} catch (IOException e) {
			finish("readOrDie(2) Exception");
			return new byte[] {};
		}
	}
}
