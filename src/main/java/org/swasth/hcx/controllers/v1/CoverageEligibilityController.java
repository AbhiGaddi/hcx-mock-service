package org.swasth.hcx.controllers.v1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.swasth.hcx.controllers.BaseController;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;

import java.util.Map;

@RestController()
@RequestMapping(value = "/"+ "${hcx_application.api_version}" +"/coverageeligibility")
public class CoverageEligibilityController extends BaseController {

    @Value("${kafka.topic.coverageeligibility}")
    private String kafkaTopic;

    @RequestMapping(value = "/check", method = RequestMethod.POST)
    public ResponseEntity<Object> checkCoverageEligibility(@RequestBody Map<String, Object> requestBody) throws Exception {
        return processRequest(requestBody, Constants.COVERAGE_ELIGIBILITY_CHECK, Constants.COVERAGE_ELIGIBILITY_CHECK, kafkaTopic);
    }

    @RequestMapping(value = "/on_check", method = RequestMethod.POST)
    public ResponseEntity<Object> onCheckCoverageEligibility(@RequestBody Map<String, Object> requestBody) throws Exception {
        return processRequestIncoming(requestBody, Constants.COVERAGE_ELIGIBILITY_ONCHECK,  Constants.COVERAGE_ELIGIBILITY_ONCHECK, kafkaTopic);
    }
}
