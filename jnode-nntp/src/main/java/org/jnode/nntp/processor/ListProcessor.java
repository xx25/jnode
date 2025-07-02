package org.jnode.nntp.processor;

import jnode.logger.Logger;
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

    private static final Logger logger = Logger.getLogger(ListProcessor.class);
    private DataProvider dataProvider = new DataProviderImpl();

    @Override
    public Collection<String> process(Collection<String> params, Long id, Long selectedArticleId, Auth auth) {

        logger.l3("LIST command received - params: " + params + ", id: " + id + ", selectedArticleId: " + selectedArticleId);
        logger.l3("Auth state - user: " + (auth != null ? auth.toString() : "null"));

        Collection<String> response = new LinkedList<>();
        response.add(NntpResponse.List.LIST_OF_NEWSGROUPS);
        logger.l4("Added LIST response header: " + NntpResponse.List.LIST_OF_NEWSGROUPS);

        // Only provide newsgroup information to authorized users
        boolean authorized = isAuthorized(auth);
        logger.l3("Authorization check result: " + authorized);
        
        if (authorized) {
            logger.l4("Processing LIST for authorized user");
            
            // Add netmail newsgroup for authorized users
            NewsGroup netmailGroup = dataProvider.newsGroup(Constants.NETMAIL_NEWSGROUP_NAME, auth);
            logger.l4("Netmail newsgroup retrieved: " + (netmailGroup != null ? netmailGroup.getName() : "null"));
            if (netmailGroup != null) {
                addNewsGroupToList(response, netmailGroup);
                logger.l4("Added netmail newsgroup to response");
            }
            
            // Add regular newsgroups for authorized users
            long startNewsGroupsTime = System.currentTimeMillis();
            Collection<NewsGroup> newsGroups = dataProvider.newsGroups(auth);
            long newsGroupsRetrievalTime = System.currentTimeMillis();
            logger.l3("newsGroups() call completed in " + (newsGroupsRetrievalTime - startNewsGroupsTime) + "ms");
            
            long startSizeTime = System.currentTimeMillis();
            int newsGroupCount = (newsGroups != null ? newsGroups.size() : 0);
            long sizeTime = System.currentTimeMillis();
            logger.l3("Retrieved " + newsGroupCount + " regular newsgroups (size() took " + (sizeTime - startSizeTime) + "ms)");
            
            if (newsGroups != null) {
                int count = 0;
                for (NewsGroup newsGroup : newsGroups) {
                    long startTime = System.currentTimeMillis();
                    addNewsGroupToList(response, newsGroup);
                    count++;
                    long endTime = System.currentTimeMillis();
                    logger.l4("Added newsgroup " + count + ": " + newsGroup.getName() + " (took " + (endTime - startTime) + "ms)");
                }
                logger.l3("Total newsgroups added to response: " + count);
            }
        } else {
            logger.l3("User not authorized - returning authentication required");
            // For unauthorized users, return empty list or error
            response.clear();
            response.add(NntpResponse.AuthInfo.AUTHENTIFICATION_REQUIRED);
            logger.l4("Returning auth required response: " + NntpResponse.AuthInfo.AUTHENTIFICATION_REQUIRED);
            return response;
        }

        response.add(NntpResponse.END);
        logger.l4("Added response terminator: " + NntpResponse.END);
        logger.l3("LIST command completed - total response lines: " + response.size());

        return response;
    }

    private void addNewsGroupToList(Collection<String> response, NewsGroup newsGroup) {
    /*
        http://tools.ietf.org/html/rfc3977#section-7.6.3
        "y" Posting is permitted.
        "n" Posting is not permitted.
        "m" Postings will be forwarded to the newsgroup moderator.
     */
        String newsGroupLine = newsGroup.getName() + " " + newsGroup.getReportedHighWatermark() + " " + newsGroup.getReportedLowWatermark() + " " + "y";
        logger.l4("Adding newsgroup line: " + newsGroupLine);
        response.add(newsGroupLine);
    }
}
