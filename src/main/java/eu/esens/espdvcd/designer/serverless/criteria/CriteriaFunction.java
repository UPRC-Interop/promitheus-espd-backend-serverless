package eu.esens.espdvcd.designer.serverless.criteria;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import eu.esens.espdvcd.codelist.enums.internal.ContractingOperatorEnum;
import eu.esens.espdvcd.designer.service.CriteriaService;
import eu.esens.espdvcd.designer.service.RegulatedCriteriaService;
import eu.esens.espdvcd.designer.service.SelfContainedCriteriaService;
import eu.esens.espdvcd.designer.util.Errors;
import eu.esens.espdvcd.retriever.exception.RetrieverException;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;

import java.util.Optional;

/** Azure Functions with HTTP Trigger. */
public class CriteriaFunction {
  /**
   * This function listens at endpoint "/api/CriteriaFunction". Two ways to invoke it using "curl"
   * command in bash: 1. curl -d "HTTP Body" {your host}/api/CriteriaFunction 2. curl {your
   * host}/api/CriteriaFunction?name=HTTP%20Query
   */
  @FunctionName("CriteriaFunction")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "req",
              methods = {HttpMethod.GET},
              route = "{version}/{qualificationApplicationType}/criteria",
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      @BindingName("version") String version,
      @BindingName("qualificationApplicationType") String qualificationApplicationType,
      final ExecutionContext context) {
    CriteriaService criteriaService;

    if (version.equalsIgnoreCase("V1") && qualificationApplicationType.equals("regulated")) {
      criteriaService = RegulatedCriteriaService.getV1Instance();
    } else if (version.equalsIgnoreCase("V2") && qualificationApplicationType.equals("regulated")) {
      criteriaService = RegulatedCriteriaService.getV2Instance();
    } else if (version.equalsIgnoreCase("V2") && qualificationApplicationType.equals("selfcontained")) {
      criteriaService = SelfContainedCriteriaService.getInstance();
    } else {
      return request
          .createResponseBuilder(HttpStatus.BAD_REQUEST)
          .body(
              Errors.notAcceptableError(
                  String.format(
                      "Version %s for qualification application type %s is not supported.",
                      version, qualificationApplicationType)))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    }

    // Parse query parameter
    String contractingOperator = request.getQueryParameters().get("contractingOperator");
    ContractingOperatorEnum contractingOperatorEnum;
    try {
      contractingOperatorEnum = ContractingOperatorEnum.valueOf(contractingOperator);
    } catch (IllegalArgumentException | NullPointerException e) {
      contractingOperatorEnum = ContractingOperatorEnum.CONTRACTING_ENTITY;
    }

    try {
      return request
          .createResponseBuilder(HttpStatus.OK)
          .body(criteriaService.getCriteria(contractingOperatorEnum))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    } catch (RetrieverException e) {
      return request
          .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Errors.retrieverError(e.getMessage()))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    }
  }
}
