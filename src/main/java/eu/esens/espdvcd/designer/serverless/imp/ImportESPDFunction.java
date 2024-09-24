package eu.esens.espdvcd.designer.serverless.imp;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import eu.esens.espdvcd.builder.exception.BuilderException;
import eu.esens.espdvcd.designer.exception.ValidationException;
import eu.esens.espdvcd.designer.serverless.util.APIUtils;
import eu.esens.espdvcd.designer.service.ImportESPDRequestService;
import eu.esens.espdvcd.designer.service.ImportESPDResponseService;
import eu.esens.espdvcd.designer.service.ImportESPDService;
import eu.esens.espdvcd.designer.util.Errors;
import eu.esens.espdvcd.designer.util.JsonUtil;
import eu.esens.espdvcd.retriever.exception.RetrieverException;
import eu.esens.espdvcd.codelist.enums.internal.ContractingOperatorEnum;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Azure Functions with HTTP Trigger. */
public class ImportESPDFunction {
  static final String LOGGER_DOCUMENT_ERROR =
      "Error occurred in ESPDEndpoint while converting an XML response to an object. ";

  /**
   * This function listens at endpoint "/api/ImportESPDFunction". Two ways to invoke it using "curl"
   * command in bash: 1. curl -d "HTTP Body" {your host}/api/ImportESPDFunction 2. curl {your
   * host}/api/ImportESPDFunction?name=HTTP%20Query
   */
  @FunctionName("ImportESPDFunction")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "req",
              methods = {HttpMethod.POST},
              route = "importESPD/{artefactType}",
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      @BindingName("artefactType") String artefactTypeParam,
      final ExecutionContext context) {

    ImportESPDService service;
    switch (artefactTypeParam) {
      case "request":
        service = ImportESPDRequestService.getInstance();
        break;
      case "response":
        service = ImportESPDResponseService.getInstance();
        break;
      default:
        return request
            .createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(
                Errors.standardError(
                    400,
                    String.format(
                        "Artefact type must be request or response. \"%s\" is not supported.",
                        artefactTypeParam)))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .build();
    }

    if (request.getBody().isEmpty())
      return request
          .createResponseBuilder(HttpStatus.BAD_REQUEST)
          .body(Errors.standardError(400, "Request body must not be empty."))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();

    // Parse query parameter
    String contractingOperator = request.getQueryParameters().get("contractingOperator");
    ContractingOperatorEnum contractingOperatorEnum;
    try {
      contractingOperatorEnum = ContractingOperatorEnum.valueOf(contractingOperator);
    } catch (IllegalArgumentException | NullPointerException e) {
      contractingOperatorEnum = ContractingOperatorEnum.CONTRACTING_ENTITY;
    }

    if (request
        .getHeaders()
        .get(HttpHeaders.CONTENT_TYPE.toLowerCase())
        .contains(ContentType.MULTIPART_FORM_DATA.getMimeType())) {
      String contentType = request.getHeaders().get("content-type"); // Get content-type header
      // here the "content-type" must be lower-case
      String body = request.getBody().get(); // Get request body
      InputStream in = new ByteArrayInputStream(body.getBytes()); // Convert body to an input stream
      String boundary =
          contentType.split(";")[1].split("=")[1]; // Get boundary from content-type header
      int bufSize = 2048;
      MultipartStream multipartStream =
          new MultipartStream(
              in,
              boundary.getBytes(),
              bufSize,
              null); // Using MultipartStream to parse body input stream
      try {
        boolean nextPart = multipartStream.skipPreamble();
        if (nextPart) {
          if (!multipartStream.readHeaders().toLowerCase().contains("xml"))
            return request
                .createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(Errors.standardError(400, "Please provide an XML file."))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .build();
          File tempFile = File.createTempFile("espd-file", ".tmp");
          tempFile.deleteOnExit();
          FileOutputStream fos = new FileOutputStream(tempFile);
          multipartStream.readBodyData(fos);
          APIUtils.writeDumpedFile(tempFile);

          try {
            return request
                .createResponseBuilder(HttpStatus.OK)
                .body(JsonUtil.toJson(service.importESPDFile(tempFile, contractingOperatorEnum)))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .build();
          } catch (RetrieverException
              | BuilderException
              | NullPointerException
              | JAXBException
              | SAXException
              | IllegalStateException e) {
            return request
                .createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(Errors.notAcceptableError(e.getMessage()))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .build();
          } catch (ValidationException e) {
            return request
                .createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(Errors.validationError(e.getMessage(), e.getResults()))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .build();
          }
        } else {
          return request
              .createResponseBuilder(HttpStatus.BAD_REQUEST)
              .body(
                  Errors.standardError(
                      400, "There was no file found in your upload, please check your input."))
              .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
              .build();
        }
      } catch (IOException e) {
        return request
            .createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(
                Errors.standardError(400, "Request could not be parsed. Reason: " + e.getMessage()))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .build();
      }
    } else if (request
        .getHeaders()
        .get(HttpHeaders.CONTENT_TYPE.toLowerCase())
        .contains(ContentType.APPLICATION_XML.getMimeType())) {
      try {
        Path tempFile = Files.createTempFile("espd-file", ".tmp");
        byte[] xmlStream = request.getBody().get().getBytes(StandardCharsets.UTF_8);
        Files.write(tempFile, xmlStream, StandardOpenOption.CREATE);
        File espdFile = tempFile.toFile();
        APIUtils.writeDumpedFile(espdFile);

        return request
            .createResponseBuilder(HttpStatus.OK)
            .body(JsonUtil.toJson(service.importESPDFile(espdFile, contractingOperatorEnum)))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .build();
      } catch (IllegalStateException
          | IOException
          | JAXBException
          | SAXException
          | RetrieverException
          | BuilderException e) {
        return request
            .createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(Errors.notAcceptableError(e.getMessage()))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .build();
      } catch (ValidationException e) {
        return request
            .createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(Errors.validationError(e.getMessage(), e.getResults()))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .build();
      }
    } else
      return request
          .createResponseBuilder(HttpStatus.BAD_REQUEST)
          .body(
              Errors.standardError(
                  400,
                  String.format(
                      "Can't handle content type %s",
                      request.getHeaders().get(HttpHeaders.CONTENT_TYPE))))
          .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
          .build();
  }
}
