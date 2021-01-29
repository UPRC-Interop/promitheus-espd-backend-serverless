package eu.esens.espdvcd.designer.serverless.codelists;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import eu.esens.espdvcd.designer.service.CodelistsService;
import eu.esens.espdvcd.designer.service.CodelistsV1Service;
import eu.esens.espdvcd.designer.service.CodelistsV2Service;
import eu.esens.espdvcd.designer.util.Errors;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;

import java.util.Optional;

/** Azure Functions with HTTP Trigger. */
public class ShowAvailableCodelistsFunction {
  /**
   * This function listens at endpoint "/api/ShowAvailableCodelistsFunction". Two ways to invoke it
   * using "curl" command in bash: 1. curl -d "HTTP Body" {your
   * host}/api/ShowAvailableCodelistsFunction 2. curl {your
   * host}/api/ShowAvailableCodelistsFunction?name=HTTP%20Query
   */
  @FunctionName("ShowAvailableCodelistsFunction")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "req",
              route = "{version}/codelists",
              methods = {HttpMethod.GET},
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      @BindingName("version") String version,
      final ExecutionContext context) {
    CodelistsService codelistsService;
    switch (version.toUpperCase()) {
      case "V2":
        codelistsService = CodelistsV2Service.getInstance();
        break;
      case "V1":
        codelistsService = CodelistsV1Service.getInstance();
        break;
      default:
        return request
            .createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(Errors.notAcceptableError(String.format("Version %s is not supported.", version)))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .build();
    }

    return request
        .createResponseBuilder(HttpStatus.OK)
        .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
        .body(codelistsService.getAvailableCodelists())
        .build();
  }
}
