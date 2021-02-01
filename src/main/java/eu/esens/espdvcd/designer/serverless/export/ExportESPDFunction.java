package eu.esens.espdvcd.designer.serverless.export;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.typesafe.config.ConfigException;
import eu.esens.espdvcd.builder.exception.BuilderException;
import eu.esens.espdvcd.codelist.enums.EULanguageCodeEnum;
import eu.esens.espdvcd.designer.exception.ValidationException;
import eu.esens.espdvcd.designer.serverless.util.APIUtils;
import eu.esens.espdvcd.designer.service.ExportESPDService;
import eu.esens.espdvcd.designer.service.ExportESPDV1Service;
import eu.esens.espdvcd.designer.service.ExportESPDV2Service;
import eu.esens.espdvcd.designer.typeEnum.ExportType;
import eu.esens.espdvcd.designer.util.AppConfig;
import eu.esens.espdvcd.designer.util.Errors;
import eu.esens.espdvcd.designer.util.JsonUtil;
import eu.esens.espdvcd.model.ESPDRequest;
import eu.esens.espdvcd.model.ESPDRequestImpl;
import eu.esens.espdvcd.model.ESPDResponse;
import eu.esens.espdvcd.model.ESPDResponseImpl;
import eu.esens.espdvcd.schema.enums.EDMVersion;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.apache.http.protocol.HTTP;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Azure Functions with HTTP Trigger. */
public class ExportESPDFunction {
  /**
   * This function listens at endpoint "/api/ExportESPDFunction". Two ways to invoke it using
   * "curl" command in bash: 1. curl -d "HTTP Body" {your host}/api/ExportESPDFunction 2. curl
   * {your host}/api/ExportESPDFunction?name=HTTP%20Query
   */
  private static final String LOGGER_DESERIALIZATION_ERROR = "Error occurred in ESPDEndpoint while converting a JSON object to XML.";

  @FunctionName("ExportESPDFunction")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "req",
              methods = {HttpMethod.POST},
              route = "{version}/espd/{artefactType}/{exportType}",
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      @BindingName("version") String versionParam,
      @BindingName("artefactType") String artefactTypeParam,
      @BindingName("exportType") String exportTypeParam,
      final ExecutionContext context) {

    EDMVersion version;
    try {
      version = EDMVersion.valueOf(versionParam);
    } catch (IllegalArgumentException e) {
      return request
          .createResponseBuilder(HttpStatus.BAD_REQUEST)
          .body(
              Errors.standardError(
                  400, String.format("Version %s is not supported.", exportTypeParam)))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    }

    ExportESPDService service;
    switch (version) {
      case V1:
        service = ExportESPDV1Service.getInstance();
        break;
      case V2:
        service = ExportESPDV2Service.getInstance();
        break;
      default:
        throw new IllegalArgumentException("Version supplied cannot be null.");
    }

    ExportType exportType;
    InputStream streamToReturn;
    try {
      exportType = ExportType.valueOf(exportTypeParam.toUpperCase());
    } catch (IllegalArgumentException e) {
      return request
          .createResponseBuilder(HttpStatus.BAD_REQUEST)
          .body(
              Errors.standardError(
                  400, String.format("Export type %s is not supported.", exportTypeParam)))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    }

    EULanguageCodeEnum languageCode;
    try {
      languageCode =
          EULanguageCodeEnum.valueOf(request.getQueryParameters().get("language").toUpperCase());
    } catch (IllegalArgumentException | NullPointerException e) {
      return request
          .createResponseBuilder(HttpStatus.BAD_REQUEST)
          .body(Errors.standardError(400, "Language code is missing or is invalid."))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
    }

    if (request.getBody().isEmpty())
      return request
          .createResponseBuilder(HttpStatus.BAD_REQUEST)
          .body(Errors.standardError(400, "Request body must not be empty."))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();

    if (request
        .getHeaders()
        .get(HttpHeaders.CONTENT_TYPE)
        .equals(ContentType.APPLICATION_JSON.getMimeType())) {
      if (AppConfig.getInstance().isArtefactDumpingEnabled()) {
        try {
          Files.createDirectories(
              Paths.get(AppConfig.getInstance().dumpIncomingArtefactsLocation() + "/json/"));
          File dumpedFile =
              new File(
                  AppConfig.getInstance().dumpIncomingArtefactsLocation()
                      + "/json/"
                      + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMddHHmmss"))
                      + ".json");
          byte[] jsonStream = request.getBody().get().getBytes(StandardCharsets.UTF_8);
          Files.write(dumpedFile.toPath(), jsonStream, StandardOpenOption.CREATE);
          context
              .getLogger()
              .info("Dumping exported json artefact to " + dumpedFile.getAbsolutePath());
        } catch (IOException e) {
          context.getLogger().warning("Dumping of artefacts is enabled, but it failed.");
          context.getLogger().warning(e.getMessage());
        }
      }

      try {
        if (artefactTypeParam.equalsIgnoreCase("request")) {
          // Handle request
          ESPDRequest document;
          document = APIUtils.getJacksonMapper(version).readValue(request.getBody().get(), ESPDRequestImpl.class);
          streamToReturn = service.exportESPDRequestAs(document, languageCode, exportType);
        } else if (artefactTypeParam.equalsIgnoreCase("response")) {
          // Handle response
          ESPDResponse document;
          document = APIUtils.getJacksonMapper(version).readValue(request.getBody().get(), ESPDResponseImpl.class);
          streamToReturn = service.exportESPDResponseAs(document, languageCode, exportType);
        } else {
          return request
                  .createResponseBuilder(HttpStatus.BAD_REQUEST)
                  .body(Errors.standardError(400, "Document type (request or response) must be specified."))
                  .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                  .build();
        }
        return request
                .createResponseBuilder(HttpStatus.OK)
                .body(streamToReturn)
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_OCTET_STREAM.getMimeType())
                .header(
                        "Content-Disposition",
                        String.format(
                                "attachment; filename=\"%s.%s\";",
                                artefactTypeParam.toLowerCase(), exportType.name().toLowerCase()))
                .build();
      } catch (IOException e) {

                          return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(Errors.standardError(400, LOGGER_DESERIALIZATION_ERROR + e.getMessage()))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .build();

      } catch (ValidationException e) {
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(Errors.validationError(e.getMessage(), e.getResults()))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .build();
      } catch (UnsupportedOperationException e) {
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(Errors.notAcceptableError(e.getMessage()))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .build();
      } catch (BuilderException | JAXBException | SAXException | ConfigException ex) {
        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Errors.standardError(500, ex.getMessage()))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .build();
      }
    } else {
      context.getLogger().warning("Got unexpected content-type: " + request.getHeaders().get(HttpHeaders.CONTENT_TYPE));
      return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
              .body(Errors.unacceptableContentType())
              .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
              .build();
    }
  }
}
