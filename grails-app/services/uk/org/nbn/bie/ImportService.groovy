package uk.org.nbn.bie

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.util.Encoder
import au.org.ala.vocab.ALATerm
import grails.async.PromiseList
import grails.converters.JSON
import groovy.json.JsonSlurper
import org.apache.commons.io.IOUtils
import org.apache.solr.common.params.MapSolrParams
import org.apache.commons.lang.StringEscapeUtils
import org.gbif.dwc.terms.DwcTerm
import org.gbif.dwca.record.Record
import org.grails.web.json.JSONObject

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

class ImportService extends au.org.ala.bie.ImportService{

    /**
     * Removes field values from all records in index
     * @param fld
     * @throws Exception
     */
    def clearFieldValues(String fld) throws Exception {
        try {
            clearFieldValues(fld, "", false)
        } catch (Exception ex) {
            log.warn "Error clearing occurrenceCounts: ${ex.message}", ex
        }
    }
    /**
     * Removes field values from records in index matching the provided fq
     * @param fld
     * @param fq
     * @throws Exception
     */
    def clearFieldValues(String fld, String fq, Boolean online) throws Exception {
        int page = 1
        int pageSize = 1000
        def js = new JsonSlurper()
        def baseUrl = online ? grailsApplication.config.indexLiveBaseUrl : grailsApplication.config.indexOfflineBaseUrl

        try {
            while (true) {
                def solrServerUrl = baseUrl + "/select?wt=json&q=*:*&fq=" + fld + ":[*+TO+*]&start=0&rows=" + pageSize //note, always start at 0 since getting rid of all values
                if (fq != "") solrServerUrl = solrServerUrl + "&fq=" + fq
                log.info("SOLR clear field URL: " + solrServerUrl)
                def queryResponse = Encoder.encodeUrl(solrServerUrl).toURL().getText("UTF-8")
                def json = js.parseText(queryResponse)
                int total = json.response.numFound
                def docs = json.response.docs
                def buffer = []

                if (docs.isEmpty())
                    break
                docs.each { doc ->
                    def update = [:]
                    Map<String, String> partialUpdateNull = new HashMap<String, String>();
                    partialUpdateNull.put("set", null);
                    update["id"] = doc.id // doc key
                    update["idxtype"] = ["set": doc.idxtype] // required field
                    update["guid"] = ["set": doc.guid] // required field
                    update[fld] = partialUpdateNull
                    buffer << update
                }
                if (!buffer.isEmpty()) {
                    log.info("Committing cleared fields to SOLR: #" + page.toString() + " set of " + pageSize.toString() + " records")
                    indexService.indexBatch(buffer, online)
                }
                page++
            }
        } catch (Exception ex) {
            log.error("Unable to clear field " + fld + " values ", ex)
            log("Error: " + ex.getMessage())
        }
    }

    def importFeaturedRegions() {
        log ("Starting featured regions import "+grailsApplication.config.regionFeaturedLayerIds)
        String[] regionFeaturedIds
        if(grailsApplication.config.regionFeaturedLayerIds) {
            regionFeaturedIds = grailsApplication.config.regionFeaturedLayerIds.split(',')
            log("Featured regions = " + regionFeaturedIds.toString())
        }

        def layers = getLayers()

        layers.each { layer ->
            if (layer.type == "Contextual" && layer.enabled.toBoolean() && isFeaturedRegionLayer(layer)) {
                importFeaturedRegionLayer(layer)
            }
        }
        log "Finished featured regions import"
    }

    protected getLayers() {
        def js = new JsonSlurper()
        js.parseText(new URL(Encoder.encodeUrl(grailsApplication.config.layersServicesUrl + "/layers")).getText("UTF-8"))
    }

    @Override
    def importRegions() {
        log "Starting regions import"
        def js = new JsonSlurper()
        def layers = js.parseText(new URL(Encoder.encodeUrl(grailsApplication.config.layersServicesUrl + "/layers")).getText("UTF-8"))
        indexService.deleteFromIndex(IndexDocType.REGION)
        layers.each { layer ->
            if (layer.type == "Contextual"  && layer.enabled.toBoolean()) {
                importLayer(layer)
            }
        }
        log"Finished indexing ${layers.size()} region layers"
        log "Finished regions import"

    }

