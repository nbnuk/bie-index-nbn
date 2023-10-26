package uk.org.nbn.bie

import au.org.ala.bie.search.AutoCompleteDTO
import au.org.ala.bie.util.Encoder
import grails.converters.JSON
import groovy.json.JsonSlurper
import org.apache.solr.client.solrj.util.ClientUtils

import java.util.regex.Pattern

/**
 * Provides auto complete services.
 */
class AutoCompleteService extends au.org.ala.bie.AutoCompleteService{

    def placesAutoCompleteService;

//    @Override
    List auto(String q, List otherParams) {
        if (!otherParams[0]?.contains("REGIONFEATURED")) {
            super.autoSuggest(q, otherParams)
        }
        else {
            log.debug("calling placesAutoCompleteService")
            placesAutoCompleteService.autoSuggest(q, otherParams)
        }
    }


}