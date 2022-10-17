package uk.org.nbn.bie

import grails.test.mixin.TestFor
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(ImportService)
class ImportServiceSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    void "test buildNameComplete if nameComplete provided, nomenclaturalStatus is not added"() {
        expect:
        def nameComplete = service.buildNameComplete("nameComplete", "scientificName", "scientificNameAuthorship", "nomenclaturalStatus")
        nameComplete == "nameComplete"
    }

    void "test buildNameComplete if nameComplete not provided but scientificNameAuthorship is, nomenclaturalStatus is added"() {
        expect:
        def nameComplete = service.buildNameComplete("", "scientificName", "scientificNameAuthorship", "nomenclaturalStatus")
        nameComplete == "scientificName scientificNameAuthorship nomenclaturalStatus"
    }

    void "test buildNameComplete if nameComplete, nomenclaturalStatus not provided, scientificNameAuthorship is"() {
        expect:
        def nameComplete = service.buildNameComplete("", "scientificName", "scientificNameAuthorship", null)
        nameComplete == "scientificName scientificNameAuthorship"
    }

    void "test buildNameComplete if nameComplete, scientificNameAuthorship not provided, nomenclaturalStatus is"() {
        expect:
        def nameComplete = service.buildNameComplete("", "scientificName", "", "nomenclaturalStatus")
        nameComplete == "scientificName nomenclaturalStatus"
    }

    void "test buildNameComplete if nameComplete, scientificNameAuthorship, nomenclaturalStatus not provided, result is scientificName"() {
        expect:
        def nameComplete = service.buildNameComplete("", "scientificName", "", null)
        nameComplete == "scientificName"
    }

    void "test buildNameFormatted if nameFormatted provided"() {
        expect:
        def nameFormatted = service.buildNameFormatted("nameFormatted", "nameComplete", "scientificName",
                 "",  "rank", [:],  "nomenclaturalStatus")
        nameFormatted == "nameFormatted"
    }

    void "test buildNameFormatted if nameComplete, nomenclaturalStatus provided"() {
        expect:
        def nameFormatted = service.buildNameFormatted("", "nameComplete", "scientificName",
                "",  "rank", [:],  "nomenclaturalStatus")
        nameFormatted.indexOf("nomenclaturalStatus")<0
    }

    void "test buildNameFormatted if scientificNameAuthorship, nomenclaturalStatus provided"() {
        expect:
        def nameFormatted = service.buildNameFormatted("", "", "scientificName",
                "scientificNameAuthorship",  "rank", [:],  "nomenclaturalStatus")
        nameFormatted.endsWith("<span class=\"author\">scientificNameAuthorship nomenclaturalStatus</span></span>")
    }

    void "test buildNameFormatted if scientificName, nomenclaturalStatus provided"() {
        expect:
        def nameFormatted = service.buildNameFormatted("", "", "scientificName",
                "",  "rank", [:],  "nomenclaturalStatus")
        nameFormatted.endsWith("<span class=\"author\">nomenclaturalStatus</span></span>")
    }

    void "test nbnDenormaliseEntry"() {
        setup:
        service.searchService = Mock(SearchService)
        service.searchService.lookupSynonyms(_,_) >> [[scientificName:"scientificName1",nameComplete:"nameComplete1"],
                                                    [scientificName:"scientificName2",nameComplete:"nameComplete2"]]
        def update = [:]

        when:
        service.nbnDenormaliseEntry("guid", update, false)

        then:
//        update["synonym"] == [set:["scientificName1","scientificName2"]]
//        update["synonymComplete"] == [set:["nameComplete1","nameComplete2"]]
        update["synonym"]["set"][0]=="scientificName1"
        update["synonym"]["set"][1]=="scientificName2"
        update["synonymComplete"]["set"][0]=="nameComplete1"
        update["synonymComplete"]["set"][1]=="nameComplete2"

    }

}
