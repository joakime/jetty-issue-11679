import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.JsonRecyclerPools;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import jakarta.ws.rs.ext.ContextResolver;

import static com.fasterxml.jackson.core.JsonParser.Feature.USE_FAST_BIG_NUMBER_PARSER;
import static com.fasterxml.jackson.core.JsonParser.Feature.USE_FAST_DOUBLE_PARSER;

public class JacksonProvider implements ContextResolver<ObjectMapper> {
    public static final ObjectMapper OBJECT_MAPPER;

    static {
        JsonFactory jsonFactory = JsonFactory.builder()
            .recyclerPool(JsonRecyclerPools.sharedLockFreePool())
            .build();
        OBJECT_MAPPER = JsonMapper
            .builder(jsonFactory)
            .addModule(new Jdk8Module())
            .addModule(new JavaTimeModule())
            .addModule(new BlackbirdModule())
            .enable(USE_FAST_DOUBLE_PARSER, USE_FAST_BIG_NUMBER_PARSER)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .serializationInclusion(JsonInclude.Include.NON_ABSENT)
            .build();
    }


    @Override
    public ObjectMapper getContext(Class<?> type) {
        return OBJECT_MAPPER;
    }
}