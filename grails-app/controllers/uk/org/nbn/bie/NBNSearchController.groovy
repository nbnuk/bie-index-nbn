package uk.org.nbn.bie

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.plugins.openapi.Path
import grails.config.Config
import grails.converters.JSON
import grails.core.support.GrailsConfigurationAware
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.apache.solr.common.SolrException

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

/**
 * A set of JSON based search web services.
 */
class NBNSearchController extends au.org.ala.bie.SearchController {

    /**
     * Main search across the entire index.
     *
     * @return
     */
    @Operation(
            method = "GET",
            tags = "search",
            operationId = "Search the BIE",
            summary = "Search the BIE",
            description = "Search the BIE by solr query  or free text search",
            parameters = [
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "Primary search  query for the form field:value e.g. q=rk_genus:Macropus or freee text e.g q=Macropus",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "fq",
                            in = QUERY,
                            description = "Filters to be applied to the original query. These are additional params of the form fq=INDEXEDFIELD:VALUE e.g. fq=rank:kingdom. See http://bie.ala.org.au/ws/indexFields for all the fields that a queryable.",
                            schema = @Schema(implementation = String),
                            required = false
                    ),
                    @Parameter(
                            name = "start",
                            in = QUERY,
                            description = "The records offset, to enable paging",
                            schema = @Schema(implementation = Integer),
                            required = false
                    ),
                    @Parameter(
                            name = "pageSize",
                            in = QUERY,
                            description = "The number of records to return",
                            schema = @Schema(implementation = Integer),
                            required = false
                    ),
                    @Parameter(
                            name = "sort",
                            in = QUERY,
                            description = "The field  on which to sort the records list",
                            schema = @Schema(implementation = String),
                            required = false
                    ),
                    @Parameter(
                            name = "dir",
                            in = QUERY,
                            description = "Sort direction 'asc' or 'desc'",
                            schema = @Schema(implementation = String),
                            required = false
                    ),
                    @Parameter(
                            name = "facets",
                            in = QUERY,
                            description = "Comma separated list of fields to display facets for. Available fields listed http://bie.ala.org.au/ws/indexFields.",
                            schema = @Schema(implementation = String),
                            required = false
                    ),
                    @Parameter(
                            name = "flimit",
                            in = QUERY,
                            description = "Set the limit on the number of facets returned",
                            schema = @Schema(implementation = Integer),
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )

    @Path("/internal/search{format}")
    @Produces("application/json")
    @RequireApiKey(roles = ['ROLE_ADMIN'])
    def searchInternal() {
        try {
            def facets = []
            def requestFacets = params.getList("facets")
            def locales = [request.locale, defaultLocale]
            def flimit = params.getInt("flimit")

            if (requestFacets) {
                requestFacets.each {
                    it.split(",").each { facet -> facets << facet }
                }
            }
            def results = searchService.search(params.q, params, facets, locales, flimit)
            asJson([searchResults: results])
        } catch (Exception e) {
            log.error(e.getMessage(), e)
            render(["error": e.getMessage(), indexServer: grailsApplication.config.solr.live.connection] as JSON)
        }
    }

//    This is private on super
    private def asJson = { model ->
        response.setContentType("application/json;charset=UTF-8")
        render(model as JSON)
    }
}