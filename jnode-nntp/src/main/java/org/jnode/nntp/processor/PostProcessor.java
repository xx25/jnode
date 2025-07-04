package org.jnode.nntp.processor;

import com.google.common.collect.Lists;
import jnode.dto.Echoarea;
import jnode.dto.Echomail;
import jnode.dto.Mail;
import jnode.dto.Netmail;
import jnode.event.Notifier;
import jnode.ftn.FtnTools;
import jnode.logger.Logger;
import org.apache.commons.lang3.StringUtils;
import org.jnode.nntp.Constants;
import org.jnode.nntp.DataProvider;
import org.jnode.nntp.DataProviderImpl;
import org.jnode.nntp.Processor;
import org.jnode.nntp.event.PostEndEvent;
import org.jnode.nntp.event.PostStartEvent;
import org.jnode.nntp.exception.NntpException;
import org.jnode.nntp.model.Auth;
import org.jnode.nntp.model.NntpResponse;
import org.jnode.nntp.util.Converter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;

public class PostProcessor extends BaseProcessor implements Processor {

    private static final Logger logger = Logger.getLogger(PostProcessor.class);
    private static final int MAX_SUBJECT_LENGTH = 72;
    private static final int MAX_NAME_LENGTH = 36;
    private static final int MAX_MESSAGE_LENGTH = 65536;
    private static final int MAX_EMAIL_LENGTH = 254;

    private DataProvider dataProvider = new DataProviderImpl();

    @Override
    public Collection<String> process(Collection<String> params, Long selectedGroupId, Long selectedArticleId, Auth auth) {
        
        // Check authorization for posting
        if (!isAuthorized(auth)) {
            Collection<String> errorResponse = Lists.newLinkedList();
            errorResponse.add(NntpResponse.AuthInfo.AUTHENTIFICATION_REQUIRED);
            return errorResponse;
        }

        Collection<String> response = Lists.newLinkedList();

        if (params.isEmpty()) {
            // start posting
            Notifier.INSTANCE.notify(new PostStartEvent());
            response.add(NntpResponse.Post.SEND_ARTICLE_TO_BE_POSTED);
        } else {
            // end posting
            Notifier.INSTANCE.notify(new PostEndEvent());
            
            // Validate input parameters before processing
            if (!validatePostParams(params)) {
                response.add(NntpResponse.Post.POSTING_FAILED);
                return response;
            }
            
            response.add(NntpResponse.Post.ARTICLE_RECEIVED_OK);

            if (isNetmail(params)) {
                try {
                    Netmail netmail = convertToNetmail(params);
                    // todo validate
                    dataProvider.post(netmail);
                } catch (NntpException e) {
                    logger.l1("Can't save netmail.", e);
                }
            } else {
                try {
                    Echomail echomail = convertToEchomail(params);
                    // todo validate
                    dataProvider.post(auth, echomail);
                } catch (NntpException e) {
                    logger.l1("Can't save echomail.", e);
                }
            }
        }

        return response;
    }

    private Echomail convertToEchomail(Collection<String> params) {
        Echomail echomail = new Echomail();
        convertToMail(echomail, params);
        echomail.setArea(findEchoarea(params));
        echomail.setToName(findTo(params));
        echomail.setMsgid(echomail.getFromFTN() + " " + FtnTools.generate8d());
        return echomail;
    }

    private String findTo(Collection<String> params) {

        String to = null;
        for (String param : params) {
            if (StringUtils.startsWithIgnoreCase(param, Constants.TO)) {
                // +1 becaus of ":"
                to = StringUtils.trim(StringUtils.substring(param, Constants.TO.length() + 1));
            }
        }

        return to == null ? "All" : to;
    }

    private Echoarea findEchoarea(Collection<String> params) {
        Echoarea echoarea = null;
        String echoareaName = null;

        for (String param : params) {
            if (StringUtils.startsWithIgnoreCase(param, Constants.NEWSGROUPS)) {
                // +1 becaus of ":"
                echoareaName = StringUtils.trim(StringUtils.substring(param, Constants.NEWSGROUPS.length() + 1));
                echoarea = dataProvider.echoarea(echoareaName);
            }
        }

        if (echoarea == null) {
            if (StringUtils.isEmpty(echoareaName)) {
                logger.l1("Echoarea is empty.");
            } else {
                logger.l1("Can't find echoarea by name: " + echoareaName + ".");
            }

            throw new NntpException();
        }

        return echoarea;
    }

