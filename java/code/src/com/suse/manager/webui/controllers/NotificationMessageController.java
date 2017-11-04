/**
 * Copyright (c) 2015 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.suse.manager.webui.controllers;

import com.redhat.rhn.common.security.CSRFTokenValidator;
import com.redhat.rhn.domain.notification.NotificationMessage;
import com.redhat.rhn.domain.notification.NotificationMessageFactory;
import com.redhat.rhn.domain.user.User;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import spark.ModelAndView;
import spark.Request;
import spark.Response;

/**
 * Controller class providing backend code for the messages page.
 */
public class NotificationMessageController {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new ECMAScriptDateAdapter())
            .serializeNulls()
            .create();

    private NotificationMessageController() { }

    /**
     * Displays a list of messages.
     *
     * @param request the request object
     * @param response the response object
     * @return the ModelAndView object to render the page
     */
    public static ModelAndView getList(Request request, Response response, User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("csrf_token", CSRFTokenValidator.getToken(request.session().raw()));
        return new ModelAndView(data, "notification-messages/list.jade");
    }


    /**
     * Returns JSON data from messages
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return JSON result of the API call
     */
    public static String dataUnread(Request request, Response response, User user) {
        Object data = NotificationMessageFactory.listUnread();

        response.type("application/json");
        return GSON.toJson(data);
    }

    /**
     * Returns JSON data from messages
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return JSON result of the API call
     */
    public static String dataAll(Request request, Response response, User user) {
        Object data = NotificationMessageFactory.listAll();

        response.type("application/json");
        return GSON.toJson(data);
    }

    /**
     * Update the read status of the message
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return JSON result of the API call
     */
    public static String updateMessageStatus(Request request, Response response, User user) {
        Map<String, Object> map = GSON.fromJson(request.body(), Map.class);
        Long messageId = Double.valueOf(map.get("messageId").toString()).longValue();
        boolean isRead = (boolean) map.get("isRead");

        Optional<NotificationMessage> nm = NotificationMessageFactory.lookupById(messageId);

        if (nm.isPresent()) {
            NotificationMessageFactory.updateStatus(nm.get(), isRead);
        }

        Map<String, String> data = new HashMap<>();
        data.put("message", "Message status updated");
        response.type("application/json");
        return GSON.toJson(data);
    }
}
