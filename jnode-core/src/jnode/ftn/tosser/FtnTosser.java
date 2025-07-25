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

package jnode.ftn.tosser;

import jnode.core.FileUtils;
import jnode.dto.*;
import jnode.event.NewEchomailEvent;
import jnode.event.NewFilemailEvent;
import jnode.event.NewNetmailEvent;
import jnode.event.Notifier;
import jnode.ftn.FtnTools;
import jnode.ftn.EchoareaLookupResult;
import jnode.ftn.FilesBBSWriter;
import jnode.ftn.FileIdDizWriter;
import jnode.ftn.types.*;
import jnode.logger.Logger;
import jnode.main.MainHandler;
import jnode.main.threads.PollQueue;
import jnode.main.threads.TosserQueue;
import jnode.orm.ORMManager;
import jnode.protocol.io.Message;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static jnode.ftn.FtnTools.*;

/**
 * @author kreon
 */
public class FtnTosser {
	private static final String FILEECHO_ENABLE = "fileecho.enable";
	private static final String FILEECHO_PATH = "fileecho.path";
	private static final String FILES_BBS_ENABLE = "fileecho.files_bbs.enable";
	private static final String FILE_ID_DIZ_ENABLE = "fileecho.file_id_diz.enable";
	private static final String FILEECHO_8BIT_ENABLE = "fileecho.8bit_output.enable";
	private static final String FILEECHO_CHARSET = "fileecho.output_charset";
	private static final Logger logger = Logger.getLogger(FtnTosser.class);
	private static final String MAIL_LIMIT = "tosser.mail_limit";
	private static final String LOOP_PREVENTION_ECHOMAIL = "tosser.loop_prevention.echomail";
	private static final String LOOP_PREVENTION_NETMAIL = "tosser.loop_prevention.netmail";
	private static final String OLD_MESSAGE_DAYS_THRESHOLD = "tosser.old_message_days_threshold";
	private static final String TROUBLESHOOTING_DIRECTORY = "tosser.troubleshooting.directory";
	private final Map<String, Integer> tossed = new HashMap<>();
	private final Map<String, Integer> bad = new HashMap<>();
	private final Set<Link> pollLinks = new HashSet<>();

	private boolean running;

	/**
	 * Netmail parsing
	 * 
	 * @param netmail
	 * @param secure
	 */
	private void tossNetmail(FtnMessage netmail, boolean secure) {
		if (secure) {
			if (checkRobot(netmail)) {
				return;
			}
		}
		
		// Check for VIA loops if loop prevention is enabled
		if (isNetmailLoopPreventionEnabled()) {
			List<String> viaLines = extractViaLines(netmail);
			FtnAddress loopAddr = checkOurAddressInVia(viaLines);
			if (loopAddr != null) {
				String errorMsg = String.format("Netmail loop detected - message already passed through %s", 
						loopAddr.toString());
				logger.l2("MAIL ERROR: " + errorMsg);
				
				// Notify sysop about netmail loop
				notifySysop("Netmail Loop Detected", 
						String.format("A netmail message was dropped due to routing loop detection.\n\n" +
								"From: %s (%s)\n" +
								"To: %s (%s)\n" +
								"Subject: %s\n" +
								"Message Date: %s\n\n" +
								"Loop detected at address: %s\n\n" +
								"VIA lines:\n%s\n\n" +
								"Our addresses: %s\n\n" +
								"This message has already passed through our system and is being routed " +
								"back to us, creating a circular route. Check your routing configuration.",
								netmail.getFromAddr().toString(), netmail.getFromName(),
								netmail.getToAddr().toString(), netmail.getToName(),
								netmail.getSubject(), 
								(netmail.getDate() != null) ? netmail.getDate().toString() : "unknown",
								loopAddr.toString(),
								viaLines != null ? String.join("\n", viaLines) : "No VIA lines found",
								MainHandler.getCurrentInstance().getInfo().getAddressList().toString()));
				
				Integer n = bad.get("netmail");
				bad.put("netmail", (n == null) ? 1 : n + 1);
				return;
			}
		}
		
		boolean drop = checkNetmailMustDropped(netmail);

		if (drop) {
			String errorMsg = String.format("Netmail dropped from %s to %s - failed validation", 
					netmail.getFromAddr().toString(), netmail.getToAddr().toString());
			logger.l2("MAIL ERROR: " + errorMsg);
			
			// Notify sysop about netmail validation failure
			notifySysop("Netmail Validation Error", 
					String.format("A netmail message was dropped due to validation failure.\n\n" +
							"From: %s (%s)\n" +
							"To: %s (%s)\n" +
							"Subject: %s\n" +
							"Message Date: %s\n\n" +
							"This typically indicates issues with address validation, " +
							"unknown sender, or destination not found in nodelist.",
							netmail.getFromAddr().toString(), netmail.getFromName(),
							netmail.getToAddr().toString(), netmail.getToName(),
							netmail.getSubject(), 
							(netmail.getDate() != null) ? netmail.getDate().toString() : "unknown"));
			
			Integer n = bad.get("netmail");
			bad.put("netmail", (n == null) ? 1 : n + 1);
		} else {
			if ((netmail.getAttribute() & FtnMessage.ATTR_ARQ) > 0) {
				writeReply(netmail, "ARQ reply",
						"Your message was successfully reached this system");
			}
			processRewrite(netmail);
			Link routeVia = getRouting(netmail);
			
			// Handle TRACE functionality for PING messages in transit
			if (routeVia != null && "PING".equalsIgnoreCase(netmail.getToName()) && isTraceEnabled()) {
				sendTraceNotification(netmail, routeVia);
			}

			Netmail dbnm = new Netmail();
			dbnm.setRouteVia(routeVia);
			dbnm.setDate(netmail.getDate());
			dbnm.setFromFTN(netmail.getFromAddr().toString());
			dbnm.setToFTN(netmail.getToAddr().toString());
			dbnm.setFromName(netmail.getFromName());
			dbnm.setToName(netmail.getToName());
			dbnm.setSubject(netmail.getSubject());
			dbnm.setText(netmail.getText());
			dbnm.setAttr(netmail.getAttribute());
			ORMManager.get(Netmail.class).save(dbnm);
			Notifier.INSTANCE.notify(new NewNetmailEvent(dbnm));
			Integer n = tossed.get("netmail");
			tossed.put("netmail", (n == null) ? 1 : n + 1);
			if (routeVia == null) {
				logger.l4(String
						.format("Netmail %s -> %s is not transferred ( routing not found )",
								netmail.getFromAddr().toString(), netmail
										.getToAddr().toString()));
			} else {
				routeVia = ORMManager.get(Link.class).getById(routeVia.getId());
				logger.l4(String.format("Netmail %s -> %s transferred via %s",
						netmail.getFromAddr().toString(), netmail.getToAddr()
								.toString(), routeVia.getLinkAddress()));
				if (getOptionBooleanDefTrue(routeVia,
						LinkOption.BOOLEAN_CRASH_NETMAIL)) {
					PollQueue.getSelf().add(routeVia);
				}
			}
		}
	}

