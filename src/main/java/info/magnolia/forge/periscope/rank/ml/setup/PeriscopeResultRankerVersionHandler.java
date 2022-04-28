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
package info.magnolia.forge.periscope.rank.ml.setup;

import info.magnolia.module.DefaultModuleVersionHandler;
import info.magnolia.module.delta.BootstrapSingleResource;
import info.magnolia.module.delta.DeltaBuilder;
import info.magnolia.module.delta.SetupModuleRepositoriesTask;
import info.magnolia.module.model.Version;

/**
 * PeriscopeResultRanker module version handler.
 */
public class PeriscopeResultRankerVersionHandler extends DefaultModuleVersionHandler {

    public PeriscopeResultRankerVersionHandler() {
        register(DeltaBuilder.install(Version.parseVersion("1.0"), "Installs new rankings workspace")
                .addTask(new SetupModuleRepositoriesTask())
                .addTask(new BootstrapSingleResource("Create role 'ranker'", "Creates new role 'ranker' for the rankings workspace", "/mgnl-bootstrap/periscope-ai-result-ranker/userroles.ranker.xml"))
        );
    }
}
