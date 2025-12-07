package org.joget.marketplace.datagenerator.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.directory.model.User;
import org.joget.marketplace.datagenerator.model.FormElement;
import org.joget.marketplace.datagenerator.model.FormGridInfo;
import org.joget.marketplace.datagenerator.model.SpreadSheetGridInfo;

public class DataGenUtil {

    private static final String[] TARGET_ELEMENT_TYPES = {
        "org.joget.apps.form.lib.TextField",
        "org.joget.apps.form.lib.TextArea",
        "org.joget.apps.form.lib.Radio",
        "org.joget.apps.form.lib.CheckBox",
        "org.joget.apps.form.lib.DatePicker",
        "org.joget.apps.form.lib.SelectBox",
        "org.joget.plugin.enterprise.FormGrid",
        "org.joget.plugin.enterprise.SpreadSheetGrid",
        "org.joget.plugin.enterprise.RichTextEditorField"
    };

    private Map<String, JsonObject> formGridMap = new HashMap<>();
    private Map<String, JsonObject> spreadSheetGridMap = new HashMap<>();

    public String buildPrompt(String formGridRows, String numberOfRows, long timestamp, String contextField, String requestYaml) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Here is a YAML representation of form fields. For each field where the value is 'GENERATE VALUE', please generate an appropriate value based on the field type, label, and provided context ")
                .append(timestamp)
                .append(".\n\n");

        if (contextField != null && !contextField.trim().isEmpty()) {
            promptBuilder.append("USER CONTEXT:\n")
                    .append(contextField.trim())
                    .append("\n\n");
        }

        promptBuilder.append("Additionally, there are some grids specified as separate objects. ")
                .append("For each grid under 'gridData', generate ").append(formGridRows)
                .append(" rows of data based on the columns in 'options'. ")
                .append("For each grid under 'spreadsheetGridData', generate ").append(formGridRows)
                .append(" rows of data based on the columns in 'options'. ")
                .append("Rows should be under an array named 'data' and use the value as the key.\n\n")
                .append("IMPORTANT:\n")
                .append("- Generate ").append(numberOfRows).append(" complete records. ")
                .append("Each record should be a SINGLE YAML object where keys are the field IDs and values are the generated data.\n")
                .append("- Return a complete YAML string. Never truncate.\n")
                .append("- If the output is too long, reduce the number of records but do not cut midway.\n\n")
                .append("Expected output format:\n")
                .append("- field_id_1: value1\n")
                .append("  field_id_2: value2\n")
                .append("  grid_field:\n")
                .append("    data:\n")
                .append("      - ...\n")
                .append("      - ...\n")
                .append("- ...\n\n")
                .append(requestYaml);

