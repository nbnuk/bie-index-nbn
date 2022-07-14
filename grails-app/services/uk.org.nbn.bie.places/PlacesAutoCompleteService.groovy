package uk.org.nbn.bie.places

import au.org.ala.bie.search.AutoCompleteDTO
import au.org.ala.bie.util.Encoder
import grails.converters.JSON
import groovy.json.JsonSlurper
import org.apache.solr.client.solrj.util.ClientUtils
import uk.org.nbn.bie.search.PlacesAutoCompleteDTO

import java.util.regex.Pattern

/**
 * Provides auto complete services - this is the code from pre FFTF AutoCompleteService.
 * Its what is used by BBG
 */
class PlacesAutoCompleteService{


    List autoSuggest(String q, List otherParams){

        log.debug("auto called with q = " + q)

        def autoCompleteList = []

        List query = []
        if (!q || q.trim() == "*") {
            query << "q=*:*"
        } else if (q) {
            // encode query (no fields needed due to qf params
            query << "q=" + URLEncoder.encode("" + q + "","UTF-8")
        }
        query << "wt=json"
        query.addAll(otherParams)

        log.debug "autocomplete query = ${query}"

        String queryUrl
//        if (otherParams[0]?.contains("REGIONFEATURED")) {
            queryUrl = grailsApplication.config.indexLiveBaseUrl + "/suggest_region_featured?" + query.join('&')
//        } else {
//            queryUrl = grailsApplication.config.indexLiveBaseUrl + "/suggest?" + query.join('&')
//        }
        log.debug "queryUrl = |${queryUrl}|"
        def queryResponse = new URL(Encoder.encodeUrl(queryUrl)).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)


//        if (otherParams[0]?.contains("REGIONFEATURED")) {
            json.grouped.bbg_name_s.groups.each { group ->
                autoCompleteList << createRegionFeaturedAutoCompleteFromIndex(group.doclist.docs[0], q)
//            }
//        } else {
//            json.grouped.scientificName_s.groups.each { group ->
//                autoCompleteList << createAutoCompleteFromIndex(group.doclist.docs[0], q)
//            }
        }
        log.debug("results: " + autoCompleteList.size())
        autoCompleteList
    }

    /**
     * Creates an auto complete DTO from the supplied result for featured regions.
     * @param qr
     * @param doc
     * @param value
     * @return
     */
    private def createRegionFeaturedAutoCompleteFromIndex(Map doc, String value){
        log.debug "doc = ${doc as JSON}"
        def autoDto = new PlacesAutoCompleteDTO();
        autoDto.guid = doc.guid
        autoDto.name = doc.bbg_name_s

        List<String> matchedNames = [] // temp list to stored matched names

        String[] name1 = new String[0];
        Object o = doc.get("bbg_name_s");
        if(o != null){
            name1 = (String)o
        }

        ArrayList<String> regionNames = new ArrayList<String>();
        regionNames.add(name1);


        String nc = doc.get("bbg_name_s")
        if (nc != null) {
            regionNames.add(nc);
            autoDto.setRegionFeaturedMatches(getHighlightedNames([nc], value, "<b>", "</b>"));
        }

        if (regionNames) {
            matchedNames.addAll(getHighlightedNames(regionNames, value, "", ""));
        } else if (doc.doc_name) {
            matchedNames.addAll(getHighlightedNames(doc.doc_name, value, "", ""));
        }


        if(!matchedNames){
            matchedNames << autoDto.name
        }

        autoDto.matchedNames = matchedNames

        autoDto
    }

    /**
     * Applies a prefix and suffix to higlight the search terms in the
     * supplied list.
     *
     * NC: This is a workaround as I can not get SOLR highlighting to work for partial term matches.
     *
     * @param names
     * @param m
     * @return
     */
    private List<String> getHighlightedNames(names, java.util.regex.Matcher m, String prefix, String suffix){
        LinkedHashSet<String> hlnames = null;
        List<String> lnames = null;
        if(names != null){
            hlnames = new LinkedHashSet<String>();
            for(String name : names){
                String name1 = concatName(name.trim());
                m.reset(name1);
                if(m.find()){
                    //insert <b> and </b>at the start and end index
                    name = name.substring(0, m.start()) + prefix + name.substring(m.start(), m.end()) +
                            suffix + name.substring(m.end(), name.length());
                    hlnames.add(name);
                }
            }
            if(!hlnames.isEmpty()){
                lnames = new ArrayList<String>(hlnames);
                Collections.sort(lnames);
            } else {
                lnames = new ArrayList<String>();
            }
        }
        return lnames;
    }

    /**
     * if word highlight enabled then do the exact match, otherwise do the concat match
     *
     * @param names
     * @param term
     * @param prefix
     * @param suffix
     * @return
     */
    private List<String> getHighlightedNames(List<String> names, String term, String prefix, String suffix){
        LinkedHashSet<String> hlnames =null;
        List<String> lnames = null;
        String value = null;
        boolean isHighlight = false;

        //have word highlight
        if(prefix != null && suffix != null && prefix.trim().length() > 0 && suffix.trim().length() > 0 && term != null){
            value = cleanName(term.trim());
            isHighlight = true;
        } else {
            value = concatName(term);
        }

        Pattern p = Pattern.compile(value, Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(value);
        if(names != null){
            hlnames = new LinkedHashSet<String>();
            for(String name : names){
                String name1 = null;
                name = name.trim();
                if(isHighlight){
                    name1 = name;
                } else {
                    name1 = concatName(name);
                }
                m.reset(name1);
                if(m.find()){
                    //insert <b> and </b>at the start and end index
                    name = name.substring(0, m.start()) + prefix + name.substring(m.start(), m.end()) + suffix + name.substring(m.end(), name.length());
                    hlnames.add(name);
                }
            }
            if(!hlnames.isEmpty()){
                lnames = new ArrayList<String>(hlnames);
                Collections.sort(lnames);
            } else {
                lnames = new ArrayList<String>();
            }
        }
        return lnames;
    }

    private static String concatName(String name){
        String patternA = "[^a-zA-Z]";
        /* replace multiple whitespaces between words with single blank */
        String patternB = "\\b\\s{2,}\\b";

        String cleanQuery = "";
        if(name != null){
            cleanQuery = ClientUtils.escapeQueryChars(name);//.toLowerCase();
            cleanQuery = cleanQuery.toLowerCase();
            cleanQuery = cleanQuery.replaceAll(patternA, "");
            cleanQuery = cleanQuery.replaceAll(patternB, "");
            cleanQuery = cleanQuery.trim();
        }
        cleanQuery
    }

    private static String cleanName(String name){
        String patternA = "[^a-zA-Z]";
        /* replace multiple whitespaces between words with single blank */
        String patternB = "\\b\\s{2,}\\b";

        String cleanQuery = "";
        if(name != null){
            cleanQuery = ClientUtils.escapeQueryChars(name);//.toLowerCase();
            cleanQuery = cleanQuery.toLowerCase();
            cleanQuery = cleanQuery.replaceAll(patternA, " ");
            cleanQuery = cleanQuery.replaceAll(patternB, " ");
            cleanQuery = cleanQuery.trim();
        }
        cleanQuery
    }
}