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

package jnode.jscript;

import jnode.dto.Echoarea;
import jnode.ftn.FtnTools;
import jnode.jscript.impl.CommandExecResult;
import jnode.jscript.impl.LameCommand;
import jnode.logger.Logger;

import java.text.MessageFormat;

/**
 * Shell command execution helper for jscripts
 * 
 * @author Kirill Temnenkov (ktemnenkov@intervale.ru)
 * @version 1.1
 */
public class ShellHelper extends IJscriptHelper {

    private final Logger logger = Logger.getLogger(getClass());

    @Override
    public Version getVersion() {
        return new Version() {
            @Override
            public int getMajor() {
                return 1;
            }

            @Override
            public int getMinor() {
                return 1;
            }
        };
    }

    /**
     * Execute a command and post result to echoarea
     * 
     * @param echoArea echoarea name
     * @param subject message subject
     * @param exec command to execute
     * @param cmd command arguments
     * @param workDir working directory
     */
    public void execCommand(String echoArea, String subject, String exec, String cmd, String workDir) {
        LameCommand command = new LameCommand(exec, cmd, workDir);
        CommandExecResult execResult = command.execute();

        if (execResult.getOutput() != null && execResult.getOutput().length() > 0) {
            Echoarea area = FtnTools.getAreaByName(echoArea, null);

            String messageText;
            if (execResult.getErrOutput() != null && execResult.getErrOutput().length() != 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(execResult.getOutput());
                sb.append("\nerror output:\n");
                sb.append(execResult.getErrOutput());
                messageText = sb.toString();
            } else {
                messageText = execResult.getOutput();
            }

            FtnTools.writeEchomail(area, subject, messageText);
            logger.l4(MessageFormat.format("write message to {0} with subject {1}", area, subject));
        } else {
            // Post empty result or error information
            String messageText = "Command executed with no output.\n" +
                               "Command: " + exec + " " + (cmd != null ? cmd : "") + "\n" +
                               "Exit status: " + execResult.getStatus();
            
            if (execResult.getErrOutput() != null && execResult.getErrOutput().length() > 0) {
                messageText += "\nError output:\n" + execResult.getErrOutput();
            }
            
            Echoarea area = FtnTools.getAreaByName(echoArea, null);
            FtnTools.writeEchomail(area, subject, messageText);
            logger.l4(MessageFormat.format("write empty result message to {0} with subject {1}", area, subject));
        }
    }
}