	private void tossEchomail(FtnMessage echomail, Link link, boolean secure) {

		if (!secure) {
			String linkAddr = (link != null) ? link.getLinkAddress() : "unknown";
			logger.l2(String.format("MAIL ERROR: Echomail from insecure link dropped - area: %s, from: %s", 
					echomail.getArea(), linkAddr));
			
			// Notify sysop about insecure echomail
			notifySysop("Insecure Echomail Dropped", 
					String.format("An echomail message was dropped because it came from an insecure link.\n\n" +
							"Echoarea: %s\n" +
							"From Link: %s\n" +
							"Message From: %s (%s)\n" +
							"Subject: %s\n" +
							"Message Date: %s\n\n" +
							"Only secure (password-protected) links can post echomail. " +
							"Check link configuration and packet passwords.",
							echomail.getArea(), linkAddr,
							echomail.getFromAddr().toString(), echomail.getFromName(),
							echomail.getSubject(), 
							(echomail.getDate() != null) ? echomail.getDate().toString() : "unknown"));
			return;
		}
		EchoareaLookupResult lookupResult = FtnTools.getAreaByNameWithDetails(echomail.getArea(), link);
		if (!lookupResult.isSuccess()) {
			String linkAddr = (link != null) ? link.getLinkAddress() : "unknown";
			logger.l2(String.format("MAIL ERROR: Echoarea %s - %s - message dropped", 
					echomail.getArea(), lookupResult.getLogMessage()));
			
			// Create specific error messages based on the error type
			String errorTitle;
			String errorDescription;
			String suggestedAction;
			
			switch (lookupResult.getErrorType()) {
				case AUTO_CREATE_DISABLED:
					errorTitle = "Echoarea Auto-Creation Disabled";
					errorDescription = "A message was dropped because the echoarea does not exist and automatic echoarea creation is disabled for this link.";
					suggestedAction = "You can either:\n" +
						"1. Enable automatic echoarea creation for link " + linkAddr + " in the link configuration\n" +
						"2. Manually create the echoarea '" + echomail.getArea() + "' and add a subscription for this link\n" +
						"3. Contact the sender to verify the correct echoarea name";
					break;
				case LINK_NOT_SUBSCRIBED:
					errorTitle = "Link Not Subscribed to Echoarea";
					errorDescription = "A message was dropped because the echoarea exists but the sending link is not subscribed to it.";
					suggestedAction = "You need to add a subscription for link " + linkAddr + " to echoarea '" + echomail.getArea() + "' in the link configuration.";
					break;
				default:
					errorTitle = "Unknown Echoarea Error";
					errorDescription = "A message was dropped because the echoarea is not available.";
					suggestedAction = "Check the echoarea configuration and link settings.";
					break;
			}
			
			// Notify sysop with detailed error information
			notifySysop(errorTitle, 
					String.format("%s\n\n" +
							"Echoarea: %s\n" +
							"From Link: %s\n" +
							"Message From: %s (%s)\n" +
							"Subject: %s\n" +
							"Message Date: %s\n" +
							"Message ID: %s\n\n" +
							"PATH Information:\n" +
							"================\n" +
							"Raw PATH: %s\n" +
							"Parsed PATH: %s\n" +
							"Last node in PATH: %s\n\n" +
							"SEEN-BY Information:\n" +
							"===================\n" +
							"SEEN-BY: %s\n\n" +
							"Error Details: %s\n\n" +
							"Suggested Action:\n%s",
							errorDescription,
							echomail.getArea(), linkAddr,
							echomail.getFromAddr().toString(), echomail.getFromName(),
							echomail.getSubject(),
							(echomail.getDate() != null) ? echomail.getDate().toString() : "unknown",
							(echomail.getMsgid() != null) ? echomail.getMsgid() : "none",
							echomail.getPath() != null ? echomail.getPath().toString() : "empty",
							echomail.getPath() != null && !echomail.getPath().isEmpty() ? 
								formatPath(echomail.getPath()) : "empty",
							echomail.getPath() != null && !echomail.getPath().isEmpty() ? 
								getLastPathNode(echomail.getPath()) : "none",
							echomail.getSeenby() != null && !echomail.getSeenby().isEmpty() ? 
								formatSeenBy(echomail.getSeenby()) : "empty",
							lookupResult.getErrorMessage(),
							suggestedAction));
			
			Integer n = bad.get(echomail.getArea());
			bad.put(echomail.getArea(), (n == null) ? 1 : n + 1);
			return;
		}
		
		Echoarea area = lookupResult.getEchoarea();

		Long rl = (link != null) ? getOptionLong(link, LinkOption.LONG_LINK_LEVEL) : 0L;
		if (link != null && rl < area.getWritelevel()) {
			writeNetmail(
					getPrimaryFtnAddress(),
					new FtnAddress(link.getLinkAddress()),
					MainHandler.getCurrentInstance().getInfo().getStationName(),
					link.getLinkName(),
					"[" + area.getName() + "]: post rejected",
					String.format(
							"Sorry, you have no enough level to post %s to this area\n%s",
							quote(echomail), MainHandler.getVersion()));
			logger.l2(String.format("MAIL ERROR: Echoarea %s access denied for link %s - insufficient level (required: %d, has: %d)", 
					echomail.getArea(), link.getLinkAddress(), area.getWritelevel(), rl));
			
			// Notify sysop about access level mismatch
			notifySysop("Echoarea Access Denied", 
					String.format("An echomail message was dropped due to insufficient access level.\n\n" +
							"Echoarea: %s (requires level %d)\n" +
							"From Link: %s (has level %d)\n" +
							"Message From: %s (%s)\n" +
							"Subject: %s\n" +
							"Message Date: %s\n\n" +
							"The link's access level is too low to post to this echoarea. " +
							"You may need to adjust the link's level or the echoarea's write level.",
							echomail.getArea(), area.getWritelevel(),
							link.getLinkAddress(), rl,
							echomail.getFromAddr().toString(), echomail.getFromName(),
							echomail.getSubject(), 
							(echomail.getDate() != null) ? echomail.getDate().toString() : "unknown"));
			
			Integer n = bad.get(echomail.getArea());
			bad.put(echomail.getArea(), (n == null) ? 1 : n + 1);
			return;
		}
		
		// Check for PATH loops if loop prevention is enabled
		if (isEchomailLoopPreventionEnabled()) {
			Ftn2D loopAddr = checkOurAddressInPath(echomail.getPath());
			if (loopAddr != null) {
				String linkAddr = (link != null) ? link.getLinkAddress() : "unknown";
				logger.l2(String.format("MAIL ERROR: Echomail loop detected - area: %s, loop at: %s, from link: %s", 
						echomail.getArea(), loopAddr.toString(), linkAddr));
				
				// Notify sysop about echomail loop
				notifySysop("Echomail Loop Detected", 
						String.format("An echomail message was dropped due to routing loop detection.\n\n" +
								"Echoarea: %s\n" +
								"From Link: %s\n" +
								"Message From: %s (%s)\n" +
								"Subject: %s\n" +
								"Message Date: %s\n\n" +
								"Loop detected at address: %s\n\n" +
								"PATH: %s\n" +
								"Parsed PATH: %s\n" +
								"Our addresses: %s\n\n" +
								"This message has already passed through our system (%s) as shown in the PATH. " +
								"This indicates a circular routing configuration. Check your echomail links and routing.",
								echomail.getArea(), linkAddr,
								echomail.getFromAddr().toString(), echomail.getFromName(),
								echomail.getSubject(), 
								(echomail.getDate() != null) ? echomail.getDate().toString() : "unknown",
								loopAddr.toString(), 
								echomail.getPath() != null ? echomail.getPath() : "null",
								echomail.getPath() != null ? echomail.getPath().toString() : "null",
								MainHandler.getCurrentInstance().getInfo().getAddressList().toString(),
								loopAddr.toString()));
				
				Integer n = bad.get(echomail.getArea());
				bad.put(echomail.getArea(), (n == null) ? 1 : n + 1);
				return;
			}
		}
		
		// malicious messages without MSGID occur

		if (echomail.getMsgid() != null) {
			if (isADupe(area, echomail.getMsgid())) {
				logger.l2(String.format("MAIL ERROR: Duplicate message detected - area: %s, msgid: %s, from: %s", 
						echomail.getArea(), echomail.getMsgid(), echomail.getFromAddr().toString()));
				
				// Notify sysop about duplicate message (may indicate routing loops or resends)
				notifySysop("Duplicate Echomail Detected", 
						String.format("A duplicate echomail message was detected and dropped.\n\n" +
								"Echoarea: %s\n" +
								"Message ID: %s\n" +
								"From Link: %s\n" +
								"Message From: %s (%s)\n" +
								"Subject: %s\n" +
								"Message Date: %s\n\n" +
								"This may indicate routing loops, duplicate sends, or " +
								"links processing the same message multiple times.",
								echomail.getArea(), echomail.getMsgid(),
								(link != null) ? link.getLinkAddress() : "unknown",
								echomail.getFromAddr().toString(), echomail.getFromName(),
								echomail.getSubject(), 
								(echomail.getDate() != null) ? echomail.getDate().toString() : "unknown"));
				
				Integer n = bad.get(echomail.getArea());
				bad.put(echomail.getArea(), (n == null) ? 1 : n + 1);
				return;
			}
		}

		processRewrite(echomail);

		Echomail mail = new Echomail();
		mail.setArea(area);
		mail.setDate(echomail.getDate());
		mail.setFromFTN(echomail.getFromAddr().toString());
		mail.setFromName(echomail.getFromName());
		mail.setToName(echomail.getToName());
		mail.setSubject(echomail.getSubject());
		mail.setText(echomail.getText());
		mail.setSeenBy(write2D(echomail.getSeenby(), true));
		mail.setPath(write2D(echomail.getPath(), false));
		mail.setMsgid(echomail.getMsgid());
		ORMManager.get(Echomail.class).save(mail);
		if (mail.getId() != null) {
			// Check if message is too old to forward to links
			boolean tooOldToForward = isMessageTooOldToForward(echomail.getDate());
			if (tooOldToForward) {
				logger.l3(String.format("Message in area %s is older than threshold - stored locally but not forwarded to links (date: %s)", 
						echomail.getArea(), echomail.getDate()));
			}
			
			for (Subscription sub : getSubscription(area)) {
				if (link == null
						|| !sub.getLink().equals(link)
						&& !getOptionBooleanDefFalse(sub.getLink(),
								LinkOption.BOOLEAN_PAUSE)) {
					
					// Only forward to links if message is not too old
					if (!tooOldToForward) {
						ORMManager.get(EchomailAwaiting.class).save(
								new EchomailAwaiting(sub.getLink(), mail));
						pollLinks.add(sub.getLink());
					}
				}
			}
		}
		Notifier.INSTANCE.notify(new NewEchomailEvent(mail));
		Integer n = tossed.get(echomail.getArea());
		tossed.put(echomail.getArea(), (n == null) ? 1 : n + 1);

	}

