package uk.org.nbn.bie

//import grails.test.mixin.integration.Integration
import groovy.json.JsonSlurper
import spock.lang.Specification

//@Integration
class IndexComparisonTest extends Specification{

    def INDEX_LIVE_BASE_URL="http://localhost:8984/solr/bie-offline"
    def INDEX_OFFLINE_BASE_URL="http://localhost:8985/solr/bie-offline"
//    def INDEX_LIVE_BASE_URL=http://localhost:8984/solr/bie
//    def INDEX_OFFLINE_BASE_URL=http://localhost:8984/solr/bie-offline


    def grailsApplication

    def setup() {

    }

    def cleanup() {
    }

    void "test total number of docs is the same"() {
        setup:
        def queryString = "/select?wt=json&q=*:*&rows=0"

        when:
        def js = new JsonSlurper()
        def indexLive = js.parseText(solrIndexAQuery(queryString))
        def indexOffline = js.parseText(solrIndexBQuery(queryString))

        then:
        indexLive.response.numFound > 0
        indexLive.response.numFound == indexOffline.response.numFound

    }

    void "test each idxtype count"() {
        setup:
        def queryString = "/select?q=*%3A*&rows=0&wt=json&indent=true&facet=true&facet.field=idxtype"

        int i = 0;
        def js = new JsonSlurper()
        def indexLive = js.parseText(solrIndexAQuery(queryString))
        Map<String,Integer> live = new HashMap();
        while (i < indexLive.facet_counts.facet_fields.idxtype.size()-1){
            String idxtype = indexLive.facet_counts.facet_fields.idxtype.get(i++);
            Integer count = indexLive.facet_counts.facet_fields.idxtype.get(i++);
            live.put(idxtype,count)
        }
        println("live idxtype")
        println(live)

        i = 0;
        def indexOffline = js.parseText(solrIndexBQuery(queryString))
        Map<String,Integer> offline = new HashMap();
        while (i < indexOffline.facet_counts.facet_fields.idxtype.size()-1){
            String idxtype = indexOffline.facet_counts.facet_fields.idxtype.get(i++);
            Integer count = indexOffline.facet_counts.facet_fields.idxtype.get(i++);
            offline.put(idxtype,count)
        }
        println("offline idxtype")
        println(offline)

        println("** NOTE: if number is different, check with registry for current number of DataResources etc")
        println("** https://registry.nbnatlas.org/reports/list")
        expect:
        live.size() == offline.size()
        live.each{
            assert offline.get(it.key) == it.value
        }

        offline.each{
            assert live.get(it.key) == it.value
        }

    }


    void "test idxtype=TAXON docs"() {
        setup:
        def queryString = "/select?q=*:*&fq=idxtype%3ATAXON&fl=guid%2CdatasetID%2CparentGuid%2Crank%2CrankID%2CnameComplete%2CnameFormatted&wt=json&indent=true&rows=999"
        def queryStringB = "/select?fq=idxtype%3ATAXON&fl=guid%2CdatasetID%2CparentGuid%2Crank%2CrankID%2CnameComplete%2CnameFormatted&wt=json&indent=true&rows=10"

        when:
        def js = new JsonSlurper()
        def indexLive = js.parseText(solrIndexAQuery(queryString))
        def indexOffline = null

        then:
        indexLive.response.docs.each{ liveDoc ->
            indexOffline = js.parseText(solrIndexBQuery(queryStringB+"&q=guid:"+liveDoc.guid))
            assert indexOffline.response.numFound == 1
            def offlineDoc = indexOffline.response.docs[0]
            assert offlineDoc.datasetID == liveDoc.datasetID
            assert offlineDoc.parentGuid == liveDoc.parentGuid
            assert offlineDoc.rank == liveDoc.rank
            assert offlineDoc.rankID == liveDoc.rankID
            assert offlineDoc.nameComplete == liveDoc.nameComplete
            assert offlineDoc.nameFormatted == liveDoc.nameFormatted
        }

    }

    void "test idxtype=COMMON taxonGuid counts"() {
        setup:
        def queryString = "/select?q=*%3A*&fq=idxtype%3ACOMMON&rows=0&fl=taxonGuid&wt=json&indent=true&facet=true&facet.field=taxonGuid"
        def queryStringB = "/select?fq=idxtype%3ACOMMON&rows=0&wt=json&indent=true&facet=true"

        when:
        def js = new JsonSlurper()
        def indexLive = js.parseText(solrIndexAQuery(queryString))
        def indexOffline
        int i = 0;
        List<String> guids = new ArrayList();
        List<Integer> counts = new ArrayList();
        while (i < indexLive.facet_counts.facet_fields.taxonGuid.size()){
            guids.add(indexLive.facet_counts.facet_fields.taxonGuid.get(i++))
            counts.add(indexLive.facet_counts.facet_fields.taxonGuid.get(i++))
        }


        then:
        def j = 0
        guids.each{
            indexOffline = js.parseText(solrIndexBQuery(queryStringB+"&q=taxonGuid:"+it))
            assert indexOffline.response.numFound == counts.get(j++)
        }

    }

