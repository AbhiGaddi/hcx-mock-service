package org.swasth.hcx.service;

import io.hcxprotocol.init.HCXIntegrator;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.swasth.hcx.exception.ClientException;

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
        System.out.println("We are intiliazing the integrator SDK: " + env.getProperty("hcx_application.user"));
        if(!configCache.containsKey(participantCode))
            configCache.put(participantCode, HCXIntegrator.getInstance(getParticipantConfig(participantCode)));
        return (HCXIntegrator) configCache.get(participantCode);
    }

    public Map<String,Object> getParticipantConfig(String participantCode) throws ClientException, SQLException, IOException {
        String query = String.format("SELECT * FROM %s WHERE child_participant_code='%s'", env.getProperty("postgres.table.mockParticipant"), participantCode);
        ResultSet resultSet = postgres.executeQuery(query);
        if(resultSet.next()){
            return getConfig(participantCode, resultSet.getString("primary_email"), resultSet.getString("password"),  resultSet.getString("private_key"));
        } else {
            return getConfig(env.getProperty("mock_payer.participant_code"), env.getProperty("mock_payer.username"), env.getProperty("mock_payer.password"),env.getProperty("mock_payer.private_key"));
        }
    }

    public Map<String,Object> getConfig(String code, String username, String password, String privateKey) throws IOException {
        String certificate = IOUtils.toString(new URL(privateKey), StandardCharsets.UTF_8.toString());

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("protocolBasePath", env.getProperty("hcx_application.url") + "/api/" + env.getProperty("hcx_application.api_version"));
        configMap.put("participantCode", code);
        configMap.put("authBasePath", env.getProperty("hcx_application.token_url"));
        configMap.put("username", username);
        configMap.put("password", password);
        configMap.put("encryptionPrivateKey", certificate);

        return configMap;
    }
}
