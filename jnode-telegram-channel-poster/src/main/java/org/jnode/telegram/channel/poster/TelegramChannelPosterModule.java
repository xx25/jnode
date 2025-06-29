package org.jnode.telegram.channel.poster;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;

import jnode.dto.Echoarea;
import jnode.event.IEvent;
import jnode.ftn.FtnTools;
import jnode.logger.Logger;
import jnode.module.JnodeModule;
import jnode.module.JnodeModuleException;

public class TelegramChannelPosterModule extends JnodeModule {
	private static final String CONFIG_CHANNELS_FOLDER = "channels_folder";
	private static final String CONFIG_NEW_MESSAGES_CHECK_INTERVAL = "new_messages_check_interval";
	private static final String CONFIG_AREAS_FILE = "areas_file";
	
	private static final int MAX_SUBJECT_LENGTH = 72;
	
	private static final Logger logger = Logger
			.getLogger(TelegramChannelPosterModule.class);

	private String channelsFolder;
	private String areasFile;
	private int newMessagesCheckInterval;

	public TelegramChannelPosterModule(String configFile) throws JnodeModuleException {
		super(configFile);
		channelsFolder = properties.getProperty(CONFIG_CHANNELS_FOLDER);
		areasFile = properties.getProperty(CONFIG_AREAS_FILE);
		newMessagesCheckInterval = Integer.valueOf(properties.getProperty(CONFIG_NEW_MESSAGES_CHECK_INTERVAL, "30"));
	}

	@Override
	public void handle(IEvent event) {}

	@Override
	public void start() {
		logger.l3("Module started");
		try {
			while (true) {
				Properties areaList = new Properties();
				areaList.load(new FileInputStream(areasFile));				
				File channelsDir = new File(channelsFolder);
				File[] channels = channelsDir.listFiles();
				for(File channel: channels) {
					if(!channel.isDirectory()) {
						continue;
					}
					String areaName = areaList.getProperty(channel.getName());
					if(areaName == null) {
						logger.l4("no area found for channel " + channel.getName());
						continue;
					}
			        Echoarea area = FtnTools.getAreaByName(areaName, null);
			        if (area == null) {
			            logger.l4("No such echoarea - " + areaName);
			            continue;
			        }
					File[] messageFiles = channel.listFiles();
					Arrays.sort(messageFiles, new Comparator<File>() {
						@Override
						public int compare(File o1, File o2) {
							return o1.getName().compareTo(o2.getName());
						}
					});
					for(File messageFile: messageFiles) {
						ArrayList<String> strings = new ArrayList<>();
						BufferedReader br = new BufferedReader(new FileReader(messageFile)); 
						String st; 
						while ((st = br.readLine()) != null) {
							strings.add(st); 
						} 
						br.close();
						if(strings.isEmpty()) {
							logger.l4("empty file: " + messageFile.getName());
						} else {
							//use 1st line as message subject
							String subject = strings.get(0);
							if(subject.length() > MAX_SUBJECT_LENGTH) {
								subject = subject.substring(0, MAX_SUBJECT_LENGTH - 4) + "...";
							}
							StringBuffer stringBuffer = new StringBuffer();
							for(String str: strings) {
								stringBuffer.append(str);
								stringBuffer.append("\n");
							}
							String message = stringBuffer.toString();
							logger.l4("posting to " + areaName + ": " + subject);
							FtnTools.writeEchomail(area, subject, message, "Telegram Channels Robot", "All");
						}
						messageFile.delete();
					}
				}
				
				try {
					Thread.sleep(newMessagesCheckInterval * 1000 * 60);
				} catch (InterruptedException e) {
				}
			}			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}

}
