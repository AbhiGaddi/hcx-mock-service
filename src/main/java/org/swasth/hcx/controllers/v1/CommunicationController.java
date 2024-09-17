package org.swasth.hcx.controllers.v1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.controllers.BaseController;

import java.util.Map;

@RestController()
@RequestMapping(Constants.VERSION_PREFIX)
public class CommunicationController extends BaseController {

    @Value("${kafka.topic.communication}")
    private String kafkaTopic;

    @PostMapping(Constants.COMMUNICATION_REQUEST)
    public ResponseEntity<Object> communicationRequest(@RequestBody Map<String, Object> requestBody) throws Exception {
        return processRequest(requestBody, Constants.COMMUNICATION_REQUEST, Constants.COMMUNICATION_ONREQUEST, kafkaTopic);
    }

    @RequestMapping(value = "/on_request", method = RequestMethod.POST)
    public ResponseEntity<Object> communicationOnRequest(@RequestBody Map<String, Object> requestBody) throws Exception {
        return processRequest(requestBody, Constants.COMMUNICATION_ONREQUEST,Constants.COMMUNICATION_ONREQUEST, kafkaTopic);
    }
}
