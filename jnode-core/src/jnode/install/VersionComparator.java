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

import jnode.dto.Version;

/**
 * Utility class for comparing versions
 */
public class VersionComparator {
    
    /**
     * Compare two versions
     * @param v1 First version
     * @param v2 Second version
     * @return negative if v1 < v2, 0 if equal, positive if v1 > v2
     */
    public static int compare(Version v1, Version v2) {
        int majorCompare = Long.compare(v1.getMajorVersion(), v2.getMajorVersion());
        if (majorCompare != 0) {
            return majorCompare;
        }
        return Long.compare(v1.getMinorVersion(), v2.getMinorVersion());
    }
    
    /**
     * Check if version is less than the specified major.minor
     */
    public static boolean isLessThan(Version version, long major, long minor) {
        if (version.getMajorVersion() < major) {
            return true;
        }
        if (version.getMajorVersion() == major) {
            return version.getMinorVersion() < minor;
        }
        return false;
    }
    
    /**
     * Check if version equals the specified major.minor
     */
    public static boolean isEqual(Version version, long major, long minor) {
        return version.getMajorVersion() == major && version.getMinorVersion() == minor;
    }
}