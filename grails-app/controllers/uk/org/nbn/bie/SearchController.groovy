package uk.org.nbn.bie

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

/**
 * A set of JSON based search web services.
 */
class SearchController extends au.org.ala.bie.SearchController {
    def bieAuthService


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
                            description = "Set the limit on the number of facets returned. Requires API key",
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
    /**
     * Override  in order to accept an flimit value for the search
     *
     * @return
     */
    @Override
    def search(){
        def checkRequest = bieAuthService.checkApiKey(request.getHeader("Authorization")?:"")
        if (!checkRequest.valid) {
            params.remove("flimit")
        }
        else{
            //The ALA do not support setting a facet limit, however NBN require it (for BBG).
            //NBN use a convention where, if a facet limit is specified in the request, it is initially added to the
            //list of facets, like below. Later, just before the query is sent to solr, the flimit is retrieved
            //(and removed from the list of facets). This is done in uk.org.nbn.bie.IndexService.query
            params.facets += ",flimit:${params.flimit}"
        }
        super.search()
    }

}