	/**
	 * Get messages from bundles
	 * 
	 * @param message
	 *            message
	 */
	public static int tossIncoming(Message message) {
		if (message == null) {
			return 0;
		}

		try {
			unpack(message);
			TosserQueue.getInstance().toss();
		} catch (IOException e) {
			logger.l1(
					"Exception file tossing message "
							+ message.getMessageName(), e);
			return 1;
		}
		try {
			message.getInputStream().close();
		} catch (IOException ignore) {
		}
		message.delete();
		return 0;
	}

	/**
	 * Parse files in inbound folder
	 */
	public void tossInboundDirectory() {
		running = true;
		logger.l5("Start tossInboundDirectory()");
		Set<Link> poll = new HashSet<>();
		File inbound = new File(getInbound());
		final File[] listFiles = inbound.listFiles();
		if (listFiles != null) {
			for (File file : listFiles) {
				String loname = file.getName().toLowerCase();
				if (loname.matches("^[a-f0-9]{8}\\.pkt$")) {
					try {
						Message m = new Message(file);
						logger.l4("Tossing file " + file.getAbsolutePath());
						FtnMessage ftnm;
						FtnPkt pkt = new FtnPkt();
						pkt.unpack(m.getInputStream());
						
						// Track if this packet contains dropped messages
						boolean hasDroppedMessages = false;
						StringBuilder droppedInfo = new StringBuilder();
						int totalMessages = 0;
						int droppedMessages = 0;
						
						while ((ftnm = pkt.getNextMessage()) != null) {
							totalMessages++;
							int initialBadCount = bad.values().stream().mapToInt(Integer::intValue).sum();
							
							if (ftnm.isNetmail()) {
								tossNetmail(ftnm, true);
							} else {
								tossEchomail(ftnm, null, true);
							}
							
							// Check if message was dropped (bad count increased)
							int currentBadCount = bad.values().stream().mapToInt(Integer::intValue).sum();
							if (currentBadCount > initialBadCount) {
								hasDroppedMessages = true;
								droppedMessages++;
								droppedInfo.append(String.format("Message %d: %s from %s to %s - Subject: %s\n",
										totalMessages,
										ftnm.isNetmail() ? "Netmail" : "Echomail to " + ftnm.getArea(),
										ftnm.getFromAddr(),
										ftnm.getToAddr(),
										ftnm.getSubject()));
							}
						}
						
						// Save packet for troubleshooting if it contained dropped messages
						if (hasDroppedMessages) {
							String additionalInfo = String.format("Packet Statistics:\n" +
									"Total Messages: %d\n" +
									"Dropped Messages: %d\n" +
									"Processing Date: %s\n\n" +
									"Dropped Message Details:\n%s",
									totalMessages, droppedMessages, new Date().toString(), droppedInfo.toString());
							savePacketForTroubleshooting(file, "dropped_messages", additionalInfo);
						}
						
						file.delete();
					} catch (Exception e) {
						markAsBad(file, "Tossing failed");
						logger.l2(String.format("MAIL ERROR: Tossing failed for file %s - %s", 
								file.getName(), e.getLocalizedMessage()), e);
					}
				} else if (loname.matches("(s|u)inb\\d*.pkt")) {
					try {
						Message m = new Message(file);
						logger.l4("Tossing file " + file.getAbsolutePath());
						FtnPkt pkt = new FtnPkt();
						pkt.unpack(m.getInputStream());
						Link link = getLinkByFtnAddress(pkt.getFromAddr());
						boolean secure = loname.charAt(0) == 's'
								&& link != null;
						if (secure) {
							if (!getOptionBooleanDefTrue(link,
									LinkOption.BOOLEAN_IGNORE_PKTPWD)) {
								if (!link.getPaketPassword().equalsIgnoreCase(
										pkt.getPassword())) {
									logger.l2(String.format("MAIL ERROR: Packet password mismatch for link %s - file: %s", 
											link.getLinkAddress(), file.getName()));
									markAsBad(file, "Password mismatch");
									continue;
								}
							}
						}
						
						// Track if this packet contains dropped messages
						boolean hasDroppedMessages = false;
						StringBuilder droppedInfo = new StringBuilder();
						int totalMessages = 0;
						int droppedMessages = 0;
						
						FtnMessage ftnm;
						while ((ftnm = pkt.getNextMessage()) != null) {
							totalMessages++;
							int initialBadCount = bad.values().stream().mapToInt(Integer::intValue).sum();
							
							if (ftnm.isNetmail()) {
								tossNetmail(ftnm, secure);
							} else {
								tossEchomail(ftnm, link, secure);
							}
							
							// Check if message was dropped (bad count increased)
							int currentBadCount = bad.values().stream().mapToInt(Integer::intValue).sum();
							if (currentBadCount > initialBadCount) {
								hasDroppedMessages = true;
								droppedMessages++;
								droppedInfo.append(String.format("Message %d: %s from %s to %s - Subject: %s\n",
										totalMessages,
										ftnm.isNetmail() ? "Netmail" : "Echomail to " + ftnm.getArea(),
										ftnm.getFromAddr(),
										ftnm.getToAddr(),
										ftnm.getSubject()));
							}
						}
						
						// Save packet for troubleshooting if it contained dropped messages
						if (hasDroppedMessages) {
							String linkInfo = (link != null) ? link.getLinkAddress() : "unknown";
							String additionalInfo = String.format("Packet Statistics:\n" +
									"Total Messages: %d\n" +
									"Dropped Messages: %d\n" +
									"From Link: %s\n" +
									"Packet Type: %s\n" +
									"Processing Date: %s\n\n" +
									"Dropped Message Details:\n%s",
									totalMessages, droppedMessages, linkInfo, 
									secure ? "Secure" : "Unsecure", 
									new Date().toString(), droppedInfo.toString());
							savePacketForTroubleshooting(file, "dropped_messages", additionalInfo);
						}
						
						file.delete();
					} catch (Exception e) {
						markAsBad(file, "Tossing failed");
						logger.l2(String.format("MAIL ERROR: Tossing failed for file %s - %s", 
								file.getName(), e.getLocalizedMessage()), e);
					}
				} else if (loname.matches("^[a-z0-9]{8}\\.tic$")) {
					if (!MainHandler.getCurrentInstance().getBooleanProperty(
							FILEECHO_ENABLE, true)) {
						continue;
					}
					logger.l3("Processing " + file.getName());
					try {
						FileInputStream fis = new FileInputStream(file);
						FtnTIC tic = new FtnTIC();
						tic.unpack(fis);
						fis.close();
						String filename = tic.getFile().toLowerCase();
						File attach = FtnTools.guessFilename(filename, true);
						if (attach != null && attach.canRead()) { // processing
							logger.l3("File found as " + filename);
							if (!MainHandler.getCurrentInstance().getInfo()
									.getAddressList().contains(tic.getTo())) {
								markAsBad(file, "Destination mismatch");
								continue;
							}
							Link source = getLinkByFtnAddress(tic.getFrom());
							if (source == null) {
								markAsBad(file, "Source address not found");
								continue;
							}
							logger.l4("Processing file from " + tic.getFrom() + " (link: " + source.getLinkAddress() + ") for area: " + tic.getArea());
							Filearea area = null;
							try {
								area = getFileareaByName(tic.getArea(), source);
							} catch (Exception e) {
								logger.l2("Exception in getFileareaByName: " + e.getMessage(), e);
								e.printStackTrace();
							}
							if (area == null) {
								markAsBad(file, "Filearea '" + tic.getArea() + "' is not available for link " + source.getLinkAddress());
								continue;
							}
							Filemail mail = new Filemail();
							String filePath = getFilePath(area.getName(), tic.getFile());
							logger.l3("File destination path: " + filePath);
							File newFile = new File(filePath);
							logger.l3("Moving file from " + attach.getAbsolutePath() + " to " + newFile.getAbsolutePath());
							if (FileUtils.move(attach, newFile, true)) {
								logger.l3("File successfully moved to " + newFile.getAbsolutePath());
								mail.setFilepath(newFile.getAbsolutePath());
								
								// Generate FILES.BBS entry in the destination directory
								boolean filesBbsEnabled = MainHandler.getCurrentInstance().getBooleanProperty(FILES_BBS_ENABLE, true);
								logger.l3("FILES.BBS feature enabled: " + filesBbsEnabled);
								if (filesBbsEnabled) {
									try {
										boolean use8bit = MainHandler.getCurrentInstance().getBooleanProperty(FILEECHO_8BIT_ENABLE, false);
										String charset = MainHandler.getCurrentInstance().getProperty(FILEECHO_CHARSET, "CP866");
										logger.l3("Writing FILES.BBS entry for " + tic.getFile() + " to " + newFile.getParentFile().getAbsolutePath());
										logger.l4("File description: " + tic.getDesc());
										logger.l4("8-bit output enabled: " + use8bit + ", charset: " + (use8bit ? charset : "US-ASCII"));
										FilesBBSWriter.appendEntry(newFile.getParentFile(), 
												tic.getFile(), tic.getDesc(), use8bit, charset);
										logger.l3("Successfully wrote FILES.BBS entry");
									} catch (Exception e) {
										logger.l2("Failed to write FILES.BBS entry for " + 
												tic.getFile() + " in " + newFile.getParentFile().getAbsolutePath(), e);
										logger.l3("Exception details: " + e.getMessage());
										e.printStackTrace();
									}
								} else {
									logger.l3("FILES.BBS feature is disabled");
								}
								
								// Generate FILE_ID.DIZ entry in the destination directory
								boolean fileIdDizEnabled = MainHandler.getCurrentInstance().getBooleanProperty(FILE_ID_DIZ_ENABLE, true);
								logger.l3("FILE_ID.DIZ feature enabled: " + fileIdDizEnabled);
								if (fileIdDizEnabled) {
									try {
										boolean use8bit = MainHandler.getCurrentInstance().getBooleanProperty(FILEECHO_8BIT_ENABLE, false);
										String charset = MainHandler.getCurrentInstance().getProperty(FILEECHO_CHARSET, "CP866");
										logger.l3("Writing FILE_ID.DIZ entry for " + tic.getFile() + " to " + newFile.getParentFile().getAbsolutePath());
										logger.l4("File description: " + tic.getDesc());
										logger.l4("8-bit output enabled: " + use8bit + ", charset: " + (use8bit ? charset : "US-ASCII"));
										FileIdDizWriter.appendEntry(newFile.getParentFile(), 
												tic.getFile(), tic.getDesc(), use8bit, charset);
										logger.l3("Successfully wrote FILE_ID.DIZ entry");
									} catch (Exception e) {
										logger.l2("Failed to write FILE_ID.DIZ entry for " + 
												tic.getFile() + " in " + newFile.getParentFile().getAbsolutePath(), e);
										logger.l3("Exception details: " + e.getMessage());
										e.printStackTrace();
									}
								} else {
									logger.l3("FILE_ID.DIZ feature is disabled");
								}
							} else {
								mail.setFilepath(attach.getAbsolutePath());
							}
							mail.setFilearea(area);
							mail.setFilename(tic.getFile());
							mail.setFiledesc(tic.getDesc());
							mail.setOrigin(tic.getOrigin().toString());
							mail.setPath(tic.getPath());
							mail.setSeenby(write4D(tic.getSeenby()));
							mail.setCreated(new Date());
							ORMManager.get(Filemail.class).save(mail);
							if (mail.getId() != null) {
								for (FileSubscription sub : getSubscription(area)) {
									if (source != null) {
										if (sub.getLink().getId()
												.equals(source.getId())) {
											continue;
										}
									}
									if (!getOptionBooleanDefFalse(
											sub.getLink(),
											LinkOption.BOOLEAN_PAUSE)) {
										ORMManager.get(FilemailAwaiting.class)
												.save(new FilemailAwaiting(sub
														.getLink(), mail));
										if (getOptionBooleanDefFalse(
												sub.getLink(),
												LinkOption.BOOLEAN_CRASH_FILEMAIL)) {
											poll.add(sub.getLink());
										}
									}
								}
							}
							Notifier.INSTANCE
									.notify(new NewFilemailEvent(mail));
						} else {
							logger.l4("File " + filename
									+ " not found in inbound, waiting");
							continue;
						}
						file.delete();
					} catch (Exception e) {
						markAsBad(file, "TIC processing failed");
						logger.l2(String.format("MAIL ERROR: TIC processing failed for file %s - %s", 
								file.getName(), e.getLocalizedMessage()), e);
					}
				} else if (loname.matches("^[0-9a-f]{8}\\..?lo$")) {
					FtnAddress address = getPrimaryFtnAddress().clone();
					address.setPoint(0);
					try {
						address.setNet(Integer.parseInt(loname.substring(0, 4),
								16));
						address.setNode(Integer.parseInt(
								loname.substring(4, 8), 16));
						Link l = getLinkByFtnAddress(address);
						if (l != null) {
							try {
								BufferedReader br = new BufferedReader(
										new FileReader(file));
								while (br.ready()) {
									String name = br.readLine();
									if (name != null) {
										File f = new File(name);
										if (f.exists() && f.canRead()) {
											FileForLink ffl = new FileForLink();
											ffl.setLink(l);
											ffl.setFilename(name);
											ORMManager.get(FileForLink.class)
													.save(ffl);
										} else {
											logger.l2("File from ?lo not exists: "
													+ name);
										}
									}
								}
								br.close();
							} catch (Exception e) {
								markAsBad(file, "Unable to read files in ?lo");
							}
							poll.add(l);
						}
					} catch (NumberFormatException e) {
						markAsBad(file, "?LO file is invalid");
					}
					file.delete();

				}
			}
		}
		for (Link l : poll) {
			PollQueue.getSelf().add(
					ORMManager.get(Link.class).getById(l.getId()));
		}
		running = false;
	}

