package org.jnode.nntp.processor;

import org.apache.commons.lang3.StringUtils;
import org.jnode.nntp.model.Auth;

public class BaseProcessor {

    public boolean isAuthorized(Auth auth) {
        if (auth == null) {
            return false;
        }
        
        // Check if authentication is complete - user must have both valid linkId and ftnAddress
        return auth.getLinkId() != null && 
               StringUtils.isNotBlank(auth.getFtnAddress()) &&
               StringUtils.isNotBlank(auth.getUser());
    }
    
    public boolean requiresAuthentication() {
        return true;
    }
}
