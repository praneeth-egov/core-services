package org.egov.chat.xternal.restendpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.WriteContext;
import lombok.extern.slf4j.Slf4j;
import org.egov.chat.models.Response;
import org.egov.chat.service.restendpoint.RestEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Service
public class HomeIsolationHealthDetails implements RestEndpoint {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RestTemplate restTemplate;

    @Value("${home.isolation.user.service.host}")
    private String homeIsolationUserServiceHost;
    @Value("${home.isolation.user.service.search.endpoint}")
    private String homeIsolationUserServiceSearchEndpoint;

    @Value("${case.management.service.host}")
    private String caseManagementServiceHost;
    @Value("${case.management.health.detail.create.endpoint}")
    private String healthDetailCreateEndpoint;

    private String healthDetailsCreateRequest = "{\"RequestInfo\":{\"userInfo\":{}},\"tenantId\":\"\",\"mobileNumber\":\"\",\"healthDetails\":[{\"temperature\":98.6,\"symptoms\":\"\"}]}";

    private String responseMessage = "Thank You! You have successfully assessed and updated your health details as on" +
            " {{date}}.\nStay safe and kindly ensure that you assess and update your health details on this WhatsApp " +
            "number tomorrow as well.";

    @Override
    public ObjectNode getMessageForRestCall(ObjectNode params) throws Exception {
        String tenantId = params.get("tenantId").asText();
        String mobileNumber = params.get("mobileNumber").asText();
        Double temperature = Double.parseDouble(params.get("temperature").asText());
        String symptoms = params.get("symptoms").asText();

        JsonNode userInfo = getUserInfo(tenantId, mobileNumber);
        String permanentCity = userInfo.get("permanentCity").asText();

        DocumentContext documentContext = JsonPath.parse(userInfo.toString());
        WriteContext writeContext = JsonPath.parse(healthDetailsCreateRequest);

        writeContext.set("$.RequestInfo.userInfo", documentContext.json());
        writeContext.set("$.tenantId", permanentCity);
        writeContext.set("$.mobileNumber", mobileNumber);
        writeContext.set("$.healthDetails.[0].temperature", temperature);
        writeContext.set("$.healthDetails.[0].symptoms", symptoms);


        JsonNode requestJson = objectMapper.readTree(writeContext.jsonString());

        JsonNode responseJson = restTemplate.postForObject(caseManagementServiceHost + healthDetailCreateEndpoint,
                requestJson, JsonNode.class);

        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
        String dateString = formatter.format(date);

        String responseString = responseMessage.replace("{{date}}", dateString);

        Response response = Response.builder().type("text").text(responseString).build();

        ObjectNode objectNode = objectMapper.convertValue(response, ObjectNode.class);

        return objectNode;
    }

    JsonNode getUserInfo(String tenantId, String mobileNumber) throws IOException {
        String searchUserRequestBody = "{\"RequestInfo\":{},\"tenantId\":\"\",\"mobileNumber\":\"\"}";
        ObjectNode request = (ObjectNode) objectMapper.readTree(searchUserRequestBody);
        request.put("tenantId", tenantId);
        request.put("mobileNumber", mobileNumber);
        JsonNode response = restTemplate.postForObject(homeIsolationUserServiceHost + homeIsolationUserServiceSearchEndpoint,
                request, JsonNode.class);
        JsonNode users = response.get("user");

        return users.get(0);
    }

}