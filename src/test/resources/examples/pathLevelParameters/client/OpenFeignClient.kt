package examples.pathLevelParameters.client

import feign.HeaderMap
import feign.Param
import feign.RequestLine
import kotlin.String
import kotlin.Suppress
import kotlin.collections.Map

@Suppress("unused")
public interface ExampleClient {
    /**
     *
     *
     * @param a
     * @param b
     */
    @RequestLine("GET /example?a={a}&b={b}")
    public fun getExample(
        @Param("a") a: String,
        @Param("b") b: String,
        @HeaderMap additionalHeaders: Map<String, String> = emptyMap(),
    )
}
