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
							"Subject: %s\n\n" +
							"Error Details: %s\n\n" +
							"Suggested Action:\n%s",
							errorDescription,
							echomail.getArea(), linkAddr,
							echomail.getFromAddr().toString(), echomail.getFromName(),
							echomail.getSubject(),
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
			for (Subscription sub : getSubscription(area)) {
				if (link == null
						|| !sub.getLink().equals(link)
						&& !getOptionBooleanDefFalse(sub.getLink(),
								LinkOption.BOOLEAN_PAUSE)) {
					ORMManager.get(EchomailAwaiting.class).save(
							new EchomailAwaiting(sub.getLink(), mail));
					pollLinks.add(sub.getLink());
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
						while ((ftnm = pkt.getNextMessage()) != null) {
							if (ftnm.isNetmail()) {
								tossNetmail(ftnm, true);
							} else {
								tossEchomail(ftnm, null, true);
							}
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
						FtnMessage ftnm;
						while ((ftnm = pkt.getNextMessage()) != null) {
							if (ftnm.isNetmail()) {
								tossNetmail(ftnm, secure);
							} else {
								tossEchomail(ftnm, link, secure);
							}
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
		
		// Notify sysop about bad file
		notifySysop("Packet Processing Error", 
				String.format("A packet file was marked as bad and moved to quarantine.\n\n" +
						"File: %s\n" +
						"Reason: %s\n" +
						"Action: File renamed to %s.bad\n\n" +
						"This may indicate corrupted packets, password issues, or format problems.",
						file.getName(), message, file.getName()));
		
		file.renameTo(new File(file.getAbsolutePath() + ".bad"));
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
				email = getEchoMail(link);
				if (!email.isEmpty()) {
					for (EchomailAwaiting e : email) {
						Echomail mail = e.getMail();
						if (mail == null) {
							deleteEAmail(e);
							continue;
						}
						Echoarea area = mail.getArea();
						if (area == null) {
							deleteEAmail(e);
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
		return messages;
	}

	private void deleteEAmail(EchomailAwaiting e) {
		ORMManager.get(EchomailAwaiting.class).delete("link_id", "=",
				e.getLink(), "echomail_id", "null");
		ORMManager.get(EchomailAwaiting.class).delete("link_id", "=",
				e.getLink(), "echomail_id", "=", e.getMail());

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
				if (line.startsWith("\001Via")) {
					viaLines.add(line);
				}
			}
		}
		return viaLines;
	}

	/**
	 * Check if any of our addresses appear in the PATH indicating a true loop.
	 * A loop is only detected if our address appears in the PATH and there are 
	 * other addresses after it. If our address is the last entry in the PATH,
	 * it's not a loop (normal POINT system behavior).
	 * @param path List of Ftn2D addresses in PATH
	 * @return Our address if found in PATH with other addresses after it (indicating a loop), null otherwise
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
		
		// Check if any of our addresses appear in the path, but ignore if it's the last entry
		for (int i = 0; i < path.size() - 1; i++) {  // Note: path.size() - 1 to exclude last entry
			Ftn2D pathAddr = path.get(i);
			if (ourAddresses.contains(pathAddr)) {
				// Found our address with other addresses after it - this is a true loop
				return pathAddr;
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
			// Via line format: \001Via address jNode version date/time
			// Example: \001Via 2:5020/1042 jNode ver 2.0.1 Fri Nov 06 2014 at 20:17:07
			String[] parts = viaLine.split(" ");
			if (parts.length >= 2) {
				try {
					// The address is the second part (after \001Via)
					FtnAddress viaAddr = new FtnAddress(parts[1]);
					
					// Check if this is one of our addresses
					for (FtnAddress ourAddr : ourAddresses) {
						if (viaAddr.equals(ourAddr)) {
							return ourAddr;
						}
					}
				} catch (Exception e) {
					// If we can't parse the address, log it and continue
					logger.l3("Could not parse VIA address from line: " + viaLine);
				}
			}
		}
		
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

}
