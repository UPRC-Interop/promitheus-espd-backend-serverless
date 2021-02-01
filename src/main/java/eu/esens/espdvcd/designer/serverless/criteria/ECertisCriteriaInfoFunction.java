package eu.esens.espdvcd.designer.serverless.criteria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import eu.esens.espdvcd.designer.service.NationalCriteriaMappingService;
import eu.esens.espdvcd.designer.util.Errors;
import eu.esens.espdvcd.designer.util.JsonUtil;
import eu.esens.espdvcd.retriever.exception.RetrieverException;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;

import java.util.Optional;

/** Azure Functions with HTTP Trigger. */
public class ECertisCriteriaInfoFunction {
  /**
   * This function listens at endpoint "/api/ECertisCriteriaInfoFunction". Two ways to invoke it
   * using "curl" command in bash: 1. curl -d "HTTP Body" {your
   * host}/api/ECertisCriteriaInfoFunction 2. curl {your
   * host}/api/ECertisCriteriaInfoFunction?name=HTTP%20Query
   */
  @FunctionName("ECertisCriteriaInfoFunction")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "req",
              route =
                  "{version}/{qualificationApplicationType}/criteria/eCertisData/{criterionID}/country/{countryCode}",
              methods = {HttpMethod.GET},
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      @BindingName("qualificationApplicationType") String qualificationApplicationType,
      @BindingName("criterionID") String criterionID,
      @BindingName("countryCode") String countryCode,
      final ExecutionContext context) {
    NationalCriteriaMappingService criteriaEvidenceService =
            NationalCriteriaMappingService.INSTANCE;
    try {
      return request
          .createResponseBuilder(HttpStatus.OK)
          .body(JsonUtil.toJson(criteriaEvidenceService.getNationalCriteria(criterionID, countryCode)))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    } catch (RetrieverException e) {
      return request
          .createResponseBuilder(HttpStatus.BAD_GATEWAY)
          .body(Errors.retrieverError(e.getMessage()))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    } catch (IllegalArgumentException e) {
      return request
          .createResponseBuilder(HttpStatus.NOT_ACCEPTABLE)
          .body(Errors.notAcceptableError("Country code does not exist."))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    } catch (JsonProcessingException e) {
      return request
              .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(Errors.standardError(500, e.getMessage()))
              .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
              .build();
    }
  }
}