    private Mail convertToMail(Mail mail, Collection<String> params) {
        boolean isBody = false;
        StringBuilder message = new StringBuilder();
        for (String param : params) {
            if (StringUtils.startsWithIgnoreCase(param, Constants.FROM)) {
                String from = StringUtils.trim(StringUtils.substring(param, Constants.FROM.length() + 1));

                int ind1 = StringUtils.indexOf(from, "<");
                int ind2 = StringUtils.indexOf(from, ">");

                if (ind1 == -1 || ind2 == -1) {
                    logger.l1("Incorrect 'from' line: " + from + ".");
                    continue;
                }

                String name = StringUtils.trim(StringUtils.substring(from, 0, ind1));
                String email = StringUtils.trim(StringUtils.substring(from, ind1 + 1, ind2));

                // Validate and sanitize input
                if (!isValidName(name) || !isValidEmail(email)) {
                    logger.l2("Invalid name or email in from field: " + from);
                    throw new NntpException();
                }

                mail.setFromName(sanitizeName(name));
                mail.setFromFTN(Converter.convertEmailToFtn(email));

                continue;
            }
            if (StringUtils.startsWithIgnoreCase(param, Constants.ORGANIZATION)) {
                // ignore
                continue;
            }
            if (StringUtils.startsWithIgnoreCase(param, Constants.SUBJECT)) {
                String subject = StringUtils.trim(StringUtils.substring(param, Constants.SUBJECT.length() + 1));
                if (!isValidSubject(subject)) {
                    logger.l2("Invalid subject: " + subject);
                    throw new NntpException();
                }
                mail.setSubject(sanitizeSubject(subject));
                continue;
            }
            if (StringUtils.trim(param).equalsIgnoreCase(StringUtils.EMPTY) && !isBody) {
                isBody = true;
                continue;
            }
            if (StringUtils.startsWithIgnoreCase(param, Constants.DATE)) {
                // +1 because of ":"
                String date = StringUtils.trim(StringUtils.substring(param, Constants.DATE.length() + 1));
                SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");
                try {
                    mail.setDate(f.parse(date));
                } catch (ParseException e) {
                    logger.l1("Can't parse date: " + date + ".");
                }
                continue;
            }

            if (isBody) {
                message.append(param).append("\r\n");
                continue;
            }

            logger.l4("Unknown message line: " + param + ".");
        }
        
        String messageText = message.toString();
        if (!isValidMessage(messageText)) {
            logger.l2("Invalid message content detected");
            throw new NntpException();
        }
        
        mail.setText(sanitizeMessage(messageText));
        return mail;

    }
    
    private boolean validatePostParams(Collection<String> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        
        // Check for minimum required headers
        boolean hasFrom = false;
        boolean hasSubject = false;
        
        for (String param : params) {
            if (StringUtils.startsWithIgnoreCase(param, Constants.FROM)) {
                hasFrom = true;
            }
            if (StringUtils.startsWithIgnoreCase(param, Constants.SUBJECT)) {
                hasSubject = true;
            }
        }
        
        return hasFrom && hasSubject;
    }
    
    private boolean isValidName(String name) {
        return StringUtils.isNotBlank(name) && 
               name.length() <= MAX_NAME_LENGTH &&
               !containsControlCharacters(name);
    }
    
    private boolean isValidEmail(String email) {
        return StringUtils.isNotBlank(email) && 
               email.length() <= MAX_EMAIL_LENGTH &&
               email.contains("@") &&
               !containsControlCharacters(email);
    }
    
    private boolean isValidSubject(String subject) {
        return StringUtils.isNotBlank(subject) && 
               subject.length() <= MAX_SUBJECT_LENGTH &&
               !containsControlCharacters(subject);
    }
    
    private boolean isValidMessage(String message) {
        return message != null && 
               message.length() <= MAX_MESSAGE_LENGTH;
    }
    
    private boolean containsControlCharacters(String text) {
        for (char c : text.toCharArray()) {
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }
        return false;
    }
    
    private String sanitizeName(String name) {
        return StringUtils.left(StringUtils.trim(name), MAX_NAME_LENGTH);
    }
    
    private String sanitizeSubject(String subject) {
        return StringUtils.left(StringUtils.trim(subject), MAX_SUBJECT_LENGTH);
    }
    
    private String sanitizeMessage(String message) {
        return StringUtils.left(message, MAX_MESSAGE_LENGTH);
    }

    private Netmail convertToNetmail(Collection<String> params) {
        Netmail netmail = new Netmail();
        convertToMail(netmail, params);
        findTo(netmail, params);
        return netmail;
    }

    private void findTo(Netmail netmail, Collection<String> params) {

        // todo refactor
        for (String param : params) {
            if (StringUtils.startsWithIgnoreCase(param, Constants.TO)) {
                // +1 becaus of ":"
                String to = StringUtils.trim(StringUtils.substring(param, Constants.TO.length() + 1));

                int ind1 = StringUtils.indexOf(to, "<");
                int ind2 = StringUtils.indexOf(to, ">");

                if (ind1 == -1 || ind2 == -1) {
                    logger.l1("Incorrect 'to' line: " + to + ".");
                    continue;
                }

                String name = StringUtils.trim(StringUtils.substring(to, 0, ind1));
                String email = StringUtils.trim(StringUtils.substring(to, ind1 + 1, ind2));

                // Validate and sanitize input
                if (!isValidName(name) || !isValidEmail(email)) {
                    logger.l2("Invalid name or email in to field: " + to);
                    throw new NntpException();
                }

                netmail.setToName(sanitizeName(name));
                netmail.setToFTN(Converter.convertEmailToFtn(email));
            }
        }
    }

    // TODO refactor
    private boolean isNetmail(Collection<String> params) {
        for (String param : params) {
            if (StringUtils.startsWithIgnoreCase(param, "newsgroups:")) {
                String groupName = StringUtils.trim(StringUtils.substring(param, "newsgroups:".length()));
                return Constants.NETMAIL_NEWSGROUP_NAME.equalsIgnoreCase(groupName);
            }
        }
        return false;
    }
}