	private void markAsBad(File file, String message) {
		logger.l2(String.format("MAIL ERROR: File %s marked as bad - %s", file.getName(), message));
		
		// Get troubleshooting directory from config
		String troubleDir = MainHandler.getCurrentInstance()
				.getProperty(TROUBLESHOOTING_DIRECTORY, null);
		
		File badFile;
		if (troubleDir != null && !troubleDir.isEmpty()) {
			// Move to configured troubleshooting directory
			File troubleDirectory = new File(troubleDir);
			if (!troubleDirectory.exists()) {
				troubleDirectory.mkdirs();
			}
			badFile = new File(troubleDirectory, file.getName() + ".bad");
		} else {
			// Default behavior: rename in place
			badFile = new File(file.getAbsolutePath() + ".bad");
		}
		
		// Notify sysop about bad file
		notifySysop("Packet Processing Error", 
				String.format("A packet file was marked as bad and moved to quarantine.\n\n" +
						"File: %s\n" +
						"Reason: %s\n" +
						"Action: File moved to %s\n\n" +
						"This may indicate corrupted packets, password issues, or format problems.",
						file.getName(), message, badFile.getAbsolutePath()));
		
		file.renameTo(badFile);
	}

	/**
	 * Save packet copy to troubleshooting directory for analysis
	 * @param originalFile Original packet file
	 * @param reason Reason for saving (e.g., "dropped_echomail", "subscription_error")
	 * @param additionalInfo Additional context for the troubleshooting file
	 */
	private void savePacketForTroubleshooting(File originalFile, String reason, String additionalInfo) {
		// Check if troubleshooting directory is configured
		String troubleDir = MainHandler.getCurrentInstance()
				.getProperty(TROUBLESHOOTING_DIRECTORY, null);
		
		if (troubleDir == null || troubleDir.isEmpty()) {
			logger.l4("Troubleshooting directory not configured - packet copy not saved");
			return;
		}
		
		try {
			// Create troubleshooting directory if it doesn't exist
			File troubleDirectory = new File(troubleDir);
			if (!troubleDirectory.exists()) {
				troubleDirectory.mkdirs();
			}
			
			// Create filename with timestamp and reason
			String timestamp = String.valueOf(System.currentTimeMillis());
			String filename = String.format("%s_%s_%s.pkt", 
					originalFile.getName().replaceAll("\\.pkt$", ""), 
					reason, 
					timestamp);
			
			File troubleFile = new File(troubleDirectory, filename);
			
			// Copy the original file to troubleshooting directory
			FileInputStream fis = new FileInputStream(originalFile);
			FileOutputStream fos = new FileOutputStream(troubleFile);
			byte[] buffer = new byte[4096];
			int len;
			while ((len = fis.read(buffer)) > 0) {
				fos.write(buffer, 0, len);
			}
			fos.close();
			fis.close();
			
			// Create accompanying info file with details
			String infoFilename = filename.replaceAll("\\.pkt$", ".info");
			File infoFile = new File(troubleDirectory, infoFilename);
			
			StringBuilder infoContent = new StringBuilder();
			infoContent.append("Packet Troubleshooting Information\n");
			infoContent.append("==================================\n\n");
			infoContent.append("Original File: ").append(originalFile.getAbsolutePath()).append("\n");
			infoContent.append("Saved Time: ").append(new Date().toString()).append("\n");
			infoContent.append("Reason: ").append(reason).append("\n");
			infoContent.append("File Size: ").append(originalFile.length()).append(" bytes\n");
			infoContent.append("Last Modified: ").append(new Date(originalFile.lastModified()).toString()).append("\n");
			infoContent.append("\nAdditional Information:\n");
			infoContent.append("=======================\n");
			infoContent.append(additionalInfo != null ? additionalInfo : "No additional information provided");
			infoContent.append("\n\n--- Generated by jNode ").append(MainHandler.getVersion()).append(" ---\n");
			
			FileOutputStream infoFos = new FileOutputStream(infoFile);
			infoFos.write(infoContent.toString().getBytes());
			infoFos.close();
			
			logger.l3(String.format("Packet saved for troubleshooting: %s (reason: %s)", 
					troubleFile.getAbsolutePath(), reason));
			
		} catch (Exception e) {
			logger.l2("Failed to save packet for troubleshooting: " + originalFile.getName(), e);
		}
	}

