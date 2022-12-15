package uk.org.nbn.bie

import grails.test.mixin.TestFor
import org.aspectj.asm.IModelFilter
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(SearchService)
class SearchServiceSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }
//ac.put(cl.label, [dr: cl.uid, status: cs])
    void "test getTaxonExtra"() {
        setup:
        def model = [:]
        model.conservationStatuses = ["label1": [dr: "cl.uid", status: "cs"],
                                      "": [dr: "cl.uid2", status: "cs2"],
                                      "label3": [dr: "cl.uid3", status: "cs3"]]
        model.synonyms = [[name:"woof",nameComplete:"dog"],
                          [name:"grrr",nameComplete:"bull"],
                          [name:"meow",nameComplete:"cat"]]

        when:
        service.getTaxonExtra(model)

        then:
        model.conservationStatuses.size() == 2
        model.conservationStatuses["label1"]
        model.conservationStatuses["label3"]

        model.synonyms[0].nameComplete == "bull"
        model.synonyms[1].nameComplete == "cat"
        model.synonyms[2].nameComplete == "dog"
    }
}
