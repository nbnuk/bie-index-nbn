package uk.org.nbn.bie

import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.util.Encoder
import grails.transaction.Transactional
import groovy.json.JsonSlurper


class SearchService extends au.org.ala.bie.SearchService{

    /**
     * Retrieve details of a taxon by common name or scientific name
     * NBN implementation.
     * @param taxonID
     * @return
     */
    @Override
    protected def lookupTaxonByName(String taxonName, Boolean useOfflineIndex = false){
        def indexServerUrlPrefix = grailsApplication.config.indexLiveBaseUrl
        if (useOfflineIndex) {
            indexServerUrlPrefix = grailsApplication.config.indexOfflineBaseUrl
        }
        def solrServerUrl = indexServerUrlPrefix + "/select?wt=json&q=" +
                "commonNameExact:\"" + taxonName + "\" OR scientificName:\"" + taxonName + "\" OR exact_text:\"" + taxonName + "\"" + // exact_text added to handle case differences in query vs index
                (grailsApplication.config?.solr?.bq ? "&" + grailsApplication.config.solr.bq + "&defType=dismax" : "") //use boosting if provided, since the first match will be selected which is otherwise fairly random

        def queryResponse = new URL(Encoder.encodeUrl(solrServerUrl)).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs[0]
    }

    /**
     * Retrieve details of synonyms for a taxonID
     *
     * @param taxonID The taxon identifier
     * @param useOfflineIndex
     * @return
     */

    def lookupSynonyms(String taxonID, Boolean useOfflineIndex = false){
        def indexServerUrlPrefix = useOfflineIndex ? grailsApplication.config.indexOfflineBaseUrl : grailsApplication.config.indexLiveBaseUrl
        def encID = URLEncoder.encode(taxonID, 'UTF-8')

        def synonymQueryUrl = indexServerUrlPrefix + "/select?wt=json&q=" +
                "acceptedConceptID:\"" + taxonID + "\"" + "&fq=idxtype:" + IndexDocType.TAXON.name() +
                "&sort=nameComplete+ASC&rows=200"

        def queryResponse = new URL(synonymQueryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs
    }

    @Override
    def getTaxon(taxonLookup) {
        def model = super.getTaxon(taxonLookup)

        getTaxonExtra(model)

        addOccurrenceCountsToTaxonModel(model, super.lookupTaxon(model.taxonConcept.guid))

        model

    }

    /**
     * Method created really for unit testing purposes. It adds extra properties to the taxon model
     * @param model
     * @return
     */
    protected def getTaxonExtra(model) {
        //exclude entries where label is empty
        model.conservationStatuses = model.conservationStatuses.findAll{
            it.key != ""
        }

        //sort=nameComplete+ASC"
        model.synonyms.sort{
            it.nameComplete.toLowerCase()
        }

        model
    }


    protected void addOccurrenceCountsToTaxonModel(model, taxon) {
        if (!taxon) {
            return;
        }

        def docStats = [:]
        if (taxon.containsKey("occurrenceCount")) {
            docStats.put("occurrenceCount", taxon["occurrenceCount"])
        }
        def jsonSlurper = new JsonSlurper()
        def AdditionalOccStats = jsonSlurper.parseText(grailsApplication.config?.additionalOccurrenceCountsJSON ?: "[]")
        AdditionalOccStats.each { stats ->
            if (taxon.containsKey(stats.solrfield)) {
                docStats.put(stats.solrfield, taxon[stats.solrfield])
            }
        }

        model.occurrenceCounts = docStats
    }
}
