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

package jnode.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import jnode.ftn.types.FtnAddress;
import jnode.ndl.FtnNdlAddress;
import jnode.ndl.NodelistScanner;

/**
 * 
 * @author kreon
 * 
 */
@DatabaseTable(tableName = "links")
public class Link {
	@DatabaseField(columnName = "id", generatedId = true)
	private Long id;
	@DatabaseField(columnName = "station_name", canBeNull = false)
	private String linkName;
	@DatabaseField(columnName = "ftn_address", uniqueIndex = true, canBeNull = false)
	private String linkAddress;
	@DatabaseField(columnName = "pkt_password", defaultValue = "", canBeNull = false)
	private String paketPassword;
	@DatabaseField(columnName = "password", defaultValue = "-", canBeNull = false)
	private String protocolPassword;
	@DatabaseField(columnName = "address", defaultValue = "-", canBeNull = false)
	private String protocolAddress;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getLinkName() {
		return linkName;
	}

	public void setLinkName(String linkName) {
		this.linkName = linkName;
	}

	public String getLinkAddress() {
		return linkAddress;
	}

	public void setLinkAddress(String linkAddress) {
		this.linkAddress = linkAddress;
	}

	public String getPaketPassword() {
		return paketPassword;
	}

	public void setPaketPassword(String paketPassword) {
		this.paketPassword = paketPassword;
	}

	public String getProtocolPassword() {
		return protocolPassword;
	}

	public void setProtocolPassword(String protocolPassword) {
		this.protocolPassword = protocolPassword;
	}

	public String getProtocolHost() {
		if (protocolAddress.equals("-")) {
			return "-";
		} else {
			String[] parts = protocolAddress.split(":");
			return parts[0];
		}
	}

	public void setProtocolHost(String protocolHost) {
		protocolAddress = protocolHost;
	}

	public Integer getProtocolPort() {
		if ("-".equals(protocolAddress)) {
			return 0;
		} else {
			String[] parts = protocolAddress.split(":");
			if (parts.length == 1) {
				return 24554; // TODO fix this ?
			}
			return Integer.valueOf(parts[parts.length - 1]);
		}
	}

	public void setProtocolPort(Integer protocolPort) {
		if (!"-".endsWith(protocolAddress)) {
			protocolAddress += ":" + protocolPort;
			if (protocolPort != 24554) {// TODO fix this ?
				protocolAddress = getLinkAddress() + ":" + protocolPort;
			}
		}
	}

	public String getProtocolAddress() {
		return protocolAddress;
	}

	public void setProtocolAddress(String protocolAddress) {
		this.protocolAddress = protocolAddress;
	}

	/**
	 * Gets the protocol address for this link, with nodelist fallback.
	 * 
	 * Logic:
	 * 1. If protocolAddress is "-", return "-" (no connection)
	 * 2. If protocolAddress is not empty and not "-", return it as-is
	 * 3. If protocolAddress is empty, try to resolve from nodelist:
	 *    a. Check IBN flag first - if it has host/IP, use it
	 *    b. If IBN has no host, check INA flag
	 *    c. If no valid address found, return "-"
	 * 
	 * @return Resolved protocol address or "-" if no valid address found
	 */
	@JsonIgnore
	public String getResolvedProtocolAddress() {
		// If explicitly set to "-", don't connect
		if ("-".equals(protocolAddress)) {
			return "-";
		}
		
		// If we have a configured address, use it
		if (protocolAddress != null && !protocolAddress.isEmpty() && !"-".equals(protocolAddress)) {
			return protocolAddress;
		}
		
		// Try to resolve from nodelist
		try {
			FtnAddress ftnAddr = new FtnAddress(linkAddress);
			FtnNdlAddress ndlAddr = NodelistScanner.getInstance().isExists(ftnAddr);
			
			if (ndlAddr == null) {
				// Not in nodelist
				return "-";
			}
			
			// Get all addresses in priority order (IPv6 first if enabled)
			List<String> allAddresses = getAllResolvedProtocolAddresses();
			if (!allAddresses.isEmpty()) {
				// Return the first (highest priority) address
				return allAddresses.get(0);
			}
			
			// No valid address found in nodelist
			return "-";
			
		} catch (Exception e) {
			// If any error occurs during resolution, don't connect
			return "-";
		}
	}

