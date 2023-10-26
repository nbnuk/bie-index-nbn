package uk.org.nbn.bie.util

import groovy.json.JsonSlurper
import org.slf4j.LoggerFactory

/**
 * A source of conservation status information.
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 *
 * @copyright Copyright (c) 2016 CSIRO
 */
class ConservationListsSourceDecorator extends au.org.ala.bie.util.ConservationListsSource {

    def deleteFirst = []

    ConservationListsSourceDecorator(String url) {
        super(url)

        try {
            URL source = this.class.getResource(url)
            if (!source)
                source = new URL(url)
            log.info("Loading conservation lists from ${url} -> ${source}")
            JsonSlurper slurper = new JsonSlurper()
            def config = slurper.parse(source)

            deleteFirst = config?.deleteFirst ?: []

        } catch (Exception ex) {
            log.error("Unable to initialise conservation status source from " + url, ex)
        }

    }
}