	/**
	 * Send error notification to sysop
	 * @param subject Subject of the error notification
	 * @param errorDetails Detailed error message
	 */
	private void notifySysop(String subject, String errorDetails) {
		// Check if sysop notifications are enabled (default: true)
		boolean notificationsEnabled = MainHandler.getCurrentInstance()
				.getBooleanProperty("tosser.sysop.notifications", true);
		
		if (!notificationsEnabled) {
			logger.l4("Sysop notification skipped (disabled): " + subject);
			return;
		}
		
		try {
			FtnAddress sysopAddress = getPrimaryFtnAddress();
			String sysopName = MainHandler.getCurrentInstance().getInfo().getSysop();
			String stationName = MainHandler.getCurrentInstance().getInfo().getStationName();
			
			StringBuilder message = new StringBuilder();
			message.append("System Error Notification\n");
			message.append("========================\n\n");
			message.append(errorDetails);
			message.append("\n\n");
			message.append("--- ").append(MainHandler.getVersion()).append("\n");
			message.append("Time: ").append(new Date().toString()).append("\n");
			
			writeNetmail(sysopAddress, sysopAddress, stationName, sysopName, 
					subject, message.toString());
			
			logger.l3("Error notification sent to sysop: " + subject);
		} catch (Exception e) {
			logger.l2("Failed to send error notification to sysop", e);
		}
	}

	public static String getFileechoPath() {
		return MainHandler.getCurrentInstance().getProperty(FILEECHO_PATH,
				getInbound());
	}

	public void end() {
		if (!tossed.isEmpty()) {
			logger.l3("Messages wrote:");
			for (String area : tossed.keySet()) {
				logger.l3(String.format("\t%s - %d", area, tossed.get(area)));
			}
		}
		if (!bad.isEmpty()) {
			logger.l2("Messages dropped:");
			for (String area : bad.keySet()) {
				logger.l2(String.format("\t%s - %d", area, bad.get(area)));
			}
		}

		for (Link l : pollLinks) {
			if (getOptionBooleanDefFalse(l, LinkOption.BOOLEAN_CRASH_ECHOMAIL)) {
				PollQueue.getSelf().add(
						ORMManager.get(Link.class).getById(l.getId()));
			}
		}
		tossed.clear();
		bad.clear();
		pollLinks.clear();
		running = false;
	}

	private List<Message> packNetmail(FtnAddress address) {
		Link link = getLinkByFtnAddress(address);
		LinkedList<Message> messages = new LinkedList<>();
		if (link == null) {
			link = new Link();
			link.setLinkAddress(address.toString());
			link.setPaketPassword("-");
		}
		FtnAddress ourAka = selectOurAka(link, "packNetmail");
		FtnPkt header = new FtnPkt(ourAka, address,
				link.getPaketPassword(), new Date());
		List<Netmail> mail = null;
		boolean pack = (link.getId() != null) ? getOptionBooleanDefFalse(link,
				LinkOption.BOOLEAN_PACK_NETMAIL) : false;
		int num = 0;
		try {
			File f = createOutboundFile(link);
			FileOutputStream fos = new FileOutputStream(f);
			OutputStream os = (pack) ? new ZipOutputStream(fos) : fos;
			if (pack) {
				((ZipOutputStream) os).putNextEntry(new ZipEntry(generate8d()
						+ ".pkt"));
			}
			header.write(os);
			do {
				mail = new ArrayList<>();
				mail.addAll(ORMManager.get(Netmail.class).getAnd("send", "=",
						false, "to_address", "=", address.toString(),
						"route_via", "null"));
				if (link != null) {
					mail.addAll(getNetmail(link));
				}
				if (!mail.isEmpty()) {
					try {

						for (Netmail n : mail) {
							FtnMessage m = netmailToFtnMessage(n, ourAka);
							m.write(os);
							num++;
							if ((n.getAttr() & FtnMessage.ATTR_FILEATT) >= FtnMessage.ATTR_FILEATT) {
								String filename = n.getSubject();
								File file = guessFilename(filename, true);
								if (file != null && file.canRead()) {
									messages.add(new Message(file));
									logger.l5("Netmail with attached file "
											+ filename);
								}
							}
						}
						for (Netmail n : mail) {
							n.setSend(true);
							ORMManager.get(Netmail.class).update(n);
							logger.l4(String
									.format("Netmail #%d %s -> %s for %s flags %d was packed",
											n.getId(), n.getFromFTN(),
											n.getToFTN(),
											link.getLinkAddress(), n.getAttr()));
						}
					} catch (IOException e) {
						logger.l2("Error while packing netmail", e);
					}
					mail.clear();
				}
			} while (mail != null && !mail.isEmpty());
			header.finalz(os);
			fos.close();
			if (num == 0) {
				f.delete();
			} else {
				Message m = new Message(f);
				if (pack) {
					m.setMessageName(generateEchoBundle());
				} else {
					m.setMessageName(generate8d() + ".pkt");
				}
				messages.add(m);
			}
		} catch (IOException e) {
			logger.l2("Error while packing messages", e);
		}
		return messages;
	}

