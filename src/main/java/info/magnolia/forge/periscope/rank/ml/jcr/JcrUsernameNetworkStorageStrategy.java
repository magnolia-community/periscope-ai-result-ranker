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
 * Strategy based on JCR and username. Each user, based on their unique name, will get personalised search rankings.
 */
@Singleton
public class JcrUsernameNetworkStorageStrategy extends AbstractJcrNetworkStorageStrategy {

    @Override
    protected Optional<Node> getNetworkNode(User user) {
        try {
            final Session session = MgnlContext.getJCRSession(WORKSPACE);
            final String fullPath = PARENT_PATH + user.getName() + "/" + FILENAME;

            return MgnlContext.doInSystemContext(() -> session.nodeExists(fullPath) ? Optional.of(session.getNode(fullPath)) : Optional.empty());
        } catch (RepositoryException e) {
            log.error("Failed to load ranking neural network persistence node", e);
            return Optional.empty();
        }
    }

    @Override
    protected Node getOrCreateNetworkNode(User user) throws RepositoryException {
        final Session session = MgnlContext.getJCRSession(WORKSPACE);
        return MgnlContext.doInSystemContext(()-> JcrUtils.getOrCreateByPath(PARENT_PATH + user.getName(), NodeTypes.Content.NAME, session));
    }
}
