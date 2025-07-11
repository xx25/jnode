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

package jnode.protocol.binkp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jnode.dto.Link;
import jnode.ftn.tosser.FtnTosser;
import jnode.logger.Logger;
import jnode.protocol.binkp.connector.BinkpAbstractConnector;
import jnode.protocol.binkp.types.BinkpCommand;
import jnode.protocol.binkp.types.BinkpFrame;
import jnode.protocol.binkp.util.FilenameEscaper;
import jnode.protocol.io.Message;

public class BinkpProtocolTools {
	private static final Logger logger = Logger.getLogger(BinkpProtocolTools.class);

	public static byte hex2decimal(String s) {
		String digits = "0123456789ABCDEF";
		s = s.toUpperCase();
		byte val = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			int d = digits.indexOf(c);
			val = (byte) (16 * val + d);
		}
		return val;
	}

	public static String getAuthPassword(Link foreignLink, boolean secure,
			String cramAlgo, String cramText) {
		logger.l5("cramAlgo = " + cramAlgo);
		logger.l5("cramText = " + cramText);
		MessageDigest md;
		String password = (secure) ? foreignLink.getProtocolPassword() : "-";
		logger.l5("protocol password = [REDACTED]");
		if (!secure || cramAlgo == null || cramText == null) {
			return password;
		} else {
			try {
				md = MessageDigest.getInstance(cramAlgo);
			} catch (NoSuchAlgorithmException e) {
				return password;
			}
			byte[] text = new byte[cramText.length() / 2];
			byte[] key = password.getBytes();
			byte[] k_ipad = new byte[64];
			byte[] k_opad = new byte[64];
			for (int i = 0; i < cramText.length(); i += 2) {
				text[i / 2] = hex2decimal(cramText.substring(i, i + 2));
			}

			for (int i = 0; i < key.length; i++) {
				k_ipad[i] = key[i];
				k_opad[i] = key[i];
			}

			for (int i = 0; i < 64; i++) {
				k_ipad[i] ^= 0x36;
				k_opad[i] ^= 0x5c;
			}
			md.update(k_ipad);
			md.update(text);
			byte[] digest = md.digest();
			md.update(k_opad);
			md.update(digest);
			digest = md.digest();
			StringBuilder builder = new StringBuilder();
			builder.append("CRAM-" + cramAlgo + "-");
			for (int i = 0; i < 16; i++) {
				builder.append(String.format("%02x", digest[i]));
			}
			return builder.toString();
		}
	}

	public static boolean available(Socket socket) {
		try {
			return socket.getInputStream().available() > 2;
		} catch (IOException e) {
			return false;
		}
	}

	public static BinkpCommand getCommand(int command) {
		for (BinkpCommand c : BinkpCommand.values()) {
			if (c.getCmd() == command) {
				return c;
			}
		}
		return null;
	}

	public static int write(BinkpFrame frame, SocketChannel socket) {
		if (frame != null) {
			try {
				logger.l5("write() called with frame: " + frame);
				ByteBuffer buf = ByteBuffer.wrap(frame.getBytes());
				int totalBytes = buf.remaining();
				int writtenBytes = 0;
				
				while (buf.hasRemaining()) {
					int written = socket.write(buf);
					writtenBytes += written;
					if (written == 0) {
						logger.l2("CRITICAL: channel.write() returned 0, remaining=" + buf.remaining() + ", socket.isConnected()=" + socket.isConnected());
						Thread.yield(); // Give the channel a moment
					} else if (written > 0) {
						logger.l5("Network write: " + written + " bytes written, total so far: " + writtenBytes + "/" + totalBytes);
					}
				}
				logger.l5("NETWORK TRANSMISSION COMPLETE: " + writtenBytes + "/" + totalBytes + " bytes for " + (frame.getCommand() != null ? "command " + frame.getCommand() : "data frame"));
				return 1;
			} catch (IOException e) {
				e.printStackTrace();
				return 0;
			}
		}
		return 0;
	}

	public static boolean messageEquals(Message message, String arg) {
		// Compare using escaped filename for consistency
		return (getString(message).equals(arg));
	}

	public static Message createMessage(String arg, boolean secure) {
		String[] args = arg.split(" ");
		try {
			// Unescape filename according to BinkP specification
			String filename = FilenameEscaper.unescape(args[0]);
			Long len = Long.valueOf(args[1]);
			Long unixtime = Long.valueOf(args[2]);
			Message message = new Message(filename, len);
			message.setUnixtime(unixtime);
			message.setSecure(secure);
			return message;
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static String getString(Message message, int skip) {
		// Escape filename according to BinkP specification
		String escapedFilename = FilenameEscaper.escape(message.getMessageName());
		return String.format("%s %d %d %d", escapedFilename,
				message.getMessageLength(), message.getUnixtime(), skip);
	}

	public static String getString(Message message) {
		// Escape filename according to BinkP specification
		String escapedFilename = FilenameEscaper.escape(message.getMessageName());
		return String.format("%s %d %d", escapedFilename,
				message.getMessageLength(), message.getUnixtime());
	}

	public static int forwardToTossing(Message message, File file,
			OutputStream os) {
		InputStream is = null;
		try {
			is = (file != null) ? new FileInputStream(file)
					: new ByteArrayInputStream(
							((ByteArrayOutputStream) os).toByteArray());
			message.setInputStream(is);
			int ret = FtnTosser.tossIncoming(message);
			if (file != null) {
				file.delete();
			}
			return ret;
		} catch (IOException e) {
		}
		return 1;
	}
}