    @Override
    protected def importLayer(layer) {
        //there are layers that are disabled that need to be imported, layer 17 (OS gazateer layer for example (unsed in importLocalities).
        // NBN introduced disable layer only for importRegions. It doesnt seem right way to do it, but need to leave as is for now
//        if (!layer.enabled.toBoolean()) {
//            return false;
//        }

        super.importLayer(layer);
        return true;
    }

    protected def importFeaturedRegionLayer(layer) {
        if (!isFeaturedRegionLayer(layer))
            return false;
        log("Loading featured regions from layer " + layer.name + " (" + layer.id + ")")

        def keywords = []

        if (grailsApplication.config.localityKeywordsUrl) {
            keywords = this.getConfigFile(grailsApplication.config.localityKeywordsUrl)
        }

        def tempFilePath = "/tmp/objects_${layer.id}.csv.gz"
        def url = grailsApplication.config.layersServicesUrl + "/objects/csv/cl" + layer.id
        def file = new File(tempFilePath).newOutputStream()
        file << new URL(Encoder.encodeUrl(url)).openStream()
        file.flush()
        file.close()

        //START featuredRegionLayer (BBG) specific
        def featuredDynamicFields = [:]

        if (grailsApplication.config.regionFeaturedLayerFields) {
            //get additional dynamic fields to store
            def workspaceLayer = "ALA:" + layer.name
            def idField = 'ALA:' + grailsApplication.config.regionFeaturedLayerIDfield
            def urlAttribs = grailsApplication.config.geoserverUrl + "/wfs?request=GetFeature&version=1.0.0&service=wfs&typeName=" + workspaceLayer + "&propertyname=" + grailsApplication.config.regionFeaturedLayerFields
            def tempFileAttribsPath = "/tmp/attribs_${layer.id}.xml"
            def fileAttribs = new File(tempFileAttribsPath).newOutputStream()
            fileAttribs << new URL(Encoder.encodeUrl(urlAttribs)).openStream()
            fileAttribs.flush()
            fileAttribs.close()
            if (new File(tempFileAttribsPath).exists() && new File(tempFileAttribsPath).length() > 0) {
                def xmlDoc = new XmlParser().parse(tempFileAttribsPath)
                for (fm in xmlDoc.'gml:featureMember') {
                    def idValue = fm.("ALA:" + layer.name).(idField.toString()).text()
                    def faMap = [:]
                    for (fa in fm.(workspaceLayer.toString())[0].children()) {
                        def attrName = fa.name().localPart
                        def attrVal = fa.text()
                        faMap.put(attrName, attrVal)
                    }
                    featuredDynamicFields.put(idValue, faMap)
                }

                //xmlDoc.value()[1]
                //xmlDoc.'gml:featureMember'[0].'ALA:London'.'ALA:gid'.text()


            }
        }
        //END featuredRegionLayer (BBG) specific

        if (new File(tempFilePath).exists() && new File(tempFilePath).length() > 0) {

            def gzipInput = new GZIPInputStream(new FileInputStream(tempFilePath))

            //read file and index
            def csvReader = new CSVReader(new InputStreamReader(gzipInput))

            def expectedHeaders = ["pid", "id", "name", "description", "centroid", "featuretype"]

            def headers = csvReader.readNext()
            def currentLine = []
            def batch = []
            while ((currentLine = csvReader.readNext()) != null) {

                if (currentLine.length >= expectedHeaders.size()) {

                    def doc = [:]
                    doc["id"] = currentLine[0]
                    doc["guid"] = currentLine[0]

                    if (currentLine[5] == "POINT") {
                        doc["idxtype"] = IndexDocType.LOCALITY.name()
                    } else {
                        doc["idxtype"] = IndexDocType.REGION.name()
                    }

                    doc["name"] = currentLine[2]

                    if (currentLine[3] && currentLine[2] != currentLine[3]) {
                        doc["description"] = currentLine[3]
                    } else {
                        doc["description"] = layer.displayname
                    }

                    doc["centroid"] = currentLine[4]


                    doc["distribution"] = "N/A"

                    keywords.each {
                        if(doc["description"].contains(it)){
                            doc["distribution"] = it
                        }
                    }

                    //batch << doc //this doc is created in importRegions -> importLayer (although it seems to get deleted at the beginning of importLocalities)

                    //START featuredRegionLayer (BBG) specific
                    def doc2 = doc.findAll {it.key != "idxtype"}
                    doc2["idxtype"] = "REGIONFEATURED"
                    def shp_idValue = currentLine[1]
                    if (featuredDynamicFields.containsKey(shp_idValue)) {
                        //find shp_idfield in xml FIELDSSID (="BBG_UNIQUE" for our example)
                        def shpAttrs = featuredDynamicFields.get(shp_idValue)
                        for (attr in shpAttrs.keySet()) {
                            //add attr key to doc2[] with value attr.value
                            doc2[attr + '_s'] = shpAttrs.get(attr)
                        }
                        def centroid = doc['centroid']?:'' //centroid will be something like POINT(-2.24837969557765 53.5201084106602)
                        if (centroid) {
                            def vals = centroid.findAll( /-?\d+\.\d*|-?\d*\.\d+|-?\d+/ )*.toDouble()
                            if (vals.size() == 2) {
                                doc2['longitude'] = vals[0]
                                doc2['latitude'] = vals[1]
                                doc2['point-0.0001'] = vals[1].round(4).toString() + ',' + vals[0].round(4).toString()
                            }
                        }
                    }
                    batch << doc2
                    //END featuredRegionLayer (BBG) specific

                    if (batch.size() > 10000) {
                        indexService.indexBatch(batch)
                        batch.clear()
                    }
                }
            }
            if (batch) {
                indexService.indexBatch(batch)
                batch.clear()
            }
        }
        return true;
    }



