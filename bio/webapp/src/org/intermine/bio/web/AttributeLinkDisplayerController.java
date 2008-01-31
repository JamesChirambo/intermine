package org.intermine.bio.web;

/*
 * Copyright (C) 2002-2007 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.tiles.ComponentContext;
import org.apache.struts.tiles.actions.TilesAction;
import org.flymine.model.genomic.Organism;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.ObjectStore;
import org.intermine.util.TypeUtil;
import org.intermine.web.logic.Constants;

/**
 * Set up maps for the attributeLinkDisplayer.jsp
 * @author Kim Rutherford
 */
public class AttributeLinkDisplayerController extends TilesAction
{

    protected static final Logger LOG = Logger.getLogger(AttributeLinkDisplayerController.class);

    static final String ATTR_MARKER_RE = "<<attributeValue>>";

    private class ConfigMap extends HashMap<String, Object>
    {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActionForward execute(@SuppressWarnings("unused")  ComponentContext context,
                                 @SuppressWarnings("unused") ActionMapping mapping,
                                 @SuppressWarnings("unused") ActionForm form,
                                 HttpServletRequest request,
                                 @SuppressWarnings("unused") HttpServletResponse response) {
        ServletContext servletContext = request.getSession().getServletContext();
        InterMineObject imo = (InterMineObject) request.getAttribute("object");
        ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
        Model model = os.getModel();
        Set<ClassDescriptor> classDescriptors = model.getClassDescriptorsForClass(imo.getClass());
        StringBuffer sb = new StringBuffer();
        for (ClassDescriptor cd : classDescriptors) {
            if (sb.length() <= 0) {
                // (?: is a non-matching group
                sb.append("(");
            } else {
                sb.append("|");
            }
            sb.append(TypeUtil.unqualifiedName(cd.getName()));
        }
        sb.append(")");
        Organism organismReference = null;


        try {
            organismReference = (Organism) TypeUtil.getFieldValue(imo, "organism");
        } catch (IllegalAccessException e) {
            // no organism field
        }

        String geneOrgKey = sb.toString() + "(\\.(";
        if (organismReference == null || organismReference.getTaxonId() == null) {
            geneOrgKey += null + "|\\*))?";
        } else {
            // we need to check against * as well in case we want it to work for all taxonIds
            geneOrgKey += organismReference.getTaxonId() + "|\\*))?";
        }

        // map from eg. 'Gene.Drosophila.melanogaster' to map from configName (eg. "flybase")
        // to the configuration
        Map<String, ConfigMap> linkConfigs = new HashMap<String, ConfigMap>();
        Properties webProperties =
            (Properties) servletContext.getAttribute(Constants.WEB_PROPERTIES);
        final String regexp =
            "attributelink\\.([^.]+)\\." + geneOrgKey + "\\.([^.]+)\\.(url|text|imageName)";
        Pattern p = Pattern.compile(regexp);
        String className = null;
        for (Map.Entry<Object, Object> entry: webProperties.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            Matcher matcher = p.matcher(key);
            if (matcher.matches()) {
                String configKey = matcher.group(1);
                className = matcher.group(2);
                String attrName = matcher.group(5);
                String propType = matcher.group(6);

                ConfigMap config;
                if (linkConfigs.containsKey(configKey)) {
                    config = linkConfigs.get(configKey);
                } else {
                    config = new ConfigMap();
                    config.put("attributeName", attrName);
                    linkConfigs.put(configKey, config);
                }

                Object attrValue = null;
                if (config.containsKey("attributeValue")) {
                    attrValue = config.get("attributeValue");
                } else {
                    try {
                        attrValue = TypeUtil.getFieldValue(imo, attrName);
                        config.put("attributeValue", attrValue);
                        config.put("valid", Boolean.TRUE);
                    } catch (IllegalAccessException e) {
                        config.put("attributeValue", e);
                        config.put("valid", Boolean.FALSE);
                        LOG.error("configuration problem in AttributeLinkDisplayerController: "
                                  + "couldn't get a value for field " + attrName
                                  + " in class " + className);
                    }
                }

                if (propType.equals("url")) {
                    if (attrValue != null) {
                        String url;
                        if (value.contains(ATTR_MARKER_RE)) {
                            url = value.replaceAll(ATTR_MARKER_RE, String.valueOf(attrValue));
                        } else {
                            url = value + attrValue;
                        }
                        config.put("url", url);
                    }
                } else {
                    if (propType.equals("imageName")) {
                        config.put("imageName", value);
                    } else {
                        String text = value.replaceAll(ATTR_MARKER_RE, String.valueOf(attrValue));
                        config.put("text", text);
                    }
                }
            }
        }
        request.setAttribute("attributeLinkConfiguration", linkConfigs);
        request.setAttribute("attributeLinkClassName", className);
        return null;
    }
}
