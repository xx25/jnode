package org.jnode.nntp.event;

import jnode.event.IEvent;
import org.apache.commons.lang3.StringUtils;

public class AuthPassEvent implements IEvent {

    @Override
    public String getEvent() {
        return StringUtils.EMPTY;
    }
}
