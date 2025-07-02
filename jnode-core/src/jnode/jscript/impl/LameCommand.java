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

package jnode.jscript.impl;

import jnode.logger.Logger;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import static org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;

/**
 * Command executor using Plexus utilities
 * 
 * @author Kirill Temnenkov (ktemnenkov@intervale.ru)
 */
public class LameCommand {

    private final Logger logger = Logger.getLogger(getClass());
    private final Commandline command;

    public LameCommand(String exec, String cmd, String workDir) {
        command = new Commandline();
        command.setExecutable(exec);
        if (workDir != null && !workDir.trim().isEmpty()) {
            command.setWorkingDirectory(workDir);
        }
        if (cmd != null && !cmd.trim().isEmpty()) {
            command.createArg().setValue(cmd);
        }
    }

    private CommandExecResult execute(StreamConsumer out, StringStreamConsumer err) {
        CommandExecResult result = new CommandExecResult();
        logger.l5("Command line - " + getCommandString());
        logger.l5("Working directory - " + getWorkDir());
        int status;
        try {
            status = CommandLineUtils.executeCommandLine(command, out, err);
        } catch (CommandLineException e) {
            logger.l3("fail execute command " + this, e);
            result.setStatus(-1);
            return result;
        }
        result.setErrOutput(err.getOutput());
        if (out instanceof StringStreamConsumer) {
            StringStreamConsumer sc = (StringStreamConsumer) out;
            result.setOutput(sc.getOutput());
        }

        result.setStatus(status);
        logger.l5("execution result " + result);
        return result;
    }

    public CommandExecResult execute() {
        StringStreamConsumer out = new StringStreamConsumer();
        StringStreamConsumer err = new StringStreamConsumer();
        return execute(out, err);
    }

    String getCommandString() {
        return command.toString();
    }

    Commandline getCommandline() {
        return command;
    }

    String getWorkDir() {
        return command.getWorkingDirectory() != null ? 
               command.getWorkingDirectory().toString() : 
               System.getProperty("user.dir");
    }

    @Override
    public String toString() {
        return "LameCommand{" + "command=" + getCommandline() + '}';
    }
}