    protected def isFeaturedRegionLayer(layer) {
        String[] regionFeaturedIds = grailsApplication.config.regionFeaturedLayerIds?
                grailsApplication.config.regionFeaturedLayerIds.split(','):[];
        return regionFeaturedIds.contains(layer.id.toString())
    }


    /**
     *
     * @param online
     * @param forRegionFeatured
     * @return
     * @throws Exception
     */
    def importSpeciesCounts(Boolean online = false) throws Exception {
        def pageSize = 1000
        def paramsMap = [
                q: "idxtype:REGIONFEATURED",
                cursorMark: "*", // gets updated by subsequent searches
                fl: "id,idxtype,guid,bbg_name_s", // will restrict results to docs with these fields (bit like fq)
                rows: pageSize,
                sort: "id asc", // needed for cursor searching
                wt: "json"
        ]

        try {
            clearFieldValues("speciesCount", "idxtype:REGIONFEATURED", online)
        } catch (Exception ex) {
            log.warn "Error clearing speciesCounts: ${ex.message}", ex
        }


        // first get a count of results so we can determine number of pages to process
        Map countMap = paramsMap.clone(); // shallow clone is OK
        countMap.rows = 0
        countMap.remove("cursorMark")
        def searchCount = searchService.getCursorSearchResults(new MapSolrParams(countMap), !online) // could throw exception
        def totalDocs = searchCount?.response?.numFound?:0
        int totalPages = (totalDocs + pageSize - 1) / pageSize
        log.debug "Featured Region - totalDocs = ${totalDocs} || totalPages = ${totalPages}"
        log("Processing " + String.format("%,d", totalDocs) + " places (via ${paramsMap.q})...<br>")
        // send to browser

        def promiseList = new PromiseList() // for biocache queries
        Queue commitQueue = new ConcurrentLinkedQueue()  // queue to put docs to be indexes
        ExecutorService executor = Executors.newSingleThreadExecutor() // consumer of queue - single blocking thread
        executor.execute {
            indexDocInQueue(commitQueue, "initialised", online) // will keep polling the queue until terminated via cancel()
        }

        // iterate over pages
        (1..totalPages).each { page ->
            try {
                MapSolrParams solrParams = new MapSolrParams(paramsMap)
                log.debug "${page}. paramsMap = ${paramsMap}"
                def searchResults = searchService.getCursorSearchResults(solrParams, !online) // use offline or online index to search
                def resultsDocs = searchResults?.response?.docs?:[]


                // buckets to group results into
                def placesToSearchSpecies = []

                // iterate over the result set
                resultsDocs.each { doc ->
                    placesToSearchSpecies.add(doc)
                }
                promiseList << { searchSpeciesCountsForPlaces(resultsDocs, commitQueue) }
                log("${page}. placesToSearchSpecies = ${placesToSearchSpecies.size()}")


                // update cursor
                paramsMap.cursorMark = searchResults?.nextCursorMark?:""
                // update view via via JS
                updateProgressBar(totalPages, page)

            } catch (Exception ex) {
                log.warn "Error calling BIE SOLR: ${ex.message}", ex
                log("ERROR calling SOLR: ${ex.message}")
            }
        }

        log("Waiting for all species searches and SOLR commits to finish (could take some time)")

        //promiseList.get() // block until all promises are complete
        promiseList.onComplete { List results ->
            //executor.shutdownNow()
            isKeepIndexing = false // stop indexing thread
            executor.shutdown()
            log("Total places found with species counts = ${results.sum()}")
            log("waiting for indexing to finish...")
        }
    }

