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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jnode.ftn.types.FtnAddress;

public class FtnNdlAddress extends FtnAddress {
	private static final Pattern IBN_PATTERN = Pattern.compile(
			".*,IBN(:[^,]+)?.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern INA_PATTERN = Pattern.compile(
			".*,INA(:[^,]+)?.*", Pattern.CASE_INSENSITIVE);

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
}
