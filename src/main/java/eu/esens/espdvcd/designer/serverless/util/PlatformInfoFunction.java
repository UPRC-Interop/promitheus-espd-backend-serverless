package eu.esens.espdvcd.designer.serverless.util;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import eu.esens.espdvcd.designer.util.AppInfo;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;

import java.util.Optional;

/** Azure Functions with HTTP Trigger. */
public class PlatformInfoFunction {
  /**
   * This function listens at endpoint "/api/PlatformInfoFunction". Two ways to invoke it using
   * "curl" command in bash: 1. curl -d "HTTP Body" {your host}/api/PlatformInfoFunction 2. curl
   * {your host}/api/PlatformInfoFunction?name=HTTP%20Query
   */
  @FunctionName("PlatformInfoFunction")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "req",
              route = "platform-info",
              methods = {HttpMethod.GET},
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      final ExecutionContext context) {

    return request
        .createResponseBuilder(HttpStatus.OK)
        .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
        .body(AppInfo.getInstance().getInfo())
        .build();
  }
}