	private List<Message> packEchomail(Link link, FtnAddress address) {
		LinkedList<Message> messages = new LinkedList<>();
		boolean pack = getOptionBooleanDefTrue(link,
				LinkOption.BOOLEAN_PACK_ECHOMAIL);
		Ftn2D link2d = new Ftn2D(address.getNet(), address.getNode());
		int num = 0;
		int orphanedRecordsFound = 0;
		int loopCount = 0;
		final int MAX_LOOPS = 100; // Emergency circuit breaker
		try {
			List<EchomailAwaiting> email = null;
			File f = createOutboundFile(link);
			FileOutputStream fos = new FileOutputStream(f);
			OutputStream os = (pack) ? new ZipOutputStream(fos) : fos;
			FtnPkt header = new FtnPkt(selectOurAka(link, "packEchomail"), address,
					link.getPaketPassword(), new Date());
			if (pack) {
				((ZipOutputStream) os).putNextEntry(new ZipEntry(generate8d()
						+ ".pkt"));
			}
			header.write(os);
			do {
				loopCount++;
				if (loopCount > MAX_LOOPS) {
					logger.l1("EMERGENCY STOP: packEchomail exceeded maximum loops (" + MAX_LOOPS + 
						") for link_id=" + link.getId() + " - preventing infinite loop");
					break;
				}
				
				email = getEchoMail(link);
				if (!email.isEmpty()) {
					// Create a list to track records that need to be removed to prevent infinite loops
					List<EchomailAwaiting> toRemove = new ArrayList<>();
					
					for (EchomailAwaiting e : email) {
						Echomail mail = e.getMail();
						if (mail == null) {
							// Log orphaned record with available information
							String linkInfo = "link_id=" + e.getLink().getId();
							try {
								String linkAddress = e.getLink().getLinkAddress();
								if (linkAddress != null) {
									linkInfo += " (address=" + linkAddress + ")";
								}
							} catch (Exception ex) {
								// Link address might not be accessible due to foreign key issues
								linkInfo += " (address lookup failed)";
							}
							
							logger.l2("ERROR-TRIGGERED CLEANUP: echomailawait " + linkInfo + 
								" - echomail record does not exist, performing emergency cleanup of broken record");
							
							// Emergency cleanup only triggered by detection of broken database integrity
							deleteEAmailSafe(e);
							toRemove.add(e);
							orphanedRecordsFound++;
							continue;
						}
						Echoarea area = mail.getArea();
						if (area == null) {
							logger.l2("ERROR-TRIGGERED CLEANUP: echomail_id=" + mail.getId() + 
								" has no echoarea, performing emergency cleanup for link_id=" + e.getLink().getId());
							deleteEAmailSafe(e);
							toRemove.add(e);
							orphanedRecordsFound++;
							continue;
						}
						List<Ftn2D> path = read2D(mail.getPath());
						Set<Ftn2D> seenby = new HashSet<>(
								read2D(mail.getSeenBy()));
						if (seenby.contains(link2d) && address.getPoint() == 0) {
							logger.l5(link2d + " is in seenby for " + address);
							deleteEAmail(e);
							continue;
						}
						seenby.add(link2d);
						seenby.addAll(createSeenBy(area));
						FtnAddress ourAka = selectOurAka(link, "packEchomail-seenby");
						Ftn2D me = new Ftn2D(ourAka.getNet(), ourAka.getNode());
						seenby.add(me);
						if (!path.contains(me)) {
							path.add(me);
						}

						FtnMessage msg = createEchomail(address, mail, area,
								seenby, path);
						logger.l4("Echomail #" + mail.getId() + " ("
								+ area.getName() + ") packed for "
								+ link.getLinkAddress());
						msg.write(os);
						num++;
					}

					// Remove any orphaned records from the processing list to prevent infinite loops
					email.removeAll(toRemove);
					
					for (EchomailAwaiting e : email) {
						deleteEAmail(e);
					}
				}
			} while (!email.isEmpty());
			header.finalz(os);
			fos.close();
			if (num == 0) {
				f.delete();
			} else {
				Message m = new Message(f);
				if (pack) {
					m.setMessageName(generateEchoBundle());
				} else {
					m.setMessageName(generate8d() + ".pkt");
				}
				messages.add(m);
			}
		} catch (IOException e) {
			logger.l2("Error while packing echomails ", e);
		}
		
		// Log summary of orphaned records found and cleaned
		if (orphanedRecordsFound > 0) {
			logger.l2("SUMMARY: Cleaned up " + orphanedRecordsFound + " orphaned echomailawait records for link_id=" + 
				link.getId() + " (address=" + link.getLinkAddress() + ")");
		}
		
		return messages;
	}

	private void deleteEAmail(EchomailAwaiting e) {
		ORMManager.get(EchomailAwaiting.class).delete("link_id", "=",
				e.getLink(), "echomail_id", "null");
		ORMManager.get(EchomailAwaiting.class).delete("link_id", "=",
				e.getLink(), "echomail_id", "=", e.getMail());
	}

	/**
	 * Enhanced cleanup method for orphaned echomailawait records
	 * Handles transaction issues and provides better error reporting
	 */
	private void deleteEAmailSafe(EchomailAwaiting e) {
		boolean deleted = false;
		Long linkId = null;
		
		try {
			linkId = (e.getLink() != null) ? e.getLink().getId() : null;
		} catch (Exception ex) {
			logger.l1("ERROR: Cannot get link_id from orphaned echomailawait record: " + ex.getMessage());
			return;
		}
		
		if (linkId == null) {
			logger.l1("ERROR: Cannot delete orphaned echomailawait record - link_id is null");
			return;
		}
		
		// Method 1: Try direct object delete first
		try {
			ORMManager.get(EchomailAwaiting.class).delete(e);
			deleted = true;
			logger.l4("Successfully deleted orphaned echomailawait record using object delete for link_id=" + linkId);
		} catch (Exception ex) {
			logger.l4("Object delete failed for link_id=" + linkId + " (expected for tables without ID field): " + ex.getMessage());
		}
		
		// Method 2: If object delete failed, use criteria-based delete
		if (!deleted) {
			try {
				// Delete using the original deleteEAmail approach but with better logging
				ORMManager.get(EchomailAwaiting.class).delete("link_id", "=", e.getLink(), "echomail_id", "null");
				ORMManager.get(EchomailAwaiting.class).delete("link_id", "=", e.getLink(), "echomail_id", "=", e.getMail());
				deleted = true;
				logger.l4("Successfully deleted orphaned echomailawait record using criteria delete for link_id=" + linkId);
			} catch (Exception ex) {
				logger.l3("Criteria delete also failed for link_id=" + linkId + ": " + ex.getMessage());
			}
		}
		
		// Method 3: If all else fails, use raw SQL with better targeting
		if (!deleted) {
			try {
				// Get count before deletion to verify
				List<EchomailAwaiting> beforeCount = ORMManager.get(EchomailAwaiting.class).getAnd("link_id", "=", linkId);
				logger.l4("Found " + beforeCount.size() + " echomailawait records for link_id=" + linkId + " before cleanup");
				
				// Use executeRaw with a more comprehensive delete
				ORMManager.get(EchomailAwaiting.class).executeRaw(
					"DELETE FROM echomailawait WHERE link_id = " + linkId + 
					" AND (echomail_id IS NULL OR echomail_id NOT IN (SELECT id FROM echomail))");
				
				List<EchomailAwaiting> afterCount = ORMManager.get(EchomailAwaiting.class).getAnd("link_id", "=", linkId);
				logger.l3("RAW SQL cleanup for link_id=" + linkId + " completed. Before: " + beforeCount.size() + 
					", After: " + afterCount.size());
				
				deleted = (afterCount.size() < beforeCount.size());
				
			} catch (Exception fallbackEx) {
				logger.l1("CRITICAL: All cleanup methods failed for link_id=" + linkId + ": " + fallbackEx.getMessage());
			}
		}
		
		if (!deleted) {
			logger.l1("CRITICAL: Could not delete orphaned echomailawait record for link_id=" + linkId);
		}
	}

	/**
	 * Emergency cleanup method for orphaned echomailawait records
	 * NOTE: This method is intentionally disabled to prevent proactive cleanup.
	 * Cleanup should only be triggered by actual error detection during message processing.
	 * 
	 * @deprecated This method is not used - cleanup is error-triggered only
	 * @return always returns 0
	 */
	@Deprecated
	private int cleanupOrphanedEchomailAwait() {
		// Cleanup is now error-triggered only during message processing
		// This method is kept for reference but not used
		logger.l4("cleanupOrphanedEchomailAwait() called but disabled - cleanup is error-triggered only");
		return 0;
	}

	private void deleteFAMail(FilemailAwaiting f) {
		ORMManager.get(FilemailAwaiting.class).delete("link_id", "=",
				f.getLink(), "filemail_id", "null");
		ORMManager.get(FilemailAwaiting.class).delete("link_id", "=",
				f.getLink(), "filemail_id", "=", f.getMail());

	}

