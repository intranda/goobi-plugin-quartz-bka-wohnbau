package io.goobi.api.job;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.production.flow.jobs.AbstractGoobiJob;

import de.sub.goobi.config.ConfigPlugins;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class BkaWohnbauQuartzPlugin extends AbstractGoobiJob {
    @Getter
    private String value;

    /**
     * When called, this method gets executed
     */

    @Override
    public void execute() {
        parseConfiguration();
        // logic goes here
        System.out.println(value);
        System.out.println("BkaWohnbau Plugin executed");
    }

    @Override
    public String getJobName() {
        return "intranda_quartz_bka_wohnbau";
    }

    /**
     * Parse the configuration file
     */
    public void parseConfiguration() {
        XMLConfiguration config = ConfigPlugins.getPluginConfig(getJobName());
        config.setExpressionEngine(new XPathExpressionEngine());
        config.setReloadingStrategy(new FileChangedReloadingStrategy());
        value = config.getString("/value", "my default value");
    }

}
