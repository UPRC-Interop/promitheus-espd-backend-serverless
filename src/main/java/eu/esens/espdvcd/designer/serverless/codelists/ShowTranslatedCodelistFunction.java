package eu.esens.espdvcd.designer.serverless.codelists;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import eu.esens.espdvcd.designer.exception.LanguageNotExistsException;
import eu.esens.espdvcd.designer.service.CodelistsService;
import eu.esens.espdvcd.designer.service.CodelistsV1Service;
import eu.esens.espdvcd.designer.service.CodelistsV2Service;
import eu.esens.espdvcd.designer.util.Errors;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;

import java.util.Optional;

/** Azure Functions with HTTP Trigger. */
public class ShowTranslatedCodelistFunction {
  /**
   * This function listens at endpoint "/api/HttpExample". Two ways to invoke it using "curl"
   * command in bash: 1. curl -d "HTTP Body" {your host}/api/HttpExample 2. curl "{your
   * host}/api/HttpExample?name=HTTP%20Query"
   */
  @FunctionName("ShowTranslatedCodelistFunction")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "req",
              route = "{version}/codelists/{codelist}/lang/{lang}",
              methods = {HttpMethod.GET},
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      @BindingName("version") String version,
      @BindingName("codelist") String codelist,
      @BindingName("lang") String lang,
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

    if ((codelist == null || codelist.isBlank()) && (lang == null || lang.isBlank())) {
      return request
          .createResponseBuilder(HttpStatus.OK)
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .body(codelistsService.getAvailableCodelists())
          .build();
    }

    if (lang == null || lang.isBlank()) {
      try {
        return request
            .createResponseBuilder(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .body(codelistsService.getCodelist(codelist))
            .build();
      } catch (IllegalArgumentException e) {
        return request
            .createResponseBuilder(HttpStatus.NOT_FOUND)
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .body(Errors.codelistNotFoundError())
            .build();
      }
    }

    try {
      return request
          .createResponseBuilder(HttpStatus.OK)
          .body(codelistsService.getTranslatedCodelist(codelist, lang))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    } catch (LanguageNotExistsException e) {
      return request
          .createResponseBuilder(HttpStatus.NOT_FOUND)
          .body(Errors.notFoundError(e.getMessage()))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    } catch (IllegalArgumentException e) {
      return request
          .createResponseBuilder(HttpStatus.BAD_REQUEST)
          .body(Errors.codelistNotFoundError())
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    } catch (UnsupportedOperationException e) {
      return request
          .createResponseBuilder(HttpStatus.BAD_REQUEST)
          .body(Errors.notAcceptableError("Translation for V1 codelists is not supported."))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    }
  }
}