        return promptBuilder.toString();
    }

    public void extractElements(JsonObject node, List<FormElement> elements, int formGridRows) {
        if (node.has("className") && isTargetElementType(node.get("className").getAsString())) {
            JsonObject properties = node.getAsJsonObject("properties");
            String id = properties.get("id").getAsString();
            String label = properties.has("label") ? properties.get("label").getAsString() : "";
            String type = getTypeFromClassName(node.get("className").getAsString());
            String value = "GENERATE VALUE";

            String className = node.get("className").getAsString();
            //String type = getTypeFromClassName(className);

            if ("selectbox".equalsIgnoreCase(type) || "radio".equalsIgnoreCase(type) || "checkbox".equalsIgnoreCase(type)) {
                value = getRandomOptionValue(properties.getAsJsonArray("options"));
            }

            if ("datepicker".equalsIgnoreCase(type)) {
                value = "GENERATE VALUE IN MM/DD/YYYY Format";
            }

            // Handle FormGrid specifically
            if ("FormGrid".equalsIgnoreCase(type)) {
                JsonArray rawOptions = properties.getAsJsonArray("options");
                JsonArray cleanedOptions = cleanGridOptions(rawOptions, false);
                JsonObject formGridProperties = new JsonObject();
                formGridProperties.add("options", cleanedOptions);
                formGridProperties.addProperty("generateRows", formGridRows);
                formGridMap.put("grid_field", formGridProperties);
            } else if ("SpreadSheetGrid".equalsIgnoreCase(type)) {
                JsonArray rawOptions = properties.getAsJsonArray("options");
                JsonArray cleanedOptions = cleanGridOptions(rawOptions, true);
                JsonObject spreadSheetGridProperties = new JsonObject();
                spreadSheetGridProperties.add("options", cleanedOptions);
                spreadSheetGridProperties.addProperty("generateRows", formGridRows);
                spreadSheetGridMap.put("spreadsheet_field", spreadSheetGridProperties);
            } else {
                // Only add non-grid elements to the main elements list
                elements.add(new FormElement(id, label, value, type));
            }
        }

        if (node.has("elements")) {
            JsonArray elementsArray = node.getAsJsonArray("elements");
            for (JsonElement element : elementsArray) {
                extractElements(element.getAsJsonObject(), elements, formGridRows);
            }
        }
    }

    public JsonObject prepareFinalJson(List<FormElement> elements) {
        JsonObject finalJson = new JsonObject();
        JsonArray elementsArray = new JsonArray();

        for (FormElement element : elements) {
            JsonObject elementJson = new JsonObject();
            elementJson.addProperty("id", element.getId());
            elementJson.addProperty("label", element.getLabel());
            elementJson.addProperty("value", element.getValue());
            elementJson.addProperty("type", element.getType());

            elementsArray.add(elementJson);
        }

        finalJson.add("elements", elementsArray);

        // Add gridData object for each form grid
        JsonObject gridData = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : formGridMap.entrySet()) {
            gridData.add(entry.getKey(), entry.getValue());
        }
        finalJson.add("gridData", gridData);

        JsonObject spreadsheetGridData = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : spreadSheetGridMap.entrySet()) {
            spreadsheetGridData.add(entry.getKey(), entry.getValue());
        }
        finalJson.add("spreadsheetGridData", spreadsheetGridData);

        return finalJson;
    }

    private String getTypeFromClassName(String className) {
        return className.substring(className.lastIndexOf(".") + 1);
    }

    private static boolean isTargetElementType(String className) {
        for (String targetType : TARGET_ELEMENT_TYPES) {
            if (targetType.equals(className)) {
                return true;
            }
        }
        return false;
    }

    private String getRandomOptionValue(JsonArray options) {
        if (options != null && options.size() > 0) {
            Random random = new Random();
            int randomIndex = random.nextInt(options.size());
            JsonObject option = options.get(randomIndex).getAsJsonObject();

            String value = option.get("value").getAsString();

            // Check if the option value is empty
            if (!value.isEmpty()) {
                return value;
            } else {
                // If the value is empty, try another option
                return getRandomOptionValue(options);
            }
        }
        return "";
    }

    private JsonArray cleanGridOptions(JsonArray options, boolean isSpreadsheet) {
        JsonArray cleanedArray = new JsonArray();

        for (JsonElement optElem : options) {
            JsonObject original = optElem.getAsJsonObject();
            JsonObject cleaned = new JsonObject();
            cleaned.addProperty("label", original.get("label").getAsString());
            cleaned.addProperty("formatType", original.get("formatType").getAsString());
            cleaned.addProperty("value", original.get("value").getAsString());
            cleanedArray.add(cleaned);
        }

        return cleanedArray;
    }
    
    public void extractFormGrids(JsonObject node, List<FormGridInfo> grids) {

        if (node.has("className") && "org.joget.plugin.enterprise.FormGrid".equals(node.get("className").getAsString())) {
            JsonObject properties = node.getAsJsonObject("properties");
            String id = properties.get("id").getAsString();
            String formDefId = properties.get("formDefId").getAsString();
            // Extract loadBinder properties
            JsonObject loadBinder = properties.getAsJsonObject("loadBinder").getAsJsonObject("properties");
            String loadFormDefId = loadBinder.has("formDefId") ? loadBinder.get("formDefId").getAsString() : "";
            String loadForeignKey = loadBinder.has("foreignKey") ? loadBinder.get("foreignKey").getAsString() : "";
            // Extract storeBinder properties
            JsonObject storeBinder = properties.getAsJsonObject("storeBinder").getAsJsonObject("properties");
            String storeFormDefId = storeBinder.has("formDefId") ? storeBinder.get("formDefId").getAsString() : "";
            String storeForeignKey = storeBinder.has("foreignKey") ? storeBinder.get("foreignKey").getAsString() : "";
            grids.add(new FormGridInfo(id, formDefId, loadFormDefId, loadForeignKey, storeFormDefId, storeForeignKey));
        }

        // Recursive scan child nodes
        if (node.has("elements")) {
            for (JsonElement child : node.getAsJsonArray("elements")) {
                extractFormGrids(child.getAsJsonObject(), grids);
            }
        }
    }

    public void extractSpreadSheetGrids(JsonObject node, List<SpreadSheetGridInfo> grids) {

        if (node.has("className") && "org.joget.plugin.enterprise.SpreadSheetGrid".equals(node.get("className").getAsString())) {
            JsonObject properties = node.getAsJsonObject("properties");
            String id = properties.get("id").getAsString();
            // LoadBinder details
            JsonObject loadBinderProps = properties.getAsJsonObject("loadBinder").getAsJsonObject("properties");
            String loadFormDefId = loadBinderProps.has("formDefId") ? loadBinderProps.get("formDefId").getAsString() : "";
            String loadForeignKey = loadBinderProps.has("foreignKey") ? loadBinderProps.get("foreignKey").getAsString() : "";
            // StoreBinder details
            JsonObject storeBinderProps = properties.getAsJsonObject("storeBinder").getAsJsonObject("properties");
            String storeFormDefId = storeBinderProps.has("formDefId") ? storeBinderProps.get("formDefId").getAsString() : "";
            String storeForeignKey = storeBinderProps.has("foreignKey") ? storeBinderProps.get("foreignKey").getAsString() : "";
            grids.add(new SpreadSheetGridInfo(
                    id,
                    loadFormDefId, loadForeignKey,
                    storeFormDefId, storeForeignKey
            ));
        }
        // Continue scanning children recursively
        if (node.has("elements")) {
            for (JsonElement child : node.getAsJsonArray("elements")) {
                extractSpreadSheetGrids(child.getAsJsonObject(), grids);
            }
        }
    }

    public String insertParentRecord(Map<String, Object> parentFields, User currentUser, AppService appService, AppDefinition selectedAppDef, FormDataDao formDataDao, String formDefId) {
        String id = UuidGenerator.getInstance().getUuid();
        FormRowSet frs = new FormRowSet();
        Date date = new Date();
        FormRow fr = new FormRow();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        for (Map.Entry<String, Object> entry : parentFields.entrySet()) {
            String key = entry.getKey();
            Object valueObj = entry.getValue();
            if (valueObj == null) {
                continue;
            }
            String value;
            if (valueObj instanceof Date) {
                value = dateFormat.format((Date) valueObj);
            } else {
                value = valueObj.toString();
            }
            fr.setProperty(key, value);
        }
        fr.setId(id);
        fr.setDateCreated(date);
        fr.setDateModified(date);
        fr.setCreatedBy(currentUser.getUsername());
        fr.setCreatedByName(currentUser.getFirstName() + " " + currentUser.getLastName());
        fr.setModifiedBy(currentUser.getUsername());
        fr.setModifiedByName(currentUser.getFirstName() + " " + currentUser.getLastName());
        frs.add(fr);
        String tableName = appService.getFormTableName(selectedAppDef, formDefId);
        formDataDao.saveOrUpdate(formDefId, tableName, frs);
        return id;
    }

    public void insertGridRecord(Map<String, Object> rowData, String parentId, String storeFormDefId, String storeForeignKey, User currentUser, AppService appService, AppDefinition selectedAppDef, FormDataDao formDataDao) {
        // Insert into grid table
        FormRowSet frs = new FormRowSet();
        Date date = new Date();
        FormRow fr = new FormRow();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            String key = entry.getKey();
            Object valueObj = entry.getValue();
            if (valueObj == null) {
                continue;
            }
            String value;
            if (valueObj instanceof Date) {
                value = dateFormat.format((Date) valueObj);
            } else {
                value = valueObj.toString();
            }
            fr.setProperty(key, value);
        }
        fr.setProperty(storeForeignKey, parentId);
        fr.setId(UuidGenerator.getInstance().getUuid());
        fr.setDateCreated(date);
        fr.setDateModified(date);
        fr.setCreatedBy(currentUser.getUsername());
        fr.setCreatedByName(currentUser.getFirstName() + " " + currentUser.getLastName());
        fr.setModifiedBy(currentUser.getUsername());
        fr.setModifiedByName(currentUser.getFirstName() + " " + currentUser.getLastName());
        frs.add(fr);
        String tableName = appService.getFormTableName(selectedAppDef, storeFormDefId);
        formDataDao.saveOrUpdate(storeFormDefId, tableName, frs);
    }

}