    def updatePlacesWithLocationInfo(List docs, Queue commitQueue) {
        def totalDocumentsUpdated = 0

        docs.each { Map doc ->
            if (doc.containsKey("id") && doc.containsKey("idxtype")) {
                Map updateDoc = [:]
                updateDoc["id"] = doc.id // doc key
                updateDoc["idxtype"] = ["set": doc.idxtype] // required field
                updateDoc["guid"] = ["set": doc.guid] // required field
                if(doc.containsKey("occurrenceCount")){
                    updateDoc["occurrenceCount"] = ["set": doc["occurrenceCount"]]
                }
                commitQueue.offer(updateDoc) // throw it on the queue
                totalDocumentsUpdated++
            } else {
                log.warn "Updating doc error: missing keys ${doc}"
            }
        }

        totalDocumentsUpdated
    }

    /**
     * TODO this method is for BBG and will be moved to an NBN extension to bie-index
     * @param docs
     * @param commitQueue
     * @return
     */
    def updatePlacesWithSpeciesCount(List docs, Queue commitQueue) {
        def totalDocumentsUpdated = 0

        docs.each { Map doc ->
            if (doc.containsKey("id") && doc.containsKey("idxtype") && doc.containsKey("speciesCount")) {
                Map updateDoc = [:]
                updateDoc["id"] = doc.id // doc key
                updateDoc["idxtype"] = ["set": doc.idxtype] // required field
                updateDoc["guid"] = ["set": doc.guid] // required field
                updateDoc["speciesCount"] = ["set": doc["speciesCount"]]
                commitQueue.offer(updateDoc) // throw it on the queue
                totalDocumentsUpdated++
            } else {
                log.warn "Updating doc error: missing keys ${doc}"
            }
        }

        totalDocumentsUpdated
    }

