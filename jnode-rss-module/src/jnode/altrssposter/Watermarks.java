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

package jnode.altrssposter;

import jnode.logger.Logger;
import jnode.store.XMLSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Manjago (kirill@temnenkov.com)
 */
public class Watermarks {

    private static final Logger logger = Logger.getLogger(Watermarks.class);
    private final String datafile;

    public Watermarks(String datafile) {
        this.datafile = datafile;
    }

    private Map<String, String> load() throws FileNotFoundException {
        synchronized (Watermarks.class){
            return internalLoad();
        }
    }

    @SuppressWarnings("unchecked")
	private Map<String, String> internalLoad() throws FileNotFoundException {
        return new File(datafile).exists() ?
                    (Map<String, String>) XMLSerializer.read(datafile) :
                    new HashMap<String, String>();
    }

    public String readValue(String key) throws FileNotFoundException {
        Map<String, String> watermarks = load();
        return watermarks.containsKey(key) ? watermarks.get(key) : null;
    }

    public void storeValue(String key, String value) throws FileNotFoundException {
        synchronized (Watermarks.class){
            Map<String, String> watermarks = internalLoad();
            watermarks.put(key, value);
            XMLSerializer.write(watermarks, datafile);
        }
    }

    public static void main(String[] args) {
        class TestThread extends Thread{

            @Override
            public void run() {
                try {
                    String key = ""+getId();
                    Watermarks w = new Watermarks("/temp/del/t.xml");

                    String value = null;

                    while(!"19".equals(value)){
                        value = w.readValue(key);
                        if (value == null){
                            value = "0";
                        }

                        sleep(1000);
                        int next = Integer.valueOf(value) + 1;
                        logger.l4("RSS: Watermark thread " + key + " oldvalue " + value + " newvalue " + next);
                        w.storeValue(key, ""+next);
                        sleep(1000);
                    }

                } catch (FileNotFoundException | InterruptedException e) {
                    logger.l1("RSS: Error in watermark test thread", e);
                }
            }
        }

        for(int i = 0; i < 20; ++i){
           new TestThread().start();
        }

    }
}
