package uk.org.nbn.bie


import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.QueryResponse

class IndexService extends au.org.ala.bie.IndexService {

    /**
     * Make a query to the index
     *
     * @param query The query
     * @param online Use the online/offline index
     *
     * @return The solr, which are essentially key->value maps
     */
    @Override
    QueryResponse query(SolrQuery query, boolean online) {
        //The nbn convention is, if an flmit is required, we add it to the end of the list of facets
        //like this flimit:-1
        def lastFacetField = query.facetFields?.last()
        if (lastFacetField && lastFacetField.contains("flimit:")) {
            def flimit = lastFacetField.split(":")[1]
            query.removeFacetField(lastFacetField)
            query.setFacetLimit(flimit.toInteger())
        }

        def client = online ? liveSolrClient : offlineSolrClient

        return client.query(query)
    }
}
