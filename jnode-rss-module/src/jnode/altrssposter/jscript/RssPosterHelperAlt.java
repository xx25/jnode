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

package jnode.altrssposter.jscript;

import jnode.altrssposter.RssPoster;
import jnode.altrssposter.Watermarks;
import jnode.dto.Echoarea;
import jnode.ftn.FtnTools;
import jnode.jscript.IJscriptHelper;
import jnode.logger.Logger;

import java.io.FileNotFoundException;
import java.text.MessageFormat;

/**
 * Another RSS poster
 *
 * @author Manjago
 */
public class RssPosterHelperAlt extends IJscriptHelper {
    private static final Logger logger = Logger
            .getLogger(RssPosterHelperAlt.class);

    @Override
    public Version getVersion() {
        return new Version() {

            @Override
            public int getMinor() {
                return 1;
            }

            @Override
            public int getMajor() {
                return 1;
            }
        };
    }

    /**
     * Posts news to fido area
     *
     * @param title    message subject
     * @param echoarea area
     * @param url      source url
     * @param datafile data file
     * @param limit    maximum number of messages for positive value, or unlimited
     */
    public void postNewsToEchoarea(String title, String echoarea, String url, String datafile, int limit) {

        logger.l5(MessageFormat.format("postNewsToEchoarea title = {0}, echoarea = {1}, url = {2}",
                title, echoarea, url));

        Echoarea area = FtnTools.getAreaByName(echoarea, null);
        if (area == null) {
            logger.l4("No such echoarea - " + echoarea);
            return;
        }

        try {
            Watermarks watermarks = new Watermarks(datafile);

            StringBuilder sb = RssPoster.getText(url, watermarks, limit);
            if (sb != null && sb.length() != 0) {
                FtnTools.writeEchomail(area, title, sb.toString());
            }

        } catch (FileNotFoundException e) {
            logger.l4(MessageFormat.format("File {0} not found", datafile), e);
        }

    }
}
