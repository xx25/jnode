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

package jnode.report;

import jnode.ftn.types.FtnAddress;
import jnode.logger.Logger;
import jnode.store.XMLSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Manjago (kirill@temnenkov.com)
 */
public class ConnectionStatData {
    private static final Logger logger = Logger.getLogger(ConnectionStatData.class);
    private final String statPath;

    public ConnectionStatData(String statPath) {
        this.statPath = statPath;
    }

    public List<ConnectionStatDataElement> load() {
        synchronized (ConnectionStatData.class) {
            return internalLoad();
        }
    }

    public List<ConnectionStatDataElement> loadAndDrop() {
        synchronized (ConnectionStatData.class) {
            List<ConnectionStatDataElement> result = internalLoad();
            
            try {
                XMLSerializer.write(new ArrayList<ConnectionStatDataElement>(), statPath);
                logger.l4("Cleared statistics file: " + statPath);
            } catch (FileNotFoundException e) {
                logger.l1(MessageFormat.format(" Failed to clear data, file {0} not found", statPath), e);
            } catch (Exception e) {
                logger.l1("Unexpected exception while clearing statistics file: " + statPath, e);
            }
            return result;
        }
    }

    public void store(FtnAddress ftnAddress, ConnectionStatDataElement element) {
        synchronized (ConnectionStatData.class) {
            List<ConnectionStatDataElement> elements = internalLoad();
            int pos = findPos(ftnAddress, elements);
            
            if (element == null) {
                logger.l1("Attempting to store null element for address: " + 
                         (ftnAddress != null ? ftnAddress.toString() : "null"));
                return;
            }
            
            element.linkStr = (ftnAddress != null) ? ftnAddress.toString() : null;
            
            if (pos == -1) {
                elements.add(element);
            } else {
                elements.set(pos, element);
            }
            
            try {
                XMLSerializer.write(elements, statPath);
                logger.l4("Updated statistics file: " + statPath);
            } catch (FileNotFoundException e) {
                logger.l1(MessageFormat.format(" Failed to store data, file {0} not found", statPath), e);
            } catch (Exception e) {
                logger.l1("Unexpected exception while storing statistics to file: " + statPath, e);
            }
        }
    }

    public int findPos(FtnAddress ftnAddress, List<ConnectionStatDataElement> elements) {
        String searchAddress = (ftnAddress != null) ? ftnAddress.toString() : null;
        
        int pos = -1;
        for (int i = 0; i < elements.size(); ++i) {
            ConnectionStatDataElement element = elements.get(i);
            
            if (element == null) {
                logger.l2("Found null element at position " + i + " while searching");
                continue;
            }
            
            if (ftnAddress == null) {
                if (element.linkStr == null) {
                    pos = i;
                    break;
                }
            } else if (element.linkStr != null) {
                if (element.linkStr.equals(searchAddress)) {
                    pos = i;
                    break;
                }
            }
        }
        
        return pos;
    }

    @SuppressWarnings("unchecked")
	private List<ConnectionStatDataElement> internalLoad() {
        File statFile = new File(statPath);
        if (!statFile.exists()) {
            return new ArrayList<ConnectionStatDataElement>();
        }
        
        if (!statFile.canRead()) {
            logger.l1("Cannot read statistics file: " + statPath + ", returning empty list");
            return new ArrayList<ConnectionStatDataElement>();
        }
        
        List<ConnectionStatDataElement> result;
        try {
            Object loaded = XMLSerializer.read(statPath);
            if (loaded == null) {
                logger.l2("XMLSerializer returned null for file: " + statPath);
                return new ArrayList<ConnectionStatDataElement>();
            }
            
            if (!(loaded instanceof List)) {
                logger.l1("Loaded object is not a List but " + loaded.getClass().getName() + 
                         " for file: " + statPath);
                return new ArrayList<ConnectionStatDataElement>();
            }
            
            result = (List<ConnectionStatDataElement>) loaded;
            logger.l4("Loaded " + result.size() + " elements from file: " + statPath);
            
            // Validate loaded elements
            for (int i = 0; i < result.size(); i++) {
                ConnectionStatDataElement element = result.get(i);
                if (element == null) {
                    logger.l2("Found null element at index " + i + " in file: " + statPath);
                }
            }
            
        } catch (FileNotFoundException e) {
            logger.l2("Statistics file not found: " + statPath, e);
            return new ArrayList<ConnectionStatDataElement>();
        } catch (ClassCastException e) {
            logger.l1("Failed to cast loaded object to List<ConnectionStatDataElement> from file: " + 
                     statPath, e);
            return new ArrayList<ConnectionStatDataElement>();
        } catch (RuntimeException e) {
            logger.l1("Runtime exception while loading statistics from file: " + statPath + 
                     ". File may be corrupted.", e);
            // Try to backup the corrupted file
            try {
                File backupFile = new File(statPath + ".corrupted." + System.currentTimeMillis());
                if (statFile.renameTo(backupFile)) {
                    logger.l2("Backed up corrupted file to: " + backupFile.getAbsolutePath());
                }
            } catch (Exception backupEx) {
                logger.l2("Failed to backup corrupted file", backupEx);
            }
            return new ArrayList<ConnectionStatDataElement>();
        } catch (Exception e) {
            logger.l1("Unexpected exception while loading statistics from file: " + statPath, e);
            return new ArrayList<ConnectionStatDataElement>();
        }
        return result;
    }

    public static class ConnectionStatDataElement {
        public String linkStr;
        public int bytesReceived;
        public int bytesSended;
        public int incomingOk;
        public int incomingFailed;
        public int outgoingOk;
        public int outgoingFailed;
        
        @Override
        public String toString() {
            return "ConnectionStatDataElement{" +
                    "linkStr='" + linkStr + '\'' +
                    ", bytesReceived=" + bytesReceived +
                    ", bytesSended=" + bytesSended +
                    ", incomingOk=" + incomingOk +
                    ", incomingFailed=" + incomingFailed +
                    ", outgoingOk=" + outgoingOk +
                    ", outgoingFailed=" + outgoingFailed +
                    '}';
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ConnectionStatData{");
        sb.append("statPath='").append(statPath).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
