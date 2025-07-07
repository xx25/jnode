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

package jnode.install;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import jnode.dto.Link;
import jnode.ftn.types.FtnAddress;
import jnode.main.MainHandler;
import jnode.orm.ORMManager;

public class GUIConfigurator {
	private String configFile;

	private List<String> config = fillConfigList();
	private Map<String, Component> configMap;
	private Map<String, String> configNames = fillConfigNames();
	private JFrame frmJnodeConfigurator;

	private JPanel linksPanel;

	/**
	 * Launch the application.
	 */
	public static void main(final String[] args) {
		if (args.length == 0) {
			System.err.println("Args must have a config!");
			System.exit(0);
		}

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					GUIConfigurator window = new GUIConfigurator(args[0]);
					window.frmJnodeConfigurator.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private ArrayList<String> fillConfigList() {
		ArrayList<String> ret = new ArrayList<>();
		ret.add("info.stationname");
		ret.add("info.location");
		ret.add("info.sysop");
		ret.add("info.ndl");
		ret.add("info.address");
		ret.add("log.level");
		ret.add("jdbc.url");
		ret.add("jdbc.user");
		ret.add("jdbc.pass");
		ret.add("binkp.server");
		ret.add("binkp.bind ");
		ret.add("binkp.port");
		ret.add("binkp.ipv6.enable");
		ret.add("binkp.bind6");
		ret.add("binkp.inbound");
		ret.add("binkp.client");
		ret.add("poll.period");
		ret.add("poll.delay");
		ret.add("fileecho.enable");
		ret.add("fileecho.path");
		ret.add("fileecho.files_bbs.enable");
		ret.add("fileecho.file_id_diz.enable");
		ret.add("fileecho.8bit_output.enable");
		ret.add("fileecho.output_charset");
		ret.add("stat.enable");
		ret.add("stat.area");
		ret.add("jscript.enable");
		ret.add("threadpool.queue_size");
		ret.add("tosser.loop_prevention.echomail");
		ret.add("tosser.loop_prevention.netmail");
		return ret;
	}

	private HashMap<String, String> fillConfigNames() {
		HashMap<String, String> props = new HashMap<>();
		props.put("info.stationname", "Node name");
		props.put("info.location", "Node location");
		props.put("info.sysop", "Sysop name");
		props.put("info.ndl", "NDL");
		props.put("info.address", "Node address");
		props.put("log.level", "Logging level (1-5)");
		props.put("jdbc.url", "Database URL");
		props.put("jdbc.user", "Database username");
		props.put("jdbc.pass", "Database password");
		props.put("binkp.server", "Accept connections");
		props.put("binkp.bind ", "Address for receiving");
		props.put("binkp.port", "Port for receiving");
		props.put("binkp.ipv6.enable", "Enable IPv6 support");
		props.put("binkp.bind6", "IPv6 address for receiving");
		props.put("binkp.inbound", "Path to incoming mail");
		props.put("binkp.client", "Call nodes by timer");
		props.put("poll.period", "Call period, s.");
		props.put("poll.delay", "First call delay, s.");
		props.put("fileecho.enable", "Enable file echoes");
		props.put("fileecho.path", "Path to file echoes folder");
		props.put("fileecho.files_bbs.enable", "Generate FILES.BBS in file echo directories");
		props.put("fileecho.file_id_diz.enable", "Generate FILE_ID.DIZ in file echo directories");
		props.put("fileecho.8bit_output.enable", "Enable 8-bit output for FILES.BBS and FILE_ID.DIZ");
		props.put("fileecho.output_charset", "Charset for 8-bit output (e.g., CP866, CP437)");
		props.put("stat.enable", "Enable statistics");
		props.put("stat.area", "Echo for statistics");
		props.put("jscript.enable", "Enable scripts");
		props.put("threadpool.queue_size", "Thread pool queue size");
		props.put("tosser.loop_prevention.echomail", "Enable echomail loop prevention");
		props.put("tosser.loop_prevention.netmail", "Enable netmail loop prevention");
		return props;
	}

	private Properties fillDefaultConfig() {
		Properties props = new Properties();
		props.setProperty("info.stationname", "Sample Node");
		props.setProperty("info.location", "City, Country");
		props.setProperty("info.sysop", "Bill Joe");
		props.setProperty("info.ndl", "115200,TCP,BINKP");
		props.setProperty("info.address", "2:9999/9999");
		props.setProperty("log.level", "4");
		props.setProperty("jdbc.url", "jdbc:h2:tcp:jn");
		props.setProperty("jdbc.user", "jnode");
		props.setProperty("jdbc.pass", "jnode");
		props.setProperty("binkp.server", "true");
		props.setProperty("binkp.bind ", "0.0.0.0");
		props.setProperty("binkp.port", "24554");
		props.setProperty("binkp.inbound", "inbound");
		props.setProperty("binkp.client", "true");
		props.setProperty("poll.period", "600");
		props.setProperty("poll.delay", "600");
		props.setProperty("fileecho.enable", "true");
		props.setProperty("fileecho.path", "files");
		props.setProperty("fileecho.files_bbs.enable", "true");
		props.setProperty("fileecho.file_id_diz.enable", "true");
		props.setProperty("fileecho.8bit_output.enable", "false");
		props.setProperty("fileecho.output_charset", "CP866");
		props.setProperty("stat.enable", "true");
		props.setProperty("stat.area", "9999.stat");
		props.setProperty("jscript.enable", "true");
		props.setProperty("tosser.loop_prevention.echomail", "true");
		props.setProperty("tosser.loop_prevention.netmail", "true");
		return props;
	}

	/**
	 * Create the application.
	 */
	public GUIConfigurator(String configFile) {
		this.configFile = configFile;
		if (MainHandler.getCurrentInstance() == null) {
			try {
				new MainHandler(configFile);
			} catch (Exception e) {
				e.printStackTrace();
				new MainHandler(fillDefaultConfig());
			}
		}
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		Toolkit tk = Toolkit.getDefaultToolkit();
		frmJnodeConfigurator = new JFrame();
		frmJnodeConfigurator.setTitle("jNode configurator");
		frmJnodeConfigurator.setBounds(0, 0, tk.getScreenSize().width,
				tk.getScreenSize().height);
		frmJnodeConfigurator.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmJnodeConfigurator.getContentPane().setLayout(null);

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setBounds(0, 0, frmJnodeConfigurator.getWidth(),
				frmJnodeConfigurator.getHeight());
		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		tabbedPane.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent changeEvent) {
				JTabbedPane sourceTabbedPane = (JTabbedPane) changeEvent
						.getSource();
				int index = sourceTabbedPane.getSelectedIndex();
				switch (index) {
				case 0:
					updateConfig();
					break;
				case 1:
					updateLinks();
				default:
					break;
				}
			}
		});

		frmJnodeConfigurator.getContentPane().add(tabbedPane);

		JPanel configPanel = new JPanel();
		configPanel.setLayout(new GridLayout(30, 3, 10, 2));
		configMap = new HashMap<>();
		for (String key : config) {
			configPanel.add(new JLabel(configNames.get(key)));
			Component comp;
			Component comment;
			switch (key) {
			case "binkp.server":
			case "binkp.client":
			case "fileecho.enable":
			case "fileecho.files_bbs.enable":
			case "fileecho.file_id_diz.enable":
			case "fileecho.8bit_output.enable":
			case "stat.enable":
			case "jscript.enable":
			case "tosser.loop_prevention.echomail":
			case "tosser.loop_prevention.netmail":
				comp = new Checkbox();
				break;
			default:
				comp = new TextField();
				break;
			}
			if (key.equals("fileecho.path") || key.equals("binkp.inbound")) {
				comment = new FileChooserButton((TextField) comp);
			} else {
				comment = new JLabel("Comment");
			}
			configPanel.add(comp);
			configPanel.add(comment);
			configMap.put(key.toString(), comp);
		}
		Button saveButton = new Button("Save");
		saveButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				Properties props = new Properties();
				for (String key : config) {

					Component comp = configMap.get(key);
					String value = null;
					if (comp instanceof TextField) {
						value = ((TextField) comp).getText();
					} else if (comp instanceof Checkbox) {
						value = (((Checkbox) comp).getState()) ? "true"
								: "false";
					}
					if (value != null) {
						props.setProperty(key, value);
					}
				}
				try {
					OutputStream os = new FileOutputStream(configFile);
					props.store(os, "GUIConfigurator process");
					os.close();
					new MainHandler(configFile);
				} catch (IOException ig) {

				}
			}
		});
		Button defButton = new Button("Default");
		defButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				new MainHandler(fillDefaultConfig());
			}
		});

		configPanel.add(saveButton);
		configPanel.add(defButton);

		tabbedPane.addTab("Configuration", configPanel);

		linksPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
		tabbedPane.addTab("Links", linksPanel);
	}

	private void updateLinks() {
		try {
			ORMManager.INSTANCE.start();
			List<Link> links = ORMManager.get(Link.class).getAll();
			linksPanel.removeAll();
			for (final Link l : links) {
				JLabel label = new JLabel(l.getLinkName() + " "
						+ l.getLinkAddress() + " @ " + l.getProtocolHost()
						+ ":" + l.getProtocolPort());
				label.setBorder(BorderFactory.createLineBorder(Color.BLACK));
				label.addMouseListener(new MouseListener() {

					@Override
					public void mouseReleased(MouseEvent e) {
						// TODO Auto-generated method stub

					}

					@Override
					public void mousePressed(MouseEvent e) {
						// TODO Auto-generated method stub

					}

					@Override
					public void mouseExited(MouseEvent e) {
						// TODO Auto-generated method stub

					}

					@Override
					public void mouseEntered(MouseEvent e) {
						// TODO Auto-generated method stub

					}

					@Override
					public void mouseClicked(MouseEvent e) {
						new LinkDialog(frmJnodeConfigurator, l);

					}
				});
				linksPanel.add(label);
			}
			Button button = new Button("New link");
			button.addActionListener(new ActionListener() {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					new LinkDialog(frmJnodeConfigurator, new Link());
					
				}
			});
			linksPanel.add(button);
		} catch (Exception e) {

		}
	}

	protected void updateConfig() {
		for (String key : config) {
			Component comp = configMap.get(key);
			if (comp instanceof TextField) {
				String value = MainHandler.getCurrentInstance().getProperty(
						key, "");
				((TextField) comp).setText(value);
			} else if (comp instanceof Checkbox) {
				boolean value = MainHandler.getCurrentInstance()
						.getBooleanProperty(key, true);
				((Checkbox) comp).setState(value);
			}
		}

	}

	class FileChooserButton extends Button {
		private static final long serialVersionUID = 1L;

		public FileChooserButton(final TextField path) throws HeadlessException {
			super();
			setLabel("Browse");
			addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					JFileChooser chooser = new JFileChooser();
					chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					int rval = chooser.showOpenDialog(frmJnodeConfigurator);
					if (rval == JFileChooser.APPROVE_OPTION) {
						String txt = chooser.getSelectedFile()
								.getAbsolutePath();
						path.setText(txt);
					}
				}
			});

		}
	}

	class LinkDialog extends JDialog {
		private static final long serialVersionUID = 1L;
		private Link link;
		private TextField linkName;
		private JTextField linkAddress;
		private TextField linkHost;
		private TextField linkPort;
		private TextField linkPassword;
		private TextField linkPktPassword;

		public LinkDialog(Frame owner, Link link) {
			super(owner);
			this.link = link;
			initialize();
			publish();
			setVisible(true);
		}

		public LinkDialog(Window owner, Link link) {
			super(owner);
			this.link = link;
			initialize();
			publish();
			setVisible(true);
		}

		private void publish() {
			if (link != null) {
				if (link.getLinkName() != null) {
					linkName.setText(link.getLinkName());
				}
				if (link.getLinkAddress() != null) {
					linkAddress.setText(link.getLinkAddress());
				}
				if (link.getProtocolHost() != null) {
					linkHost.setText(link.getProtocolHost());
				}
				if (link.getProtocolPort() != null) {
					linkPort.setText(link.getProtocolPort().toString());
				}
				if (link.getProtocolPassword() != null) {
					linkPassword.setText(link.getProtocolPassword());
				}
				if (link.getPaketPassword() != null) {
					linkPktPassword.setText(link.getPaketPassword());
				}
			}

		}

		private void initialize() {
			setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			setModal(true);
			setBounds(30, 40, 400, 300);
			setTitle("Link management");
			setLayout(null);
			Insets insets = getInsets();
			JLabel l = new JLabel("Link name");
			l.setBounds(insets.left+10, insets.top+10, 180, 20);
			add(l);
			linkName = new TextField(10);
			linkName.setBounds(insets.left+190, insets.top+10, 190, 20);
			add(linkName);
			l = new JLabel("Link address");
			l.setBounds(insets.left+10, insets.top+40, 180, 20);
			add(l);
			linkAddress = new JTextField(10);
			linkAddress.setBounds(insets.left+190, insets.top+40, 190, 20);
			linkAddress.setInputVerifier(new InputVerifier() {

				@Override
				public boolean verify(JComponent input) {
					if (input instanceof JTextField) {
						String text = ((JTextField) input).getText();
						try {
							new FtnAddress(text);
							return true;
						} catch (NumberFormatException e) {
						}
					}
					return false;
				}
			});
			add(linkAddress);
			l = new JLabel("Link host");
			l.setBounds(insets.left+10, insets.top+80, 180, 20);
			add(l);
			linkHost = new TextField(10);
			linkHost.setBounds(insets.left+190, insets.top+80, 190, 20);
			add(linkHost);
			
			l = new JLabel("Link port");
			l.setBounds(insets.left+10, insets.top+120, 180, 20);
			add(l);
			linkPort = new TextField(10);
			linkPort.setBounds(insets.left+190, insets.top+120, 190, 20);
			add(linkPort);
			
			l = new JLabel("Connection password");
			l.setBounds(insets.left+10, insets.top+160, 180, 20);
			add(l);
			linkPassword = new TextField(10);
			linkPassword.setBounds(insets.left+190, insets.top+160, 190, 20);
			add(linkPassword);
			
			l = new JLabel("Packet password");
			l.setBounds(insets.left+10, insets.top+200, 180, 20);
			add(l);
			
			linkPktPassword = new TextField(10);
			linkPktPassword.setBounds(insets.left+190, insets.top+200, 190, 20);
			add(linkPktPassword);

			Button save = new Button("Save");
			save.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					link.setLinkAddress(linkAddress.getText());
					link.setLinkName(linkName.getText());
					link.setProtocolHost(linkHost.getText());
					link.setPaketPassword(linkPktPassword.getText());
					link.setProtocolPort(Integer.valueOf(linkPort.getText()));
					link.setProtocolPassword(linkPassword.getText());
					ORMManager.get(Link.class).saveOrUpdate(link);
					updateLinks();
					LinkDialog.this.dispose();
				}
			});
			
			Button close = new Button("Cancel");
			close.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					LinkDialog.this.dispose();

				}
			});
			save.setBounds(insets.left+100, insets.top+240, 90, 30);
			close.setBounds(insets.left+300, insets.top+240, 90, 30);
			add(save);
			add(close);
		}

	}
}