	private List<Message> packFilemail(Link link, FtnAddress address) {
		List<Message> msgs = new LinkedList<>();
		List<FilemailAwaiting> filemail = null;
		do {
			filemail = getFileMail(link);
			if (!filemail.isEmpty()) {
				for (FilemailAwaiting f : filemail) {
					Filemail mail = f.getMail();
					if (mail == null) {
						deleteFAMail(f);
						continue;
					}
					Filearea area = mail.getFilearea();
					if (area == null) {

						continue;
					}

					File attach = new File(mail.getFilepath());
					if (!attach.canRead()) {
						deleteFAMail(f);
						logger.l3("File unavailable");
						continue;
					}

					Set<FtnAddress> seenby = new HashSet<>(
							read4D(mail.getSeenby()));
					if (seenby.contains(address)) {
						deleteFAMail(f);
						logger.l3("This file have a seen-by for link");
						continue;
					}
					for (FtnAddress our : MainHandler.getCurrentInstance()
							.getInfo().getAddressList()) {
						seenby.add(our);
					}
					for (FileSubscription sub : getSubscription(area)) {
						try {
							Link l = ORMManager.get(Link.class).getById(
									sub.getLink().getId());
							seenby.add(new FtnAddress(l.getLinkAddress()));
						} catch (NullPointerException e) {
							logger.l4("bad link for FileSubscription " + sub
									+ " - ignored", e);
						}
					}
					List<FtnAddress> sb = new ArrayList<>(seenby);
					Collections.sort(sb, new Ftn4DComparator());
					FtnTIC tic = createTic(link, mail, attach);
					tic.setTo(address);
					tic.setSeenby(sb);
					tic.setPath(mail.getPath() + "Path "
							+ getPrimaryFtnAddress() + " "
							+ System.currentTimeMillis() / 1000 + " "
							+ FORMAT.format(new Date()) + " "
							+ MainHandler.getVersion() + "\r\n");
					try {
						File tmp = File.createTempFile("outtic", ".tic",
								new File(getInbound()));
						FileOutputStream fos = new FileOutputStream(tmp);
						fos.write(tic.pack());
						fos.close();
						Message message = new Message(tmp);
						message.setMessageName(generateTic());
						msgs.add(message);
						Message m2 = new Message(mail.getFilename(),
								attach.length());
						m2.setInputStream(new FileInputStream(attach));
						msgs.add(m2);
						deleteFAMail(f);
					} catch (IOException e) {
						logger.l2("Error while packing tic:", e);
					}
				}
			}
		} while (!filemail.isEmpty());
		List<FileForLink> ffls = ORMManager.get(FileForLink.class).getAnd(
				"link_id", "eq", link);
		for (FileForLink ffl : ffls) {
			try {
				File file = new File(ffl.getFilename());
				Message m = new Message(file);
				ORMManager.get(FileForLink.class).delete("link_id", "=", link,
						"filename", "=", ffl.getFilename());
				msgs.add(m);
			} catch (Exception ex) {
				logger.l1(MessageFormat
						.format("Exception during get file {0} for link {1}",
								ffl, link), ex);
			}
		}
		return msgs;
	}

	public List<Message> getMessages2(FtnAddress address) {
		LinkedList<Message> messages = new LinkedList<>();
		String key = address.toString().intern();
		synchronized (key) {
			messages.addAll(packNetmail(address));
			Link link = getLinkByFtnAddress(address);
			if (link != null) {
				messages.addAll(packEchomail(link, address));
				messages.addAll(packFilemail(link, address));
				if (messages.isEmpty()) {
					File inbound = new File(getInbound());
					final File[] listFiles = inbound.listFiles();
					if (listFiles != null) {
						for (File file : listFiles) {
							String loname = file.getName().toLowerCase();
							if (loname.matches("^out_" + link.getId()
									+ "\\..*$")) {
								boolean packed = true;
								try {
									new ZipFile(file).close();
								} catch (Exception e) {
									packed = false;
								}
								try {
									Message m = new Message(file);
									if (packed) {
										m.setMessageName(generateEchoBundle());
									} else {
										m.setMessageName(generate8d() + ".pkt");
									}
									messages.add(m);
									break;
								} catch (Exception e) {
									// ignore
								}

							}
						}
					}
				}
			}

		}
		return messages;
	}

	/**
	 * Get new messages for link
	 * 
	 * @param link
	 * @return
	 */
	public static List<Message> getMessagesForLink(Link link) {
		return TosserQueue.getInstance().getMessages(link);
	}

	protected FtnTIC createTic(Link link, Filemail mail, File attach) {
		FtnTIC tic = new FtnTIC();
		try {
			CRC32 crc32 = new CRC32();
			FileInputStream fis = new FileInputStream(attach);
			int len;
			do {
				byte buf[];
				len = fis.available();
				if (len > 1024) {
					buf = new byte[1024];
				} else {
					buf = new byte[len];
				}
				fis.read(buf);
				crc32.update(buf);
			} while (len > 0);
			tic.setCrc32(crc32.getValue());
			fis.close();
			tic.setArea(mail.getFilearea().getName().toUpperCase());
			tic.setAreaDesc(mail.getFilearea().getDescription());
			tic.setFile(mail.getFilename());
			tic.setSize(attach.length());
			tic.setDesc(mail.getFiledesc());
			tic.setPassword(link.getPaketPassword());
			tic.setFrom(FtnTools.selectOurAka(link, "createTic"));
			tic.setTo(null);
			tic.setOrigin(new FtnAddress(mail.getOrigin()));
			return tic;
		} catch (IOException e) {
			logger.l2("fail process tic", e);
		}
		return null;
	}

	protected Set<Ftn2D> createSeenBy(Echoarea area) {
		List<Subscription> ssubs = getSubscription(area);
		Set<Ftn2D> seenby = new HashSet<>();
		for (Subscription ssub : ssubs) {
			try {
				Link _sslink = ORMManager.get(Link.class).getById(
						ssub.getLink().getId());
				FtnAddress addr = new FtnAddress(_sslink.getLinkAddress());
				Ftn2D d2 = new Ftn2D(addr.getNet(), addr.getNode());
				seenby.add(d2);
			} catch (NullPointerException e) {
				logger.l1("Bad link for subscription " + ssub + " : ignored", e);
			}

		}
		return seenby;
	}

	protected FtnMessage createEchomail(FtnAddress link_address, Echomail mail,
			Echoarea area, Set<Ftn2D> seenby, List<Ftn2D> path) {
		FtnMessage message = new FtnMessage();
		message.setNetmail(false);
		message.setArea(area.getName().toUpperCase());
		message.setFromName(mail.getFromName());
		message.setToName(mail.getToName());
		message.setFromAddr(getPrimaryFtnAddress());
		message.setToAddr(link_address);
		message.setDate(mail.getDate());
		message.setSubject(mail.getSubject());
		message.setText(mail.getText());
		message.setSeenby(new ArrayList<>(seenby));
		message.setPath(path);
		message.setMsgid(mail.getMsgid());
		return message;
	}

	private List<Netmail> getNetmail(Link link) {
		if (link.getId() != null) {
			return ORMManager.get(Netmail.class).getLimitAnd(
					MainHandler.getCurrentInstance().getIntegerProperty(
							MAIL_LIMIT, 100), "send", "=", false, "route_via",
					"=", link);
		} else {
			return new ArrayList<>();
		}
	}

	private List<EchomailAwaiting> getEchoMail(Link link) {
		if (link.getId() != null) {
			return ORMManager.get(EchomailAwaiting.class).getLimitAnd(
					MainHandler.getCurrentInstance().getIntegerProperty(
							MAIL_LIMIT, 100), "link_id", "=", link);
		} else {
			return new ArrayList<>();
		}
	}

	private List<Subscription> getSubscription(Echoarea area) {
		return ORMManager.get(Subscription.class).getAnd("echoarea_id", "=",
				area);
	}

	private List<FilemailAwaiting> getFileMail(Link link) {
		if (link.getId() != null) {
			return ORMManager.get(FilemailAwaiting.class).getLimitAnd(
					MainHandler.getCurrentInstance().getIntegerProperty(
							MAIL_LIMIT, 100), "link_id", "=", link);
		} else {
			return new ArrayList<>();
		}
	}

	private List<FileSubscription> getSubscription(Filearea area) {
		return ORMManager.get(FileSubscription.class).getAnd("filearea_id",
				"=", area);
	}

	public boolean isRunning() {
		return running;
	}

	private boolean isTraceEnabled() {
		return MainHandler.getCurrentInstance().getBooleanProperty("trace.enabled", false);
	}

