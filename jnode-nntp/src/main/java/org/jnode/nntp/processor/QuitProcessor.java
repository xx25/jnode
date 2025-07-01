package org.jnode.nntp.processor;


import org.jnode.nntp.Processor;
import org.jnode.nntp.exception.EndOfSessionException;
import org.jnode.nntp.model.Auth;
import org.jnode.nntp.model.NntpResponse;

import java.util.Collection;
import java.util.Collections;

public class QuitProcessor implements Processor {

    @Override
    public Collection<String> process(Collection<String> params, Long id, Long selectedArticleId, Auth auth) {
        // The response will be sent by NntpClient before the exception is caught
        throw new EndOfSessionException();
    }
}
