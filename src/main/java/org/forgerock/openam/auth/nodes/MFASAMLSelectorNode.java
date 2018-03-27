/*
 * Copyright © 2018 ForgeRock, AS.
 *
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Portions Copyrighted 2018 Charan Mann
 *
 * mfaSAMLSelector: Created by Charan Mann on 3/20/18 , 9:37 AM.
 */


package org.forgerock.openam.auth.nodes;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.util.i18n.PreferredLocales;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;


/**
 * An authentication decision node which prompts with a choice to select OTP mode like Email, SMS etc
 */
@Node.Metadata(outcomeProvider = MFASAMLSelectorNode.ChoiceCollectorOutcomeProvider.class,
        configClass = MFASAMLSelectorNode.Config.class)
public class MFASAMLSelectorNode implements Node {

    private final static String DEBUG_FILE = "MFASAMLSelectorNode";
    private static final String BUNDLE = MFASAMLSelectorNode.class.getName().replace(".", "/");
    private final Config config;
    protected Debug debug = Debug.getInstance(DEBUG_FILE);

    /**
     * Guice constructor.
     *
     * @param config The service config for the node.
     * @throws NodeProcessException If there is an error reading the configuration.
     */
    @Inject
    public MFASAMLSelectorNode(@Assisted Config config)
            throws NodeProcessException {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        //Pull user-agent out of headers
        List<String> refererList = context.request.headers.get("referer");
        debug.message("referer : " + refererList);

        //If no user-agent present at all
        if (refererList.size() != 1 && refererList.get(0).contains("spEntityID")) {
            debug.message("No specific referer found containing spEntityID");
            return goTo("Other").build();
        }

        String referer = refererList.get(0);
        String[] params = referer.split("&");
        Map<String, String> paramMap = new HashMap<>();
        for (String param : params) {
            String[] nameValue = param.split("=");
            if (nameValue.length == 2) {
                String name = param.split("=")[0];
                String value = param.split("=")[1];
                paramMap.put(name, value);
            }
        }

        String spEntityID = paramMap.get("spEntityID");

        if (config.choices().contains(spEntityID)) {
            debug.message("Matched spEntityID: " + spEntityID);
            return goTo(spEntityID).build();
        } else {
            debug.message("No match found for spEntityID: " + spEntityID);
            return goTo("Other").build();
        }
    }

    private Action.ActionBuilder goTo(String outcome) {
        return Action.goTo(outcome);
    }

    /**
     * Configuration for the node.
     */
    public interface Config {
        @Attribute(order = 100)
        List<String> choices();
    }

    /**
     * Provides the outcomes for the choice collector node.
     */
    public static class ChoiceCollectorOutcomeProvider implements OutcomeProvider {


        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            try {
                return nodeAttributes.get("choices").required()
                        .asList(String.class)
                        .stream()
                        .map(choice -> new Outcome(choice, (bundle.containsKey(choice)) ? bundle.getString(choice) : choice))
                        .collect(Collectors.toList());
            } catch (JsonValueException e) {
                return emptyList();
            }
        }
    }

}

