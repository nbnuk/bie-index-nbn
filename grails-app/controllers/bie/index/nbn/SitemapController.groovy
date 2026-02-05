package bie.index.nbn

class SitemapController {

    def sitemapService

    /**
     * /sitemap.xml -> index
     * /sitemap1.xml -> chunk 1
     * /sitemap2.xml -> chunk 2
     */
    def index(Integer idx) {
        if (!sitemapService.enabled()) {
            response.status = 404
            return
        }

        response.contentType = "application/xml"
        if (idx == null) {
            render text: sitemapService.renderIndexXml(), contentType: "application/xml", encoding: "UTF-8"
        } else {
            render text: sitemapService.renderChunkXml(idx), contentType: "application/xml", encoding: "UTF-8"
        }
    }
}
