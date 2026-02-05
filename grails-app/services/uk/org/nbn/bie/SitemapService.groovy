package uk.org.nbn.bie

import groovy.xml.MarkupBuilder
import org.apache.solr.client.solrj.SolrQuery

/**
 * Generates /sitemap.xml and /sitemapN.xml from the TAXON index.
 *
 * Note:
 * - Uses grails.serverURL as the base URL so ala-bie-hub / nbn-bie can proxy and replace it.
 * - Uses in-memory caching with TTL to avoid hammering Solr.
 */
class SitemapService {

    def grailsApplication
    def liveSolrClient

    private static class CacheEntry {
        String xml
        long expiresAt

        boolean valid() { System.currentTimeMillis() < expiresAt }
    }

    private final Object lock = new Object()
    private volatile CacheEntry indexCache
    private final Map<Integer, CacheEntry> chunkCache = [:].asSynchronized()

    boolean enabled() {
        grailsApplication?.config?.sitemap?.enabled != false
    }

    int chunkSize() {
        (grailsApplication?.config?.sitemap?.chunkSize ?: 45000) as int
    }

    long cacheTtlMs() {
        ((grailsApplication?.config?.sitemap?.cacheTtlMs ?: (6 * 60 * 60 * 1000L)) as long) // 6h
    }

    String baseUrl() {
        // IMPORTANT: must be the WS base (species-ws). nbn-bie will replace this with its UI base.
        grailsApplication?.config?.grails?.serverURL ?: ""
    }

    String renderIndexXml() {
        if (!enabled()) return emptySitemapIndexXml()

        def cached = indexCache
        if (cached?.valid()) return cached.xml

        synchronized (lock) {
            cached = indexCache
            if (cached?.valid()) return cached.xml

            int total = totalTaxa()
            int chunks = Math.max(1, (int) Math.ceil(total / (double) chunkSize()))
            String xml = buildSitemapIndexXml(chunks)

            indexCache = new CacheEntry(xml: xml, expiresAt: System.currentTimeMillis() + cacheTtlMs())
            return xml
        }
    }

    String renderChunkXml(int idx) {
        if (!enabled()) return emptyUrlSetXml()
        if (idx < 1) return emptyUrlSetXml()

        CacheEntry cached = chunkCache.get(idx)
        if (cached?.valid()) return cached.xml

        int start = (idx - 1) * chunkSize()

        // Query Solr for taxon docs
        def q = new SolrQuery("idxtype:TAXON")
        q.setStart(start)
        q.setRows(chunkSize())
        q.setFields("guid")
        q.setSort("guid", SolrQuery.ORDER.asc)

        def rsp = liveSolrClient.query(q)
        def docs = rsp?.results ?: []

        String xml = buildUrlSetXml(docs)

        chunkCache.put(idx, new CacheEntry(xml: xml, expiresAt: System.currentTimeMillis() + cacheTtlMs()))
        return xml
    }

    private int totalTaxa() {
        def q = new SolrQuery("idxtype:TAXON")
        q.setRows(0)
        def rsp = liveSolrClient.query(q)
        return (rsp?.results?.numFound ?: 0) as int
    }

    private String buildSitemapIndexXml(int chunks) {
        def sw = new StringWriter()
        def mkp = new MarkupBuilder(sw)
        mkp.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")

        mkp.sitemapindex(xmlns: "http://www.sitemaps.org/schemas/sitemap/0.9") {
            (1..chunks).each { int i ->
                sitemap {
                    loc("${baseUrl()}/sitemap${i}.xml")
                }
            }
        }
        sw.toString()
    }

    private String buildUrlSetXml(def docs) {
        def sw = new StringWriter()
        def mkp = new MarkupBuilder(sw)
        mkp.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")

        mkp.urlset(xmlns: "http://www.sitemaps.org/schemas/sitemap/0.9") {
            docs.each { d ->
                def guid = d.getFieldValue("guid")?.toString()
                if (guid) {
                    url {
                        // IMPORTANT: point to WS species path; nbn-bie will replace WS base with UI base.
                        loc("${baseUrl()}/species/${urlEncodePathSegment(guid)}")
                    }
                }
            }
        }
        sw.toString()
    }

    private String emptySitemapIndexXml() {
        def sw = new StringWriter()
        def mkp = new MarkupBuilder(sw)
        mkp.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")
        mkp.sitemapindex(xmlns: "http://www.sitemaps.org/schemas/sitemap/0.9") {}
        sw.toString()
    }

    private String emptyUrlSetXml() {
        def sw = new StringWriter()
        def mkp = new MarkupBuilder(sw)
        mkp.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")
        mkp.urlset(xmlns: "http://www.sitemaps.org/schemas/sitemap/0.9") {}
        sw.toString()
    }

    private String urlEncodePathSegment(String s) {
        return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
