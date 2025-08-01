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

package jnode.ndl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jnode.ftn.types.FtnAddress;
import jnode.main.MainHandler;

public class FtnNdlAddress extends FtnAddress {
	private static final Pattern IBN_PATTERN = Pattern.compile(
			".*,IBN(:[^,]+)?.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern INA_PATTERN = Pattern.compile(
			".*,INA(:[^,]+)?.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern INA_GLOBAL_PATTERN = Pattern.compile(
			"INA(:[^,]+)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern IBN_GLOBAL_PATTERN = Pattern.compile(
			"IBN(:[^,]+)?", Pattern.CASE_INSENSITIVE);
	
	// IPv6 address detection pattern - matches addresses in square brackets
	private static final Pattern IPV6_PATTERN = Pattern.compile(
			"\\[([a-fA-F0-9:]+)\\].*");
	
	// IPv4 address detection pattern
	private static final Pattern IPV4_PATTERN = Pattern.compile(
			"(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(?::(\\d+))?");
	
	// Hostname pattern - anything that's not an IP address
	private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
			"([a-zA-Z0-9.-]+)(?::(\\d+))?");

	public static enum Status {
		HOLD, DOWN, HUB, HOST, PVT, NORMAL
	}

	private static final long serialVersionUID = 1L;
	private Status status;

	private String line;

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getLine() {
		return line;
	}

	public void setLine(String line) {
		this.line = line;
	}

	public FtnNdlAddress(String addr, Status status) {
		super(addr);
		this.status = status;
	}

	public FtnNdlAddress(String addr) {
		super(addr);
		this.status = Status.NORMAL;
	}

	public FtnNdlAddress(FtnAddress address) {
		this.zone = address.getZone();
		this.net = address.getNet();
		this.node = address.getNode();
		this.point = address.getPoint();
		this.status = Status.NORMAL;
	}

	public FtnNdlAddress(Status status) {
		super();
		this.status = status;
	}

	public FtnNdlAddress() {
		super();
	}

	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (object == null) {
			return false;
		}
		if (!(object instanceof FtnAddress)) {
			return false;
		}
		return object.equals(this);
	}

	@Override
	public String toString() {
		return "FtnNdlAddress [" + super.toString() + ", status=" + status
				+ "]";
	}

	public int getBinkpPort() {
		if (line != null) {
			Matcher m = IBN_PATTERN.matcher(line);
			if (m.matches()) {
				if (m.group(1) != null) {
					// Remove the leading colon
					String ibnValue = m.group(1).substring(1);
					String[] parts = ibnValue.split(":");
					
					// Check last part for port number
					if (parts.length > 1) {
						try {
							return Integer.parseInt(parts[parts.length - 1]);
						} catch (NumberFormatException e) {
							return 24554; // Default port if parsing fails
						}
					} else if (parts.length == 1) {
						// Single value - check if it's a port number
						try {
							int port = Integer.parseInt(parts[0]);
							// If it parses as a number and is in valid port range, it's a port
							if (port > 0 && port <= 65535) {
								return port;
							}
						} catch (NumberFormatException e) {
							// It's a hostname, not a port
						}
					}
				}
				// IBN flag exists but no port specified
				return 24554;
			}
		}
		return -1; // No IBN flag found
	}

	public String getInetHost() {
		if (line != null) {
			Matcher m = INA_PATTERN.matcher(line);
			if (m.matches()) {
				if (m.group(1) != null) {
					return m.group(1).substring(1);
				} else {
					return "-";
				}
			}
		}
		return null;
	}

	/**
	 * Extracts all INA addresses from the nodelist line.
	 * Examples:
	 * - "INA:server1.com,INA:server2.com" -> ["server1.com", "server2.com"]
	 * - "INA:1.2.3.4,INA:5.6.7.8:25555" -> ["1.2.3.4", "5.6.7.8:25555"]
	 * - "INA" -> ["-"] (flag present but no address)
	 * - No INA flags -> empty list
	 * 
	 * @return List of INA addresses found in the nodelist line
	 */
	public List<String> getInetHosts() {
		List<String> hosts = new ArrayList<>();
		if (line != null) {
			Matcher m = INA_GLOBAL_PATTERN.matcher(line);
			while (m.find()) {
				if (m.group(1) != null) {
					// Extract address after the colon
					String address = m.group(1).substring(1);
					if (!address.isEmpty()) {
						hosts.add(address);
					}
				} else {
					// INA flag without address
					hosts.add("-");
				}
			}
		}
		return hosts;
	}

	/**
	 * Extracts the host address from IBN flag.
	 * Examples:
	 * - IBN:domain.name -> "domain.name"
	 * - IBN:1.2.3.4 -> "1.2.3.4"
	 * - IBN:domain.name:24555 -> "domain.name"
	 * - IBN -> null (no host specified)
	 * 
	 * @return The host address from IBN flag, or null if not present
	 */
	public String getIbnHost() {
		if (line != null) {
			Matcher m = IBN_PATTERN.matcher(line);
			if (m.matches()) {
				if (m.group(1) != null) {
					// Remove the leading colon
					String ibnValue = m.group(1).substring(1);
					// Split by colon to handle cases like "domain.name:24555"
					String[] parts = ibnValue.split(":");
					if (parts.length > 0 && !parts[0].isEmpty()) {
						// Check if it's a numeric port (no host specified)
						try {
							Integer.parseInt(parts[0]);
							// It's just a port number, no host
							return null;
						} catch (NumberFormatException e) {
							// It's a host address
							return parts[0];
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Extracts all IBN addresses from the nodelist line.
	 * Examples:
	 * - "IBN:server1.com:24554,IBN:server2.com" -> ["server1.com:24554", "server2.com"]
	 * - "IBN:1.2.3.4,IBN:5.6.7.8:25555" -> ["1.2.3.4", "5.6.7.8:25555"]
	 * - "IBN:24554" -> [] (port only, no host)
	 * - "IBN" -> [] (flag present but no address)
	 * - No IBN flags -> empty list
	 * 
	 * @return List of IBN addresses found in the nodelist line
	 */
	public List<String> getIbnHosts() {
		List<String> hosts = new ArrayList<>();
		if (line != null) {
			Matcher m = IBN_GLOBAL_PATTERN.matcher(line);
			while (m.find()) {
				if (m.group(1) != null) {
					// Extract address after the colon
					String ibnValue = m.group(1).substring(1);
					if (!ibnValue.isEmpty()) {
						// Split by colon to handle cases like "domain.name:24555"
						String[] parts = ibnValue.split(":");
						if (parts.length > 0 && !parts[0].isEmpty()) {
							// Check if it's a numeric port (no host specified)
							try {
								Integer.parseInt(parts[0]);
								// It's just a port number, no host - skip this entry
								continue;
							} catch (NumberFormatException e) {
								// It's a host address, add the full value
								hosts.add(ibnValue);
							}
						}
					}
				}
			}
		}
		return hosts;
	}
	
	/**
	 * Checks if IPv6 is enabled in the BinkP configuration.
	 * @return true if IPv6 is enabled, false otherwise
	 */
	public boolean isIPv6Enabled() {
		return MainHandler.getCurrentInstance().getBooleanProperty("binkp.ipv6.enable", false);
	}
	
	/**
	 * Categorizes an address as IPv6, IPv4, or hostname.
	 * @param address The address to categorize
	 * @return "ipv6", "ipv4", or "hostname"
	 */
	public String getAddressType(String address) {
		if (address == null || address.isEmpty()) {
			return "hostname";
		}
		
		// Check for IPv6 address in square brackets
		if (IPV6_PATTERN.matcher(address).matches()) {
			return "ipv6";
		}
		
		// Check for IPv4 address
		if (IPV4_PATTERN.matcher(address).matches()) {
			return "ipv4";
		}
		
		// Everything else is treated as hostname
		return "hostname";
	}
	
	/**
	 * Gets all IBN addresses sorted by priority when IPv6 is enabled.
	 * When IPv6 is enabled: Priority order is IPv6 > IPv4 > hostname
	 * When IPv6 is disabled: IPv6 addresses are filtered out entirely
	 * @return List of IBN addresses sorted by priority (or filtered)
	 */
	public List<String> getIbnHostsByPriority() {
		List<String> hosts = getIbnHosts();
		if (!isIPv6Enabled()) {
			// Filter out IPv6 addresses when IPv6 is disabled
			List<String> filteredHosts = new ArrayList<>();
			for (String host : hosts) {
				if (!getAddressType(host).equals("ipv6")) {
					filteredHosts.add(host);
				}
			}
			return filteredHosts;
		}
		
		// Sort by priority: IPv6 first, then IPv4, then hostname
		List<String> ipv6Hosts = new ArrayList<>();
		List<String> ipv4Hosts = new ArrayList<>();
		List<String> hostnameHosts = new ArrayList<>();
		
		for (String host : hosts) {
			String type = getAddressType(host);
			switch (type) {
				case "ipv6":
					ipv6Hosts.add(host);
					break;
				case "ipv4":
					ipv4Hosts.add(host);
					break;
				default:
					hostnameHosts.add(host);
					break;
			}
		}
		
		// Combine lists with IPv6 first
		List<String> sortedHosts = new ArrayList<>();
		sortedHosts.addAll(ipv6Hosts);
		sortedHosts.addAll(ipv4Hosts);
		sortedHosts.addAll(hostnameHosts);
		
		return sortedHosts;
	}
	
	/**
	 * Gets all INA addresses sorted by priority when IPv6 is enabled.
	 * When IPv6 is enabled: Priority order is IPv6 > IPv4 > hostname
	 * When IPv6 is disabled: IPv6 addresses are filtered out entirely
	 * @return List of INA addresses sorted by priority (or filtered)
	 */
	public List<String> getInetHostsByPriority() {
		List<String> hosts = getInetHosts();
		if (!isIPv6Enabled()) {
			// Filter out IPv6 addresses when IPv6 is disabled
			List<String> filteredHosts = new ArrayList<>();
			for (String host : hosts) {
				if (!"-".equals(host) && !getAddressType(host).equals("ipv6")) {
					filteredHosts.add(host);
				}
			}
			return filteredHosts;
		}
		
		// Sort by priority: IPv6 first, then IPv4, then hostname
		List<String> ipv6Hosts = new ArrayList<>();
		List<String> ipv4Hosts = new ArrayList<>();
		List<String> hostnameHosts = new ArrayList<>();
		
		for (String host : hosts) {
			if ("-".equals(host)) {
				continue; // Skip invalid entries
			}
			
			String type = getAddressType(host);
			switch (type) {
				case "ipv6":
					ipv6Hosts.add(host);
					break;
				case "ipv4":
					ipv4Hosts.add(host);
					break;
				default:
					hostnameHosts.add(host);
					break;
			}
		}
		
		// Combine lists with IPv6 first
		List<String> sortedHosts = new ArrayList<>();
		sortedHosts.addAll(ipv6Hosts);
		sortedHosts.addAll(ipv4Hosts);
		sortedHosts.addAll(hostnameHosts);
		
		return sortedHosts;
	}
}
