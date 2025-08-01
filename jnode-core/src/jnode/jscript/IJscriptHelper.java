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

public abstract class IJscriptHelper {
	public abstract class Version {
		public abstract int getMajor();

		public abstract int getMinor();

		@Override
		public String toString() {
			return String.format("v.%d.%d", getMajor(), getMinor());
		}
	}

	public abstract Version getVersion();

	public String toString() {
		return String.format("%s %s", getClass().getSimpleName(), getVersion()
				.toString());
	}
}