    def searchOccurrencesWithSampledPlace(List docs, Queue commitQueue) {
        int batchSize = 25 // even with POST SOLR throws 400 code if batchSize is more than 100
        def clField = "cl" + grailsApplication.config.regionFeaturedLayerIds
        def sampledField = grailsApplication.config.regionFeaturedLayerSampledField + '_s'
        List place_names = docs.collect { it.bbg_name_s } //TODO:
        int totalPages = ((place_names.size() + batchSize - 1) / batchSize) -1
        log.debug "total = ${place_names.size()} || batchSize = ${batchSize} || totalPages = ${totalPages}"
        List docsWithRecs = [] // docs to index
        //log("Getting occurrence data for ${docs.size()} docs")

        (0..totalPages).each { index ->
            int start = index * batchSize
            int end = (start + batchSize < place_names.size()) ? start + batchSize : place_names.size()
            log "paging place biocache search - ${start} to ${end-1}"
            def placeSubset = place_names.subList(start,end)
            //URIUtil.encodeWithinQuery(place).replaceAll("%26","&").replaceAll("%3D","=").replaceAll("%3A",":")
            def placeParamList = placeSubset.collect { String place -> '"' + place + '"' } // URL encode place names
            def query = clField + ":" + placeParamList.join("+OR+" + clField + ":")


            try {
                // def json = searchService.doPostWithParamsExc(grailsApplication.config.biocache.solr.url +  "/select", postBody)
                // log.debug "results = ${json?.resp?.response?.numFound}"
                def url = grailsApplication.config.biocache.solr.url + "/select?q=${query}"

                def url_clean = Encoder.encodeUrl(url)
                        .replaceAll("&amp;","&")
                        .replaceAll("&","%26")
                        .replaceAll("%3D","=")
                        .replaceAll("%3A",":")
                        .replaceAll("'","%27")
                url_clean = url_clean + "&wt=json&indent=true&rows=0&facet=true&facet.field=" + clField + "&facet.mincount=1"

                def queryResponse = new URL(url_clean).getText("UTF-8")
                JSONObject jsonObj = JSON.parse(queryResponse)

                if (jsonObj.containsKey("facet_counts")) {

                    def facetCounts = jsonObj?.facet_counts?.facet_fields.get(clField)
                    facetCounts.eachWithIndex { val, idx ->
                        // facets results are a list with key, value, key, value, etc
                        if (idx % 2 == 0) {
                            def docWithRecs = docs.find { it.bbg_name_s == val }
                            docWithRecs["occurrenceCount"] = facetCounts[idx + 1] //add the count
                            if(docWithRecs){
                                docsWithRecs.add(docWithRecs )
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn "Error calling biocache SOLR: ${ex.message}", ex
                log("ERROR calling biocache SOLR: ${ex.message}")
            }
        }

        if (docsWithRecs.size() > 0) {
            log.debug "docsWithRecs size = ${docsWithRecs.size()} vs docs size = ${docs.size()}"
            updatePlacesWithLocationInfo(docsWithRecs, commitQueue)
        }

    }


/**
 *
 * @param docs
 * @param commitQueue
 * @return
 */
    def searchSpeciesCountsForPlaces(List docs, Queue commitQueue) {
        int batchSize = 25 // even with POST SOLR throws 400 code if batchSize is more than 100
        def clField = "cl" + grailsApplication.config.regionFeaturedLayerIds
        def sampledField = grailsApplication.config.regionFeaturedLayerSampledField + '_s'
        List place_names = docs.collect { it.bbg_name_s } //TODO:
        int totalPages = ((place_names.size() + batchSize - 1) / batchSize) -1
        log.debug "total = ${place_names.size()} || batchSize = ${batchSize} || totalPages = ${totalPages}"
        List docsWithRecs = [] // docs to index
        //log("Getting occurrence data for ${docs.size()} docs")

        (0..totalPages).each { index ->
            int start = index * batchSize
            int end = (start + batchSize < place_names.size()) ? start + batchSize : place_names.size()
            log "paging place biocache search - ${start} to ${end-1}"
            def placeSubset = place_names.subList(start,end)
            //URIUtil.encodeWithinQuery(place).replaceAll("%26","&").replaceAll("%3D","=").replaceAll("%3A",":")
            def placeParamList = placeSubset.collect { String place -> '"' + place + '"' } // URL encode place names
            def query = clField + ":" + placeParamList.join("+OR+" + clField + ":")


            try {
                // def json = searchService.doPostWithParamsExc(grailsApplication.config.biocache.solr.url +  "/select", postBody)
                // log.debug "results = ${json?.resp?.response?.numFound}"
                def url = grailsApplication.config.biocache.solr.url + "/select?q=${query}"

                def url_clean = Encoder.encodeUrl(url)
                        .replaceAll("&amp;","&")
                        .replaceAll("&","%26")
                        .replaceAll("%3D","=")
                        .replaceAll("%3A",":")
                        .replaceAll("'","%27")
                url_clean = url_clean + "&wt=json&indent=true&rows=0&facet=true&facet.pivot=lsid," + clField + "&facet.mincount=1&facet.limit=-1"

                def queryResponse = new URL(url_clean).getText("UTF-8")
                JSONObject jsonObj = JSON.parse(queryResponse)

                if (jsonObj.containsKey("facet_counts")) {

                    def facetPivots = jsonObj?.facet_counts?.facet_pivot.get("lsid,"+clField)

                    facetPivots.each { facetPivot ->
                        facetPivot.pivot.each { pivot ->
                            def docWithRecs = docs.find { it.bbg_name_s == pivot.value }
                            if (docWithRecs) {
                                if (docWithRecs["speciesCount"]) {
                                    docWithRecs["speciesCount"] ++
                                } else {
                                    docWithRecs["speciesCount"] = 1
                                }
                                if (!docsWithRecs.find {it.id == docWithRecs.id }) {
                                    docsWithRecs.add(docWithRecs)
                                }

                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn "Error calling biocache SOLR: ${ex.message}", ex
                log("ERROR calling biocache SOLR: ${ex.message}")
            }
        }

        if (docsWithRecs.size() > 0) {
            log.debug "docsWithRecs size = ${docsWithRecs.size()} vs docs size = ${docs.size()}"
            updatePlacesWithSpeciesCount(docsWithRecs, commitQueue)
        }

    }

    @Override
    def buildTaxonRecord(Record record, Map doc, Map attributionMap, Map datasetMap, Map taxonRanks, String defaultTaxonomicStatus, String defaultDatasetName) {

        super.buildTaxonRecord(record, doc, attributionMap, datasetMap, taxonRanks, defaultTaxonomicStatus, defaultDatasetName)
        def nomenclaturalStatus = record.value(DwcTerm.nomenclaturalStatus)
        def nameComplete = record.value(ALATerm.nameComplete)
        def nameFormatted = record.value(ALATerm.nameFormatted)
        def scientificName = record.value(DwcTerm.scientificName)
        def scientificNameAuthorship = record.value(DwcTerm.scientificNameAuthorship)
        def taxonRank = doc["rank"]
        doc["nameComplete"] = buildNameComplete(nameComplete, scientificName, scientificNameAuthorship, nomenclaturalStatus)
        doc["nameFormatted"] = buildNameFormatted(nameFormatted, nameComplete, scientificName, scientificNameAuthorship, taxonRank, taxonRanks, nomenclaturalStatus)

    }

    /**
     * Build a complete name + author
     * <p>
     * Some names are funny. So if there is a name supplied used that.
     * Otherwise try to build the name from scientific name + authorship
     *
     * @param nameComplete The supplied complete name, if available
     * @param scientificName The scientific name
     * @param scientificNameAuthorship The authorship
     * @return
     */
    String buildNameComplete(String nameComplete, String scientificName, String scientificNameAuthorship, String nomenclaturalStatus = "") {
        String name = super.buildNameComplete(nameComplete, scientificName, scientificNameAuthorship)
        if (nameComplete || !nomenclaturalStatus) {
            return name
        }
        else {
            return name + " " + nomenclaturalStatus
        }
    }

    /**
     * Build an HTML formatted name
     * <p>
     * If a properly formatted name is supplied, then use that.
     * Otherwise, try yo build the name from the supplied information.
     * The HTMLised name is escaped and uses spans to encode formatting information.
     *
     *
     * @param nameFormatted The formatted name, if available
     * @param nameComplete The complete name, if available
     * @param scientificName The scientific name
     * @param scientificNameAuthorship The name authorship
     * @param rank The taxon rank
     * @param rankMap The lookup table for ranks
     *
     * @return The formatted name
     */
    String buildNameFormatted(String nameFormatted, String nameComplete, String scientificName,
                              String scientificNameAuthorship, String rank, Map rankMap, String nomenclaturalStatus = "") {
        String name = super.buildNameFormatted(nameFormatted, nameComplete, scientificName, scientificNameAuthorship, rank, rankMap)
        if (nomenclaturalStatus && !(nameFormatted || nameComplete)) {
            if (scientificNameAuthorship) {
                int i = name.lastIndexOf("</span></span>")
                name = name.replaceAll(/<\/span><\/span>$/, " ${StringEscapeUtils.escapeHtml(nomenclaturalStatus)}</span></span>")
            } else {
                int i = name.lastIndexOf("</span></span>")
                name = name.replaceAll(/<\/span><\/span>$/, "</span> <span class=\"author\">${StringEscapeUtils.escapeHtml(nomenclaturalStatus)}</span></span>")
            }
        }


        return name
    }

    /**
     * This is copied from ALA ImportService. It is not in the upgrade but it is needed
     * for BBG's importSpeciesCounts (in this class)
     * Poll the queue of docs and index in batches
     *
     * @param updateDocs
     * @return
     */
    @Override
    def indexDocInQueue(Queue updateDocs, msg, Boolean online = false) {
        int batchSize = 1000

        while (isKeepIndexing || updateDocs.size() > 0) {
            if (updateDocs.size() > 0) {
                log.info "Starting indexing of ${updateDocs.size()} docs"
                try {
                    // batch index docs
                    List batchDocs = []
                    int end = (batchSize < updateDocs.size()) ? batchSize : updateDocs.size()

                    (1..end).each {
                        if (updateDocs.peek()) {
                            batchDocs.add(updateDocs.poll())
                        }
                    }

                    indexService.indexBatch(batchDocs, online) // index
                } catch (Exception ex) {
                    log.warn "Error batch indexing: ${ex.message}", ex
                    log.warn "updateDocs = ${updateDocs}"
                    log("ERROR batch indexing: ${ex.message} <br><code>${ex.stackTrace}</code>")
                }
            } else {
                sleep(500)
            }
        }

        log("Indexing thread is done: ${msg}")
    }


        //This is legacy (pre FFTF) importOccurrenceData. It was customised for BBG. Before FFTF, BBG stopped
    //requiring this method. If they want it back again, then either uncomment this out (may need a little
    //work and retrieval of the method clearFieldValues obtained from the legacy fork) or reimplement using the new
    //ALA version as an example
    def importOccurrenceDataForPlaces(Boolean online = false, Boolean forRegionFeatured = true) throws Exception {
        log("Operation not supported...")
        return
//        String nationalSpeciesDatasets = grailsApplication.config.nationalSpeciesDatasets // comma separated String
//        def pageSize = 10000
//        def paramsMap = [
//                q         : "taxonomicStatus:accepted", // "taxonomicStatus:accepted",
//                //fq: "datasetID:dr2699", // testing only with AFD
//                cursorMark: "*", // gets updated by subsequent searches
//                fl        : "id,idxtype,guid,scientificName,datasetID", // will restrict results to docs with these fields (bit like fq)
//                rows      : pageSize,
//                sort      : "id asc", // needed for cursor searching
//                wt        : "json"
//        ]
//        if (forRegionFeatured) {
//            pageSize = 1000
//            def sampledField = grailsApplication.config.regionFeaturedLayerSampledField + '_s'
//            //TODO set fl below with this
//            paramsMap = [
//                    q         : "idxtype:REGIONFEATURED",
//                    cursorMark: "*", // gets updated by subsequent searches
//                    fl        : "id,idxtype,guid,bbg_name_s", // will restrict results to docs with these fields (bit like fq)
//                    rows      : pageSize,
//                    sort      : "id asc", // needed for cursor searching
//                    wt        : "json"
//            ]
//        }
//        try {
//            if (forRegionFeatured) {
//                clearFieldValues("occurrenceCount", "idxtype:REGIONFEATURED", online)
//            } else {
//                clearFieldValues("occurrenceCount", "idxtype:TAXON", online)
//
//                if (grailsApplication.config?.additionalOccurrenceCountsJSON) {
//                    def jsonSlurper = new JsonSlurper()
//                    def AdditionalOccStats = jsonSlurper.parseText(grailsApplication.config?.additionalOccurrenceCountsJSON ?: "[]")
//                    AdditionalOccStats.each {
//                        log.info("it.solrfield = " + it.solrfield)
//                        clearFieldValues(it.solrfield, "idxtype:TAXON", online)
//                    }
//                }
//
//            }
//        } catch (Exception ex) {
//            log.warn "Error clearing occurrenceCounts: ${ex.message}", ex
//        }
//
//
//        // first get a count of results so we can determine number of pages to process
//        Map countMap = paramsMap.clone(); // shallow clone is OK
//        countMap.rows = 0
//        countMap.remove("cursorMark")
//        def searchCount = searchService.getCursorSearchResults(new MapSolrParams(countMap), !online)
//        // could throw exception
//        def totalDocs = searchCount?.response?.numFound ?: 0
//        int totalPages = (totalDocs + pageSize - 1) / pageSize
//        if (!forRegionFeatured) {
//            log.debug "totalDocs = ${totalDocs} || totalPages = ${totalPages}"
//            log("Processing " + String.format("%,d", totalDocs) + " taxa (via ${paramsMap.q})...<br>")
//            // send to browser
//        } else {
//            log.debug "Featured Region - totalDocs = ${totalDocs} || totalPages = ${totalPages}"
//            log("Processing " + String.format("%,d", totalDocs) + " places (via ${paramsMap.q})...<br>")
//            // send to browser
//        }
//
//        def promiseList = new PromiseList() // for biocache queries
//        Queue commitQueue = new ConcurrentLinkedQueue()  // queue to put docs to be indexes
//        ExecutorService executor = Executors.newSingleThreadExecutor() // consumer of queue - single blocking thread
//        executor.execute {
//            indexDocInQueue(commitQueue, "initialised", online)
//            // will keep polling the queue until terminated via cancel()
//        }
//
//        // iterate over pages
//        (1..totalPages).each { page ->
//            try {
//                MapSolrParams solrParams = new MapSolrParams(paramsMap)
//                log.debug "${page}. paramsMap = ${paramsMap}"
//                def searchResults = searchService.getCursorSearchResults(solrParams, !online)
//                // use offline or online index to search
//                def resultsDocs = searchResults?.response?.docs ?: []
//
//
//                // buckets to group results into
//                def taxaLocatedInHubCountry = []  // automatically get included
//                def taxaToSearchOccurrences = []  // need to search biocache to see if they are located in hub country
//                def placesToSearchOccurrences = []
//
//                if (!forRegionFeatured) {
//                    // iterate over the result set
//                    resultsDocs.each { doc ->
//                        if (nationalSpeciesDatasets && nationalSpeciesDatasets.contains(doc.datasetID)) {
//                            taxaLocatedInHubCountry.add(doc)
//                            // in national list so _assume_ it is located in host/hub county
//                        } else {
//                            taxaToSearchOccurrences.add(doc)
//                            // search occurrence records to determine if it is located in host/hub county
//                        }
//                    }
//                    log("${page}. taxaLocatedInHubCountry = ${taxaLocatedInHubCountry.size()} | taxaToSearchOccurrences = ${taxaToSearchOccurrences.size()}")
//                    // update national list without occurrence record lookup
//                    updateTaxaWithLocationInfo(taxaLocatedInHubCountry, commitQueue)
//                    // update the rest via occurrence search (non blocking via promiseList)
//                    promiseList << { searchOccurrencesWithGuids(resultsDocs, commitQueue) }
//                } else {
//                    // iterate over the result set
//                    resultsDocs.each { doc ->
//                        placesToSearchOccurrences.add(doc) // count occurrence records
//                    }
//                    promiseList << { searchOccurrencesWithSampledPlace(resultsDocs, commitQueue) }
//                    log("${page}. placesToSearchOccurrences = ${placesToSearchOccurrences.size()}")
//                }
//
//                // update cursor
//                paramsMap.cursorMark = searchResults?.nextCursorMark ?: ""
//                // update view via via JS
//                updateProgressBar(totalPages, page)
//
//            } catch (Exception ex) {
//                log.warn "Error calling BIE SOLR: ${ex.message}", ex
//                log("ERROR calling SOLR: ${ex.message}")
//            }
//        }
//
//        log("Waiting for all occurrence searches and SOLR commits to finish (could take some time)")
//
//        //promiseList.get() // block until all promises are complete
//        promiseList.onComplete { List results ->
//            //executor.shutdownNow()
//            isKeepIndexing = false // stop indexing thread
//            executor.shutdown()
//            if (!forRegionFeatured) {
//                log("Total taxa found with occurrence records = ${results.sum()}")
//            } else {
//                log("Total places found with occurrence records = ${results.sum()}")
//            }
//            log("waiting for indexing to finish...")
//        }
    }

    /**
     * Helper method to do a HTTP GET and return String content
     *
     * @param url
     * @return
     */
    private String getStringForUrl(String url) throws IOException {
        String output = ""
        def inStm = new URL(Encoder.encodeUrl(url)).openStream()
        try {
            output = IOUtils.toString(inStm)
        } finally {
            IOUtils.closeQuietly(inStm)
        }
        output
    }
}
