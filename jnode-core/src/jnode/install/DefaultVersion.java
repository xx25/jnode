/*
 * Licensed to the jNode FTN Platform Develpoment Team (jNode Team)
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import jnode.dto.Version;
import jnode.logger.Logger;

public class DefaultVersion extends Version {
	private static final Logger logger = Logger.getLogger(DefaultVersion.class);
	private static DefaultVersion self;
	private static final String VERSION_PROPERTIES = "/version.properties";
	private static final String DEFAULT_VERSION = "2.0.0";

	public static Version getSelf() {
		if (self == null) {
			synchronized (DefaultVersion.class) {
				self = new DefaultVersion();
			}
		}
		return self;
	}

	private DefaultVersion() {
		String versionString = DEFAULT_VERSION;
		
		// Try to load version from properties file
		try (InputStream is = DefaultVersion.class.getResourceAsStream(VERSION_PROPERTIES)) {
			if (is != null) {
				Properties props = new Properties();
				props.load(is);
				versionString = props.getProperty("version", DEFAULT_VERSION);
			} else {
				logger.l3("Version properties file not found, using default version: " + DEFAULT_VERSION);
			}
		} catch (IOException e) {
			logger.l3("Error loading version properties, using default version: " + DEFAULT_VERSION, e);
		}
		
		// Parse version string (handle formats like "2.0.0-SNAPSHOT" or "2.0")
		String[] parts = versionString.split("[.-]");
		long major = 2L;
		long minor = 0L;
		
		try {
			if (parts.length >= 1) {
				major = Long.parseLong(parts[0]);
			}
			if (parts.length >= 2) {
				minor = Long.parseLong(parts[1]);
			}
		} catch (NumberFormatException e) {
			logger.l3("Error parsing version string: " + versionString + ", using defaults", e);
		}
		
		setMajorVersion(major);
		setMinorVersion(minor);
		setInstalledAt(new Date());
		
		logger.l4("Initialized version: " + toString());
	}

	@Override
	public String toString() {
		return String.format("%d.%d", getMajorVersion(), getMinorVersion());
	}

}