package org.swasth.hcx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.swasth.hcx.dto.Request;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.swasth.hcx.utils.Constants.PENDING;

@Service
public class PayerService {

    @Value("${postgres.table.payerData}")
    private String table;

    @Autowired
    private PostgresService postgres;

    public void process(Request request, String reqFhirObj, String respFhirObj) throws ClientException, JsonProcessingException {
        Map<String,Object> info = new HashMap<>();
        if(!request.getAction().contains("coverageeligibility")) {
            info.put("medical", Collections.singletonMap("status", PENDING));
            info.put("financial", Collections.singletonMap("status", PENDING));
        }
        String query = String.format("INSERT INTO %s (request_id,sender_code,recipient_code,action,raw_payload,request_fhir,response_fhir,status,additional_info,created_on,updated_on,correlation_id,mobile,otp_verification,workflow_id,account_number,ifsc_code,bank_details) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s',%d,%d,'%s','%s','%s','%s','%s','%s','%s');",
                table, request.getApiCallId(), request.getSenderCode(), request.getRecipientCode(), getEntity(request.getAction()), request.getPayload().getOrDefault("payload", ""), reqFhirObj, respFhirObj, PENDING, JSONUtils.serialize(info), System.currentTimeMillis(), System.currentTimeMillis(), request.getCorrelationId(), "", PENDING, request.getWorkflowId(), "", "", PENDING);
        postgres.execute(query);
    }

    private String getEntity(String action){
        Map<String,String> actionMap = new HashMap<>();
        actionMap.put("/v0.7/coverageeligibility/check", "coverageeligibility");
        actionMap.put("/v0.7/preauth/submit", "preauth");
        actionMap.put("/v0.7/claim/submit", "claim");
        return actionMap.get(action);
    }
}
