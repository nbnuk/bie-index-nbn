package uk.org.nbn.bie.places

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.util.Encoder
import grails.transaction.Transactional
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
}
