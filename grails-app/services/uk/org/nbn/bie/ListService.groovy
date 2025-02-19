package uk.org.nbn.bie

import au.org.ala.bie.util.Encoder
import groovy.json.JsonSlurper

/**
 * Interface to the list servers
 */
class ListService extends au.org.ala.bie.ListService {

    /**
     * Get the details of a list
     *
     * @param uid The list UID
     */
    def getInfo(uid, max = 10000, offset = 0) {
        def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.items, uid, max, offset)
        def slurper = new JsonSlurper()
        def json = slurper.parseText(url.getText('UTF-8'))
        return json;
    }
}
