package uk.org.nbn.bie

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.util.Encoder
import groovy.json.JsonSlurper
import java.util.zip.GZIPInputStream

class ImportService extends au.org.ala.bie.ImportService{

    def importFeaturedRegions() {
        log "Starting featured regions import"
        String[] regionFeaturedIds
        if(grailsApplication.config.regionFeaturedLayerIds) {
            regionFeaturedIds = grailsApplication.config.regionFeaturedLayerIds.split(',')
            log("Featured regions = " + regionFeaturedIds.toString())
        }
        def js = new JsonSlurper()
        def layers = js.parseText(new URL(Encoder.encodeUrl(grailsApplication.config.layersServicesUrl + "/layers")).getText("UTF-8"))
        layers.each { layer ->
            if (layer.type == "Contextual" && layer.enabled.toBoolean()) {
                importFeaturedRegionLayer(layer)
            }
        }
        log "Finished indexing ${layers.size()} featured region layers"
        log "Finished featured regions import"
    }







        @Override
    protected def importLayer(layer) {
        if (!layer.enabled.toBoolean()) {
            return false;
        }

        super.importLayer(layer);
    }

    private def importFeaturedRegionLayer(layer) {
        if (!isFeaturedRegionLayer(layer))
            return
        log("Loading regions from layer " + layer.name + " (" + layer.id + ")")

        def tempFilePath = "/tmp/objects_${layer.id}.csv.gz"
        def url = grailsApplication.config.layersServicesUrl + "/objects/csv/cl" + layer.id
        def file = new File(tempFilePath).newOutputStream()
        file << new URL(Encoder.encodeUrl(url)).openStream()
        file.flush()
        file.close()

        def featuredDynamicFields = buildFeaturedDynamicFields(layer)

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

                    def doc2 = doc.findAll {it.key != "idxtype"}
                    doc2["idxtype"] = IndexDocType.REGIONFEATURED.name()
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
    }



    protected def isFeaturedRegionLayer(layer) {
        String[] regionFeaturedIds = grailsApplication.config.regionFeaturedLayerIds?
                grailsApplication.config.regionFeaturedLayerIds.split(','):[];
        return regionFeaturedIds.contains(layer.id.toString())
    }

    protected buildFeaturedDynamicFields(layer) {
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
        return featuredDynamicFields;
    }


    //This is legacy (pre FFTF) importOccurrenceData. It was customised for BBG. Before FFTF, BBG stopped
    //requiring this method. If they want it back again, then either uncomment this out (may need a little
    //work and retrieval of the method clearFieldValues obtained from the legacy fork) or reimplement using the new
    //ALA version as an example
    def importOccurrenceDataForPlaces(Boolean online = false, Boolean forRegionFeatured = true) throws Exception {
        throw new UnsupportedOperationException()
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
}
