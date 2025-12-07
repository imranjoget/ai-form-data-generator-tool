package org.joget.marketplace.datagenerator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Set;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.marketplace.datagenerator.model.Chat;
import org.joget.marketplace.datagenerator.model.ChatGptErrorResponse;
import org.joget.marketplace.datagenerator.model.ChatGptMessage;
import org.joget.marketplace.datagenerator.model.ChatGptResponse;
import org.joget.marketplace.datagenerator.model.FormElement;
import org.joget.marketplace.datagenerator.model.FormGridInfo;
import org.joget.marketplace.datagenerator.model.Result;
import org.joget.marketplace.datagenerator.model.SpreadSheetGridInfo;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joget.directory.model.User;
import org.joget.marketplace.datagenerator.model.util.DataGenUtil;
import org.joget.workflow.model.service.WorkflowUserManager;

public class FormDataGeneratorTool extends DefaultApplicationPlugin {

    private final static String MESSAGE_PATH = "messages/FormDataGeneratorTool";

    @Override
    public Object execute(Map properties) {

        FormDefinitionDao dao = (FormDefinitionDao) FormUtil.getApplicationContext().getBean("formDefinitionDao");
        FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

        ApplicationContext appContext = (ApplicationContext) AppUtil.getApplicationContext();
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) appContext.getBean("workflowUserManager");
        
        DataGenUtil genUtil = new DataGenUtil();

        AppDefinition currentAppDef = AppUtil.getCurrentAppDefinition();

        String appVersion = String.valueOf(currentAppDef.getVersion());
        String appId = currentAppDef.getAppId();

        String recordId = "";
        if (wfAssignment != null) {
            recordId = appService.getOriginProcessId(wfAssignment.getProcessId());
        } else {
            recordId = (String) properties.get("recordId");
        }

        String model = (String) properties.get("model");
        String apiKey = (String) properties.get("apiKey");
        String proxyDomain = (String) properties.get("proxyDomain");
        String formDefId = (String) properties.get("formDefId");
        String appProperty = (String) properties.get("appField");
        String formProperty = (String) properties.get("formField");
        String contextProperty = (String) properties.get("contextField");

        //load data
        FormRowSet rows = appService.loadFormData(appId, appVersion, formDefId, recordId);
        FormRow formRow = rows.get(0);

        String appField = (String) formRow.get(appProperty);
        String formField = (String) formRow.get(formProperty);
        String contextField = (String) formRow.get(contextProperty);

        // get the appDef for the selected app
        AppDefinition selectedAppDef = appService.getPublishedAppDefinition(appField);

        FormDefinition formDef = dao.loadById(formField, selectedAppDef);
        String json = formDef.getJson();

        JsonObject rootNode = JsonParser.parseString(json).getAsJsonObject();
        List<FormElement> elements = new ArrayList<>();
        genUtil.extractElements(rootNode, elements, 0);

        JsonObject finalJson = genUtil.prepareFinalJson(elements);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String elementsJson = gson.toJson(finalJson);

        List<FormGridInfo> formGrids = new ArrayList<>();
        genUtil.extractFormGrids(rootNode, formGrids);

        String gridStoreFormDefId = "";
        String gridStoreForeignKey = "";

        for (FormGridInfo grid : formGrids) {
            gridStoreFormDefId = grid.storeBinderFormDefId;
            gridStoreForeignKey = grid.storeBinderForeignKey;
        }

        List<SpreadSheetGridInfo> sheetGrids = new ArrayList<>();
        genUtil.extractSpreadSheetGrids(rootNode, sheetGrids);

        String ssStoreFormDefId = "";
        String ssStoreForeignKey = "";

        for (SpreadSheetGridInfo grid : sheetGrids) {
            ssStoreFormDefId = grid.getStoreBinderFormDefId();
            ssStoreForeignKey = grid.getStoreBinderForeignKey();
        }

        String formGridRows;
        String numberOfRows;
        int maxTokens;

        // Firxt 3 small-token models
        Set<String> smallModels = Set.of(
                "gpt-3.5-turbo",
                "gpt-4",
                "gpt-4-turbo"
        );

        if (smallModels.contains(model)) {
            formGridRows = "2";
            numberOfRows = "5";
            maxTokens = 4096;      // small models token limit
        } else {
            formGridRows = "2";
            numberOfRows = "10";

            if (null == model) {
                // Fallback if unknown model
                maxTokens = 8192;
            } else switch (model) {
                case "gpt-4o":
                    maxTokens = 16384;
                    break;
                case "gpt-4.1":
                    maxTokens = 32768;
                    break;
                default:
                    // Fallback if unknown model
                    maxTokens = 8192;
                    break;
            }
        }

        // convert to yaml
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode;
        String rawYaml = "";
        try {
            jsonNode = mapper.readTree(elementsJson);
            YAMLMapper yamlMapper = new YAMLMapper();
            rawYaml = yamlMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException ex) {
            LogUtil.error(getClassName(), ex, ex.getMessage());
        }

        // Re-load YAML and emit clean YAML without unnecessary quotes
        Yaml yaml = new Yaml();
        Object data = yaml.load(rawYaml);
        String cleanYaml = yaml.dump(data);

        long timestamp = System.currentTimeMillis();

        String prompt = genUtil.buildPrompt(formGridRows, numberOfRows, timestamp, contextField, cleanYaml);

