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

package jnode.store;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.beans.ExceptionListener;
import java.io.*;
import jnode.logger.Logger;

/**
 * @author Manjago (kirill@temnenkov.com)
 */
public final class XMLSerializer {

    private static final Logger logger = Logger.getLogger(XMLSerializer.class);

    private XMLSerializer() {
    }

    public static void write(Object f, String filename) throws FileNotFoundException {
        logger.l5("XMLSerializer: Writing object of type " + (f != null ? f.getClass().getName() : "null") + " to file: " + filename);
        
        File file = new File(filename);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            logger.l3("XMLSerializer: Parent directory does not exist: " + parentDir.getAbsolutePath() + ", attempting to create it");
            if (!parentDir.mkdirs()) {
                logger.l1("XMLSerializer: Failed to create parent directory: " + parentDir.getAbsolutePath());
                throw new FileNotFoundException("Cannot create parent directory: " + parentDir.getAbsolutePath());
            }
        }
        
        // Use atomic write: write to temp file first, then rename
        String tempFilename = filename + ".tmp." + System.currentTimeMillis();
        File tempFile = new File(tempFilename);
        XMLEncoder encoder = null;
        
        try {
            logger.l5("XMLSerializer: Writing to temporary file: " + tempFilename);
            encoder = new XMLEncoder(
                    new BufferedOutputStream(
                            new FileOutputStream(tempFile)));
            
            // Add exception listener for better error tracking
            encoder.setExceptionListener(new ExceptionListener() {
                @Override
                public void exceptionThrown(Exception e) {
                    logger.l1("XMLSerializer: Exception during XML encoding of file " + filename, e);
                }
            });
            
            encoder.writeObject(f);
            encoder.close();
            encoder = null;
            
            // Verify temp file is not empty
            if (tempFile.length() == 0) {
                logger.l1("XMLSerializer: Temporary file is empty after write: " + tempFilename);
                tempFile.delete();
                throw new IOException("Failed to write data - temporary file is empty");
            }
            
            // Delete existing file if it exists
            if (file.exists()) {
                if (!file.delete()) {
                    logger.l2("XMLSerializer: Could not delete existing file: " + filename);
                }
            }
            
            // Atomic rename temp file to target file
            if (!tempFile.renameTo(file)) {
                logger.l1("XMLSerializer: Failed to rename temp file " + tempFilename + " to " + filename);
                throw new IOException("Failed to rename temporary file to target file");
            }
            
            logger.l5("XMLSerializer: Successfully wrote object to file: " + filename + " (via atomic rename)");
        } catch (IOException e) {
            logger.l1("XMLSerializer: IO error while writing to file: " + filename, e);
            // Clean up temp file if it exists
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw new FileNotFoundException("IO error: " + e.getMessage());
        } catch (Exception e) {
            logger.l1("XMLSerializer: Failed to write object to file: " + filename, e);
            // Clean up temp file if it exists
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw e;
        } finally {
            if (encoder != null) {
                try {
                    encoder.close();
                } catch (Exception e) {
                    logger.l2("XMLSerializer: Error closing encoder", e);
                }
            }
        }
    }

    public static Object read(String filename) throws FileNotFoundException {
        logger.l5("XMLSerializer: Reading object from file: " + filename);
        
        File file = new File(filename);
        if (!file.exists()) {
            logger.l2("XMLSerializer: File does not exist: " + filename);
            throw new FileNotFoundException("File does not exist: " + filename);
        }
        
        if (!file.canRead()) {
            logger.l1("XMLSerializer: Cannot read file: " + filename);
            throw new FileNotFoundException("Cannot read file: " + filename);
        }
        
        XMLDecoder decoder = null;
        try {
            decoder = new XMLDecoder(new BufferedInputStream(
                    new FileInputStream(filename)));
            
            // Add exception listener for better error tracking
            decoder.setExceptionListener(new ExceptionListener() {
                @Override
                public void exceptionThrown(Exception e) {
                    logger.l1("XMLSerializer: Exception during XML decoding of file " + filename, e);
                }
            });
            
            Object o = decoder.readObject();
            logger.l5("XMLSerializer: Successfully read object of type " + 
                     (o != null ? o.getClass().getName() : "null") + " from file: " + filename);
            return o;
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.l1("XMLSerializer: ArrayIndexOutOfBoundsException while reading file: " + filename + 
                     ". File may be corrupted or contain invalid data.", e);
            throw new RuntimeException("Failed to deserialize XML file: " + filename + 
                     ". File may be corrupted or contain invalid data.", e);
        } catch (Exception e) {
            logger.l1("XMLSerializer: Failed to read object from file: " + filename, e);
            throw new RuntimeException("Failed to read XML file: " + filename, e);
        } finally {
            if (decoder != null) {
                decoder.close();
            }
        }
    }
}
