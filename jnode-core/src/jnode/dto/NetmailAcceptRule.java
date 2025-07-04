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

package jnode.dto;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Netmail acceptance rule for controlling which netmail messages are accepted
 * from nodes that exist in nodelist but not in LINKS table
 * 
 * @author jnode
 */
@DatabaseTable(tableName = "netmail_accept_rules")
public class NetmailAcceptRule {
	@DatabaseField(generatedId = true)
	private Long id;
	
	@DatabaseField(canBeNull = false, defaultValue = "0", columnName = "nice")
	private Long priority;
	
	@DatabaseField(canBeNull = false, defaultValue = "true")
	private Boolean enabled;
	
	@DatabaseField(columnName = "from_addr", defaultValue = "*")
	private String fromAddress;
	
	@DatabaseField(columnName = "to_addr", defaultValue = "*") 
	private String toAddress;
	
	@DatabaseField(columnName = "from_name", defaultValue = "*")
	private String fromName;
	
	@DatabaseField(columnName = "to_name", defaultValue = "*")
	private String toName;
	
	@DatabaseField(defaultValue = "*")
	private String subject;
	
	@DatabaseField(dataType = DataType.ENUM_STRING, canBeNull = false)
	private Action action;
	
	@DatabaseField(canBeNull = false, defaultValue = "false")
	private Boolean stopProcessing;
	
	@DatabaseField(columnName = "description")
	private String description;
	
	public enum Action {
		ACCEPT, REJECT
	}
	
	public NetmailAcceptRule() {
		this.enabled = true;
		this.priority = 100L;
		this.fromAddress = "*";
		this.toAddress = "*";
		this.fromName = "*";
		this.toName = "*";
		this.subject = "*";
		this.action = Action.REJECT;
		this.stopProcessing = false;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getPriority() {
		return priority;
	}

	public void setPriority(Long priority) {
		this.priority = priority;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public String getFromAddress() {
		return fromAddress;
	}

	public void setFromAddress(String fromAddress) {
		this.fromAddress = fromAddress;
	}

	public String getToAddress() {
		return toAddress;
	}

	public void setToAddress(String toAddress) {
		this.toAddress = toAddress;
	}

	public String getFromName() {
		return fromName;
	}

	public void setFromName(String fromName) {
		this.fromName = fromName;
	}

	public String getToName() {
		return toName;
	}

	public void setToName(String toName) {
		this.toName = toName;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}

	public Boolean getStopProcessing() {
		return stopProcessing;
	}

	public void setStopProcessing(Boolean stopProcessing) {
		this.stopProcessing = stopProcessing;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}