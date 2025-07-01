package org.jnode.nntp.processor;

import org.jnode.nntp.Constants;
import org.jnode.nntp.DataProvider;
import org.jnode.nntp.DataProviderImpl;
import org.jnode.nntp.Processor;
import org.jnode.nntp.model.Auth;
import org.jnode.nntp.model.NewsGroup;
import org.jnode.nntp.model.NntpResponse;

import java.util.Collection;
import java.util.LinkedList;

public class ListProcessor extends BaseProcessor implements Processor {

    private DataProvider dataProvider = new DataProviderImpl();

    @Override
    public Collection<String> process(Collection<String> params, Long id, Long selectedArticleId, Auth auth) {

        Collection<String> response = new LinkedList<>();
        response.add(NntpResponse.List.LIST_OF_NEWSGROUPS);

        // Only provide newsgroup information to authorized users
        if (isAuthorized(auth)) {
            // Add netmail newsgroup for authorized users
            NewsGroup netmailGroup = dataProvider.newsGroup(Constants.NETMAIL_NEWSGROUP_NAME, auth);
            if (netmailGroup != null) {
                addNewsGroupToList(response, netmailGroup);
            }
            
            // Add regular newsgroups for authorized users
            for (NewsGroup newsGroup : dataProvider.newsGroups(auth)) {
                addNewsGroupToList(response, newsGroup);
            }
        } else {
            // For unauthorized users, return empty list or error
            response.clear();
            response.add(NntpResponse.AuthInfo.AUTHENTIFICATION_REQUIRED);
            return response;
        }

        response.add(NntpResponse.END);

        return response;
    }

    private void addNewsGroupToList(Collection<String> response, NewsGroup newsGroup) {
    /*
        http://tools.ietf.org/html/rfc3977#section-7.6.3
        "y" Posting is permitted.
        "n" Posting is not permitted.
        "m" Postings will be forwarded to the newsgroup moderator.
     */
        response.add(newsGroup.getName() + " " + newsGroup.getReportedHighWatermark() + " " + newsGroup.getReportedLowWatermark() + " " + "y");
    }
}
