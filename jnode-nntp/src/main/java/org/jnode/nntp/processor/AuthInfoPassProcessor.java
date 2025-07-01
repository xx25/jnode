package org.jnode.nntp.processor;

import com.google.common.collect.Lists;
import jnode.dto.Link;
import org.jnode.nntp.DataProvider;
import org.jnode.nntp.DataProviderImpl;
import org.jnode.nntp.Processor;
import org.jnode.nntp.exception.UnknownCommandException;
import org.jnode.nntp.model.Auth;
import org.jnode.nntp.model.NntpResponse;

import java.util.Collection;

public class AuthInfoPassProcessor implements Processor {

    private DataProvider dataProvider = new DataProviderImpl();

    @Override
    public Collection<String> process(Collection<String> params, Long selectedGroupId, Long selectedArticleId, Auth auth) {

        if (params == null || params.size() != 1) {
            throw new UnknownCommandException();
        }
        
        if (auth == null) {
            Collection<String> errorResponse = Lists.newLinkedList();
            errorResponse.add(NntpResponse.AuthInfo.AUTHENTIFICATION_FAILED_OR_REJECTED);
            return errorResponse;
        }

        String pass = params.iterator().next();
        
        // Validate password parameter
        if (pass == null || pass.trim().isEmpty() || pass.length() > 256) {
            Collection<String> errorResponse = Lists.newLinkedList();
            errorResponse.add(NntpResponse.AuthInfo.AUTHENTIFICATION_FAILED_OR_REJECTED);
            auth.reset();
            return errorResponse;
        }

        Link link = dataProvider.link(auth, pass.trim());

        Collection<String> response = Lists.newLinkedList();

        if (link == null) {
            response.add(NntpResponse.AuthInfo.AUTHENTIFICATION_FAILED_OR_REJECTED);
            // Always reset auth state on failed authentication
            synchronized (auth) {
                auth.reset();
            }
        } else {
            // Set link ID atomically to prevent race conditions
            synchronized (auth) {
                auth.setLinkId(link.getId());
            }
            response.add(NntpResponse.AuthInfo.AUTHENTIFICATION_ACCEPTED);
        }

        return response;
    }

}
