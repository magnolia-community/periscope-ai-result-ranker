/**
 * This file Copyright (c) 2019 Magnolia International
 * Ltd.  (http://www.magnolia-cms.com). All rights reserved.
 *
 *
 * This program and the accompanying materials are made
 * available under the terms of the Magnolia Network Agreement
 * which accompanies this distribution, and is available at
 * http://www.magnolia-cms.com/mna.html
 *
 * Any modifications to this file must keep this entire header
 * intact.
 *
 */
package info.magnolia.forge.periscope.rank.ml.jcr;

import static info.magnolia.cms.security.UserManager.SYSTEM_USER;

import info.magnolia.cms.security.User;
import info.magnolia.context.MgnlContext;
import info.magnolia.jcr.util.NodeTypes;

import java.util.Optional;

import javax.inject.Singleton;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.commons.JcrUtils;

/**
 * Strategy based on JCR and user role. Users with a {@link #RANKING_USERROLE} role
 * will get personalised search rankings. Other users will get default rankings based
 * on a common ranking neural network.
 */
@Singleton
public class JcrUserRoleNetworkStorageStrategy extends AbstractJcrNetworkStorageStrategy {

    protected static final String DEFAULT_RANKING_NODE_NAME = "default-neural-network-rankings";

    @Override
    protected Optional<Node> getNetworkNode(User user) {
        try {
            final Session session = MgnlContext.getJCRSession(WORKSPACE);

            if (userHasAccessToRankingsWorkspace(user)) {
                String fullPath = PARENT_PATH + user.getName() + "/" + FILENAME;
                return session.nodeExists(fullPath) ? Optional.of(session.getNode(fullPath)) : Optional.empty();
            }
            final String fullPath = PARENT_PATH + DEFAULT_RANKING_NODE_NAME + "/" + FILENAME;
            return MgnlContext.doInSystemContext(() -> session.nodeExists(fullPath) ? Optional.of(session.getNode(fullPath)) : Optional.empty());

        } catch (RepositoryException e) {
            log.error("Failed to load ranking neural network persistence node", e);
            return Optional.empty();
        }
    }

    @Override
    protected Node getOrCreateNetworkNode(User user) throws RepositoryException {
        final Session session = MgnlContext.getJCRSession(WORKSPACE);

        if (userHasAccessToRankingsWorkspace(user)) {
            return JcrUtils.getOrCreateByPath(PARENT_PATH + user.getName(), NodeTypes.Content.NAME, session);
        }

        return MgnlContext.doInSystemContext(()-> JcrUtils.getOrCreateByPath(PARENT_PATH + DEFAULT_RANKING_NODE_NAME, NodeTypes.Content.NAME, session));
    }

    private boolean userHasAccessToRankingsWorkspace(User user) {
        return user.hasRole(RANKING_USERROLE) || user.hasRole(SYSTEM_USER);
    }
}
