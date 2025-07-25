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

package jnode.logger;

import jnode.core.FileUtils;
import jnode.main.threads.ThreadPool;

import java.io.*;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Redirector implements Runnable {
    private static final long MILLISEC_IN_DAY = 86400000L;

    private final Logger logger = Logger.getLogger(Redirector.class);
    private final String pathPrefix;
    private final String zipPath;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yy-MM-dd-HH-mm-ss");

    private String lastLogFilename;
    private String currentLogFile;

    public Redirector(String logFile, String zipPath) {
        this.pathPrefix = FileUtils.getPathPart(logFile);
        this.zipPath = zipPath;
        this.currentLogFile = logFile;
    }

    public void invoke() {

        if (currentLogFile == null) {
            return;
        }

        // Create log directory if it doesn't exist
        File logDir = new File(pathPrefix);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        File[] files = needZip() ? getFilesToZip() : null;
        redirect();
        schedule();
        zipFiles(files);
    }

    private void zipFiles(File[] files) {
        if (files == null){
            return;
        }
        for(File file : files){
            moveToZip(file.getAbsolutePath());
        }
    }

    private File[] getFilesToZip() {
        File directory = new File(FileUtils.getPathPart(currentLogFile));

        return directory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getName();
                // Accept files matching the pattern: prefix + timestamp + .log
                // but not the current log file
                return name.endsWith(".log") && 
                       name.matches(".*\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}\\.log$") &&
                       !pathname.getAbsolutePath().equals(currentLogFile);
            }
        });
    }

    private void schedule() {
        Date showDate = getNextLaunchDate();
        Date now = new Date();
        long initialDelay = showDate.getTime() - now.getTime();
        if (initialDelay < 0) {
            initialDelay = 0;
        }

        logger.l3("next log redirect will run at " + showDate
                + " and every 1 day after");
        new ScheduledThreadPoolExecutor(1).scheduleAtFixedRate(this,
                initialDelay, MILLISEC_IN_DAY, TimeUnit.MILLISECONDS);
    }

    private String redirect() {

        String result = lastLogFilename;
        lastLogFilename = DATE_FORMAT.format(new Date());
        String logpath = currentLogFile;

        try {
            PrintStream out = new PrintStream(
                    new BufferedOutputStream(
                            new FileOutputStream(logpath)), true, "UTF8");
            System.setOut(out);
            System.setErr(out);
            logger.l5("log redirected to " + logpath);

        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            logger.l1(MessageFormat.format("fail redirect log to {0}", logpath), e);
        }

        return result;
    }

    private String fullLogFileName(String logFileName) {
        String baseName = new File(currentLogFile).getName();
        if (baseName.endsWith(".log")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        return pathPrefix + File.separator + baseName + "-" + logFileName + ".log";
    }

    private String fullZipFileName(String filename) {
        // Ensure zipPath ends with separator
        String zipDir = zipPath;
        if (!zipDir.endsWith(File.separator)) {
            zipDir += File.separator;
        }
        return zipDir + new File(filename).getName() + ".zip";
    }

    @Override
    public void run() {
        PrintStream oldOut = System.out;
        logger.l5("oldOut " + oldOut);
        
        // Rename current log file with timestamp before redirecting
        final String oldLogName = DATE_FORMAT.format(new Date());
        File currentLog = new File(currentLogFile);
        File renamedLog = new File(fullLogFileName(oldLogName));
        
        if (currentLog.exists()) {
            if (currentLog.renameTo(renamedLog)) {
                logger.l5("renamed current log file to " + renamedLog.getAbsolutePath());
            } else {
                logger.l1("failed to rename current log file to " + renamedLog.getAbsolutePath());
            }
        }
        
        // Now redirect to fresh log file
        redirect();
        oldOut.close();
        logger.l5("close " + oldOut);

        if (needZip() && renamedLog.exists()) {
            ThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    moveToZip(renamedLog.getAbsolutePath());
                }
            });
        }
    }

    private boolean needZip() {
        return zipPath != null && zipPath.length() != 0;
    }

    private void moveToZip(String filename) {
        if (!needZip()){
            return;
        }
        
        // Create zip directory if it doesn't exist
        File zipDir = new File(zipPath);
        if (!zipDir.exists()) {
            zipDir.mkdirs();
        }
        
        String nameInsideZip = new File(filename).getName();
        String zipFilePath = fullZipFileName(filename);
        try {
            FileUtils.zipFile(filename,
                    zipFilePath, nameInsideZip);
            logger.l5(MessageFormat.format("zip file {0} to {1}",
                    filename, zipFilePath));
            if (new File(filename).delete()){
                logger.l5("delete " + filename);
            } else {
                logger.l1("fail delete " + filename);
            }
        } catch (IOException e) {
            logger.l1(MessageFormat.format("fail zip file {0} to {1}",
                    filename, zipFilePath), e);
        }
    }

    private static Date getNextLaunchDate() {
        Calendar calendar = Calendar.getInstance(Locale.US);
        calendar.set(Calendar.DAY_OF_YEAR, calendar.get(Calendar.DAY_OF_YEAR));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        return new Date(calendar.getTime().getTime() + MILLISEC_IN_DAY);
    }
}
