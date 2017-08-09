/*
 * Copyright 2014 Haulmont
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.haulmont.yarg.loaders.impl;

import com.haulmont.yarg.exception.DataLoadingException;
import com.haulmont.yarg.loaders.impl.json.JsonMap;
import com.haulmont.yarg.structure.BandData;
import com.haulmont.yarg.structure.ReportQuery;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads data from json string
 * Uses JsonPath to access necessary parts of json object
 * Example:
 * JSON:
 * { "store": {
 * "book": [
 * { "category": "reference",
 * "author": "Nigel Rees",
 * "title": "Sayings of the Century",
 * "price": 8.95
 * },
 * { "category": "fiction",
 * "author": "Evelyn Waugh",
 * "title": "Sword of Honour",
 * "price": 12.99,
 * "isbn": "0-553-21311-3"
 * }
 * ],
 * "bicycle": {
 * "color": "red",
 * "price": 19.95
 * }
 * }
 * }
 * Query string:
 * parameter=param1 $.store.book[*]
 * We get json string from parameter param1 and select all "book" objects from the "store" object
 */
public class JsonDataLoader extends AbstractDataLoader {
    protected Pattern parameterPattern = Pattern.compile("parameter=([A-z0-9_.]+)");

    private Configuration configuration;

    public JsonDataLoader() {
        this.configuration = Configuration.defaultConfiguration();
    }

    public JsonDataLoader(Configuration configuration) {
        this.configuration = configuration == null ? Configuration.defaultConfiguration() : configuration;
    }

    @Override
    public List<Map<String, Object>> loadData(ReportQuery reportQuery, BandData parentBand, Map<String, Object> reportParams) {
        Map<String, Object> currentParams = new HashMap<String, Object>();
        if (reportParams != null) {
            currentParams.putAll(reportParams);
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

        Matcher matcher = parameterPattern.matcher(reportQuery.getScript());
        String parameterName = getParameterName(matcher);

        //adds parameters from parent bands hierarchy
        BandData curentParentBand = parentBand;
        while (curentParentBand != null) {
            addParentBandDataToParameters(curentParentBand, currentParams);
            curentParentBand = curentParentBand.getParentBand();
        }

        if (parameterName != null) {
            Object parameterValue = currentParams.get(parameterName);
            if (parameterValue != null && StringUtils.isNotBlank(parameterValue.toString())) {
                String json = parameterValue.toString();
                String script = matcher.replaceAll("");

                if (StringUtils.isBlank(script)) {
                    throw new DataLoadingException(
                            String.format("The script doesn't contain json path expression. " +
                                    "Script [%s]", reportQuery.getScript()));
                }

                matcher = AbstractDbDataLoader.COMMON_PARAM_PATTERN.matcher(script);
                while (matcher.find()) {
                    String parameter = matcher.group(1);
                    script = matcher.replaceFirst(String.valueOf(currentParams.get(parameter)));
                    matcher = AbstractDbDataLoader.COMMON_PARAM_PATTERN.matcher(script);
                }

                try {
                    Object scriptResult = JsonPath.parse(json, this.configuration).read(script);
                    parseScriptResult(result, script, scriptResult);
                } catch (com.jayway.jsonpath.PathNotFoundException e) {
                    return Collections.emptyList();
                } catch (Throwable e) {
                    throw new DataLoadingException(
                            String.format("An error occurred while loading data with script [%s]", reportQuery.getScript()), e);
                }
            } else {
                return Collections.emptyList();
            }
        } else {
            throw new DataLoadingException(String.format("Query string doesn't contain link to parameter. " +
                    "Script [%s]", reportQuery.getScript()));
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    protected void parseScriptResult(List<Map<String, Object>> result, String script, Object scriptResult) {
        if (scriptResult instanceof List) {//JSONArray is also list
            List theList = (List) scriptResult;
            if (!theList.isEmpty()) {
                Object listObject = theList.get(0);
                if (listObject instanceof Map) {
                    for (Object object : theList) {
                        result.add(createMap((Map) object));
                    }
                } else {
                    throw new DataLoadingException(
                            String.format("The list collected with script does not contain objects. " +
                                    "It contains %s instead. " +
                                    "Script [%s]", listObject, script));
                }
            }
        } else if (scriptResult instanceof Map) {
            result.add(createMap((Map) scriptResult));
        } else {
            throw new DataLoadingException(
                    String.format("The script collects neither object nor list of objects. " +
                            "Script [%s]", script));
        }
    }

    protected String getParameterName(Matcher matcher) {
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    protected Map<String, Object> createMap(Map jsonObject) {
        return new JsonMap(jsonObject);
    }
}
