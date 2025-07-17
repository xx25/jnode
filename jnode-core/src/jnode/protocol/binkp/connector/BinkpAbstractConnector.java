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

import jnode.core.ConcurrentDateFormatAccess;
import jnode.dto.Link;
import jnode.dto.LinkOption;
import jnode.event.ConnectionEndEvent;
import jnode.event.Notifier;
import jnode.ftn.FtnTools;
import jnode.ftn.types.FtnAddress;
import jnode.logger.Logger;
import jnode.main.MainHandler;
import jnode.main.SystemInfo;
import jnode.main.threads.PollQueue;
import jnode.main.threads.ThreadPool;
import jnode.main.threads.TosserQueue;
import jnode.ndl.NodelistScanner;
import jnode.protocol.binkp.exceprion.ConnectionEndException;
import jnode.protocol.binkp.types.BinkpCommand;
import jnode.protocol.binkp.types.BinkpFrame;
import jnode.protocol.io.Message;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jnode.protocol.binkp.BinkpProtocolTools.*;

/**
 * Abstract binkp through any protocol
 * 
 * @author kreon
 * 
 */
public abstract class BinkpAbstractConnector implements Runnable {
	private static final Logger logger = Logger.getLogger(BinkpAbstractConnector.class);

	private static final ConcurrentDateFormatAccess format = new ConcurrentDateFormatAccess(
			"EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
	private static final Pattern cramPattern = Pattern
			.compile("^CRAM-([-A-Z0-9]+)-([a-f0-9]+)$");
	private static final int STATE_GREET = 0;
	protected static final int STATE_ERROR = 1;
	protected static final int STATE_END = 2;
	private static final int STATE_ADDR = 3;
	private static final int STATE_AUTH = 4;
	private static final int STATE_TRANSFER = 5;
	private static final String BINKP_NETWORK_NAME = "binkp.network";
	private static final String BINKP_MAX_MEM = "binkp.maxmem";
	private static final String BINKP_TEMP = "binkp.temp";
	private static final String BINKP_SIZE = "binkp.size";
	private static final String BINKP_TIMEOUT = "binkp.timeout";
	private static final String BINKP_CONNECT_TIMEOUT = "binkp.connect.timeout";
	protected static Integer staticMemMaxSize = null;
	protected static Integer staticBufMaxSize = null;
	protected static File staticTempDirectory = null;
	protected static String staticNetworkName = null;
	protected static Long staticMaxTimeout = null;
	protected static Long staticConnectTimeout = null;

	protected static void init() {
		if (staticTempDirectory == null) {
			staticTempDirectory = new File(MainHandler.getCurrentInstance()
					.getProperty(BINKP_TEMP,
							System.getProperty("java.io.tmpdir")));
		}
		if (staticNetworkName == null) {
			staticNetworkName = MainHandler.getCurrentInstance().getProperty(
					BINKP_NETWORK_NAME, "fidonet");
		}
		if (staticMemMaxSize == null) {
			staticMemMaxSize = MainHandler.getCurrentInstance()
					.getIntegerProperty(BINKP_MAX_MEM, 10485760);
		}
		if (staticBufMaxSize == null) {
			staticBufMaxSize = MainHandler.getCurrentInstance()
					.getIntegerProperty(BINKP_SIZE, 32767);
			if (staticBufMaxSize > 32767) {
				staticBufMaxSize = 32767;
			}
		}
		if (staticMaxTimeout == null) {
			staticMaxTimeout = (long) MainHandler.getCurrentInstance()
					.getIntegerProperty(BINKP_TIMEOUT, 30);
			staticMaxTimeout *= 1000;
		}
		if (staticConnectTimeout == null) {
			staticConnectTimeout = (long) MainHandler.getCurrentInstance()
					.getIntegerProperty(BINKP_CONNECT_TIMEOUT, 10);
			staticConnectTimeout *= 1000;
		}
	}

	protected int connectionState = STATE_GREET;
	protected List<FtnAddress> foreignAddress = new ArrayList<>();
	private List<FtnAddress> ourAddress = new ArrayList<>();
	protected Link foreignLink;
	private String protocolAddress = null; // Store original address for error reporting
	private boolean secure = false;
	protected boolean clientConnection = true;
	private String cramAlgo = null;
	private String cramText = null;
	protected boolean binkp1_0 = true;
	private boolean remoteBinkp11Supported = false;
	private boolean versionNegotiated = false;
	// current messages 'to send' before EOF
	protected ArrayList<Message> messages = new ArrayList<>();
	protected InputStream currentInputStream;
	protected int messages_index = 0;
	// protected transferringMessage = null;
	private Message receivingMessage = null;
	private File currentFile;
	protected OutputStream currentOS;
	private long receivingBytesLeft;
	protected boolean flag_leob = false;
	protected boolean flag_reob = false;
	protected int sent_bytes = 0;
	protected int recv_bytes = 0;
	protected int total_sent_bytes = 0;
	protected int total_recv_bytes = 0;
	protected int total_sent_files = 0;
	protected int total_recv_files = 0;
	protected long lastTimeout;

	protected LinkedList<BinkpFrame> frames = new LinkedList<>();
	private long time = 0;

	public abstract void run();

	private String getStateString() {
		switch (connectionState) {
		case STATE_GREET:
			return "GREET";
		case STATE_ERROR:
			return "ERROR";
		case STATE_END:
			return "END";
		case STATE_ADDR:
			return "ADDR";
		case STATE_AUTH:
			return "AUTH";
		case STATE_TRANSFER:
			return "TRANSFER";
		default:
			return "UNKNOWN(" + connectionState + ")";
		}
	}

	/**
	 * Get address information for error reporting, prioritizing FTN addresses over protocol addresses
	 */
	private String getAddressInfo() {
		if (!foreignAddress.isEmpty()) {
			return foreignAddress.get(0).toString();
		}
		if (protocolAddress != null && !protocolAddress.isEmpty()) {
			return protocolAddress;
		}
		return "unknown";
	}

	public BinkpAbstractConnector(String protocolAddress) throws IOException {
		init();
		this.clientConnection = true;
		this.protocolAddress = protocolAddress; // Store for error reporting
		logger.l3("Created " + getClass().getSimpleName()
				+ " client connection to " + protocolAddress);
	}

	public BinkpAbstractConnector() throws IOException {
		init();
		this.clientConnection = false;
		logger.l3("Created " + getClass().getSimpleName()
				+ " server connection");
	}

	protected void error(String text) {
		frames.clear();
		frames.addLast(new BinkpFrame(BinkpCommand.M_ERR, text));
		long elapsed = (time > 0) ? (new Date().getTime() - time) : 0;
		String duration = String.format("%.2f", elapsed / 1000.0);
		String addressInfo = getAddressInfo();
		logger.l2("[STATE:" + getStateString() + "->ERROR] Local error connecting to " + addressInfo + " after " + duration + " seconds: " + text);
		connectionState = STATE_ERROR;
	}

	protected void error(String text, Exception e) {
		frames.clear();
		frames.addLast(new BinkpFrame(BinkpCommand.M_ERR, text));
		long elapsed = (time > 0) ? (new Date().getTime() - time) : 0;
		String duration = String.format("%.2f", elapsed / 1000.0);
		String addressInfo = getAddressInfo();
		logger.l2("[STATE:" + getStateString() + "->ERROR] Local error connecting to " + addressInfo + " after " + duration + " seconds: " + text, e);
		connectionState = STATE_ERROR;
	}

	protected void proccessFrame(BinkpFrame frame) {
		if (time == 0) {
			time = new Date().getTime();
		}
		addTimeout(); // it's ok :-)
		logger.l4("[STATE:" + getStateString() + "] Processing frame: " + frame);
		if (frame.getCommand() != null) {
			switch (frame.getCommand()) {
			case M_NUL:
				m_null(frame.getArg());
				break;
			case M_ADR:
				m_adr(frame.getArg());
				break;
			case M_PWD:
				m_pwd(frame.getArg());
				break;
			case M_OK:
				m_ok(frame.getArg());
				break;
			case M_ERR:
				rerror("Remote told: " + frame.getArg());
				break;

			case M_EOB:
				m_eob();
				break;

			case M_FILE:
				m_file(frame.getArg());
				break;

			case M_GOT:
				m_got(frame.getArg());
				break;

			case M_GET:
				m_get(frame.getArg());
				break;

			case M_SKIP:
				m_skip(frame.getArg());
				break;

			case M_BSY:
				m_bsy(frame.getArg());

			default:
				break;
			}
		} else {
			logger.l5("Processing DATA frame, receivingMessage=" + 
				(receivingMessage != null ? receivingMessage.getMessageName() : "null") +
				", bytesLeft=" + receivingBytesLeft);
			if (receivingMessage != null) {
				if (receivingBytesLeft > 0) {
					byte[] data = frame.getBytes();
					int len = data.length - 2;
					logger.l5("Writing " + len + " bytes to file, bytesLeft before=" + 
						receivingBytesLeft);
					try {
						if (receivingBytesLeft >= len) {
							currentOS.write(data, 2, len);
							receivingBytesLeft -= len;
						} else {
							currentOS.write(data, 2, (int) receivingBytesLeft);
							receivingBytesLeft = 0;
						}
						recv_bytes += len;
						total_recv_bytes += len;
					} catch (IOException e) {
						logger.l3(String.format("Skipped receiving file: %s (IO error after %d bytes)",
								receivingMessage.getMessageName(), recv_bytes));
						logger.l5(MessageFormat.format(
								"fail receive message {2}, recv_bytes={0}, " +
										"total_recv_bytes={1}, skip",
								recv_bytes, total_recv_bytes, receivingMessage), e);
						frames.addLast(new BinkpFrame(BinkpCommand.M_SKIP,
								getString(receivingMessage)));
						receivingMessage = null;
						receivingBytesLeft = 0;
					}
				} else {
					logger.l4("Unknown data frame " + frame);
				}
				if (receivingBytesLeft == 0) {
					try {
						currentOS.close();
					} catch (IOException e) {
					}
					int ret = forwardToTossing(receivingMessage, currentFile,
							currentOS);
					frames.addLast(new BinkpFrame(
							(ret == 0) ? BinkpCommand.M_GOT
									: BinkpCommand.M_SKIP,
							getString(receivingMessage)));
					if (ret == 0) {
						logger.l3(String.format("Received file: %s (%d bytes)",
								receivingMessage.getMessageName(),
								receivingMessage.getMessageLength()));
						total_recv_files++;
					} else {
						logger.l3(String.format("Skipped received file: %s (%d bytes) - tossing error",
								receivingMessage.getMessageName(),
								receivingMessage.getMessageLength()));
					}
					receivingMessage = null;
					receivingBytesLeft = 0;
					currentFile = null;
					currentOS = null;

				}
			} else {
				logger.l4("Unknown data frame: " + frame);
			}
		}

	}

	private void m_bsy(String arg) {
		logger.l3("Remote is busy: " + arg);
		connectionState = STATE_END;
		finish("m_bsy");
	}

	private void rerror(String string) {
		logger.l2("[STATE:" + getStateString() + "->ERROR] Remote error: " + string);
		connectionState = STATE_ERROR;
	}

	private void m_eob() {
		logger.l4("[STATE:" + getStateString() + "] Received M_EOB from remote");
		flag_reob = true;
		checkEOB();
	}

	private void m_skip(String arg) {
		Message found = null;
		for (Message message : messages) {
			if (messageEquals(message, arg)) {
				logger.l3(String.format("Skipped sending file: %s (%d bytes)",
						message.getMessageName(), message.getMessageLength()));
				found = message;
				break;
			}
		}
		if (found != null) {
			messages.remove(found);
		} else {
			logger.l3("M_GOT for file we haven't sent: " + arg);
		}

	}

	private void m_get(String arg) {
		for (Message message : messages) {
			if (messageEquals(message, arg)) {
				int skip = Integer.valueOf(arg.split(" ")[3]);
				sendMessage(message, skip);
				logger.l4("M_GET for file " + arg);
				break;
			}
		}
	}

	private void m_got(String arg) {
		Message found = null;
		for (Message message : messages) {
			if (messageEquals(message, arg)) {
				logger.l3(String.format("Sent file: %s (%d)",
						message.getMessageName(), message.getMessageLength()));
				found = message;
				break;
			}
		}
		if (found != null) {
			total_sent_files++;
			logger.l3(String.format("Sent file: %s (%d bytes)",
					found.getMessageName(), found.getMessageLength()));
			found.delete();
			messages.remove(found);
		} else {
			logger.l3("M_GOT for file we haven't sent: " + arg);
		}
	}

	private void m_file(String arg) {
		logger.l5("M_FILE received: " + arg);
		String[] parts = arg.split(" ");
		receivingMessage = createMessage(arg, secure);
		long free_space = new File(FtnTools.getInbound()).getFreeSpace();
		if (receivingMessage.getMessageLength() > free_space) {
			frames.addLast(new BinkpFrame(BinkpCommand.M_SKIP,
					getString(receivingMessage)));
			logger.l3(String.format("Skipped receiving file: %s (%d bytes) - insufficient disk space",
					receivingMessage.getMessageName(), receivingMessage.getMessageLength()));
			logger.l1("No enough free space in inbound for receiving file");
			receivingMessage = null;
		}
		logger.l5("File: " + parts[0] + ", size: " + parts[1] + 
			", time: " + parts[2] + ", offset: " + parts[3]);
		if (!parts[3].equals("0")) {
			frames.addLast(new BinkpFrame(BinkpCommand.M_GET, getString(
					receivingMessage, 0)));
			logger.l5("Requesting file from offset 0");
		} else {
			receivingBytesLeft = receivingMessage.getMessageLength();
			try {
				final String prefix = "temp";
				final String suffix = "jnode";
				logger.l5(MessageFormat.format("try create temp file with prefix {0}, suffix {1} in directory {2}",
						prefix, suffix, staticTempDirectory));
				currentFile = File.createTempFile(prefix, suffix,
						staticTempDirectory);
				logger.l5(MessageFormat.format("temp file {0} created", currentFile));
				free_space = currentFile.getFreeSpace();
				if (receivingMessage.getMessageLength() > free_space) {
					logger.l1("No enough free space in tmp for receiving file");
					currentFile.delete();
					throw new IOException();
				}
				currentOS = new FileOutputStream(currentFile);
				logger.l5("Created temp file: " + currentFile.getAbsolutePath());
			} catch (IOException e) {
				logger.l5(MessageFormat.format("fail process m_file message {0}," +
						" len {1}, memMaxSize {2}",
						receivingMessage, receivingMessage.getMessageLength(), staticMemMaxSize), e);
				currentFile = null;
				if (receivingMessage.getMessageLength() < staticMemMaxSize) {
                    logger.l5(MessageFormat.format("load {0} in memory", receivingMessage));
					currentOS = new ByteArrayOutputStream(
							(int) receivingMessage.getMessageLength());
					logger.l5("Using ByteArrayOutputStream for small file");
				} else {
					logger.l3(String.format("Skipped receiving file: %s (%d bytes) - cannot create temp file",
							receivingMessage.getMessageName(), receivingMessage.getMessageLength()));
					logger.l5("skip m_file due exception - too big for load in memory");
					frames.addLast(new BinkpFrame(BinkpCommand.M_SKIP,
							getString(receivingMessage)));
					receivingMessage = null;
					receivingBytesLeft = 0;
				}
			}
			if (receivingMessage != null) {
				logger.l5(String.format("Ready to receive file: %s (%d bytes)",
						receivingMessage.getMessageName(),
						receivingMessage.getMessageLength()));
				logger.l5("receivingBytesLeft initialized to: " + receivingBytesLeft);
			}
		}

	}

	private void m_ok(String arg) {
		if (connectionState != STATE_AUTH) {
			error("We weren't waiting for M_OK");
		}
		String text = ((secure) ? "(S) Secure" : "(U) Unsecure")
				+ " connection with "
				+ ((secure) ? foreignLink.getLinkAddress() : foreignAddress
						.get(0));
		logger.l3("[STATE:AUTH->TRANSFER] " + text);
		connectionState = STATE_TRANSFER;
		
		// CRITICAL: Check for messages immediately after authentication
		logger.l4("[STATE:TRANSFER] Connection authenticated, checking for messages to send");
		checkForMessages();
	}

	/**
	 * Processing incoming M_PWD
	 * 
	 * @param arg
	 */
	private void m_pwd(String arg) {
		if (connectionState != STATE_AUTH) {
			error("We weren't waiting for M_PWD");
		}
		boolean valid = (!secure || checkPassword(arg));
		
		String text;
		if (secure) {
			text = "(S) Secure  connection with "
					+ foreignLink.getLinkAddress();
		} else {
			text = "(U) Unsecure connection with " + foreignAddress.get(0);
		}
		if (valid) {
			logger.l3("[STATE:AUTH->TRANSFER] " + text);
			frames.addLast(new BinkpFrame(BinkpCommand.M_OK, text));
			connectionState = STATE_TRANSFER;
			
			// CRITICAL: For server connections, check for messages after sending M_OK
			if (!clientConnection) {
				logger.l4("[STATE:TRANSFER] Server authenticated client, checking for messages to send");
				checkForMessages();
			}
		} else {
			error("Invalid password");
			connectionState = STATE_ERROR;
		}
	}

	private boolean checkPassword(String arg) {
		logger.l5("checkPassword(" + arg + ")");
				
		String password = foreignLink.getProtocolPassword();
		logger.l5("protocol password: [REDACTED]");
		if (password.equals(arg)) {
			return true;
		}
		password = getAuthPassword(foreignLink, secure, cramAlgo, cramText);
		logger.l5("auth password: [REDACTED]");
		if (password.endsWith(arg)) {
			return true;
		}
		return false;
	}

	/**
	 * Processing incoming M_ADR
	 * 
	 * @param arg
	 */
	private void m_adr(String arg) {
		if (connectionState != STATE_ADDR) {
			error("We weren't waiting for M_ADR");
		}
		for (String addr : arg.trim().split(" ")) {
			if (addr.trim().isEmpty()) {
				continue; // Skip empty addresses
			}
			try {
				FtnAddress a = new FtnAddress(addr);
				Link link = FtnTools.getLinkByFtnAddress(a);
				boolean nodelist = NodelistScanner.getInstance().isExists(a) != null;
				if (link != null || nodelist) {
					foreignAddress.add(a);
					logger.l5("Address " + addr + " accepted (link=" + (link != null) + ", nodelist=" + nodelist + ")");
				} else {
					// Standard FidoNet behavior: allow unknown nodes for netmail delivery to our addresses
					foreignAddress.add(a);
					logger.l4("Address " + addr + " accepted for netmail delivery (unknown node)");
				}
			} catch (NumberFormatException e) {
				logger.l2("Invalid address format: '" + addr + "' - " + e.getMessage());
			}
		}

		if (foreignAddress.isEmpty()) {
			error("No valid address specified");
			return;
		}
		Link link = FtnTools.getLinkByFtnAddress(foreignAddress);
		if (link != null) {
			String ourAka = FtnTools.getOptionString(link,
					LinkOption.STRING_OUR_AKA);
			if (ourAka != null) {
				try {
					FtnAddress addr = new FtnAddress(ourAka);
					if (ourAddress.contains(addr)) {
						ourAddress.clear();
						ourAddress.add(addr);
					}
				} catch (NumberFormatException e) {
				}
			}
			foreignLink = link;
			secure = true;
		} else {
			boolean nodelist = false;
			for (FtnAddress a : foreignAddress) {
				if (NodelistScanner.getInstance().isExists(a) != null) {
					nodelist = true;
					break;
				}
			}
			if (!nodelist) {
				// Allow unlisted nodes for netmail delivery (FidoNet standard)
				logger.l4("Allowing connection from unlisted node for netmail delivery: " + 
					foreignAddress.toString());
				// Allow the connection but keep it unsecure - netmail acceptance rules will apply
			}
		}
		for (FtnAddress addr : foreignAddress) {
			if (!PollQueue.getSelf().isActive(addr)) {
				PollQueue.getSelf().start(addr);
			} else {
				busy("Already connected with " + addr.toString());
			}
		}
		if (clientConnection) {
			frames.addLast(new BinkpFrame(BinkpCommand.M_PWD, getAuthPassword(
					foreignLink, secure, cramAlgo, cramText)));
		} else {
			sendAddrs();
		}
		logger.l4("[STATE:ADDR->AUTH] Moving to authentication phase");
		connectionState = STATE_AUTH;

	}

	protected void busy(String string) {
		frames.clear();
		frames.addLast(new BinkpFrame(BinkpCommand.M_BSY, string));
		connectionState = STATE_END;
		logger.l3("Local busy: " + string);
	}

	private void sendAddrs() {
		StringBuilder sb = new StringBuilder();
		boolean flag = true;
		for (FtnAddress a : ourAddress) {
			if (flag) {
				flag = false;
			} else {
				sb.append(" ");
			}
			sb.append(a.toString() + "@" + staticNetworkName);
		}
		frames.addLast(new BinkpFrame(BinkpCommand.M_ADR, sb.toString()));
	}

	private void m_null(String arg) {
		logger.l4("M_NULL " + arg);
		String[] args = arg.split(" ");
		if (args[0].equals("OPT")) {
			for (int i = 1; i < args.length; i++) {
				Matcher md = cramPattern.matcher(args[i]);
				if (md.matches()) {
					String[] algos = md.group(1).split("/");
					for (String algo : algos) {
						try {
							MessageDigest.getInstance(algo);
							cramText = md.group(2);
							cramAlgo = md.group(1);
							logger.l4("Remote requires MD-mode (" + algo + ")");
							break;
						} catch (NoSuchAlgorithmException e) {
							logger.l2("fail algo ", e);
							logger.l2("Remote requires MD-mode for unknown algo");
						}
					}
				}
			}
		} else if (args[0].equals("VER")) {
			// Parse remote version and negotiate protocol version
			if (arg.matches("^.* binkp/1\\.1$")) {
				remoteBinkp11Supported = true;
				logger.l4("Remote supports BinkP/1.1");
			} else if (arg.matches("^.* binkp/1\\.0$")) {
				remoteBinkp11Supported = false;
				logger.l4("Remote supports BinkP/1.0 only");
			} else {
				// Unknown or no version specified, assume 1.0 for compatibility
				remoteBinkp11Supported = false;
				logger.l4("Remote version unknown, assuming BinkP/1.0");
			}
			
			// Negotiate final protocol version - use 1.1 only if both sides support it
			if (!versionNegotiated) {
				negotiateProtocolVersion();
			}
		}

	}

	/**
	 * Negotiate the final protocol version based on both sides' capabilities.
	 * According to BinkP specification, should fallback to 1.0 if remote doesn't support 1.1.
	 */
	private void negotiateProtocolVersion() {
		boolean localBinkp11Supported = true; // jNode supports BinkP/1.1
		
		if (localBinkp11Supported && remoteBinkp11Supported) {
			binkp1_0 = false;
			logger.l3("Protocol negotiated: BinkP/1.1 (both sides support it)");
		} else {
			binkp1_0 = true;
			if (!remoteBinkp11Supported) {
				logger.l3("Protocol negotiated: BinkP/1.0 (remote doesn't support 1.1)");
			} else {
				logger.l3("Protocol negotiated: BinkP/1.0 (local fallback)");
			}
		}
		
		versionNegotiated = true;
		
		// Send our version response if we're the server and haven't sent it yet
		if (!clientConnection && versionNegotiated) {
			String negotiatedVersion = binkp1_0 ? "binkp/1.0" : "binkp/1.1";
			frames.addLast(new BinkpFrame(BinkpCommand.M_NUL, "VER "
					+ MainHandler.getVersion() + " " + negotiatedVersion));
			logger.l4("Sent negotiated version: " + negotiatedVersion);
		}
	}

	protected void checkForMessages() {
		checkTimeout();
		logger.l5("[STATE:" + getStateString() + "] checkForMessages: flag_leob=" + flag_leob + 
			", messages.size=" + messages.size() + ", currentInputStream=" + (currentInputStream != null) + 
			", frames.size=" + frames.size());
		if (connectionState != STATE_TRANSFER) {
			logger.l5("[STATE:" + getStateString() + "] Not in TRANSFER state, skipping message check");
			return;
		}
		if (flag_leob) {
			return;
		}
		if (messages.size() > 0 && currentInputStream == null) {
			return;
		}
		if (messages.size() > 0) {
			logger.l5("[STATE:" + getStateString() + "] Attempting to read frame from current file");
			BinkpFrame frame = readFrame();
			if (frame != null) {
				logger.l5("[STATE:" + getStateString() + "] DATA FRAME CREATED: " + (frame.getBytes().length - 2) + " bytes, adding to queue position " + frames.size());
				frames.addLast(frame);
			} else { // error, null
				logger.l5("[STATE:" + getStateString() + "] readFrame returned null - EOF or error");
			}
			return;

		}
		for (FtnAddress a : foreignAddress) {
			messages.addAll(TosserQueue.getInstance().getMessages(a));
		}
		if (messages.isEmpty()) {
			if (!flag_leob) {
				logger.l4("[STATE:" + getStateString() + "] No more messages, sending M_EOB");
				flag_leob = true;
				frames.addLast(new BinkpFrame(BinkpCommand.M_EOB));
				checkEOB();
			} else {
				logger.l5("[STATE:" + getStateString() + "] Already sent EOB, waiting for remote EOB");
			}
		} else {
			logger.l4("[STATE:" + getStateString() + "] Found " + messages.size() + " messages to send");
			messages_index = 0;
			startNextFile();
		}
	}

	protected void finish(String reason) {
		long elapsed = (time > 0) ? (new Date().getTime() - time) : 0;
		String duration = String.format("%.2f", elapsed / 1000.0);
		String addressInfo = getAddressInfo();
		logger.l3("[STATE:" + getStateString() + "->END] Finishing connection to " + addressInfo + " after " + duration + " seconds: " + reason);
		for (FtnAddress addr : foreignAddress) {
			PollQueue.getSelf().end(addr);
		}
		throw new ConnectionEndException();
	}

	protected void greet() {
		// check if busy
		if (ThreadPool.isBusy()) {
			busy("Too much connections");
			finish("From greet()");
		}
		addTimeout();
		SystemInfo info = MainHandler.getCurrentInstance().getInfo();
		ourAddress.addAll(info.getAddressList());
		frames.addLast(new BinkpFrame(BinkpCommand.M_NUL, "SYS "
				+ info.getStationName()));
		frames.addLast(new BinkpFrame(BinkpCommand.M_NUL, "ZYZ "
				+ info.getSysop()));
		frames.addLast(new BinkpFrame(BinkpCommand.M_NUL, "LOC "
				+ info.getLocation()));
		frames.addLast(new BinkpFrame(BinkpCommand.M_NUL, "NDL "
				+ info.getNDL()));
		// Initially announce BinkP/1.1 capability, will fallback to 1.0 if needed
		frames.addLast(new BinkpFrame(BinkpCommand.M_NUL, "VER "
				+ MainHandler.getVersion() + " binkp/1.1"));
		frames.addLast(new BinkpFrame(BinkpCommand.M_NUL, "TIME "
				+ format.format(new Date())));

		logger.l4("[STATE:GREET->ADDR] Moving to address phase");
		connectionState = STATE_ADDR;
		if (clientConnection) {
			sendAddrs();
		} else {
			MessageDigest md;
			try {
				md = MessageDigest.getInstance("MD5");
				md.update(String.format("%d%d", System.currentTimeMillis(),
						System.nanoTime()).getBytes());
				byte[] digest = md.digest();
				StringBuilder builder = new StringBuilder();
				for (int i = 0; i < 16; i++) {
					builder.append(String.format("%02x", digest[i]));
				}
				cramText = builder.toString();
				cramAlgo = "MD5";
				frames.addLast(new BinkpFrame(BinkpCommand.M_NUL, String
						.format("OPT CRAM-MD5-%s", cramText)));

			} catch (NoSuchAlgorithmException e) {
				cramText = null;
			}
		}
	}

	protected boolean isConnected() {
		checkTimeout();
		boolean connected = !((frames.isEmpty() && connectionState == STATE_END) || connectionState == STATE_ERROR);
		if (!connected) {
			logger.l5("[STATE:" + getStateString() + "] Connection no longer active: frames.isEmpty=" + 
				frames.isEmpty() + ", connectionState=" + getStateString());
		}
		return connected;
	}

	protected BinkpFrame readFrame() {
		if (currentInputStream != null) {
			logger.l5("readFrame: currentInputStream is not null");
			try {
				int available = currentInputStream.available();
				logger.l5("Available bytes in stream: " + available);
				byte[] buf = new byte[staticBufMaxSize];
				int n = currentInputStream.read(buf);
				logger.l5("Read " + n + " bytes from file (available was " + available + ")");
				if (n > 0) {
					sent_bytes += n;
					total_sent_bytes += n;
					addTimeout();
					BinkpFrame frame = new BinkpFrame(buf, n);
					logger.l5("Created data frame with " + n + " bytes, sent_bytes=" + 
						sent_bytes + ", total_sent=" + total_sent_bytes);
					// Log first few bytes for debugging
					if (n > 0) {
						StringBuilder sb = new StringBuilder();
						for (int i = 0; i < Math.min(n, 16); i++) {
							sb.append(String.format("%02X ", buf[i] & 0xFF));
						}
						logger.l5("First bytes of frame: " + sb.toString());
					}
					return frame;
				} else {
					logger.l5("EOF reached (n=" + n + "), closing input stream");
					currentInputStream.close();
					currentInputStream = null;
					messages_index++;
					if (startNextFile()) {
						logger.l5("Started next file, recursing");
						return readFrame();
					}
				}
			} catch (IOException e) {
				logger.l5("IOException in readFrame", e);
				error("Error reading file", e);
			}
		} else {
			logger.l5("readFrame: currentInputStream is null");
		}
		return null;
	}

	protected boolean startNextFile() {
		logger.l5("[STATE:" + getStateString() + "] startNextFile() called, messages_index=" + messages_index + 
			", messages.size=" + messages.size());
		try {
			Message nextMessage = messages.get(messages_index);
			logger.l4("[STATE:" + getStateString() + "] Starting file: " + nextMessage.getMessageName());
			sendMessage(nextMessage, 0);
			return true;
		} catch (IndexOutOfBoundsException e) {
			logger.l5("[STATE:" + getStateString() + "] No more files to send");
			return false;
		}
	}

	protected void sendMessage(Message message, int skip) {
		logger.l4("[STATE:" + getStateString() + "] sendMessage(" + message.getMessageName() + ", skip=" + skip + ")");
		String fileInfo = getString(message, skip);
		logger.l5("[STATE:" + getStateString() + "] M_FILE string: " + fileInfo);
		frames.addLast(new BinkpFrame(BinkpCommand.M_FILE, fileInfo));
		logger.l3(String.format("[STATE:" + getStateString() + "] Sending file: %s (%d bytes)",
				message.getMessageName(), message.getMessageLength()));
		
		// DEBUG: Log frame queue state after M_FILE
		logger.l5("[STATE:" + getStateString() + "] After M_FILE: Queue has " + frames.size() + " frames");
		
		try {
			message.getInputStream().skip(skip);
			if (currentInputStream != null) {
				currentInputStream.close();
				currentInputStream = null;
			}
			currentInputStream = message.getInputStream();
			int available = currentInputStream.available();
			logger.l5("[STATE:" + getStateString() + "] Opened input stream for file: " + message.getMessageName() + 
				", available bytes: " + available + 
				", expected length: " + message.getMessageLength());
			
			// CRITICAL FIX: Immediately queue at least one data frame after M_FILE
			if (currentInputStream != null && available > 0) {
				logger.l5("[STATE:" + getStateString() + "] Immediately reading first data frame after M_FILE");
				BinkpFrame dataFrame = readFrame();
				if (dataFrame != null) {
					logger.l5("[STATE:" + getStateString() + "] Queued first data frame: " + (dataFrame.getBytes().length - 2) + " bytes");
					frames.addLast(dataFrame);
				}
			}
		} catch (IOException e) {
			logger.l4("[STATE:" + getStateString() + "] IOException in sendMessage", e);
			error("IOException opening file", e);
			currentInputStream = null;
		}

	}

	private void checkTimeout() {
		long last = new Date().getTime();
		long timeSinceLastActivity = last - lastTimeout;
		if (timeSinceLastActivity > staticMaxTimeout) {
			logger.l2("[STATE:" + getStateString() + "->ERROR] Connection timeout after " + 
				(timeSinceLastActivity / 1000.0) + " seconds (limit: " + (staticMaxTimeout / 1000.0) + "s)");
			connectionState = STATE_ERROR;
			finish("Connection timeout");
		} else if (timeSinceLastActivity > staticMaxTimeout * 0.8) {
			// Warn when we're approaching timeout (80% of limit)
			logger.l4("[STATE:" + getStateString() + "] Connection approaching timeout: " + 
				(timeSinceLastActivity / 1000.0) + "s / " + (staticMaxTimeout / 1000.0) + "s");
		}
	}

	private void addTimeout() {
		lastTimeout = new Date().getTime();
	}

	protected void checkEOB() {
		checkTimeout();
		logger.l5("[STATE:" + getStateString() + "] checkEOB: flag_leob=" + flag_leob + 
			", flag_reob=" + flag_reob + ", sent_bytes=" + sent_bytes + ", recv_bytes=" + recv_bytes);
		if (connectionState == STATE_END || connectionState == STATE_ERROR) {
			finish("connectionState = END|ERROR");
		}
		if (flag_leob && flag_reob) {
			if (sent_bytes + recv_bytes == 0 || binkp1_0) {
				logger.l4("[STATE:" + getStateString() + "->END] Both sides sent EOB, no data transferred or BinkP/1.0");
				connectionState = STATE_END;
			} else {
				logger.l4("[STATE:" + getStateString() + "] BinkP/1.1: reset state for potential next batch");
				flag_leob = false;
				flag_reob = false;
				sent_bytes = 0;
				recv_bytes = 0;
			}
		}
	}

	protected void done() {
		logger.l4("[STATE:" + getStateString() + "] Starting connection cleanup");
		try {
			if (currentOS != null) {
				logger.l5("[STATE:" + getStateString() + "] Closing output stream");
				currentOS.close();
			}
			for (Message message : messages) {
				if (message.getInputStream() != null) {
					logger.l5("[STATE:" + getStateString() + "] Closing input stream for: " + message.getMessageName());
					message.getInputStream().close();
				}
			}
		} catch (IOException e2) {
			logger.l2("[STATE:" + getStateString() + "] Error while closing streams", e2);
		}
		ConnectionEndEvent event = null;
		if (!foreignAddress.isEmpty()) {
			for (FtnAddress addr : foreignAddress) {
				logger.l5("[STATE:" + getStateString() + "] Ending poll queue for: " + addr);
				PollQueue.getSelf().end(addr);
			}
			long elapsed = (time > 0) ? time : 1; // Avoid division by zero
			long scps = (elapsed > 0) ? total_sent_bytes * 1000 / elapsed : total_sent_bytes;
			long rcps = (elapsed > 0) ? total_recv_bytes * 1000 / elapsed : total_recv_bytes;

			String address = (foreignLink != null) ? foreignLink
					.getLinkAddress() : (!foreignAddress.isEmpty() ? foreignAddress.get(0).toString() : getAddressInfo());
			logger.l2(String.format(
					"[STATE:" + getStateString() + "] Done: %s %s, %s, S/R: %d/%d (%d/%d bytes) (%d/%d cps)",
					(clientConnection) ? "to" : "from", address,
					(connectionState == STATE_END) ? "OK" : "ERROR",
					total_sent_files, total_recv_files, total_sent_bytes,
					total_recv_bytes, scps, rcps));
			event = new ConnectionEndEvent(new FtnAddress(address),
					!clientConnection, (connectionState == STATE_END),
					total_recv_bytes, total_sent_bytes);
		} else {
			event = new ConnectionEndEvent(clientConnection, false);
			logger.l3("[STATE:" + getStateString() + "] Connection ended as " + getAddressInfo());
		}
		logger.l4("[STATE:" + getStateString() + "] Notifying connection end event");
		Notifier.INSTANCE.notify(event);
	}

}