	private void sendTraceNotification(FtnMessage netmail, Link routeVia) {
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("TRACE Notification\n");
			sb.append("==================\n\n");
			sb.append("Your PING message has passed through this node in transit to its destination.\n\n");
			sb.append("Transit node: ").append(MainHandler.getCurrentInstance().getInfo().getStationName()).append("\n");
			sb.append("Final destination: ").append(netmail.getToAddr()).append("\n");
			sb.append("Next hop: ").append(routeVia.getLinkAddress()).append("\n\n");
			
			sb.append("Message details:\n");
			sb.append("From: ").append(netmail.getFromName()).append(" (").append(netmail.getFromAddr()).append(")\n");
			sb.append("To: ").append(netmail.getToName()).append(" (").append(netmail.getToAddr()).append(")\n");
			sb.append("Subject: ").append(netmail.getSubject()).append("\n");
			sb.append("Date: ").append(netmail.getDate()).append("\n\n");
			
			// Quote all via lines at time of transit
			sb.append("Via lines at time of transit:\n");
			sb.append("-----------------------------\n");
			List<String> viaLines = extractViaLines(netmail);
			if (!viaLines.isEmpty()) {
				for (String via : viaLines) {
					sb.append(" * ").append(via).append("\n");
				}
			} else {
				sb.append("(No via lines found)\n");
			}
			
			sb.append("\n--- TRACE Notification generated by jNode ---\n");
			
			// Send trace notification back to original sender
			FtnTools.writeReply(netmail, "TRACE Notification", sb.toString());
			
			logger.l3("TRACE notification sent to " + netmail.getFromAddr() + " for message in transit to " + netmail.getToAddr());
			
		} catch (Exception e) {
			logger.l2("Error sending TRACE notification", e);
		}
	}

	/**
	 * Extract Via lines from message text
	 * Via lines are control lines starting with \001Via
	 */
	private List<String> extractViaLines(FtnMessage fmsg) {
		List<String> viaLines = new ArrayList<>();
		if (fmsg.getText() != null) {
			String[] lines = fmsg.getText().split("\n");
			for (String line : lines) {
				if (line.startsWith("\001Via") || line.startsWith(".Via")) {
					viaLines.add(line);
				}
			}
		}
		return viaLines;
	}

	/**
	 * Check if any of our addresses appear twice in the PATH indicating a true loop.
	 * A loop is only detected if our address appears at least twice in the PATH.
	 * Single appearance is allowed for normal routing scenarios.
	 * @param path List of Ftn2D addresses in PATH
	 * @return Our address if found twice in PATH (indicating a loop), null otherwise
	 */
	private Ftn2D checkOurAddressInPath(List<Ftn2D> path) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		
		// Get all our 2D addresses
		Set<Ftn2D> ourAddresses = new HashSet<>();
		for (FtnAddress addr : MainHandler.getCurrentInstance().getInfo().getAddressList()) {
			ourAddresses.add(new Ftn2D(addr.getNet(), addr.getNode()));
		}
		
		// Check if any of our addresses appear twice in the path
		Map<Ftn2D, Integer> addressCounts = new HashMap<>();
		for (Ftn2D pathAddr : path) {
			if (ourAddresses.contains(pathAddr)) {
				int count = addressCounts.getOrDefault(pathAddr, 0) + 1;
				addressCounts.put(pathAddr, count);
				
				// If we've seen this address twice, it's a loop
				if (count >= 2) {
					return pathAddr;
				}
			}
		}
		
		return null;
	}

	/**
	 * Check if any of our addresses appear in VIA lines (indicating a loop through our system)
	 * @param viaLines List of VIA lines from the message
	 * @return Our address if found in VIA (indicating a loop), null otherwise
	 */
	private FtnAddress checkOurAddressInVia(List<String> viaLines) {
		if (viaLines == null || viaLines.isEmpty()) {
			return null;
		}
		
		// Get all our addresses
		Set<FtnAddress> ourAddresses = new HashSet<>(MainHandler.getCurrentInstance().getInfo().getAddressList());
		
		// Check each VIA line
		for (String viaLine : viaLines) {
			// Via line formats vary:
			// Standard: \001Via 2:5020/1042 jNode ver 2.0.1 Fri Nov 06 2014 at 20:17:07
			// ParToss: .Via ParToss 1.10.073/ZOO/W32 2:6078/80.0, 25 Jul 25 06:47:20
			// Other formats: \001Via program 2:net/node date/time
			
			FtnAddress foundAddress = extractAddressFromViaLine(viaLine);
			if (foundAddress != null) {
				// Check if this is one of our addresses
				for (FtnAddress ourAddr : ourAddresses) {
					if (foundAddress.equals(ourAddr)) {
						return ourAddr;
					}
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Extract FTN address from a Via line using multiple parsing strategies
	 * @param viaLine The Via line to parse
	 * @return FtnAddress if found, null otherwise
	 */
	private FtnAddress extractAddressFromViaLine(String viaLine) {
		// Check if line starts with control character (required for valid Via lines)
		// Valid control characters: \001 (ASCII 1) or . (dot)
		if (viaLine == null || viaLine.isEmpty() || 
			(viaLine.charAt(0) != '\001' && viaLine.charAt(0) != '.')) {
			return null;
		}
		
		// Strategy 1: Standard format - address is second part after Via
		String[] parts = viaLine.split(" ");
		if (parts.length >= 2) {
			try {
				FtnAddress addr = new FtnAddress(parts[1]);
				return addr;
			} catch (Exception e) {
				// Continue to other strategies
			}
		}
		
		// Strategy 2: Search for FTN address pattern anywhere in the line
		// Pattern: zone:net/node[.point] (optionally with @ and domain)
		java.util.regex.Pattern addressPattern = java.util.regex.Pattern.compile(
			"(?:^|\\s)(\\d{1,5}:\\d{1,5}/\\d{1,5}(?:\\.\\d{1,5})?(?:@\\w+(?:\\.\\w+)*)?)(?:\\s|,|$)");
		java.util.regex.Matcher matcher = addressPattern.matcher(viaLine);
		
		while (matcher.find()) {
			String addressStr = matcher.group(1);
			try {
				FtnAddress addr = new FtnAddress(addressStr);
				return addr;
			} catch (Exception e) {
				// Continue searching for other addresses
			}
		}
		
		// If we can't parse any address, log it for debugging
		logger.l4("Could not parse VIA address from line: " + viaLine);
		return null;
	}

	/**
	 * Check if echomail loop prevention is enabled
	 * @return true if enabled (default), false otherwise
	 */
	private boolean isEchomailLoopPreventionEnabled() {
		return MainHandler.getCurrentInstance().getBooleanProperty(LOOP_PREVENTION_ECHOMAIL, true);
	}

	/**
	 * Check if netmail loop prevention is enabled
	 * @return true if enabled (default), false otherwise
	 */
	private boolean isNetmailLoopPreventionEnabled() {
		return MainHandler.getCurrentInstance().getBooleanProperty(LOOP_PREVENTION_NETMAIL, true);
	}

	/**
	 * Check if message is too old to forward to links
	 * @param messageDate Date of the message
	 * @return true if message is older than threshold and should not be forwarded
	 */
	private boolean isMessageTooOldToForward(Date messageDate) {
		// Get threshold in days (0 = disabled, default 30 days)
		int thresholdDays = MainHandler.getCurrentInstance().getIntegerProperty(OLD_MESSAGE_DAYS_THRESHOLD, 30);
		
		// If threshold is 0, feature is disabled
		if (thresholdDays <= 0) {
			return false;
		}
		
		// If message has no date, don't block it
		if (messageDate == null) {
			return false;
		}
		
		// Calculate age in milliseconds
		long currentTime = System.currentTimeMillis();
		long messageTime = messageDate.getTime();
		long ageInMillis = currentTime - messageTime;
		long thresholdInMillis = thresholdDays * 24L * 60L * 60L * 1000L;
		
		return ageInMillis > thresholdInMillis;
	}

	/**
	 * Format PATH information for display in error messages
	 * @param path List of Ftn2D addresses in PATH
	 * @return Formatted string showing the path traversal
	 */
	private String formatPath(List<Ftn2D> path) {
		if (path == null || path.isEmpty()) {
			return "No PATH entries";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < path.size(); i++) {
			if (i > 0) sb.append(" -> ");
			sb.append(path.get(i).toString());
		}
		return sb.toString();
	}

	/**
	 * Get the last node in the PATH (the most recent node that handled the message)
	 * @param path List of Ftn2D addresses in PATH
	 * @return String representation of the last node or "none" if empty
	 */
	private String getLastPathNode(List<Ftn2D> path) {
		if (path == null || path.isEmpty()) {
			return "none";
		}
		return path.get(path.size() - 1).toString();
	}

	/**
	 * Format SEEN-BY information for display in error messages
	 * @param seenby List of Ftn2D addresses that have seen the message
	 * @return Formatted string showing all nodes that have seen the message
	 */
	private String formatSeenBy(List<Ftn2D> seenby) {
		if (seenby == null || seenby.isEmpty()) {
			return "No SEEN-BY entries";
		}
		List<Ftn2D> sorted = new ArrayList<>(seenby);
		Collections.sort(sorted, new Ftn2DComparator());
		StringBuilder sb = new StringBuilder();
		int count = 0;
		for (Ftn2D node : sorted) {
			if (count > 0) {
				if (count % 10 == 0) {
					sb.append("\n        ");
				} else {
					sb.append(" ");
				}
			}
			sb.append(node.toString());
			count++;
		}
		return sb.toString();
	}

}