	/**
	 * Gets all available protocol addresses for this link, with nodelist fallback.
	 * This method supports multiple IBN and INA entries for load balancing and failover.
	 * 
	 * Logic:
	 * 1. If protocolAddress is "-", return empty list (no connection)
	 * 2. If protocolAddress is configured, return it as single-item list
	 * 3. If protocolAddress is empty, try to resolve from nodelist:
	 *    a. Add all IBN addresses from nodelist to the list
	 *    b. Add all INA addresses from nodelist to the list
	 *    c. Return all valid addresses found
	 * 
	 * @return List of all available protocol addresses (empty if no valid addresses found)
	 */
	@JsonIgnore
	public List<String> getAllResolvedProtocolAddresses() {
		List<String> addresses = new ArrayList<>();
		
		// If explicitly set to "-", don't connect
		if ("-".equals(protocolAddress)) {
			return addresses;
		}
		
		// If we have a configured address, use it
		if (protocolAddress != null && !protocolAddress.isEmpty() && !"-".equals(protocolAddress)) {
			addresses.add(protocolAddress);
			return addresses;
		}
		
		// Try to resolve from nodelist
		try {
			FtnAddress ftnAddr = new FtnAddress(linkAddress);
			FtnNdlAddress ndlAddr = NodelistScanner.getInstance().isExists(ftnAddr);
			
			if (ndlAddr == null) {
				// Not in nodelist
				return addresses;
			}
			
			// Get all IBN addresses first (they have priority), sorted by IPv6 priority if enabled
			List<String> ibnHosts = ndlAddr.getIbnHostsByPriority();
			for (String ibnHost : ibnHosts) {
				if (ibnHost != null && !ibnHost.isEmpty()) {
					// Check if address already includes port (IPv6 bracket notation or standard port)
					if (ibnHost.contains(":") && !ibnHost.startsWith("[")) {
						addresses.add(ibnHost);
					} else if (ibnHost.startsWith("[") && ibnHost.contains("]:")) {
						// IPv6 address with port in bracket notation
						addresses.add(ibnHost);
					} else {
						// Add default port if not specified
						if (ibnHost.startsWith("[") && ibnHost.endsWith("]")) {
							// IPv6 address without port - add port after brackets
							addresses.add(ibnHost + ":24554");
						} else {
							// IPv4 or hostname without port
							addresses.add(ibnHost + ":24554");
						}
					}
				}
			}
			
			// Get all INA addresses sorted by IPv6 priority if enabled
			List<String> inaHosts = ndlAddr.getInetHostsByPriority();
			for (String inaHost : inaHosts) {
				if (inaHost != null && !inaHost.isEmpty() && !"-".equals(inaHost)) {
					// Check if address already includes port (IPv6 bracket notation or standard port)
					if (inaHost.contains(":") && !inaHost.startsWith("[")) {
						addresses.add(inaHost);
					} else if (inaHost.startsWith("[") && inaHost.contains("]:")) {
						// IPv6 address with port in bracket notation
						addresses.add(inaHost);
					} else {
						// Add default port if not specified
						if (inaHost.startsWith("[") && inaHost.endsWith("]")) {
							// IPv6 address without port - add port after brackets
							addresses.add(inaHost + ":24554");
						} else {
							// IPv4 or hostname without port
							addresses.add(inaHost + ":24554");
						}
					}
				}
			}
			
		} catch (Exception e) {
			// If any error occurs during resolution, return empty list
		}
		
		return addresses;
	}

    @Override
    public String toString() {
        return "Link [id=" + id + ", linkName=" + linkName + ", " +
                "linkAddress=" + linkAddress
                + ", protocolAddress=" + protocolAddress + "]";
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result
				+ ((linkAddress == null) ? 0 : linkAddress.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Link other = (Link) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

}