    void "test idxtype=COMMON docs"() {
        setup:
        def guidQueryString = "/select?q=*%3A*&fq=idxtype%3ACOMMON&rows=0&fl=taxonGuid&wt=json&indent=true&facet=true&facet.field=taxonGuid"
        def queryString = "/select?fq=idxtype%3ACOMMON&fl=taxonGuid%2Cname&wt=json&indent=true&rows=999"

        def js = new JsonSlurper()
        def indexLive = js.parseText(solrIndexAQuery(guidQueryString))
        def indexOffline
        int i = 0;
        List<String> guids = new ArrayList();
        Map<String, List<String>> guidNameMap = new HashMap();
        while (i < indexLive.facet_counts.facet_fields.taxonGuid.size()){
            guids.add(indexLive.facet_counts.facet_fields.taxonGuid.get(i++))
        }

        guids.each{ taxonGuid ->
            List<String> names = new ArrayList()
            indexLive = js.parseText(solrIndexAQuery(queryString+"&q=taxonGuid:"+taxonGuid))
            indexLive.response.docs.each{doc ->
                names.add(doc.name)
            }
            guidNameMap.put(taxonGuid, names)
        }



        expect:
        def j = 0
        guidNameMap.each{
            indexOffline = js.parseText(solrIndexBQuery(queryString+"&q=taxonGuid:"+it.key))
            indexOffline.response.docs.each{doc ->
                assert it.value.contains(doc.name)
            }
        }

    }


    void "test denormalised total"() {
        //WARNING: in upgrade denormalised_b changed to denormalised so this will
        //need to be fixed post upgrade
        setup:
        def queryString = "/select?q=denormalised_b%3Atrue&rows=0&wt=json&indent=true"
        def queryStringUpgrade = "/select?q=denormalised%3Atrue&rows=0&wt=json&indent=true"

        when:
        def js = new JsonSlurper()
        def indexLive = js.parseText(solrIndexAQuery(queryString))
        def indexOffline = js.parseText(solrIndexBQuery(queryStringUpgrade))

        then:
        indexLive.response.numFound ==  indexOffline.response.numFound

    }

    void "test denormalised TAXON docs"() {
        //WARNING: in upgrade denormalised_b changed to denormalised so this will
        //need to be fixed post upgrade
        setup:
        def queryString = "/select?q=denormalised_b%3Atrue&fq=idxtype%3ATAXON&rows=999&fl=guid%2CspeciesGroup%2CspeciesSubgroup%2Csynonym%2CsynonymComplete%2Cdenormalised_b&wt=json&indent=true"
        def queryStringB = "/select?fq=idxtype%3ATAXON&rows=1&fl=speciesGroup%2CspeciesSubgroup%2Csynonym%2CsynonymComplete%2Cdenormalised&wt=json&indent=true"

        when:
        def js = new JsonSlurper()
        def indexLive = js.parseText(solrIndexAQuery(queryString))
        def indexOffline = null

        then:
        indexLive.response.docs.each{ liveDoc ->
            indexOffline = js.parseText(solrIndexBQuery(queryStringB+"&q=guid:"+liveDoc.guid))
            assert indexOffline.response.numFound == 1
            def offlineDoc = indexOffline.response.docs[0]
            assert offlineDoc.denormalised == liveDoc.denormalised_b
            assert offlineDoc.speciesGroup == liveDoc.speciesGroup
            assert offlineDoc.speciesSubgroup == liveDoc.speciesSubgroup
            assert offlineDoc.synonym == liveDoc.synonym
            assert offlineDoc.synonymComplete == liveDoc.synonymComplete
            assert offlineDoc.speciesGroup == liveDoc.speciesGroup
        }

    }


    void "test REGIONFEATURED docs"() {
        setup:
        def queryString = "/select?q=*%3A*&fq=idxtype%3AREGIONFEATURED&rows=999&fl=id%2Cbbg_unique_s%2Cdescription%2Cparish_nam_s%2Cconsecrate_s%2Cassetid_s%2Clongitude%2Ccountry_s&wt=json&indent=true"
        def queryStringB = "/select?fq=idxtype%3AREGIONFEATURED&rows=1&fl=id%2Cbbg_unique_s%2Cdescription%2Cparish_nam_s%2Cconsecrate_s%2Cassetid_s%2Clongitude%2Ccountry_s&wt=json&indent=true"

        when:
        def js = new JsonSlurper()
        def indexLive = js.parseText(solrIndexAQuery(queryString))
        def indexOffline = null

        then:
        indexLive.response.docs.each{ liveDoc ->
            indexOffline = js.parseText(solrIndexBQuery(queryStringB+"&q=id:"+liveDoc.id))
            assert indexOffline.response.numFound == 1
            def offlineDoc = indexOffline.response.docs[0]
            assert offlineDoc.bbg_unique_s == liveDoc.bbg_unique_s
            assert offlineDoc.parish_nam_s == liveDoc.parish_nam_s
            assert offlineDoc.consecrate_s == liveDoc.consecrate_s
            assert offlineDoc.assetid_s == liveDoc.assetid_s
            assert offlineDoc.longitude == liveDoc.longitude
            assert offlineDoc.country_s == liveDoc.country_s
            assert offlineDoc.description == liveDoc.description
        }

    }



    private String solrIndexAQuery(String queryString){
       return  new URL(INDEX_LIVE_BASE_URL+queryString).getText("UTF-8")
    }

    private String solrIndexBQuery(String queryString){
        return  new URL(INDEX_OFFLINE_BASE_URL+queryString).getText("UTF-8")
    }

}
