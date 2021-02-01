package eu.esens.espdvcd.designer.serverless.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.esens.espdvcd.designer.deserialiser.RequirementDeserialiser;
import eu.esens.espdvcd.designer.util.AppConfig;
import eu.esens.espdvcd.model.requirement.Requirement;
import eu.esens.espdvcd.schema.enums.EDMVersion;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class APIUtils {

    public static ObjectMapper getJacksonMapper(EDMVersion espdVersion){
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new SimpleModule().addDeserializer(Requirement.class, new RequirementDeserialiser(espdVersion)))
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT);

    }

    public static void writeDumpedFile(File espdFile) throws IOException {
        if (AppConfig.getInstance().isArtefactDumpingEnabled()) {
            Files.createDirectories(Paths.get(AppConfig.getInstance().dumpIncomingArtefactsLocation() + "/xml/"));
            File dumpedFile;
            try {
                dumpedFile = new File(AppConfig.getInstance().dumpIncomingArtefactsLocation()
                        + "/xml/" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss")) + ".xml");
                Files.copy(espdFile.toPath(), dumpedFile.toPath());
            } catch (FileAlreadyExistsException e) {
                dumpedFile = new File(AppConfig.getInstance().dumpIncomingArtefactsLocation()
                        + "/xml/" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-"))
                        + RandomStringUtils.randomAlphabetic(3) + ".xml");
                Files.copy(espdFile.toPath(), dumpedFile.toPath());
            }
        }
    }

}
