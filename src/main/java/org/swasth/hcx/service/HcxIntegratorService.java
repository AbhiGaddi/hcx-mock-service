package org.swasth.hcx.service;

import io.hcxprotocol.init.HCXIntegrator;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.utils.JSONUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


@Service
public class HcxIntegratorService {

    @Autowired
    Environment env;

    @Autowired
    private PostgresService postgres;

    private Map<String,Object> configCache = new HashMap<>();


    public HCXIntegrator getHCXIntegrator(String participantCode) throws Exception {
        /**
         * Initializing hcx_sdk to use helper functions and FHIR validator
         * Documentation is available at https://github.com/Swasth-Digital-Health-Foundation/hcx-platform/releases/tag/hcx-integrator-sdk-1.0.0
         */
        HCXIntegrator hcxIntegrator = null;
        if(!configCache.containsKey(participantCode)) {
            hcxIntegrator = HCXIntegrator.getInstance(getParticipantConfig(participantCode));
            configCache.put(hcxIntegrator.getParticipantCode(), hcxIntegrator);
        } else {
            hcxIntegrator = (HCXIntegrator) configCache.get(participantCode);
        }
        System.out.println("We are intiliazing the integrator SDK: " + hcxIntegrator.getParticipantCode() + " :: config map: " + hcxIntegrator.getConfig().toString());
        return hcxIntegrator;
    }

    public Map<String,Object> getParticipantConfig(String participantCode) throws ClientException, SQLException, IOException {
        String query = String.format("SELECT * FROM %s WHERE child_participant_code='%s'", env.getProperty("postgres.table.mockParticipant"), participantCode);
        ResultSet resultSet = postgres.executeQuery(query);
        if(resultSet.next()){
            return getConfig(participantCode, resultSet.getString("primary_email"), resultSet.getString("password"),  resultSet.getString("private_key"));
        } else {
            String certificate = IOUtils.toString(new URL(env.getProperty("mock_payer.private_key")), StandardCharsets.UTF_8.toString());
            return getConfig(env.getProperty("mock_payer.participant_code"), env.getProperty("mock_payer.username"), env.getProperty("mock_payer.password"), certificate);
        }
    }

    public Map<String,Object> getConfig(String code, String username, String password, String privateKey) throws IOException {

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("protocolBasePath", env.getProperty("hcx_application.url") + "/api/" + env.getProperty("hcx_application.api_version"));
        configMap.put("participantCode", code);
        configMap.put("authBasePath", env.getProperty("hcx_application.token_url"));
        configMap.put("username", username);
        configMap.put("password", password);
        configMap.put("encryptionPrivateKey", privateKey);
        configMap.put("fhirValidationEnabled", false);
        configMap.put("signingPrivateKey", privateKey);

        return configMap;
    }
}