        String gptResponse = callChatGPTApi(proxyDomain, apiKey, model, prompt, maxTokens);

        gptResponse = gptResponse.replaceFirst("^```yaml\\s*", "").replaceFirst("```\\s*$", "");

        Yaml yamll = new Yaml();
        InputStream inputStream = new ByteArrayInputStream(gptResponse.getBytes(StandardCharsets.UTF_8));
        List<Map<String, Object>> formDataRows = yamll.load(inputStream);

        for (Map<String, Object> record : formDataRows) {

            Map<String, Object> parentFields = new LinkedHashMap<>();
            Map<String, List<Map<String, Object>>> gridFieldData = new LinkedHashMap<>();
            Map<String, List<Map<String, Object>>> spreadsheetFieldData = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : record.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (key.equals("grid_field") && value instanceof Map) {
                    Map<String, Object> grid = (Map<String, Object>) value;

                    if (grid.containsKey("data")) {
                        List<Map<String, Object>> gridRows = (List<Map<String, Object>>) grid.get("data");
                        gridFieldData.put(key, gridRows);
                    }
                    continue;
                }

                if (key.equals("spreadsheet_field") && value instanceof Map) {
                    Map<String, Object> spreadsheet = (Map<String, Object>) value;

                    if (spreadsheet.containsKey("data")) {
                        List<Map<String, Object>> ssRows = (List<Map<String, Object>>) spreadsheet.get("data");
                        spreadsheetFieldData.put(key, ssRows);
                    }
                    continue;
                }

                parentFields.put(key, value);
            }

            // perform data insertion to respective tables
            User currentUser = workflowUserManager.getCurrentUser();
            String parentId = genUtil.insertParentRecord(parentFields, currentUser, appService, selectedAppDef, formDataDao, formField);

            for (Map.Entry<String, List<Map<String, Object>>> gridEntry : gridFieldData.entrySet()) {
                List<Map<String, Object>> gridRows = gridEntry.getValue();
                for (Map<String, Object> gridRow : gridRows) {
                    genUtil.insertGridRecord(gridRow, parentId, gridStoreFormDefId, gridStoreForeignKey, currentUser, appService, selectedAppDef, formDataDao);
                }
            }

            for (Map.Entry<String, List<Map<String, Object>>> ssEntry : spreadsheetFieldData.entrySet()) {
                for (Map<String, Object> ssRow : ssEntry.getValue()) {
                    Map<String, Object> spreadSheetRow = new LinkedHashMap<>(ssRow);
                    genUtil.insertGridRecord(spreadSheetRow, parentId, ssStoreFormDefId, ssStoreForeignKey, currentUser, appService, selectedAppDef, formDataDao);
                }
            }

        }
        
        // set back the current app def
        AppUtil.setCurrentAppDefinition(currentAppDef);

        return null;
    }

    private String callChatGPTApi(String domain, String apiKey, String model, String prompt, int maxTokens) {
        final String endPoint = domain + "/v1/chat/completions";
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost postRequest = new HttpPost(endPoint);
        postRequest.addHeader("Content-Type", "application/json");
        postRequest.addHeader("Authorization", "Bearer " + apiKey);
        List<ChatGptMessage> messages = new ArrayList<>();

        String prime = "";
        ChatGptMessage systemRoleMessage = new ChatGptMessage();
        systemRoleMessage.setRole("system");
        systemRoleMessage.setContent(prime);
        messages.add(systemRoleMessage);

        ChatGptMessage userMessage = new ChatGptMessage();
        userMessage.setRole("user");
        userMessage.setContent(prompt);
        messages.add(userMessage);

        Chat chat = new Chat();
        chat.setModel(model);
        chat.setMaxTokens(maxTokens);
        chat.setTemperature(0.0);
        chat.setMessages(messages);

        Gson gson = new Gson();
        String requestBody = gson.toJson(chat);

        StringEntity params;

        try {
            params = new StringEntity(requestBody);
            postRequest.setEntity(params);
            org.apache.http.HttpResponse response = httpClient.execute(postRequest);
            String responseBody = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() == 200) {
                ChatGptResponse cgr = gson.fromJson(responseBody, ChatGptResponse.class);
                Result result = new Result();
                result.setCode(response.getStatusLine().getStatusCode());
                String chatResponse = cgr.getChoices().get(0).getMessage().getContent();
                result.setContent(chatResponse);
                String success = gson.toJson(result);
                return chatResponse;
            } else {
                ChatGptErrorResponse cger = gson.fromJson(responseBody, ChatGptErrorResponse.class);
                Result result = new Result();
                result.setCode(response.getStatusLine().getStatusCode());
                result.setContent(cger.getError().getMessage());
                String error = gson.toJson(result);
                return error;
            }
        } catch (UnsupportedEncodingException ex) {
            LogUtil.error(getClassName(), ex, ex.getMessage());
        } catch (IOException ex) {
            LogUtil.error(getClassName(), ex, ex.getMessage());
        }

        return "";
    }

    @Override
    public String getName() {
        return "Form Data Generator Tool";
    }

    @Override
    public String getVersion() {
        return Activator.PLUGIN_VERSION;
    }

    @Override
    public String getDescription() {
        return "Form Data Generator Tool";
    }

    @Override
    public String getLabel() {
        return "Form Data Generator Tool";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/formDataGeneratorTool.json", null, true, MESSAGE_PATH);
    }

}